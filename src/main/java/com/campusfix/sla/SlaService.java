package com.campusfix.sla;

import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.request.Priority;
import com.campusfix.sla.dto.SlaConfigRequest;
import com.campusfix.sla.dto.SlaConfigResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SlaService {

    private final SlaConfigRepository configRepository;
    private final Clock clock;

    public SlaService(SlaConfigRepository configRepository, Clock clock) {
        this.configRepository = configRepository;
        this.clock = clock;
    }

    /**
     * The deadline for a request being created now.
     *
     * <p>Falls back to the constant on the {@link Priority} enum if a row is
     * somehow missing, so a misconfigured database can never leave a request
     * without a deadline.
     */
    @Transactional(readOnly = true)
    public Instant deadlineFor(Priority priority, Instant from) {
        int hours = configRepository.findByPriority(priority)
                .map(SlaConfig::getDurationHours)
                .orElseGet(priority::getSlaHours);
        return from.plus(hours, ChronoUnit.HOURS);
    }

    /** Convenience for the single-request responses. */
    @Transactional(readOnly = true)
    public SlaState stateOf(com.campusfix.request.ServiceRequest request) {
        return snapshot().stateOf(request);
    }

    /** Reads the settings once, for judging a whole page of requests. */
    @Transactional(readOnly = true)
    public SlaSnapshot snapshot() {
        Map<Priority, Integer> percentages = configRepository.findAll().stream()
                .collect(Collectors.toMap(SlaConfig::getPriority, SlaConfig::getWarningPercentage));
        return new SlaSnapshot(percentages, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<SlaConfigResponse> findAll() {
        return configRepository.findAll().stream()
                .sorted(Comparator.comparing(config -> config.getPriority().ordinal()))
                .map(SlaConfigResponse::from)
                .toList();
    }

    @Transactional
    public SlaConfigResponse update(Priority priority, SlaConfigRequest request) {
        SlaConfig config = configRepository.findByPriority(priority)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No SLA target is configured for " + priority.getDisplayName() + " priority"));

        config.change(request.durationHours(), request.warningPercentage());
        return SlaConfigResponse.from(config);
    }

    /**
     * Changing a target only affects requests created afterwards. Existing
     * requests keep the deadline they were given, because a promise already made
     * to a student should not move.
     */
    @Transactional
    public void seedDefaults() {
        for (Priority priority : Priority.values()) {
            if (!configRepository.existsByPriority(priority)) {
                configRepository.save(new SlaConfig(priority, priority.getSlaHours(), 75));
            }
        }
    }
}
