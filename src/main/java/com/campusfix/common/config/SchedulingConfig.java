package com.campusfix.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}, which the SLA check needs.
 *
 * <p>Kept as its own class rather than annotating the application class, so it
 * is obvious that this project runs background work and where to look for it.
 *
 * <p>Worth knowing before deploying more than one instance: Spring's scheduler
 * is per-application, so every instance would run the check. Escalation survives
 * that — the unique constraint on (request, level) means a duplicate insert
 * fails rather than producing two escalations — but a heavier job would need
 * proper coordination.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
