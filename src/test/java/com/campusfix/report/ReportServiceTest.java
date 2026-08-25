package com.campusfix.report;

import com.campusfix.common.exception.InvalidRequestException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.report.dto.ReportSummary;
import com.campusfix.request.RequestStatus;
import com.campusfix.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The reporting rules that are easy to get quietly wrong: how a department with
 * no finished work is presented, what order the rows come in, and whether a
 * department head can see anyone else's numbers.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CurrentUser currentUser;

    private ReportService service() {
        return new ReportService(reportRepository, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aDepartmentWithNothingFinishedYetHasNoComplianceFigure() {
        signedInAsAdmin();
        // Two requests, neither resolved: there is no resolution fact for them.
        when(reportRepository.tallyByStatus(any(), isNull())).thenReturn(List.of(
                new StatusTally(4L, "Hostel Maintenance", RequestStatus.OPEN, 2)));
        when(reportRepository.resolutionFacts(any(), isNull())).thenReturn(List.of());
        stubEmptyExtras();

        ReportSummary report = service().build(30);

        // Null, not zero. Zero would read as "fails everything" when the truth
        // is "there is nothing to judge them on".
        assertThat(report.departments()).singleElement()
                .satisfies(department -> {
                    assertThat(department.compliancePercent()).isNull();
                    assertThat(department.averageResolutionHours()).isNull();
                    assertThat(department.total()).isEqualTo(2);
                });
        assertThat(report.slaCompliancePercent()).isNull();
    }

    @Test
    void theWorstPerformingDepartmentIsListedFirst() {
        signedInAsAdmin();
        when(reportRepository.tallyByStatus(any(), isNull())).thenReturn(List.of(
                new StatusTally(1L, "IT Support", RequestStatus.CLOSED, 2),
                new StatusTally(2L, "Electrical", RequestStatus.CLOSED, 2),
                new StatusTally(3L, "Facilities", RequestStatus.OPEN, 1)));

        when(reportRepository.resolutionFacts(any(), isNull())).thenReturn(List.of(
                // IT Support: both on time
                onTime(1L), onTime(1L),
                // Electrical: both late
                late(2L), late(2L)));
        stubEmptyExtras();

        ReportSummary report = service().build(30);

        // Electrical (0%) before IT Support (100%), and the department with
        // nothing measurable last — the page exists to show where attention is
        // needed, not to list departments alphabetically.
        assertThat(report.departments()).extracting(d -> d.departmentName() + "=" + d.compliancePercent())
                .containsExactly("Electrical=0", "IT Support=100", "Facilities=null");
    }

    @Test
    void aDepartmentHeadOnlyEverAsksAboutTheirOwnDepartment() {
        when(currentUser.require()).thenReturn(
                new AuthenticatedUser(5L, "neha@college.edu", "Neha Rao", Role.DEPARTMENT_HEAD, 1L));
        when(reportRepository.tallyByStatus(any(), eq(1L))).thenReturn(List.of(
                new StatusTally(1L, "IT Support", RequestStatus.OPEN, 3)));
        when(reportRepository.resolutionFacts(any(), eq(1L))).thenReturn(List.of());
        lenient().when(reportRepository.breachedNow(any(), eq(1L))).thenReturn(List.of());
        lenient().when(reportRepository.reopenCounts(any(), eq(1L))).thenReturn(List.of());
        lenient().when(reportRepository.volumeByCategory(any(), eq(1L))).thenReturn(List.of());

        ReportSummary report = service().build(30);

        // The scope is pushed into every query rather than filtered afterwards.
        verify(reportRepository).tallyByStatus(any(), eq(1L));
        verify(reportRepository).volumeByCategory(any(), eq(1L));
        assertThat(report.scope()).isEqualTo("IT Support");
        assertThat(report.departments()).hasSize(1);
    }

    @Test
    void anArbitraryTimeWindowIsRefused() {
        // No signed-in user is stubbed on purpose: the window is validated
        // before anything else happens, so a malformed request is rejected
        // without touching the security context or the database at all.
        assertThatThrownBy(() -> service().build(13))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("7, 30, 90 or 365");
    }

    private void signedInAsAdmin() {
        when(currentUser.require()).thenReturn(
                new AuthenticatedUser(1L, "admin@campusfix.local", "Admin", Role.ADMIN, null));
    }

    private void stubEmptyExtras() {
        lenient().when(reportRepository.breachedNow(any(), isNull())).thenReturn(List.of());
        lenient().when(reportRepository.reopenCounts(any(), isNull())).thenReturn(List.of());
        lenient().when(reportRepository.volumeByCategory(any(), isNull())).thenReturn(List.of());
    }

    /** Fixed in 4 hours against a 24-hour deadline. */
    private ResolutionFact onTime(Long departmentId) {
        Instant created = NOW.minusSeconds(86_400);
        return new ResolutionFact(departmentId, created,
                created.plusSeconds(4 * 3600), created.plusSeconds(24 * 3600));
    }

    /** Fixed in 30 hours against the same 24-hour deadline. */
    private ResolutionFact late(Long departmentId) {
        Instant created = NOW.minusSeconds(200_000);
        return new ResolutionFact(departmentId, created,
                created.plusSeconds(30 * 3600), created.plusSeconds(24 * 3600));
    }
}
