package com.waqiti.common.exceptions;

/**
 * Exception thrown when account security operations fail
 *
 * Used for security-related errors including:
 * - Account lockouts
 * - Suspicious activity detection
 * - Security policy violations
 * - Authentication/authorization failures
 */
public class AccountSecurityException extends RuntimeException {

    public AccountSecurityException(String message) {
        super(message);
    }

    public AccountSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
