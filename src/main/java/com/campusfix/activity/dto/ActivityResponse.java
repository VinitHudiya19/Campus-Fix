package com.campusfix.activity.dto;

import com.campusfix.activity.ActivityLog;
import com.campusfix.activity.ActivityType;

import java.time.Instant;

public record ActivityResponse(
        Long id,
        ActivityType type,
        String typeLabel,
        String actorName,
        String oldValue,
        String newValue,
        String message,
        Instant createdAt) {

    public static ActivityResponse from(ActivityLog log) {
        return new ActivityResponse(
                log.getId(),
                log.getType(),
                log.getType().getDisplayName(),
                // "System" rather than null, so the timeline never shows a blank author.
                log.getActor() == null ? "System" : log.getActor().getFullName(),
                log.getOldValue(),
                log.getNewValue(),
                log.getMessage(),
                log.getCreatedAt());
    }
}
