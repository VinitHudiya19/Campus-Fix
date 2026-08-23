package com.campusfix.request.dto;

import com.campusfix.request.Assignment;

import java.time.Instant;

/** One line of the "who had this, and when" history. */
public record AssignmentResponse(
        Long id,
        Long technicianId,
        String technicianName,
        String assignedByName,
        String note,
        Instant assignedAt,
        Instant unassignedAt,
        boolean active) {

    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTechnician().getId(),
                assignment.getTechnician().getFullName(),
                assignment.getAssignedBy().getFullName(),
                assignment.getNote(),
                assignment.getAssignedAt(),
                assignment.getUnassignedAt(),
                assignment.isActive());
    }
}
