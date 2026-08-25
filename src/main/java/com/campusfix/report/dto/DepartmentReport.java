package com.campusfix.report.dto;

/**
 * How one department is doing.
 *
 * <p>{@code compliancePercent} is null rather than 0 when nothing has been
 * finished yet. Zero would read as "this department fails everything", which is
 * the opposite of "there is nothing to judge them on".
 */
public record DepartmentReport(
        Long departmentId,
        String departmentName,
        long total,
        long open,
        long inProgress,
        long awaitingConfirmation,
        long closed,
        long rejected,
        long breachedNow,
        long metTarget,
        long missedTarget,
        Integer compliancePercent,
        Double averageResolutionHours,
        long reopened) {
}
