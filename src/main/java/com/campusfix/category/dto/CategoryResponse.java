package com.campusfix.category.dto;

import com.campusfix.category.Category;

/**
 * Carries the department name as well as its id so the UI can render a table
 * without a second request per row.
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long departmentId,
        String departmentName,
        boolean active) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getDepartment().getId(),
                category.getDepartment().getName(),
                category.isActive());
    }
}
