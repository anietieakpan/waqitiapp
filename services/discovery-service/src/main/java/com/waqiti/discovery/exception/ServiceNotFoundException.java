package com.waqiti.discovery.exception;

/**
 * Exception thrown when a service is not found in the registry
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(String message) {
        super(message);
    }

    public ServiceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
