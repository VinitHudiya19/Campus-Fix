package com.campusfix.user.dto;

import com.campusfix.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Deliberately has no password and no email. A password change goes through its
 * own endpoint so that an ordinary profile edit can never overwrite a hash by
 * accident, and the email is the login identity, which is not edited casually.
 */
public record UpdateUserRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name cannot be longer than 120 characters")
        String fullName,

        @NotNull(message = "Please choose a role")
        Role role,

        Long departmentId) {
}
