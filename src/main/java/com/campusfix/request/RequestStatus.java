package com.campusfix.request;

/**
 * Where a request has got to.
 *
 * <p>The full set is declared here because the database column has to accept all
 * of them, but Phase 6 only ever creates {@link #OPEN}. Which moves are legal —
 * OPEN to ASSIGNED but never OPEN straight to CLOSED — is the subject of Phase 8
 * and is deliberately not encoded yet.
 */
public enum RequestStatus {

    /** Created, nobody is working on it yet. */
    OPEN("Open"),

    /** A technician has been made responsible for it. */
    ASSIGNED("Assigned"),

    /** The technician has started work. */
    IN_PROGRESS("In Progress"),

    /** The technician says it is fixed, awaiting the student's confirmation. */
    RESOLVED("Resolved"),

    /** The student confirmed the fix. Final. */
    CLOSED("Closed"),

    /** The student says it is still broken after it was marked resolved. */
    REOPENED("Reopened"),

    /** Invalid, duplicate or out of scope. Final. */
    REJECTED("Rejected");

    private final String displayName;

    RequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** CLOSED and REJECTED are the two ways a request stops needing attention. */
    public boolean isFinal() {
        return this == CLOSED || this == REJECTED;
    }
}
