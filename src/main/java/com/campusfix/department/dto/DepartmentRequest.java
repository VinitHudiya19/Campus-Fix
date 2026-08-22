package com.campusfix.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(

        @NotBlank(message = "Department name is required")
        @Size(max = 100, message = "Department name cannot be longer than 100 characters")
        String name,

        @Size(max = 255, message = "Description cannot be longer than 255 characters")
        String description) {
}
