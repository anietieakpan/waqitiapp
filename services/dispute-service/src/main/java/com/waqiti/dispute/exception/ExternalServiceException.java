package com.waqiti.dispute.exception;

/**
 * Exception thrown when external service call fails with a non-retriable error.
 * Indicates permanent failure that should not be retried.
 *
 * Examples:
 * - 400 Bad Request (invalid request data)
 * - 401 Unauthorized (invalid credentials)
 * - 403 Forbidden (insufficient permissions)
 * - 404 Not Found (resource doesn't exist)
 * - 422 Unprocessable Entity (business rule violation)
 *
 * HTTP Status: 503 Service Unavailable (or pass through external service status)
 *
 * @author Waqiti Development Team
 * @since 1.0.0
 */
public class ExternalServiceException extends DisputeServiceException {

    private final int statusCode;
    private final String serviceName;

    public ExternalServiceException(String message, int statusCode) {
        super(message, "EXTERNAL_SERVICE_ERROR", 503);
        this.statusCode = statusCode;
        this.serviceName = "UNKNOWN";
    }

    public ExternalServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause, "EXTERNAL_SERVICE_ERROR", 503);
        this.statusCode = statusCode;
        this.serviceName = "UNKNOWN";
    }

    public ExternalServiceException(String serviceName, String message, int statusCode) {
        super(String.format("%s service error: %s", serviceName, message), "EXTERNAL_SERVICE_ERROR", 503);
        this.statusCode = statusCode;
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String serviceName, String message, int statusCode, Throwable cause) {
        super(String.format("%s service error: %s", serviceName, message), cause, "EXTERNAL_SERVICE_ERROR", 503);
        this.statusCode = statusCode;
        this.serviceName = serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getServiceName() {
        return serviceName;
    }
}

