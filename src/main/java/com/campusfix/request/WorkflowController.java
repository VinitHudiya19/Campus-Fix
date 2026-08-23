package com.campusfix.request;

import com.campusfix.activity.dto.ActivityResponse;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.StatusChangeRequest;
import com.campusfix.sla.dto.EscalationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * One endpoint per action rather than a single {@code PUT /status}.
 *
 * <p>{@code POST /requests/1/resolve} says what is happening. A generic
 * {@code PUT /status} with a target in the body would let a client ask for any
 * status at all and rely on the server to refuse — the URL would promise more
 * than the system allows. These five endpoints are exactly the five legal moves.
 */
@RestController
@RequestMapping("/api/requests/{id}")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final RequestTimelineService timelineService;

    public WorkflowController(WorkflowService workflowService, RequestTimelineService timelineService) {
        this.workflowService = workflowService;
        this.timelineService = timelineService;
    }

    @PostMapping("/start")
    public RequestDetailResponse start(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) StatusChangeRequest request) {
        return workflowService.perform(id, StatusAction.START, request);
    }

    @PostMapping("/resolve")
    public RequestDetailResponse resolve(@PathVariable Long id,
                                         @Valid @RequestBody StatusChangeRequest request) {
        return workflowService.perform(id, StatusAction.RESOLVE, request);
    }

    @PostMapping("/reject")
    public RequestDetailResponse reject(@PathVariable Long id,
                                        @Valid @RequestBody StatusChangeRequest request) {
        return workflowService.perform(id, StatusAction.REJECT, request);
    }

    @PostMapping("/confirm")
    public RequestDetailResponse confirm(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) StatusChangeRequest request) {
        return workflowService.perform(id, StatusAction.CONFIRM, request);
    }

    @PostMapping("/reopen")
    public RequestDetailResponse reopen(@PathVariable Long id,
                                        @Valid @RequestBody StatusChangeRequest request) {
        return workflowService.perform(id, StatusAction.REOPEN, request);
    }

    /** Which of the five the caller may actually use right now. */
    @GetMapping("/available-actions")
    public List<WorkflowService.AvailableAction> availableActions(@PathVariable Long id) {
        return workflowService.availableActions(id);
    }

    @GetMapping("/timeline")
    public List<ActivityResponse> timeline(@PathVariable Long id) {
        return timelineService.timelineOf(id);
    }

    @GetMapping("/escalations")
    public List<EscalationResponse> escalations(@PathVariable Long id) {
        return timelineService.escalationsOf(id);
    }
}
