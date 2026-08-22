package com.campusfix.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the signed-in user out of the security context.
 *
 * <p>Wrapped in a bean rather than calling {@code SecurityContextHolder} all over
 * the services: services stay testable, because a test can hand in a stub
 * instead of having to set up a thread-local.
 */
@Component
public class CurrentUser {

    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * For code that runs behind an authenticated endpoint, where a missing user
     * means the security configuration is wrong rather than the request being bad.
     */
    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new IllegalStateException("No authenticated user in the security context"));
    }
}
