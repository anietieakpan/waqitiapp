package com.waqiti.card.exception;

/**
 * Exception for resource not found scenarios
 */
public class ResourceNotFoundException extends CardServiceException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
