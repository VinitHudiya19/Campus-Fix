package com.campusfix.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Single error shape for the whole API. Clients can always read the same
 * fields, so the frontend needs one error handler instead of one per endpoint.
 * {@code fieldErrors} is only present on validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String message, String path) {
        return new ApiError(Instant.now(), status, message, path, null);
    }

    public static ApiError validation(String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), 400, "Validation failed", path, fieldErrors);
    }
}
