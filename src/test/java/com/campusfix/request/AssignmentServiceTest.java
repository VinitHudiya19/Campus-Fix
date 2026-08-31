package com.campusfix.request;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.category.Category;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.department.Department;
import com.campusfix.request.dto.AssignRequest;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.event.RequestAssignedEvent;
import com.campusfix.sla.SlaService;
import com.campusfix.sla.SlaState;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers who may assign, who may be assigned, and that reassignment keeps the
 * previous record instead of overwriting it.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");
    private static final AuthenticatedUser IT_HEAD =
            new AuthenticatedUser(5L, "head@college.edu", "Neha Rao", Role.DEPARTMENT_HEAD, 1L);

    @Mock
    private ServiceRequestRepository requestRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ActivityLogService activityLog;
    @Mock
    private SlaService slaService;

    @BeforeEach
    void stubSlaState() {
        // Most tests here stop at a rejected rule and never build a response.
        lenient().when(slaService.stateOf(any())).thenReturn(SlaState.ON_TRACK);
    }

    @Mock
    private ApplicationEventPublisher events;

    private AssignmentService service() {
        return new AssignmentService(requestRepository, assignmentRepository, userRepository,
                activityLog, slaService, currentUser, Clock.fixed(NOW, ZoneOffset.UTC), events);
    }

    @Test
    void assigningAnOpenRequestMakesItAssignedAndRecordsWhoDidIt() {
        ServiceRequest request = openRequest();
        signedInAs(IT_HEAD);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdWithDepartment(2L)).thenReturn(Optional.of(technician(2L, 1L, true)));
        when(assignmentRepository.findActiveForRequest(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(5L)).thenReturn(Optional.of(head()));

        RequestDetailResponse response = service().assign(1L, new AssignRequest(2L, "  On that floor today  "));

        assertThat(response.status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(response.assignedTechnicianName()).isEqualTo("Amit Sharma");
        assertThat(response.assignedAt()).isEqualTo(NOW);
        verify(assignmentRepository).save(any(Assignment.class));

        // The event is what tells the technician. Publishing it is part of the
        // job, not an implementation detail, so it is asserted here.
        ArgumentCaptor<RequestAssignedEvent> published = ArgumentCaptor.forClass(RequestAssignedEvent.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().technicianId()).isEqualTo(2L);
        assertThat(published.getValue().assignedByName()).isEqualTo("Neha Rao");
    }

    @Test
    void nothingIsAnnouncedWhenAssignmentIsRefused() {
        signedInAs(IT_HEAD);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(openRequest()));
        when(userRepository.findByIdWithDepartment(9L)).thenReturn(Optional.of(technician(9L, 2L, true)));

        assertThatThrownBy(() -> service().assign(1L, new AssignRequest(9L, null)))
                .isInstanceOf(BusinessRuleException.class);

        // No event, so no notification about an assignment that never happened.
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void reassigningEndsTheOldAssignmentInsteadOfOverwritingIt() {
        ServiceRequest request = openRequest();
        User first = technician(2L, 1L, true);
        request.assignTo(first, NOW.minusSeconds(3600));
        Assignment running = new Assignment(request, first, head(), NOW.minusSeconds(3600), null);

        signedInAs(IT_HEAD);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByIdWithDepartment(3L)).thenReturn(Optional.of(technician(3L, 1L, true)));
        when(assignmentRepository.findActiveForRequest(1L)).thenReturn(Optional.of(running));
        when(userRepository.findById(5L)).thenReturn(Optional.of(head()));

        service().assign(1L, new AssignRequest(3L, null));

        // The old row is closed, not deleted, so the history survives.
        assertThat(running.getUnassignedAt()).isEqualTo(NOW);
        assertThat(running.isActive()).isFalse();
        assertThat(request.getAssignedTechnician().getId()).isEqualTo(3L);
    }

    @Test
    void aTechnicianFromAnotherDepartmentCannotTakeTheWork() {
        signedInAs(IT_HEAD);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(openRequest()));
        when(userRepository.findByIdWithDepartment(9L)).thenReturn(Optional.of(technician(9L, 2L, true)));

        assertThatThrownBy(() -> service().assign(1L, new AssignRequest(9L, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not work in IT Support");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void aHeadOfAnotherDepartmentIsToldTheRequestDoesNotExist() {
        // 404 rather than 403, for the same reason as reading: do not confirm
        // that an id exists to someone with no business knowing about it.
        signedInAs(new AuthenticatedUser(6L, "elec@college.edu", "Vikram Das", Role.DEPARTMENT_HEAD, 2L));
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(openRequest()));

        assertThatThrownBy(() -> service().assign(1L, new AssignRequest(2L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aClosedRequestNoLongerNeedsAnyone() {
        ServiceRequest request = openRequest();
        ReflectionTestUtils.setField(request, "status", RequestStatus.CLOSED);
        signedInAs(IT_HEAD);
        when(requestRepository.findByIdWithDetail(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().assign(1L, new AssignRequest(2L, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
    }

    private void signedInAs(AuthenticatedUser user) {
        when(currentUser.require()).thenReturn(user);
    }

    private ServiceRequest openRequest() {
        Department itSupport = new Department("IT Support", null);
        ReflectionTestUtils.setField(itSupport, "id", 1L);
        Category wifi = new Category("Wi-Fi", null, itSupport);
        ReflectionTestUtils.setField(wifi, "id", 1L);

        User student = new User("Priya Nair", "priya@college.edu", "hash", Role.STUDENT, null);
        ReflectionTestUtils.setField(student, "id", 10L);

        ServiceRequest request = new ServiceRequest("Wi-Fi down", "No internet since morning",
                student, wifi, null, Priority.MEDIUM, NOW.plusSeconds(172800));
        ReflectionTestUtils.setField(request, "id", 1L);
        return request;
    }

    private User technician(Long id, Long departmentId, boolean active) {
        Department department = new Department(departmentId == 1L ? "IT Support" : "Electrical", null);
        ReflectionTestUtils.setField(department, "id", departmentId);
        User user = new User(id == 2L ? "Amit Sharma" : "Other Technician",
                "tech" + id + "@college.edu", "hash", Role.TECHNICIAN, department);
        ReflectionTestUtils.setField(user, "id", id);
        if (!active) {
            user.deactivate();
        }
        return user;
    }

    private User head() {
        Department itSupport = new Department("IT Support", null);
        ReflectionTestUtils.setField(itSupport, "id", 1L);
        User user = new User("Neha Rao", "head@college.edu", "hash", Role.DEPARTMENT_HEAD, itSupport);
        ReflectionTestUtils.setField(user, "id", 5L);
        return user;
    }
}
