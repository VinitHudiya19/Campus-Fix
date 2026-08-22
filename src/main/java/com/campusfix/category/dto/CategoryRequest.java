package com.campusfix.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name cannot be longer than 100 characters")
        String name,

        @Size(max = 255, message = "Description cannot be longer than 255 characters")
        String description,

        @NotNull(message = "Please choose a department")
        Long departmentId) {
}
