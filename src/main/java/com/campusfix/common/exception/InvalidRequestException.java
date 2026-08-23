package com.campusfix.common.exception;

/**
 * The request is malformed in a way Bean Validation cannot catch — an unknown
 * sort field, for example.
 *
 * <p>Distinct from {@link BusinessRuleException}: that one means "understood
 * perfectly, but a domain rule forbids it" (422). This means "the request itself
 * does not make sense" (400).
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
