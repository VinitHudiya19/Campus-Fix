package com.campusfix.request;

import com.campusfix.activity.ActivityLogRepository;
import com.campusfix.activity.dto.ActivityResponse;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.sla.EscalationService;
import com.campusfix.sla.dto.EscalationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads a request's history.
 *
 * <p>Separate from {@link com.campusfix.activity.ActivityLogService}, which
 * writes it. The writer is called from inside other people's transactions and
 * knows nothing about who is asking; this reader is the one that has to check
 * whether the caller is allowed to look.
 */
@Service
public class RequestTimelineService {

    private final ServiceRequestRepository requestRepository;
    private final ActivityLogRepository activityLogRepository;
    private final EscalationService escalationService;
    private final CurrentUser currentUser;

    public RequestTimelineService(ServiceRequestRepository requestRepository,
                                  ActivityLogRepository activityLogRepository,
                                  EscalationService escalationService,
                                  CurrentUser currentUser) {
        this.requestRepository = requestRepository;
        this.activityLogRepository = activityLogRepository;
        this.escalationService = escalationService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> timelineOf(Long requestId) {
        requireVisible(requestId);
        return activityLogRepository.findTimeline(requestId).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EscalationResponse> escalationsOf(Long requestId) {
        requireVisible(requestId);
        return escalationService.findForRequest(requestId);
    }

    /** Reuses the one visibility rule rather than inventing a second one here. */
    private void requireVisible(Long requestId) {
        RequestScope scope = RequestScope.forUser(currentUser.require());
        requestRepository.findByIdWithDetail(requestId)
                .filter(scope::permits)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));
    }
}
