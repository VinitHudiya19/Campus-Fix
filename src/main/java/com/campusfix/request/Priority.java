package com.campusfix.request;

/**
 * How urgent a request is, and how long it may take before it is late.
 *
 * <p>The hours here are the product defaults from the spec, not a rule of the
 * industry. Phase 9 moves them into a configurable {@code sla_configs} table so
 * a college can set its own targets; keeping them on the enum until then means
 * every request created now already has a real due date, and Phase 9 changes
 * where the number comes from rather than having to backfill it.
 */
public enum Priority {

    LOW("Low", 72, true),
    MEDIUM("Medium", 48, true),
    HIGH("High", 24, true),

    /**
     * Reserved for staff. A student marking everything CRITICAL would make the
     * priority field meaningless within a week.
     */
    CRITICAL("Critical", 4, false);

    private final String displayName;
    private final int slaHours;
    private final boolean studentSelectable;

    Priority(String displayName, int slaHours, boolean studentSelectable) {
        this.displayName = displayName;
        this.slaHours = slaHours;
        this.studentSelectable = studentSelectable;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSlaHours() {
        return slaHours;
    }

    public boolean isStudentSelectable() {
        return studentSelectable;
    }
}
