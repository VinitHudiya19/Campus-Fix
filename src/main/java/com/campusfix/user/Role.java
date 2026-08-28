package com.campusfix.user;

/**
 * The four kinds of people who use CampusFix.
 *
 * <p>This is an enum rather than a {@code roles} table because the set is fixed
 * and the code already branches on it: an administrator cannot invent a fifth
 * role at runtime, since nothing would know what that role is allowed to do.
 * Storing the name directly on the user row also removes a join from every
 * single user lookup.
 */
public enum Role {

    /** Reports problems and follows their own requests. Has no department. */
    STUDENT("Student", false),

    /** Works on requests assigned to them. Belongs to one department. */
    TECHNICIAN("Technician", true),

    /** Assigns work inside their own department and watches its queue. */
    DEPARTMENT_HEAD("Department Head", true),

    /** Manages departments, categories and users across the whole campus. */
    ADMIN("Administrator", false);

    private final String displayName;
    private final boolean departmentRequired;

    Role(String displayName, boolean departmentRequired) {
        this.displayName = displayName;
        this.departmentRequired = departmentRequired;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether a user in this role must be attached to a department. Keeping the
     * answer on the enum means the rule lives in one place instead of being
     * repeated as an if-else chain in the service.
     */
    public boolean isDepartmentRequired() {
        return departmentRequired;
    }
}
