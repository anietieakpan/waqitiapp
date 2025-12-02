package com.waqiti.familyaccount.exception;

/**
 * External Service Exception
 *
 * Thrown when an external service call fails (user-service, wallet-service, etc.)
 * Indicates a problem with service-to-service communication.
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
public class ExternalServiceException extends RuntimeException {

    private final String serviceName;

    public ExternalServiceException(String message) {
        super(message);
        this.serviceName = "unknown";
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
        this.serviceName = "unknown";
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
