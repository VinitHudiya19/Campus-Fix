package com.campusfix.request.event;

/**
 * A request was given to a technician.
 *
 * <p>Carries <strong>ids, not entities</strong>. The listener runs after the
 * transaction commits and on a different thread, where a passed entity would be
 * detached — touching any lazy association on it would throw. Ids force the
 * listener to load what it needs in its own transaction, which is also what
 * makes the event safe to handle late.
 */
public record RequestAssignedEvent(Long requestId, Long technicianId, String assignedByName) {
}
