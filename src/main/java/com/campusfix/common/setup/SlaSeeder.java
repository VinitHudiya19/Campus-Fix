package com.campusfix.common.setup;

import com.campusfix.sla.SlaService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Writes the default SLA targets on first startup.
 *
 * <p>Without a row per priority the deadline calculation would fall back to the
 * constants on the enum, which works but leaves the admin screen empty and the
 * targets invisible. Seeding makes the policy something you can see and change.
 *
 * <p>Only fills in what is missing, so an edited target is never overwritten by
 * a restart.
 */
@Component
@Order(1)
public class SlaSeeder implements ApplicationRunner {

    private final SlaService slaService;

    public SlaSeeder(SlaService slaService) {
        this.slaService = slaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        slaService.seedDefaults();
    }
}
