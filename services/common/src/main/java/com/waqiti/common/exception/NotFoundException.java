package com.waqiti.common.exception;

/**
 * Exception thrown when a requested resource cannot be found.
 * This is a business exception that should not trigger circuit breaker.
 */
public class NotFoundException extends ResourceNotFoundException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotFoundException(String resourceType, String resourceId) {
        super(String.format("%s not found: %s", resourceType, resourceId));
    }
}
