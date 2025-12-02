package com.waqiti.common.security.authorization;

/**
 * Exception thrown when an invalid resource ID is provided for authorization checks.
 *
 * Security Enhancement (2025-10-18):
 * - Prevents information disclosure through exception messages
 * - Used in conjunction with UUID validation to prevent injection attacks
 * - Provides clear security audit trail
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
public class InvalidResourceIdException extends SecurityException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new InvalidResourceIdException with the specified detail message.
     *
     * @param message the detail message (sanitized to prevent information leakage)
     */
    public InvalidResourceIdException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidResourceIdException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public InvalidResourceIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
