package com.campusfix.auth.dto;

/**
 * The token, how long it lasts, and who it belongs to.
 *
 * <p>The user is included so the browser can render the correct navigation
 * immediately after login instead of making a second call to {@code /api/auth/me}.
 */
public record LoginResponse(String token, long expiresInSeconds, CurrentUserResponse user) {
}
