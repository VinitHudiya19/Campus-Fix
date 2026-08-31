package com.campusfix.request.event;

import com.campusfix.request.RequestStatus;

/** A request moved from one status to another, and who moved it. */
public record RequestStatusChangedEvent(
        Long requestId,
        RequestStatus from,
        RequestStatus to,
        Long actorId,
        String actorName,
        String note) {
}
