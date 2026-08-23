package com.campusfix.sla;

import com.campusfix.request.Priority;
import com.campusfix.request.ServiceRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The SLA settings and the current time, captured once so a whole page of
 * requests can be judged consistently.
 *
 * <p>Without this, rendering twenty rows would either re-read the config table
 * twenty times or call {@code Instant.now()} twenty times, so two rows on the
 * same screen could be measured against slightly different moments.
 */
public record SlaSnapshot(Map<Priority, Integer> warningPercentages, Instant now) {

    public SlaState stateOf(ServiceRequest request) {
        Instant deadline = request.getDueAt();
        Instant finished = request.getResolvedAt() != null ? request.getResolvedAt() : request.getClosedAt();

        // A finished request is judged on when it was finished, not on the clock.
        // Its verdict never changes again.
        if (finished != null) {
            return finished.isAfter(deadline) ? SlaState.MISSED : SlaState.MET;
        }

        if (!now.isBefore(deadline)) {
            return SlaState.BREACHED;
        }

        return now.isBefore(warningPointOf(request)) ? SlaState.ON_TRACK : SlaState.DUE_SOON;
    }

    /**
     * The warning point is a percentage through <em>this request's own</em>
     * window, from when it was reported to when it is due.
     *
     * <p>Deriving it from the stored dates rather than from the current
     * {@code durationHours} matters: if the college shortens the MEDIUM target
     * tomorrow, an old request keeps the window it was actually given.
     */
    private Instant warningPointOf(ServiceRequest request) {
        int percentage = warningPercentages.getOrDefault(request.getPriority(), 75);
        Duration window = Duration.between(request.getCreatedAt(), request.getDueAt());
        return request.getCreatedAt().plus(window.multipliedBy(percentage).dividedBy(100));
    }
}
