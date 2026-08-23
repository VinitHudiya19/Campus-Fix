package com.campusfix.sla.dto;

import com.campusfix.sla.Escalation;
import com.campusfix.sla.EscalationLevel;

import java.time.Instant;

public record EscalationResponse(
        Long id,
        EscalationLevel level,
        String levelLabel,
        String reason,
        Instant createdAt) {

    public static EscalationResponse from(Escalation escalation) {
        return new EscalationResponse(
                escalation.getId(),
                escalation.getLevel(),
                escalation.getLevel().getDisplayName(),
                escalation.getReason(),
                escalation.getCreatedAt());
    }
}
