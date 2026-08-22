package com.campusfix.common.security;

import com.campusfix.user.Role;

/**
 * Who is making the current request, rebuilt from the token's claims.
 *
 * <p>This is the principal stored in Spring's security context. It is a plain
 * record with no database behind it, which is the point: authorising a request
 * costs one signature check rather than a query.
 */
public record AuthenticatedUser(Long id, String email, String fullName, Role role, Long departmentId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** True when this user belongs to the given department. Admins do not. */
    public boolean belongsTo(Long otherDepartmentId) {
        return departmentId != null && departmentId.equals(otherDepartmentId);
    }
}
