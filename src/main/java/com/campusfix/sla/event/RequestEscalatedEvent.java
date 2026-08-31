package com.campusfix.sla.event;

import com.campusfix.sla.EscalationLevel;

/**
 * A late request was pushed up a level.
 *
 * <p>Published by the scheduled check, so there is no signed-in user behind it —
 * the resulting notification has no actor name.
 */
public record RequestEscalatedEvent(Long requestId, EscalationLevel level, String reason) {
}
