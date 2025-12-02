package com.waqiti.common.exception;

/**
 * Integration Processing Exception
 * Thrown when integration processing fails
 */
public class IntegrationProcessingException extends RuntimeException {

    public IntegrationProcessingException(String message) {
        super(message);
    }

    public IntegrationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
