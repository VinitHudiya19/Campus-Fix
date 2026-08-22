package com.campusfix.request.dto;

import com.campusfix.request.Priority;
import com.campusfix.request.RequestStatus;
import com.campusfix.request.ServiceRequest;

import java.time.Instant;

/**
 * One row of a list screen. Deliberately smaller than the detail response: a
 * page of twenty requests does not need twenty full descriptions, and sending
 * them would multiply the payload for text nobody reads until they click.
 */
public record RequestSummaryResponse(
        Long id,
        String requestNumber,
        String title,
        String categoryName,
        String departmentName,
        String locationName,
        Priority priority,
        String priorityLabel,
        RequestStatus status,
        String statusLabel,
        String studentName,
        Instant dueAt,
        Instant createdAt) {

    public static RequestSummaryResponse from(ServiceRequest request) {
        return new RequestSummaryResponse(
                request.getId(),
                request.getRequestNumber(),
                request.getTitle(),
                request.getCategory().getName(),
                request.getCategory().getDepartment().getName(),
                request.getLocation() == null ? null : request.getLocation().displayName(),
                request.getPriority(),
                request.getPriority().getDisplayName(),
                request.getStatus(),
                request.getStatus().getDisplayName(),
                request.getStudent().getFullName(),
                request.getDueAt(),
                request.getCreatedAt());
    }
}
