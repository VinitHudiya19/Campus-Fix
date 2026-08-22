package com.campusfix.auth;

import com.campusfix.auth.dto.LoginRequest;
import com.campusfix.auth.dto.LoginResponse;
import com.campusfix.common.exception.AccountDisabledException;
import com.campusfix.common.exception.AuthenticationFailedException;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.common.security.JwtProperties;
import com.campusfix.common.security.JwtService;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The security-critical part of the application, so it is tested with a real
 * encoder and a real token service rather than mocks. A mocked encoder would
 * only prove a method was called, not that a wrong password is actually refused.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "admin12345";

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService(new JwtProperties("a-test-signing-key-long-enough-for-hs256", 60));

    private AuthService authService() {
        return new AuthService(userRepository, passwordEncoder, jwtService, new CurrentUser());
    }

    @Test
    void loginReturnsAReadableTokenForTheRightPassword() {
        when(userRepository.findByEmailWithDepartment("admin@campusfix.local"))
                .thenReturn(Optional.of(admin(true)));

        LoginResponse response = authService().login(
                new LoginRequest("  Admin@CampusFix.local  ", PASSWORD));

        assertThat(response.user().email()).isEqualTo("admin@campusfix.local");
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        assertThat(response.expiresInSeconds()).isEqualTo(3600);

        // The token must carry the identity the rest of the app will trust.
        var claims = jwtService.readClaims(response.token()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void anUnknownEmailAndAWrongPasswordFailIdentically() {
        when(userRepository.findByEmailWithDepartment("nobody@college.edu")).thenReturn(Optional.empty());
        when(userRepository.findByEmailWithDepartment("admin@campusfix.local"))
                .thenReturn(Optional.of(admin(true)));

        // Same message either way, so the response cannot be used to discover
        // which email addresses are registered.
        assertThatThrownBy(() -> authService().login(new LoginRequest("nobody@college.edu", PASSWORD)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Email or password is incorrect");

        assertThatThrownBy(() -> authService().login(new LoginRequest("admin@campusfix.local", "wrong-password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Email or password is incorrect");
    }

    @Test
    void aDeactivatedAccountCannotLogInEvenWithTheRightPassword() {
        when(userRepository.findByEmailWithDepartment("admin@campusfix.local"))
                .thenReturn(Optional.of(admin(false)));

        assertThatThrownBy(() -> authService().login(new LoginRequest("admin@campusfix.local", PASSWORD)))
                .isInstanceOf(AccountDisabledException.class)
                .hasMessageContaining("deactivated");
    }

    private User admin(boolean active) {
        User user = new User("CampusFix Administrator", "admin@campusfix.local",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, null);
        ReflectionTestUtils.setField(user, "id", 1L);
        if (!active) {
            user.deactivate();
        }
        return user;
    }
}
