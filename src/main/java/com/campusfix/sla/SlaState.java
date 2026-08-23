package com.campusfix.sla;

/**
 * Where a request stands against its deadline.
 *
 * <p>Never stored on the request. It is a function of the clock: a request that
 * is ON_TRACK now becomes BREACHED later with nothing having changed. Storing it
 * would mean a column that is wrong most of the time, and a job whose only
 * purpose is to keep rewriting it.
 */
public enum SlaState {

    /** Plenty of time left. */
    ON_TRACK("On track"),

    /** Past the warning threshold but not yet late. */
    DUE_SOON("Due soon"),

    /** The deadline has passed and the problem is still not fixed. */
    BREACHED("Breached"),

    /** Finished inside the deadline. Final. */
    MET("Met"),

    /** Finished, but late. Final, and the honest record of a missed target. */
    MISSED("Missed");

    private final String displayName;

    SlaState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
