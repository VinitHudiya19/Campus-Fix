package com.campusfix.sla.dto;

import com.campusfix.request.Priority;
import com.campusfix.sla.SlaConfig;

public record SlaConfigResponse(
        Long id,
        Priority priority,
        String priorityLabel,
        int durationHours,
        int warningPercentage) {

    public static SlaConfigResponse from(SlaConfig config) {
        return new SlaConfigResponse(
                config.getId(),
                config.getPriority(),
                config.getPriority().getDisplayName(),
                config.getDurationHours(),
                config.getWarningPercentage());
    }
}
