package com.campusfix.request;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.StatusChangeRequest;
import com.campusfix.request.event.RequestStatusChangedEvent;
import com.campusfix.sla.SlaService;
import org.springframework.context.ApplicationEventPublisher;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Moves a request through its lifecycle.
 *
 * <p>Every status change in the product goes through {@link #perform}. There is
 * no other way to write the status field from outside this package, which is
 * what makes {@link StatusAction} the real rule rather than documentation.
 */
@Service
public class WorkflowService {

    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLog;
    private final SlaService slaService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public WorkflowService(ServiceRequestRepository requestRepository,
                           UserRepository userRepository,
                           ActivityLogService activityLog,
                           SlaService slaService,
                           CurrentUser currentUser,
                           Clock clock,
                           ApplicationEventPublisher events) {
        this.events = events;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.slaService = slaService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public RequestDetailResponse perform(Long requestId, StatusAction action, StatusChangeRequest command) {
        AuthenticatedUser signedIn = currentUser.require();

        ServiceRequest request = requestRepository.findByIdWithDetail(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));

        // Checked before anything else: someone with no right to see this
        // request must not learn from the error message whether it exists.
        if (!mayAct(action, request, signedIn)) {
            throw new ResourceNotFoundException("Request", requestId);
        }

        if (!action.isAllowedFrom(request.getStatus())) {
            throw new BusinessRuleException(
                    "'%s' is not possible while the request is %s".formatted(
                            action.getLabel(),
                            request.getStatus().getDisplayName().toLowerCase()));
        }

        String note = trimOrNull(command == null ? null : command.note());
        if (action.isNoteRequired() && note == null) {
            throw new BusinessRuleException(noteRequirementMessage(action));
        }

        RequestStatus from = request.getStatus();
        Instant now = clock.instant();
        User actor = userRepository.findById(signedIn.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", signedIn.id()));

        request.applyStatus(action.getTarget(), note, now);

        activityLog.recordStatusChange(request, actor, from.name(), action.getTarget().name(),
                buildMessage(action, actor, note));

        // The listener decides who cares about this particular move; this
        // service only reports that it happened.
        events.publishEvent(new RequestStatusChangedEvent(
                request.getId(), from, action.getTarget(), actor.getId(), actor.getFullName(), note));

        return RequestDetailResponse.from(request, slaService.stateOf(request));
    }

    /**
     * Which buttons the caller should actually see on this request.
     *
     * <p>The frontend could work this out itself, but then the rule would exist
     * in two languages and drift. Asking the server means the screen and the
     * server can never disagree about what is possible.
     */
    @Transactional(readOnly = true)
    public List<AvailableAction> availableActions(Long requestId) {
        AuthenticatedUser signedIn = currentUser.require();
        RequestScope scope = RequestScope.forUser(signedIn);

        ServiceRequest request = requestRepository.findByIdWithDetail(requestId)
                .filter(scope::permits)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));

        return Arrays.stream(StatusAction.values())
                .filter(action -> action.isAllowedFrom(request.getStatus()))
                .filter(action -> mayAct(action, request, signedIn))
                .map(action -> new AvailableAction(
                        action.name(), action.getLabel(), action.isNoteRequired()))
                .toList();
    }

    public record AvailableAction(String action, String label, boolean noteRequired) {
    }

    /**
     * Whether this person stands in the right relationship to this request.
     *
     * <p>Note that it is not a plain role check. A technician may resolve the
     * request <em>they</em> hold, not any request; a department head may reject
     * within <em>their</em> department, not another one.
     */
    private boolean mayAct(StatusAction action, ServiceRequest request, AuthenticatedUser user) {
        Long owningDepartmentId = request.getCategory().getDepartment().getId();
        boolean manages = user.isAdmin()
                || (user.role() == Role.DEPARTMENT_HEAD && user.belongsTo(owningDepartmentId));

        return switch (action.getActor()) {
            // The person holding the work, or whoever manages that queue —
            // a head has to be able to finish a request when a technician leaves.
            case WORKER -> manages || isAssignedTechnician(request, user);
            case MANAGER -> manages;
            // Only the student who reported it. Not an admin: nobody else can
            // truthfully say whether the problem in their room is fixed.
            case REPORTER -> user.id().equals(request.getStudent().getId());
        };
    }

    private boolean isAssignedTechnician(ServiceRequest request, AuthenticatedUser user) {
        User assignee = request.getAssignedTechnician();
        return assignee != null && assignee.getId().equals(user.id());
    }

    private String noteRequirementMessage(StatusAction action) {
        return switch (action) {
            case RESOLVE -> "Please describe what you did to fix it";
            case REJECT -> "Please give a reason for rejecting this request";
            case REOPEN -> "Please explain what is still not working";
            default -> "A note is required for this action";
        };
    }

    private String buildMessage(StatusAction action, User actor, String note) {
        String who = actor.getFullName();
        String base = switch (action) {
            case START -> who + " started work on this";
            case RESOLVE -> who + " marked this as resolved";
            case REJECT -> who + " rejected this request";
            case CONFIRM -> who + " confirmed the problem is fixed";
            case REOPEN -> who + " reopened this";
        };
        return note == null ? base : base + " — " + note;
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
