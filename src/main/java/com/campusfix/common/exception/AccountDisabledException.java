package com.campusfix.common.exception;

/**
 * The credentials were correct but the account is deactivated.
 *
 * <p>Kept separate from {@link AuthenticationFailedException} on purpose. It does
 * reveal that the email exists, which is a small disclosure, but the alternative
 * — telling a deactivated staff member their password is wrong — sends them to
 * reset a password that was never the problem.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
