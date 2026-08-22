package com.campusfix.common.exception;

/**
 * Wrong email or wrong password. Deliberately one exception for both, so the
 * response cannot be used to work out which email addresses are registered.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
