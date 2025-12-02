package com.waqiti.security.idor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when ownership validation fails for a resource
 *
 * CRITICAL SECURITY:
 * - Indicates an IDOR (Insecure Direct Object Reference) attack attempt
 * - Maps to HTTP 403 Forbidden (not 404 to prevent resource enumeration)
 * - All instances are automatically logged by SecurityAuditLogger
 * - Triggers security monitoring alerts
 *
 * USAGE:
 * - Thrown by OwnershipValidator when user doesn't own requested resource
 * - Thrown by OwnershipValidationAspect when validation fails
 * - Caught by Spring's exception handler and returned as 403 Forbidden
 *
 * OWASP COMPLIANCE:
 * - OWASP Top 10 A01:2021 - Broken Access Control
 * - Prevents horizontal privilege escalation
 * - Prevents vertical privilege escalation
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Access to this resource is forbidden")
public class OwnershipValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ownership validation exception with the specified detail message.
     *
     * @param message the detail message explaining why ownership validation failed
     */
    public OwnershipValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new ownership validation exception with the specified detail message and cause.
     *
     * @param message the detail message explaining why ownership validation failed
     * @param cause the cause of the validation failure
     */
    public OwnershipValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ownership validation exception with the specified cause.
     *
     * @param cause the cause of the validation failure
     */
    public OwnershipValidationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new ownership validation exception with user and resource details.
     *
     * @param userId the ID of the user who attempted access
     * @param resourceType the type of resource
     * @param resourceId the ID of the resource
     * @return a new OwnershipValidationException with formatted message
     */
    public static OwnershipValidationException forResource(
            java.util.UUID userId,
            String resourceType,
            java.util.UUID resourceId) {

        String message = String.format(
            "User %s is not authorized to access %s with ID %s",
            userId, resourceType, resourceId
        );

        return new OwnershipValidationException(message);
    }

    /**
     * Constructs a new ownership validation exception for permission denial.
     *
     * @param userId the ID of the user who attempted access
     * @param resourceType the type of resource
     * @param resourceId the ID of the resource
     * @param permission the required permission
     * @return a new OwnershipValidationException with formatted message
     */
    public static OwnershipValidationException forPermission(
            java.util.UUID userId,
            String resourceType,
            java.util.UUID resourceId,
            String permission) {

        String message = String.format(
            "User %s lacks %s permission on %s with ID %s",
            userId, permission, resourceType, resourceId
        );

        return new OwnershipValidationException(message);
    }
}
