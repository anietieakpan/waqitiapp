package com.waqiti.security.rbac.exception;

/**
 * Base exception for RBAC-related errors
 */
public class RBACException extends RuntimeException {

    public RBACException(String message) {
        super(message);
    }

    public RBACException(String message, Throwable cause) {
        super(message, cause);
    }
}
