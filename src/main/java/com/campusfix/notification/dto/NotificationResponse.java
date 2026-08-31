package com.campusfix.notification.dto;

import com.campusfix.notification.Notification;
import com.campusfix.notification.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String typeLabel,
        String message,
        Long requestId,
        String requestNumber,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getType().getDisplayName(),
                notification.getMessage(),
                notification.getRequest().getId(),
                notification.getRequest().getRequestNumber(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
