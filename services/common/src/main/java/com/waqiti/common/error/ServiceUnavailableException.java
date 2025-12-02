package com.waqiti.common.error;

import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a service is temporarily unavailable.
 *
 * Features:
 * - Retry-After header support
 * - Service name tracking
 * - Maintenance mode indication
 * - Circuit breaker integration
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Getter
public class ServiceUnavailableException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String serviceName;
    private final int retryAfterSeconds;
    private final boolean maintenanceMode;

    @Getter(AccessLevel.NONE) // Prevent Lombok from generating getErrorCode() - parent has it
    private final ErrorCode errorCodeEnum;

    /**
     * Basic constructor with message
     */
    public ServiceUnavailableException(String message) {
        super(
            ErrorCode.SYS_SERVICE_UNAVAILABLE.getCode(),
            message,
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        this.serviceName = null;
        this.retryAfterSeconds = 60; // Default 60 seconds
        this.maintenanceMode = false;
        this.errorCodeEnum = ErrorCode.SYS_SERVICE_UNAVAILABLE;
    }

    /**
     * Constructor with message and retry-after
     */
    public ServiceUnavailableException(String message, int retryAfterSeconds) {
        super(
            ErrorCode.SYS_SERVICE_UNAVAILABLE.getCode(),
            message,
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        this.serviceName = null;
        this.retryAfterSeconds = retryAfterSeconds;
        this.maintenanceMode = false;
        this.errorCodeEnum = ErrorCode.SYS_SERVICE_UNAVAILABLE;
    }

    /**
     * Constructor with service name and message
     */
    public ServiceUnavailableException(String serviceName, String message) {
        super(
            ErrorCode.SYS_SERVICE_UNAVAILABLE.getCode(),
            String.format("Service '%s' is unavailable: %s", serviceName, message),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        this.serviceName = serviceName;
        this.retryAfterSeconds = 60;
        this.maintenanceMode = false;
        this.errorCodeEnum = ErrorCode.SYS_SERVICE_UNAVAILABLE;
        withMetadata("serviceName", serviceName);
    }

    /**
     * Constructor with service name, message, and retry-after
     */
    public ServiceUnavailableException(String serviceName, String message, int retryAfterSeconds) {
        super(
            ErrorCode.SYS_SERVICE_UNAVAILABLE.getCode(),
            String.format("Service '%s' is unavailable: %s", serviceName, message),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
        this.maintenanceMode = false;
        this.errorCodeEnum = ErrorCode.SYS_SERVICE_UNAVAILABLE;
        withMetadata("serviceName", serviceName);
        withMetadata("retryAfterSeconds", retryAfterSeconds);
    }

    /**
     * Full constructor with all parameters
     */
    public ServiceUnavailableException(ErrorCode errorCode, String serviceName, String message,
                                      int retryAfterSeconds, boolean maintenanceMode) {
        super(
            errorCode.getCode(),
            message,
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        this.errorCodeEnum = errorCode;
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
        this.maintenanceMode = maintenanceMode;

        if (serviceName != null) {
            withMetadata("serviceName", serviceName);
        }
        withMetadata("retryAfterSeconds", retryAfterSeconds);
        withMetadata("maintenanceMode", maintenanceMode);
    }

    /**
     * Constructor with cause
     */
    public ServiceUnavailableException(String serviceName, String message, Throwable cause) {
        super(
            ErrorCode.SYS_SERVICE_UNAVAILABLE.getCode(),
            String.format("Service '%s' is unavailable: %s", serviceName, message),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            cause
        );
        this.serviceName = serviceName;
        this.retryAfterSeconds = 60;
        this.maintenanceMode = false;
        this.errorCodeEnum = ErrorCode.SYS_SERVICE_UNAVAILABLE;
        withMetadata("serviceName", serviceName);
    }

    // ===== Static Factory Methods =====

    /**
     * Create exception for maintenance mode
     */
    public static ServiceUnavailableException maintenanceMode() {
        return new ServiceUnavailableException(
            ErrorCode.SYS_MAINTENANCE_MODE,
            null,
            "System is under maintenance. Please try again later.",
            300, // 5 minutes
            true
        );
    }

    /**
     * Create exception for maintenance mode with custom retry time
     */
    public static ServiceUnavailableException maintenanceMode(int retryAfterSeconds) {
        return new ServiceUnavailableException(
            ErrorCode.SYS_MAINTENANCE_MODE,
            null,
            "System is under maintenance. Please try again later.",
            retryAfterSeconds,
            true
        );
    }

    /**
     * Create exception for circuit breaker open
     */
    public static ServiceUnavailableException circuitBreakerOpen(String serviceName) {
        return new ServiceUnavailableException(
            ErrorCode.SYS_CIRCUIT_BREAKER_OPEN,
            serviceName,
            String.format("Circuit breaker is open for service '%s'", serviceName),
            30, // 30 seconds
            false
        );
    }

    /**
     * Create exception for circuit breaker open with custom retry
     */
    public static ServiceUnavailableException circuitBreakerOpen(String serviceName, int retryAfterSeconds) {
        return new ServiceUnavailableException(
            ErrorCode.SYS_CIRCUIT_BREAKER_OPEN,
            serviceName,
            String.format("Circuit breaker is open for service '%s'", serviceName),
            retryAfterSeconds,
            false
        );
    }

    /**
     * Create exception for resource exhaustion
     */
    public static ServiceUnavailableException resourceExhausted(String resourceType) {
        ServiceUnavailableException exception = new ServiceUnavailableException(
            ErrorCode.SYS_RESOURCE_EXHAUSTED,
            null,
            String.format("System resource exhausted: %s", resourceType),
            60,
            false
        );
        exception.withMetadata("resourceType", resourceType);
        return exception;
    }

    /**
     * Create exception for connection pool exhaustion
     */
    public static ServiceUnavailableException connectionPoolExhausted() {
        return new ServiceUnavailableException(
            ErrorCode.SYS_CONNECTION_POOL_EXHAUSTED,
            null,
            "Connection pool exhausted. Please try again.",
            30,
            false
        );
    }

    /**
     * Create exception for queue full
     */
    public static ServiceUnavailableException queueFull(String queueName) {
        ServiceUnavailableException exception = new ServiceUnavailableException(
            ErrorCode.SYS_QUEUE_FULL,
            null,
            String.format("Message queue '%s' is full", queueName),
            60,
            false
        );
        exception.withMetadata("queueName", queueName);
        return exception;
    }

    /**
     * Create exception for dependency failure
     */
    public static ServiceUnavailableException dependencyFailed(String dependencyName) {
        return new ServiceUnavailableException(
            ErrorCode.SYS_DEPENDENCY_FAILED,
            dependencyName,
            String.format("Dependent service '%s' failed", dependencyName),
            60,
            false
        );
    }

    /**
     * Create exception for dependency failure with cause
     */
    public static ServiceUnavailableException dependencyFailed(String dependencyName, Throwable cause) {
        ServiceUnavailableException exception = new ServiceUnavailableException(
            dependencyName,
            String.format("Dependent service '%s' failed", dependencyName),
            cause
        );
        exception.withMetadata("retryAfterSeconds", 60);
        return exception;
    }

    /**
     * Create exception for rate limit (internal)
     */
    public static ServiceUnavailableException rateLimited(int retryAfterSeconds) {
        return new ServiceUnavailableException(
            ErrorCode.SYS_RATE_LIMIT_INTERNAL,
            null,
            "Internal rate limit exceeded",
            retryAfterSeconds,
            false
        );
    }

    /**
     * Create exception for storage full
     */
    public static ServiceUnavailableException storageFull() {
        return new ServiceUnavailableException(
            ErrorCode.SYS_STORAGE_FULL,
            null,
            "Storage capacity full",
            -1, // No retry
            false
        );
    }

    /**
     * Create exception for timeout
     */
    public static ServiceUnavailableException timeout(String operation) {
        ServiceUnavailableException exception = new ServiceUnavailableException(
            ErrorCode.SYS_TIMEOUT,
            null,
            String.format("Operation '%s' timed out", operation),
            30,
            false
        );
        exception.withMetadata("operation", operation);
        return exception;
    }

    /**
     * Create exception for external service unavailable
     */
    public static ServiceUnavailableException externalServiceUnavailable(String serviceName) {
        return new ServiceUnavailableException(
            ErrorCode.INT_SERVICE_UNAVAILABLE,
            serviceName,
            String.format("External service '%s' is unavailable", serviceName),
            60,
            false
        );
    }

    /**
     * Create exception for external service unavailable with retry
     */
    public static ServiceUnavailableException externalServiceUnavailable(
            String serviceName, int retryAfterSeconds) {
        return new ServiceUnavailableException(
            ErrorCode.INT_SERVICE_UNAVAILABLE,
            serviceName,
            String.format("External service '%s' is unavailable", serviceName),
            retryAfterSeconds,
            false
        );
    }

    /**
     * Get the ErrorCode enum for this service unavailable exception
     */
    public ErrorCode getErrorCodeEnum() {
        return errorCodeEnum;
    }

    /**
     * Check if retry is recommended
     */
    public boolean shouldRetry() {
        return retryAfterSeconds > 0;
    }

    @Override
    public String toString() {
        return String.format(
            "ServiceUnavailableException[errorCode=%s, serviceName=%s, retryAfterSeconds=%d, maintenanceMode=%s, message=%s]",
            getErrorCode(), serviceName, retryAfterSeconds, maintenanceMode, getMessage()
        );
    }
}
