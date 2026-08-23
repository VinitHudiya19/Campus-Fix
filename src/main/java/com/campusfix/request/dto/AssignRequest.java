package com.campusfix.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignRequest(

        @NotNull(message = "Please choose a technician")
        Long technicianId,

        /** Optional note explaining the choice, kept with the assignment record. */
        @Size(max = 255, message = "Note cannot be longer than 255 characters")
        String note) {
}
