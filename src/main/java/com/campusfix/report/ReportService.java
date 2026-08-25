package com.campusfix.report;

import com.campusfix.common.exception.InvalidRequestException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.report.dto.CategoryVolume;
import com.campusfix.report.dto.DepartmentReport;
import com.campusfix.report.dto.ReportSummary;
import com.campusfix.request.RequestStatus;
import com.campusfix.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers the question the product spec opened with: which departments are
 * missing their service targets?
 *
 * <p>Everything here is read-only and computed from data the workflow already
 * records — no counters are maintained alongside the requests, because a
 * denormalised total is a number that drifts the first time something updates a
 * request without remembering to update the tally too.
 */
@Service
public class ReportService {

    /** Windows a caller may ask for. 0 means "everything ever recorded". */
    private static final Set<Integer> ALLOWED_WINDOWS = Set.of(0, 7, 30, 90, 365);

    private final ReportRepository reportRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, CurrentUser currentUser, Clock clock) {
        this.reportRepository = reportRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * A single transaction so every number on the page is read from the same
     * moment. Six separate calls could show a total that does not match the rows
     * beneath it if a request changed in between.
     */
    @Transactional(readOnly = true)
    public ReportSummary build(int windowDays) {
        if (!ALLOWED_WINDOWS.contains(windowDays)) {
            throw new InvalidRequestException(
                    "Reports can cover 7, 30, 90 or 365 days, or 0 for everything");
        }

        AuthenticatedUser signedIn = currentUser.require();

        // A department head sees their own department and nothing else. Pushed
        // into every query rather than filtered afterwards, exactly as the
        // request list does it.
        Long departmentId = signedIn.role() == Role.DEPARTMENT_HEAD ? signedIn.departmentId() : null;

        Instant now = clock.instant();
        Instant since = windowDays == 0 ? Instant.EPOCH : now.minus(windowDays, ChronoUnit.DAYS);

        List<StatusTally> tallies = reportRepository.tallyByStatus(since, departmentId);
        List<ResolutionFact> resolutions = reportRepository.resolutionFacts(since, departmentId);
        Map<Long, Long> breached = toMap(reportRepository.breachedNow(now, departmentId));
        Map<Long, Long> reopens = toMap(reportRepository.reopenCounts(since, departmentId));
        List<CategoryTally> categories = reportRepository.volumeByCategory(since, departmentId);

        List<DepartmentReport> departments = buildDepartments(tallies, resolutions, breached, reopens);

        return new ReportSummary(
                windowDays,
                departmentId == null ? "All departments" : scopeNameOf(departments, signedIn),
                departments.stream().mapToLong(DepartmentReport::total).sum(),
                departments.stream().mapToLong(d -> d.open() + d.inProgress()).sum(),
                departments.stream().mapToLong(DepartmentReport::breachedNow).sum(),
                compliance(resolutions),
                averageHours(resolutions),
                departments.stream().mapToLong(DepartmentReport::reopened).sum(),
                statusTotals(tallies),
                departments,
                categories.stream()
                        .limit(8)
                        .map(c -> new CategoryVolume(c.categoryName(), c.departmentName(), c.count()))
                        .toList());
    }

    private List<DepartmentReport> buildDepartments(List<StatusTally> tallies,
                                                    List<ResolutionFact> resolutions,
                                                    Map<Long, Long> breached,
                                                    Map<Long, Long> reopens) {

        // The tally arrives as one row per (department, status). Fold it back
        // into one row per department.
        Map<Long, Map<RequestStatus, Long>> byDepartment = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();

        for (StatusTally tally : tallies) {
            names.putIfAbsent(tally.departmentId(), tally.departmentName());
            byDepartment
                    .computeIfAbsent(tally.departmentId(), key -> new LinkedHashMap<>())
                    .merge(tally.status(), tally.count(), Long::sum);
        }

        Map<Long, List<ResolutionFact>> resolutionsByDepartment = resolutions.stream()
                .collect(Collectors.groupingBy(ResolutionFact::departmentId));

        return names.entrySet().stream()
                .map(entry -> {
                    Long id = entry.getKey();
                    Map<RequestStatus, Long> counts = byDepartment.get(id);
                    List<ResolutionFact> facts = resolutionsByDepartment.getOrDefault(id, List.of());

                    long met = facts.stream().filter(ResolutionFact::metTarget).count();

                    return new DepartmentReport(
                            id,
                            entry.getValue(),
                            counts.values().stream().mapToLong(Long::longValue).sum(),
                            count(counts, RequestStatus.OPEN) + count(counts, RequestStatus.ASSIGNED),
                            count(counts, RequestStatus.IN_PROGRESS) + count(counts, RequestStatus.REOPENED),
                            count(counts, RequestStatus.RESOLVED),
                            count(counts, RequestStatus.CLOSED),
                            count(counts, RequestStatus.REJECTED),
                            breached.getOrDefault(id, 0L),
                            met,
                            facts.size() - met,
                            compliance(facts),
                            averageHours(facts),
                            reopens.getOrDefault(id, 0L));
                })
                // Worst compliance first: the point of the screen is to show
                // where attention is needed, not to list departments neatly.
                .sorted(Comparator.comparing(
                        (DepartmentReport d) -> d.compliancePercent() == null ? 101 : d.compliancePercent())
                        .thenComparing(DepartmentReport::departmentName))
                .toList();
    }

    /**
     * @return null when nothing has been finished yet. Reporting 0% would say
     *         "this department fails everything" when the truth is "there is
     *         nothing to judge them on".
     */
    private Integer compliance(List<ResolutionFact> facts) {
        if (facts.isEmpty()) {
            return null;
        }
        long met = facts.stream().filter(ResolutionFact::metTarget).count();
        return (int) Math.round(met * 100.0 / facts.size());
    }

    private Double averageHours(List<ResolutionFact> facts) {
        if (facts.isEmpty()) {
            return null;
        }
        double average = facts.stream().mapToDouble(ResolutionFact::hoursTaken).average().orElse(0);
        return Math.round(average * 10) / 10.0;
    }

    /** Totals across the whole scope, keyed by the label the UI shows. */
    private Map<String, Long> statusTotals(List<StatusTally> tallies) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) {
            totals.put(status.getDisplayName(), 0L);
        }
        tallies.forEach(tally -> totals.merge(tally.status().getDisplayName(), tally.count(), Long::sum));
        return totals;
    }

    private long count(Map<RequestStatus, Long> counts, RequestStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    private Map<Long, Long> toMap(List<CountByDepartment> rows) {
        return rows.stream().collect(Collectors.toMap(
                CountByDepartment::departmentId, CountByDepartment::count));
    }

    /**
     * A head's own department name. Taken from the report rows when there are
     * any, so the label matches the data rather than being looked up separately.
     */
    private String scopeNameOf(List<DepartmentReport> departments, AuthenticatedUser signedIn) {
        return departments.stream()
                .filter(d -> d.departmentId().equals(signedIn.departmentId()))
                .map(DepartmentReport::departmentName)
                .findFirst()
                .orElse("Your department");
    }
}
