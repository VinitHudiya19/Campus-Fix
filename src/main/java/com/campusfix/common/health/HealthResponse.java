package com.campusfix.common.health;

import java.time.Instant;

public record HealthResponse(String application, String status, Instant timestamp) {
}
