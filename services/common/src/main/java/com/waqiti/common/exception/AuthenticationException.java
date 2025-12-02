package com.waqiti.common.exception;

/**
 * Exception thrown when authentication fails.
 * This is a business exception that should not trigger circuit breaker.
 */
public class AuthenticationException extends SecurityException {

    private final String username;
    private final String reason;

    public AuthenticationException(String message) {
        super(message);
        this.username = null;
        this.reason = null;
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.username = null;
        this.reason = null;
    }

    public AuthenticationException(String message, String username, String reason) {
        super(message);
        this.username = username;
        this.reason = reason;
    }

    public String getUsername() {
        return username;
    }

    public String getReason() {
        return reason;
    }
}
