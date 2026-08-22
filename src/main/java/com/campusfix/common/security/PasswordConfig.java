package com.campusfix.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt hashing for stored passwords.
 *
 * <p>BCrypt is used instead of a plain hash such as SHA-256 for two reasons. It
 * salts every password automatically, so two people who pick the same password
 * get different hashes, and it is deliberately slow, which makes guessing
 * millions of passwords per second impractical.
 *
 * <p>The bean is declared here rather than inside the user package so the login
 * code added in Phase 5 can reuse the exact same encoder.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
