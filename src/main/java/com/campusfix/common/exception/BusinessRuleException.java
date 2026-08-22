package com.campusfix.common.exception;

/**
 * Thrown when a request is well formed but breaks a domain rule, for example
 * deactivating a department that still has active categories.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
