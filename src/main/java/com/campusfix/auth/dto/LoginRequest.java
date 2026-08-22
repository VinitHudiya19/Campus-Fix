package com.campusfix.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * No {@code @Email} or length rules here. Validation messages on a login form
 * would tell an attacker how passwords are shaped, and a legitimate user whose
 * password predates a rule change would be blocked before the check even runs.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password) {
}
