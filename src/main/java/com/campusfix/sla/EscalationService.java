package com.campusfix.sla;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.activity.ActivityType;
import com.campusfix.request.ServiceRequest;
import com.campusfix.request.ServiceRequestRepository;
import com.campusfix.sla.dto.EscalationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Notices when a request has gone past its deadline and pushes it up.
 *
 * <p>Nobody has to press anything. A late request is exactly the case where
 * nobody is watching, so the system has to be the one that notices.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final ServiceRequestRepository requestRepository;
    private final EscalationRepository escalationRepository;
    private final ActivityLogService activityLog;
    private final Clock clock;
    private final int graceHours;

    public EscalationService(ServiceRequestRepository requestRepository,
                             EscalationRepository escalationRepository,
                             ActivityLogService activityLog,
                             Clock clock,
                             @Value("${campusfix.sla.escalation-grace-hours:24}") int graceHours) {
        this.requestRepository = requestRepository;
        this.escalationRepository = escalationRepository;
        this.activityLog = activityLog;
        this.clock = clock;
        this.graceHours = graceHours;
    }

    /**
     * Runs on a fixed delay rather than a fixed rate: the next run starts a set
     * time after the previous one <em>finished</em>, so a slow pass on a large
     * database cannot pile runs on top of each other.
     */
    @Scheduled(fixedDelayString = "${campusfix.sla.check-interval-ms:900000}",
            initialDelayString = "${campusfix.sla.initial-delay-ms:60000}")
    public void checkOverdueRequests() {
        int escalated = escalateOverdue();
        if (escalated > 0) {
            log.info("SLA check escalated {} request(s)", escalated);
        }
    }

    /**
     * Package-visible and separate from the scheduled method so it can be called
     * directly — by a test, or by the admin's "run it now" endpoint — without
     * waiting a quarter of an hour.
     */
    @Transactional
    public int escalateOverdue() {
        Instant now = clock.instant();
        List<ServiceRequest> overdue = requestRepository.findOverdue(now);
        int count = 0;

        for (ServiceRequest request : overdue) {
            if (escalate(request, EscalationLevel.DEPARTMENT_HEAD, now,
                    "Past its %s deadline and still unresolved".formatted(
                            request.getPriority().getDisplayName().toLowerCase()))) {
                count++;
            }

            // The second step only applies once the grace period has also passed.
            boolean graceExpired = now.isAfter(request.getDueAt().plus(Duration.ofHours(graceHours)));
            if (graceExpired && escalate(request, EscalationLevel.ADMIN, now,
                    "Still unresolved %d hours after the deadline".formatted(graceHours))) {
                count++;
            }
        }

        return count;
    }

    @Transactional(readOnly = true)
    public List<EscalationResponse> findForRequest(Long requestId) {
        return escalationRepository.findForRequest(requestId).stream()
                .map(EscalationResponse::from)
                .toList();
    }

    /**
     * @return true if this was a new escalation. Already-escalated requests are
     *         skipped, which is what stops the check shouting about the same
     *         request every fifteen minutes.
     */
    private boolean escalate(ServiceRequest request, EscalationLevel level, Instant now, String reason) {
        if (escalationRepository.existsByRequestIdAndLevel(request.getId(), level)) {
            return false;
        }

        escalationRepository.save(new Escalation(request, level, reason, now));

        if (level == EscalationLevel.DEPARTMENT_HEAD) {
            activityLog.recordSystemEvent(request, ActivityType.SLA_BREACHED,
                    "The deadline passed with the request still unresolved");
        }
        activityLog.recordSystemEvent(request, ActivityType.ESCALATED,
                "Escalated to " + level.getDisplayName().toLowerCase() + ": " + reason);

        return true;
    }
}
