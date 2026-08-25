package com.campusfix.report;

import java.time.Instant;

/**
 * One fixed request, reduced to the three dates the reports care about: when it
 * arrived, when it was fixed, and when it was supposed to be.
 */
public record ResolutionFact(Long departmentId, Instant createdAt, Instant resolvedAt, Instant dueAt) {

    /** Met its target if it was fixed on or before the deadline. */
    public boolean metTarget() {
        return !resolvedAt.isAfter(dueAt);
    }

    public double hoursTaken() {
        return java.time.Duration.between(createdAt, resolvedAt).toMinutes() / 60.0;
    }
}
