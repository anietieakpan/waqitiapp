package com.waqiti.common.security.awareness.exception;

/**
 * Base exception for Security Awareness module
 *
 * @author Waqiti Platform Team
 */
public class SecurityAwarenessException extends RuntimeException {

    public SecurityAwarenessException(String message) {
        super(message);
    }

    public SecurityAwarenessException(String message, Throwable cause) {
        super(message, cause);
    }
}