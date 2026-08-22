package com.campusfix.auth.dto;

import com.campusfix.user.Role;
import com.campusfix.user.User;

/** Everything the frontend needs to draw the right menu for whoever is signed in. */
public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        String roleLabel,
        Long departmentId,
        String departmentName) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getRole().getDisplayName(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName());
    }
}
