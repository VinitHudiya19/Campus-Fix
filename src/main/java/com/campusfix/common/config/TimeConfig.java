package com.campusfix.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock, as an injectable bean.
 *
 * <p>Calling {@code Instant.now()} directly inside a service makes time
 * untestable: a test cannot check "this request is due in 72 hours" without
 * either sleeping or accepting a fuzzy comparison. Injecting a {@link Clock}
 * lets a test fix the time at a known instant and assert the exact due date.
 *
 * <p>UTC everywhere, matching {@code hibernate.jdbc.time_zone=UTC}, so a server
 * in a different timezone produces identical deadlines.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
