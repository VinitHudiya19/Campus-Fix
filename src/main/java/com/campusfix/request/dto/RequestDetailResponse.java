package com.campusfix.request.dto;

import com.campusfix.request.Priority;
import com.campusfix.request.RequestStatus;
import com.campusfix.request.ServiceRequest;

import java.time.Instant;

/** The full record, for the page showing one request. */
public record RequestDetailResponse(
        Long id,
        String requestNumber,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        Long departmentId,
        String departmentName,
        Long locationId,
        String locationName,
        Priority priority,
        String priorityLabel,
        RequestStatus status,
        String statusLabel,
        Long studentId,
        String studentName,
        String studentEmail,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt) {

    public static RequestDetailResponse from(ServiceRequest request) {
        var category = request.getCategory();
        var location = request.getLocation();
        var student = request.getStudent();

        return new RequestDetailResponse(
                request.getId(),
                request.getRequestNumber(),
                request.getTitle(),
                request.getDescription(),
                category.getId(),
                category.getName(),
                category.getDepartment().getId(),
                category.getDepartment().getName(),
                location == null ? null : location.getId(),
                location == null ? null : location.displayName(),
                request.getPriority(),
                request.getPriority().getDisplayName(),
                request.getStatus(),
                request.getStatus().getDisplayName(),
                student.getId(),
                student.getFullName(),
                student.getEmail(),
                request.getDueAt(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
