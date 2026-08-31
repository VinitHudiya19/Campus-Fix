package com.campusfix.notification;

/**
 * The moments worth telling somebody about.
 *
 * <p>Deliberately short. A notification for every change would train people to
 * ignore the bell, so this covers only the points where somebody is now waiting
 * on the recipient, or where their own request moved without them.
 */
public enum NotificationType {

    /** A technician has been given work. */
    ASSIGNED("Assigned to you"),

    /** The reporter's problem has been fixed and needs their confirmation. */
    RESOLVED("Marked as resolved"),

    /** The student says it is still broken; it is back with the technician. */
    REOPENED("Reopened"),

    /** The reporter's request was refused. */
    REJECTED("Rejected"),

    /** The student confirmed the fix — the technician's work is done. */
    CLOSED("Confirmed as fixed"),

    /** A deadline passed and the request has been pushed up a level. */
    ESCALATED("Escalated");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
