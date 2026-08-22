package com.campusfix.auth;

import com.campusfix.auth.dto.ChangeMyPasswordRequest;
import com.campusfix.auth.dto.CurrentUserResponse;
import com.campusfix.auth.dto.LoginRequest;
import com.campusfix.auth.dto.LoginResponse;
import com.campusfix.common.exception.AccountDisabledException;
import com.campusfix.common.exception.AuthenticationFailedException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.common.security.JwtService;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailWithDepartment(email)
                .orElseThrow(() -> {
                    log.debug("Login failed: no account for {}", email);
                    return new AuthenticationFailedException("Email or password is incorrect");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.debug("Login failed: wrong password for {}", email);
            throw new AuthenticationFailedException("Email or password is incorrect");
        }

        if (!user.isActive()) {
            throw new AccountDisabledException("This account has been deactivated. Contact the administrator.");
        }

        log.debug("Login succeeded for {} ({})", email, user.getRole());
        return new LoginResponse(
                jwtService.issueToken(user),
                jwtService.expirySeconds(),
                CurrentUserResponse.from(user));
    }

    /**
     * Reads the signed-in user from the database rather than from the token.
     * The token is a snapshot taken at login; a name or department changed since
     * then would otherwise keep showing the old value until it expired.
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        return CurrentUserResponse.from(loadSignedInUser());
    }

    @Transactional
    public void changeMyPassword(ChangeMyPasswordRequest request) {
        User user = loadSignedInUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Your current password is incorrect");
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private User loadSignedInUser() {
        Long id = currentUser.require().id();
        return userRepository.findByIdWithDepartment(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
