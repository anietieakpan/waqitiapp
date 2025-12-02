package com.waqiti.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * System-level exception for infrastructure and technical failures
 *
 * Used for:
 * - Database connection failures
 * - External service unavailability
 * - Configuration errors
 * - System resource exhaustion
 * - Infrastructure-level failures
 *
 * Unlike BusinessException (which represents business rule violations),
 * SystemException represents technical/infrastructure failures that are
 * typically not caused by user input.
 *
 * FEATURES:
 * - Unique error IDs for incident tracking
 * - Mutable metadata support for runtime context
 * - HTTP status code mapping (defaults to 500 Internal Server Error)
 * - Timestamp tracking for incident analysis
 * - Thread-safe metadata operations
 *
 * USAGE:
 * throw new SystemException("Database connection failed", cause);
 * throw new SystemException("SYS_DB_001", "Database unavailable", cause);
 */
@Getter
public class SystemException extends RuntimeException {

    private final String errorId;
    private final String errorCode;
    private final Map<String, Object> metadata;
    private final HttpStatus status;
    private final LocalDateTime timestamp;
    private final Severity severity;

    /**
     * Severity levels for system exceptions
     */
    public enum Severity {
        LOW,        // Minor issues, service degraded but functional
        MEDIUM,     // Significant issues, some features unavailable
        HIGH,       // Critical issues, service partially down
        CRITICAL    // System-wide failure, immediate action required
    }

    /**
     * Create system exception with default error code
     *
     * @param message The error message
     */
    public SystemException(String message) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = "SYS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.timestamp = LocalDateTime.now();
        this.severity = Severity.HIGH;
    }

    /**
     * Create system exception with default error code and cause
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = "SYS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.timestamp = LocalDateTime.now();
        this.severity = Severity.HIGH;
    }

    /**
     * Create system exception with specific error code
     *
     * @param errorCode The error code
     * @param message The error message
     */
    public SystemException(String errorCode, String message) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "SYS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.timestamp = LocalDateTime.now();
        this.severity = Severity.HIGH;
    }

    /**
     * Create system exception with specific error code and cause
     *
     * @param errorCode The error code
     * @param message The error message
     * @param cause The underlying cause
     */
    public SystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "SYS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.timestamp = LocalDateTime.now();
        this.severity = Severity.HIGH;
    }

    /**
     * Create system exception with specific error code, message, and HTTP status
     *
     * @param errorCode The error code
     * @param message The error message
     * @param status The HTTP status code
     */
    public SystemException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "SYS_ERROR";
        this.metadata = new HashMap<>();
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.timestamp = LocalDateTime.now();
        this.severity = Severity.HIGH;
    }

    /**
     * Create system exception with all parameters
     *
     * @param errorCode The error code
     * @param message The error message
     * @param cause The underlying cause
     * @param status The HTTP status code
     * @param severity The severity level
     */
    public SystemException(String errorCode, String message, Throwable cause, HttpStatus status, Severity severity) {
        super(message, cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "SYS_ERROR";
        this.metadata = new HashMap<>();
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.timestamp = LocalDateTime.now();
        this.severity = severity != null ? severity : Severity.HIGH;
    }

    /**
     * Add metadata entry (fluent API)
     *
     * @param key The metadata key
     * @param value The metadata value
     * @return This exception instance for chaining
     */
    public SystemException withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Add multiple metadata entries (fluent API)
     *
     * @param additionalMetadata The metadata map to add
     * @return This exception instance for chaining
     */
    public SystemException withMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata != null) {
            this.metadata.putAll(additionalMetadata);
        }
        return this;
    }

    /**
     * Get immutable copy of metadata
     *
     * @return Unmodifiable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Get mutable metadata map for internal use
     * Package-private to allow exception handlers to enrich context
     *
     * @return Mutable metadata map
     */
    Map<String, Object> getMutableMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return String.format(
                "SystemException{errorId='%s', errorCode='%s', message='%s', status=%s, severity=%s, timestamp=%s, metadata=%s}",
                errorId, errorCode, getMessage(), status, severity, timestamp, metadata
        );
    }

    /**
     * Factory method for database-related system exceptions
     */
    public static SystemException database(String message, Throwable cause) {
        return new SystemException("SYS_DB_ERROR", message, cause, HttpStatus.INTERNAL_SERVER_ERROR, Severity.CRITICAL);
    }

    /**
     * Factory method for external service failures
     */
    public static SystemException externalService(String serviceName, String message, Throwable cause) {
        return new SystemException("SYS_EXT_SERVICE_ERROR", message, cause)
                .withMetadata("serviceName", serviceName);
    }

    /**
     * Factory method for configuration errors
     */
    public static SystemException configuration(String message) {
        return new SystemException("SYS_CONFIG_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Factory method for resource exhaustion
     */
    public static SystemException resourceExhausted(String resource, String message) {
        return new SystemException("SYS_RESOURCE_EXHAUSTED", message, HttpStatus.SERVICE_UNAVAILABLE)
                .withMetadata("resource", resource);
    }
}
