package com.campusfix.request;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.category.Category;
import com.campusfix.category.CategoryRepository;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.department.Department;
import com.campusfix.location.LocationRepository;
import com.campusfix.request.dto.CreateRequestRequest;
import com.campusfix.request.dto.RequestDetailResponse;
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
 * Covers the rules that decide whether a request may be created and who is
 * allowed to read it. The clock is fixed so the SLA due date can be asserted
 * exactly rather than approximately.
 */
@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

    @Mock
    private ServiceRequestRepository requestRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ActivityLogService activityLog;
    @Mock
    private SlaService slaService;

    /**
     * Lenient because most tests here never reach the point of building a
     * response — they assert that a rule rejected the request first.
     */
    @BeforeEach
    void stubSlaLookups() {
        lenient().when(slaService.stateOf(any())).thenReturn(SlaState.ON_TRACK);
        lenient().when(slaService.deadlineFor(any(), any()))
                .thenAnswer(call -> {
                    Priority priority = call.getArgument(0);
                    Instant from = call.getArgument(1);
                    return from.plus(priority.getSlaHours(), java.time.temporal.ChronoUnit.HOURS);
                });
    }

    private ServiceRequestService service() {
        return new ServiceRequestService(requestRepository, categoryRepository, locationRepository,
                userRepository, activityLog, slaService, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createOpensTheRequestWithANumberAndADueDateFromThePriority() {
        signedInAs(student());
        when(userRepository.findById(10L)).thenReturn(Optional.of(student()));
        when(categoryRepository.findByIdWithDepartment(1L)).thenReturn(Optional.of(wifiCategory(true)));
        when(requestRepository.saveAndFlush(any(ServiceRequest.class))).thenAnswer(call -> {
            ServiceRequest saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);   // the database would assign this
            return saved;
        });

        RequestDetailResponse response = service().create(new CreateRequestRequest(
                "  Wi-Fi down in library  ", "  No internet on the second floor since morning  ",
                1L, null, Priority.MEDIUM));

        assertThat(response.status()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.requestNumber()).isEqualTo("CF-2026-000042");
        assertThat(response.title()).isEqualTo("Wi-Fi down in library");
        // MEDIUM is 48 hours, so 10:00 on the 23rd becomes 10:00 on the 25th.
        assertThat(response.dueAt()).isEqualTo(Instant.parse("2026-08-25T10:00:00Z"));
        assertThat(response.departmentName()).isEqualTo("IT Support");
    }

    @Test
    void aStudentCannotRaiseTheirOwnRequestToCritical() {
        signedInAs(student());

        assertThatThrownBy(() -> service().create(new CreateRequestRequest(
                "Wi-Fi down", "No internet since morning", 1L, null, Priority.CRITICAL)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only be set by staff");
    }

    @Test
    void staffDoNotReportProblemsThroughThisEndpoint() {
        signedInAs(new AuthenticatedUser(2L, "tech@college.edu", "Amit Sharma", Role.TECHNICIAN, 1L));

        assertThatThrownBy(() -> service().create(new CreateRequestRequest(
                "Wi-Fi down", "No internet since morning", 1L, null, Priority.MEDIUM)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only students");
    }

    @Test
    void aRetiredCategoryStopsAcceptingNewRequests() {
        signedInAs(student());
        when(userRepository.findById(10L)).thenReturn(Optional.of(student()));
        when(categoryRepository.findByIdWithDepartment(1L)).thenReturn(Optional.of(wifiCategory(false)));

        assertThatThrownBy(() -> service().create(new CreateRequestRequest(
                "Wi-Fi down", "No internet since morning", 1L, null, Priority.LOW)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer accepting");
    }

    @Test
    void anotherStudentsRequestLooksLikeItDoesNotExist() {
        signedInAs(student());   // id 10
        when(requestRepository.findByIdWithDetail(7L)).thenReturn(Optional.of(requestReportedBy(99L)));

        // 404 rather than 403: a 403 would confirm the id exists and let anyone
        // count the college's requests by walking the id range.
        assertThatThrownBy(() -> service().findById(7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void signedInAs(AuthenticatedUser user) {
        when(currentUser.require()).thenReturn(user);
    }

    private void signedInAs(User user) {
        signedInAs(new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), null));
    }

    private User student() {
        User user = new User("Priya Nair", "priya@college.edu", "hash", Role.STUDENT, null);
        ReflectionTestUtils.setField(user, "id", 10L);
        return user;
    }

    private Category wifiCategory(boolean active) {
        Department department = new Department("IT Support", null);
        ReflectionTestUtils.setField(department, "id", 1L);
        Category category = new Category("Wi-Fi", null, department);
        ReflectionTestUtils.setField(category, "id", 1L);
        if (!active) {
            category.deactivate();
        }
        return category;
    }

    private ServiceRequest requestReportedBy(Long studentId) {
        User other = new User("Someone Else", "other@college.edu", "hash", Role.STUDENT, null);
        ReflectionTestUtils.setField(other, "id", studentId);
        return new ServiceRequest("Title", "Description", other, wifiCategory(true),
                null, Priority.LOW, NOW);
    }
}
