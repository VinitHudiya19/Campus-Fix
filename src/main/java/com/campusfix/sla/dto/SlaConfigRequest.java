package com.campusfix.sla.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SlaConfigRequest(

        @Min(value = 1, message = "The target must be at least 1 hour")
        @Max(value = 8760, message = "The target cannot be longer than a year")
        int durationHours,

        @Min(value = 1, message = "The warning threshold must be at least 1%")
        @Max(value = 99, message = "The warning threshold must be below 100% — a warning at 100% is the breach itself")
        int warningPercentage) {
}
