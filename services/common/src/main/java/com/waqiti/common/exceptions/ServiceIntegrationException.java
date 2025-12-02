package com.waqiti.common.exceptions;

/**
 * Exception thrown when service integration fails
 */
public class ServiceIntegrationException extends RuntimeException {

    public ServiceIntegrationException(String message) {
        super(message);
    }

    public ServiceIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
