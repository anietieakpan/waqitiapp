package com.waqiti.security.rbac.exception;

/**
 * Exception thrown when a role is not found
 */
public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String message) {
        super(message);
    }

    public RoleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
