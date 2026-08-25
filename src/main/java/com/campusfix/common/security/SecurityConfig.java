package com.campusfix.common.security;

import com.campusfix.common.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Decides which endpoints need a login and which role each one requires.
 *
 * <p>Read the rule list below as the security policy of the whole application.
 * It is deliberately in one place: scattering {@code @PreAuthorize} across
 * controllers makes it impossible to answer "who can reach this?" without
 * opening twenty files.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protects against a browser silently attaching a session
                // cookie to a forged request. This API has no cookies — the token
                // is attached by JavaScript on purpose — so there is nothing for
                // CSRF to protect and the check would only break every POST.
                .csrf(csrf -> csrf.disable())

                // No HttpSession at all. Every request proves itself with its own
                // token, so any instance of the app can serve any request.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public: the login endpoint itself, and the pages the
                        // browser needs before anyone has logged in.
                        .requestMatchers("/api/auth/login", "/api/hello").permitAll()
                        // The pages themselves are public; the data behind them
                        // is not. A browser loading an HTML file cannot send an
                        // Authorization header, and the token deliberately is
                        // not a cookie — so the files have to be reachable and
                        // the JavaScript redirects to the login page if there
                        // is no session. No page contains data of its own.
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()

                        // Reference data: any signed-in user may read it, because
                        // a student needs the category list to report a problem.
                        // Only an admin may change it.
                        .requestMatchers(HttpMethod.GET,
                                "/api/departments/**", "/api/categories/**", "/api/locations/**").authenticated()
                        .requestMatchers("/api/departments/**", "/api/categories/**", "/api/locations/**")
                        .hasRole("ADMIN")

                        // Service requests are open to every signed-in user, but
                        // what each one actually sees is decided by RequestScope
                        // in the service. A URL rule cannot express "your own
                        // requests only", so it must not pretend to.
                        .requestMatchers("/api/requests/**").authenticated()

                        // Any signed-in user may read the SLA targets — a
                        // student is entitled to know what turnaround the
                        // college promises. Changing them is college policy.
                        .requestMatchers(HttpMethod.GET, "/api/sla").authenticated()
                        .requestMatchers("/api/sla/**").hasRole("ADMIN")

                        // Account management is admin-only. The one exception is
                        // the signed-in user's own details, served from /api/auth.
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // Reports are for the people who act on them. A head is
                        // narrowed to their own department inside the service —
                        // a URL rule cannot express "your department only".
                        .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "DEPARTMENT_HEAD")

                        .anyRequest().authenticated())

                .exceptionHandling(handling -> handling
                        // 401: no valid token. 403: valid token, wrong role.
                        // Both are written in the API's own error shape so the
                        // frontend does not need a special case for them.
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(request, response, HttpStatus.UNAUTHORIZED,
                                        "You need to sign in to do that"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(request, response, HttpStatus.FORBIDDEN,
                                        "Your role does not allow that action")))

                // Our filter runs before the username/password filter so that a
                // request arriving with a token is already authenticated by the
                // time any other security check looks at it.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpStatus status,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), message, request.getRequestURI()));
    }
}
