package com.campusfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The current password is required as well as the new one. Without that check,
 * anyone who walked up to an unlocked laptop could lock the real owner out.
 */
public record ChangeMyPasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String newPassword) {
}
