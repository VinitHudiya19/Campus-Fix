package com.campusfix.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code campusfix.jwt.*} settings.
 *
 * <p>Typed configuration rather than {@code @Value} on scattered fields: the
 * settings are validated once at startup, and a typo in a property name shows up
 * immediately instead of silently injecting a null.
 */
@ConfigurationProperties(prefix = "campusfix.jwt")
public record JwtProperties(String secret, long expiryMinutes) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "campusfix.jwt.secret must be at least 32 characters long for HS256 signing");
        }
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("campusfix.jwt.expiry-minutes must be positive");
        }
    }
}
