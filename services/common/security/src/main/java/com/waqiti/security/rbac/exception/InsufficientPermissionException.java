package com.waqiti.security.rbac.exception;

/**
 * Exception thrown when user lacks required permissions
 */
public class InsufficientPermissionException extends RBACException {

    public InsufficientPermissionException(String message) {
        super(message);
    }

    public InsufficientPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
