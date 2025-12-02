package com.waqiti.bnpl.exception;

/**
 * Exception thrown when resource is not found
 */
public class ResourceNotFoundException extends BnplException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}