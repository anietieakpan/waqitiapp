package com.waqiti.common.exceptions;

/**
 * Exception thrown when a user attempts to access a resource they don't have permission for
 *
 * This exception is used by the RBAC authorization system to indicate
 * insufficient permissions for an operation.
 *
 * @author Waqiti Security Team
 */
public class AccessDeniedException extends RuntimeException {

    private final String userId;
    private final String resource;
    private final String action;
    private final String requiredPermission;

    public AccessDeniedException(String message) {
        super(message);
        this.userId = null;
        this.resource = null;
        this.action = null;
        this.requiredPermission = null;
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
        this.userId = null;
        this.resource = null;
        this.action = null;
        this.requiredPermission = null;
    }

    public AccessDeniedException(String userId, String resource, String action, String requiredPermission) {
        super(String.format("Access denied for user '%s' to '%s' resource '%s'. Required permission: %s",
            userId, action, resource, requiredPermission));
        this.userId = userId;
        this.resource = resource;
        this.action = action;
        this.requiredPermission = requiredPermission;
    }

    public String getUserId() {
        return userId;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}
