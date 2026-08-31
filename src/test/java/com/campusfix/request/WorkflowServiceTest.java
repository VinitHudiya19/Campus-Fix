package com.campusfix.request;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.category.Category;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.department.Department;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.StatusChangeRequest;
import com.campusfix.sla.SlaService;
import com.campusfix.sla.SlaState;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The transition table is the point of Phase 8, so these tests are about what
 * is refused as much as what is allowed.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

    private static final AuthenticatedUser TECHNICIAN =
            new AuthenticatedUser(2L, "amit@college.edu", "Amit Sharma", Role.TECHNICIAN, 1L);
    private static final AuthenticatedUser IT_HEAD =
            new AuthenticatedUser(5L, "neha@college.edu", "Neha Rao", Role.DEPARTMENT_HEAD, 1L);
    private static final AuthenticatedUser STUDENT =
            new AuthenticatedUser(10L, "priya@college.edu", "Priya Nair", Role.STUDENT, null);

    @Mock
    private ServiceRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityLogService activityLog;
    @Mock
    private SlaService slaService;
    @Mock
    private CurrentUser currentUser;

    @BeforeEach
    void stubCommonLookups() {
        lenient().when(slaService.stateOf(any())).thenReturn(SlaState.ON_TRACK);
        lenient().when(userRepository.findById(any())).thenAnswer(call -> Optional.of(person(call.getArgument(0))));
    }

    @Mock
    private ApplicationEventPublisher events;

    private WorkflowService service() {
        return new WorkflowService(requestRepository, userRepository, activityLog, slaService,
                currentUser, Clock.fixed(NOW, ZoneOffset.UTC), events);
    }

    @Test
    void resolvingRecordsWhatWasDoneAndWhen() {
        ServiceRequest request = requestAt(RequestStatus.IN_PROGRESS, technician());
        signedInAs(TECHNICIAN);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(request));

        RequestDetailResponse response = service().perform(1L, StatusAction.RESOLVE,
                new StatusChangeRequest("  Replaced the access point  "));

        assertThat(response.status()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(response.resolutionNote()).isEqualTo("Replaced the access point");
        assertThat(response.resolvedAt()).isEqualTo(NOW);
    }

    @Test
    void anOpenRequestCannotJumpStraightToResolved() {
        signedInAs(TECHNICIAN);
        when(requestRepository.findByIdWithDetail(1L))
                .thenReturn(Optional.of(requestAt(RequestStatus.OPEN, technician())));

        assertThatThrownBy(() -> service().perform(1L, StatusAction.RESOLVE,
                new StatusChangeRequest("Done")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not possible while the request is open");
    }

    @Test
    void resolvingWithoutSayingWhatWasDoneIsRefused() {
        signedInAs(TECHNICIAN);
        when(requestRepository.findByIdWithDetail(1L))
                .thenReturn(Optional.of(requestAt(RequestStatus.IN_PROGRESS, technician())));

        assertThatThrownBy(() -> service().perform(1L, StatusAction.RESOLVE, new StatusChangeRequest("   ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("describe what you did");
    }

    @Test
    void aTechnicianCannotCloseTheRequestOnTheStudentsBehalf() {
        // Only the person whose problem it was can say it is actually fixed.
        signedInAs(TECHNICIAN);
        when(requestRepository.findByIdWithDetail(1L))
                .thenReturn(Optional.of(requestAt(RequestStatus.RESOLVED, technician())));

        assertThatThrownBy(() -> service().perform(1L, StatusAction.CONFIRM, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reopeningUndoesTheResolutionSoTheSlaFiguresStayHonest() {
        ServiceRequest request = requestAt(RequestStatus.RESOLVED, technician());
        request.applyStatus(RequestStatus.RESOLVED, "Looked fine to me", NOW.minusSeconds(600));
        signedInAs(STUDENT);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(request));

        RequestDetailResponse response = service().perform(1L, StatusAction.REOPEN,
                new StatusChangeRequest("Still no signal in the corner"));

        assertThat(response.status()).isEqualTo(RequestStatus.REOPENED);
        assertThat(response.resolvedAt()).isNull();
        assertThat(response.resolutionNote()).isNull();
    }

    @Test
    void onlyTheOwningDepartmentsHeadMayReject() {
        signedInAs(new AuthenticatedUser(6L, "vikram@college.edu", "Vikram Das", Role.DEPARTMENT_HEAD, 2L));
        when(requestRepository.findByIdWithDetail(1L))
                .thenReturn(Optional.of(requestAt(RequestStatus.OPEN, null)));

        assertThatThrownBy(() -> service().perform(1L, StatusAction.REJECT,
                new StatusChangeRequest("Duplicate")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theAvailableActionsMatchWhoIsAskingAndWhereTheRequestIs() {
        ServiceRequest request = requestAt(RequestStatus.RESOLVED, technician());
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(request));

        signedInAs(STUDENT);
        assertThat(service().availableActions(1L))
                .extracting(WorkflowService.AvailableAction::action)
                .containsExactlyInAnyOrder("CONFIRM", "REOPEN");

        signedInAs(IT_HEAD);
        assertThat(service().availableActions(1L))
                .extracting(WorkflowService.AvailableAction::action)
                .isEmpty();   // nothing left for staff to do until the student answers
    }

    private void signedInAs(AuthenticatedUser user) {
        when(currentUser.require()).thenReturn(user);
    }

    private ServiceRequest requestAt(RequestStatus status, User assignee) {
        Department itSupport = new Department("IT Support", null);
        ReflectionTestUtils.setField(itSupport, "id", 1L);
        Category wifi = new Category("Wi-Fi", null, itSupport);
        ReflectionTestUtils.setField(wifi, "id", 1L);

        User student = person(10L);
        ServiceRequest request = new ServiceRequest("Wi-Fi down", "No internet since morning",
                student, wifi, null, Priority.MEDIUM, NOW.plusSeconds(172800));
        ReflectionTestUtils.setField(request, "id", 1L);
        ReflectionTestUtils.setField(request, "status", status);
        if (assignee != null) {
            ReflectionTestUtils.setField(request, "assignedTechnician", assignee);
        }
        return request;
    }

    private User technician() {
        return person(2L);
    }

    private User person(Long id) {
        Department itSupport = new Department("IT Support", null);
        ReflectionTestUtils.setField(itSupport, "id", 1L);

        User user = switch (id.intValue()) {
            case 2 -> new User("Amit Sharma", "amit@college.edu", "hash", Role.TECHNICIAN, itSupport);
            case 5 -> new User("Neha Rao", "neha@college.edu", "hash", Role.DEPARTMENT_HEAD, itSupport);
            default -> new User("Priya Nair", "priya@college.edu", "hash", Role.STUDENT, null);
        };
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
