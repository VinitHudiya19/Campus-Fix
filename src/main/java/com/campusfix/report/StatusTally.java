package com.campusfix.report;

import com.campusfix.request.RequestStatus;

/** One row of "this department has N requests in this status". */
public record StatusTally(Long departmentId, String departmentName, RequestStatus status, long count) {
}
