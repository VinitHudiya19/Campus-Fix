package com.campusfix.request;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.request.dto.AssignRequest;
import com.campusfix.request.dto.AssignmentResponse;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.sla.SlaService;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.springframework.stereotype.Service;
import com.campusfix.request.event.RequestAssignedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Decides who is responsible for a request.
 *
 * <p>Kept apart from {@link ServiceRequestService}, which is about reporting and
 * reading. Assignment has its own rules, its own table and its own audience —
 * mixing them would produce one service nobody wants to open.
 */
@Service
public class AssignmentService {

    private final ServiceRequestRepository requestRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLog;
    private final SlaService slaService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public AssignmentService(ServiceRequestRepository requestRepository,
                             AssignmentRepository assignmentRepository,
                             UserRepository userRepository,
                             ActivityLogService activityLog,
                             SlaService slaService,
                             CurrentUser currentUser,
                             Clock clock,
                             ApplicationEventPublisher events) {
        this.events = events;
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.slaService = slaService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Gives a request to a technician. Reassigning ends the previous assignment
     * and starts a new one, so the history survives.
     */
    @Transactional
    public RequestDetailResponse assign(Long requestId, AssignRequest command) {
        AuthenticatedUser signedIn = currentUser.require();
        ServiceRequest request = loadAssignableRequest(requestId, signedIn);
        User technician = eligibleTechnician(command.technicianId(), request);

        User currentAssignee = request.getAssignedTechnician();
        if (currentAssignee != null && currentAssignee.getId().equals(technician.getId())) {
            throw new BusinessRuleException(
                    technician.getFullName() + " already has this request");
        }

        Instant now = clock.instant();

        // End the running assignment before starting the next one, so the table
        // never holds two open rows for the same request.
        assignmentRepository.findActiveForRequest(requestId).ifPresent(active -> active.end(now));

        User assigner = userRepository.findById(signedIn.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", signedIn.id()));

        String note = trimOrNull(command.note());
        assignmentRepository.save(new Assignment(request, technician, assigner, now, note));
        request.assignTo(technician, now);
        activityLog.recordAssigned(request, assigner, technician, note);

        // Published, not called. This service has no compile-time knowledge of
        // notifications — the listener is delivered to only if the transaction
        // commits, so nobody is told about an assignment that got rolled back.
        events.publishEvent(new RequestAssignedEvent(
                request.getId(), technician.getId(), assigner.getFullName()));

        return RequestDetailResponse.from(request, slaService.stateOf(request));
    }

    /** Sends a request back to the unassigned queue. */
    @Transactional
    public RequestDetailResponse unassign(Long requestId) {
        AuthenticatedUser signedIn = currentUser.require();
        ServiceRequest request = loadAssignableRequest(requestId, signedIn);

        if (request.getAssignedTechnician() == null) {
            throw new BusinessRuleException("This request is not assigned to anyone");
        }

        Instant now = clock.instant();
        User previous = request.getAssignedTechnician();
        User actor = userRepository.findById(signedIn.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", signedIn.id()));

        assignmentRepository.findActiveForRequest(requestId).ifPresent(active -> active.end(now));
        request.clearAssignment();
        activityLog.recordUnassigned(request, actor, previous);

        return RequestDetailResponse.from(request, slaService.stateOf(request));
    }

    /**
     * The technicians who could take this request, with how much each is
     * already carrying.
     *
     * <p>This exists because {@code /api/users} is admin-only, and a department
     * head still has to fill the dropdown on the assignment screen. It returns
     * exactly the people they are allowed to choose — no emails, no roles, no
     * way to enumerate staff outside their own department.
     */
    @Transactional(readOnly = true)
    public List<AssignableTechnician> assignableTechnicians(Long requestId) {
        ServiceRequest request = loadAssignableRequest(requestId, currentUser.require());
        Long departmentId = request.getCategory().getDepartment().getId();

        return userRepository.search(Role.TECHNICIAN, departmentId, true).stream()
                .map(technician -> new AssignableTechnician(
                        technician.getId(),
                        technician.getFullName(),
                        assignmentRepository.countOpenWorkFor(technician.getId())))
                .toList();
    }

    public record AssignableTechnician(Long id, String fullName, long openRequests) {
    }

    /**
     * The history is readable by anyone who can already read the request, so it
     * reuses the same scope rather than inventing a second rule.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> history(Long requestId) {
        RequestScope scope = RequestScope.forUser(currentUser.require());

        requestRepository.findByIdWithDetail(requestId)
                .filter(scope::permits)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));

        return assignmentRepository.findHistoryForRequest(requestId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    /**
     * Who may hand out work: an admin anywhere, or the head of the department
     * that owns the request's category. A head of Electrical has no business
     * assigning an IT ticket.
     */
    private ServiceRequest loadAssignableRequest(Long requestId, AuthenticatedUser signedIn) {
        ServiceRequest request = requestRepository.findByIdWithDetail(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));

        Long owningDepartmentId = request.getCategory().getDepartment().getId();
        boolean allowed = signedIn.isAdmin()
                || (signedIn.role() == Role.DEPARTMENT_HEAD && signedIn.belongsTo(owningDepartmentId));

        if (!allowed) {
            // Same reasoning as reading: do not confirm that an id exists to
            // someone who is not entitled to know about it.
            throw new ResourceNotFoundException("Request", requestId);
        }

        if (request.getStatus().isFinal()) {
            throw new BusinessRuleException(
                    "This request is " + request.getStatus().getDisplayName().toLowerCase()
                            + " and no longer needs anyone working on it");
        }

        return request;
    }

    private User eligibleTechnician(Long technicianId, ServiceRequest request) {
        User technician = userRepository.findByIdWithDepartment(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("User", technicianId));

        if (technician.getRole() != Role.TECHNICIAN) {
            throw new BusinessRuleException(
                    technician.getFullName() + " is not a technician");
        }
        if (!technician.isActive()) {
            throw new BusinessRuleException(
                    technician.getFullName() + "'s account is deactivated");
        }

        Long owningDepartmentId = request.getCategory().getDepartment().getId();
        if (technician.getDepartment() == null
                || !technician.getDepartment().getId().equals(owningDepartmentId)) {
            throw new BusinessRuleException(
                    technician.getFullName() + " does not work in "
                            + request.getCategory().getDepartment().getName());
        }

        return technician;
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
