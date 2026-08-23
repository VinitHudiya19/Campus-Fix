package com.campusfix.sla;

/**
 * How far up a late request has been pushed.
 *
 * <p>Two steps, matching the spec: past the deadline it becomes the department
 * head's problem, and if it is still not fixed after a grace period it becomes
 * the administration's.
 */
public enum EscalationLevel {

    DEPARTMENT_HEAD("Department head"),
    ADMIN("Administration");

    private final String displayName;

    EscalationLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
