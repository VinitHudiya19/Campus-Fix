package com.campusfix.user.dto;

import com.campusfix.user.Role;
import com.campusfix.user.User;

/**
 * What the API gives back for a user. There is no password field of any kind,
 * so a hash cannot leak through an endpoint by accident — the main reason this
 * project returns DTOs instead of entities.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        String roleLabel,
        Long departmentId,
        String departmentName,
        boolean active) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getRole().getDisplayName(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.isActive());
    }
}
