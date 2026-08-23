package com.campusfix.activity;

/**
 * What kind of thing happened to a request.
 *
 * <p>Stored as a type rather than only a sentence so the timeline can be
 * filtered and counted later — "how often are requests reopened?" is a question
 * about data, not about text.
 */
public enum ActivityType {

    REQUEST_CREATED("Reported"),
    ASSIGNED("Assigned"),
    UNASSIGNED("Unassigned"),
    STATUS_CHANGED("Status changed"),

    /** Written by the scheduled SLA check, not by a person. */
    SLA_BREACHED("SLA breached"),
    ESCALATED("Escalated");

    private final String displayName;

    ActivityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
