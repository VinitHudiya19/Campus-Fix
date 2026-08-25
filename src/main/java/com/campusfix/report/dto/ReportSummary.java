package com.campusfix.report.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the reports screen needs, in one response.
 *
 * <p>One call rather than six. The numbers are all read inside a single
 * transaction, so the page cannot show a total that disagrees with the rows
 * underneath it because something changed between two requests.
 *
 * @param scope which department this covers — "All departments" for an admin,
 *              or the head's own department name
 */
public record ReportSummary(
        int windowDays,
        String scope,
        long totalRequests,
        long openNow,
        long breachedNow,
        Integer slaCompliancePercent,
        Double averageResolutionHours,
        long reopened,
        Map<String, Long> byStatus,
        List<DepartmentReport> departments,
        List<CategoryVolume> topCategories) {
}
