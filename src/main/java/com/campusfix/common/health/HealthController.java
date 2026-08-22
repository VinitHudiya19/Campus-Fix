package com.campusfix.common.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Temporary endpoint used to confirm the application starts and serves JSON.
 * Remove it once real endpoints and Spring Boot Actuator are in place.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/hello")
    public HealthResponse hello() {
        return new HealthResponse(applicationName, "UP", Instant.now());
    }
}
