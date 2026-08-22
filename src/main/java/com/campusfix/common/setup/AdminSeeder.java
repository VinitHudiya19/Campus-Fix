package com.campusfix.common.setup;

import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solves the chicken-and-egg problem: only an admin can create users, so on a
 * brand-new database there would be nobody able to log in and make the first one.
 *
 * <p>Runs on every startup but does nothing once an admin exists, so it is safe
 * to leave in place. It never resets or overwrites an existing account.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        String email = properties.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.warn("Cannot seed the first administrator: {} is already taken by another role", email);
            return;
        }

        userRepository.save(new User(
                "CampusFix Administrator",
                email,
                passwordEncoder.encode(properties.password()),
                Role.ADMIN,
                null));

        log.warn("""

                ==================================================================
                 First administrator created: {}
                 Sign in and change this password before using the app for real.
                ==================================================================
                """, email);
    }
}
