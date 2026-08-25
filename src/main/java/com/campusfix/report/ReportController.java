package com.campusfix.report;

import com.campusfix.report.dto.ReportSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint, one response.
 *
 * <p>A screen full of numbers could have been six endpoints, but then the page
 * would fire six requests and could render a total that disagrees with the rows
 * below it because a request changed between the second call and the fifth.
 * Everything is read in one transaction and returned together.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * @param days 7, 30, 90, 365, or 0 for everything. Anything else is a 400 —
     *             an arbitrary window would let a caller ask for a range the
     *             screen cannot label honestly.
     */
    @GetMapping
    public ReportSummary summary(@RequestParam(defaultValue = "30") int days) {
        return reportService.build(days);
    }
}
