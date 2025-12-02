package com.waqiti.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced base exception for business logic errors.
 *
 * Features:
 * - Comprehensive error code support
 * - HTTP status code mapping
 * - Additional metadata/context
 * - Retryability indication
 * - Support for user-facing messages
 * - Integration with error tracking
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 2L;

    private final String errorCode;
    private final int statusCode;
    private final Map<String, Object> metadata;
    private final boolean retryable;
    private final String userMessage;

    /**
     * Basic constructor with message
     */
    public BusinessException(String message) {
        super(message);
        this.errorCode = ErrorCode.BIZ_INVALID_OPERATION.getCode();
        this.statusCode = HttpStatus.BAD_REQUEST.value();
        this.metadata = new HashMap<>();
        this.retryable = false;
        this.userMessage = message;
    }

    /**
     * Constructor with error code and message
     */
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = HttpStatus.BAD_REQUEST.value();
        this.metadata = new HashMap<>();
        this.retryable = false;
        this.userMessage = message;
    }

    /**
     * Constructor with error code, message, and status code
     */
    public BusinessException(String errorCode, String message, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.metadata = new HashMap<>();
        this.retryable = false;
        this.userMessage = message;
    }

    /**
     * Constructor with error code, message, and cause
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = HttpStatus.BAD_REQUEST.value();
        this.metadata = new HashMap<>();
        this.retryable = false;
        this.userMessage = message;
    }

    /**
     * Constructor with error code, message, status code, and cause
     */
    public BusinessException(String errorCode, String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.metadata = new HashMap<>();
        this.retryable = false;
        this.userMessage = message;
    }

    /**
     * Full constructor with all parameters
     */
    public BusinessException(String errorCode, String message, int statusCode,
                            Map<String, Object> metadata, boolean retryable, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.retryable = retryable;
        this.userMessage = userMessage != null ? userMessage : message;
    }

    /**
     * Constructor from ErrorCode enum
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode.getCode();
        this.statusCode = errorCode.getStatusCode();
        this.metadata = new HashMap<>();
        this.retryable = errorCode.isRetryable();
        this.userMessage = message != null ? message : errorCode.getDefaultMessage();
    }

    /**
     * Constructor from ErrorCode enum with cause
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
        this.statusCode = errorCode.getStatusCode();
        this.metadata = new HashMap<>();
        this.retryable = errorCode.isRetryable();
        this.userMessage = message != null ? message : errorCode.getDefaultMessage();
    }

    /**
     * Add metadata to the exception
     */
    public BusinessException withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Add multiple metadata entries
     */
    public BusinessException withMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata != null) {
            this.metadata.putAll(additionalMetadata);
        }
        return this;
    }

    // ===== Static Factory Methods =====

    /**
     * Create a business exception with 400 Bad Request status
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(
            ErrorCode.BIZ_INVALID_OPERATION,
            message
        );
    }

    /**
     * Create a business exception with 409 Conflict status
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(
            "BIZ_CONFLICT",
            message,
            HttpStatus.CONFLICT.value()
        );
    }

    /**
     * Create a business exception with 422 Unprocessable Entity status
     */
    public static BusinessException unprocessableEntity(String message) {
        return new BusinessException(
            "BIZ_UNPROCESSABLE",
            message,
            HttpStatus.UNPROCESSABLE_ENTITY.value()
        );
    }

    /**
     * Create a business exception with 503 Service Unavailable status
     */
    public static BusinessException serviceUnavailable(String message) {
        return new BusinessException(
            ErrorCode.SYS_SERVICE_UNAVAILABLE,
            message
        );
    }

    /**
     * Create exception for invalid operation
     */
    public static BusinessException invalidOperation(String message) {
        return new BusinessException(ErrorCode.BIZ_INVALID_OPERATION, message);
    }

    /**
     * Create exception for business rule violation
     */
    public static BusinessException ruleViolation(String message) {
        return new BusinessException(ErrorCode.BIZ_RULE_VIOLATION, message);
    }

    /**
     * Create exception for limit exceeded
     */
    public static BusinessException limitExceeded(String message) {
        return new BusinessException(ErrorCode.BIZ_LIMIT_EXCEEDED, message);
    }

    /**
     * Create exception for unauthorized operation
     */
    public static BusinessException notAuthorized(String message) {
        return new BusinessException(ErrorCode.BIZ_NOT_AUTHORIZED, message);
    }

    /**
     * Create exception for state transition error
     */
    public static BusinessException invalidStateTransition(String currentState, String targetState) {
        return new BusinessException(
            ErrorCode.BIZ_STATE_TRANSITION_INVALID,
            String.format("Cannot transition from %s to %s", currentState, targetState)
        ).withMetadata("currentState", currentState)
         .withMetadata("targetState", targetState);
    }

    /**
     * Create exception for precondition failure
     */
    public static BusinessException preconditionFailed(String message) {
        return new BusinessException(
            ErrorCode.BIZ_PRECONDITION_FAILED,
            message
        );
    }

    /**
     * Create exception for duplicate request
     */
    public static BusinessException duplicateRequest(String requestId) {
        return new BusinessException(
            ErrorCode.BIZ_DUPLICATE_REQUEST,
            "Duplicate request detected"
        ).withMetadata("requestId", requestId);
    }

    /**
     * Create exception for locked resource
     */
    public static BusinessException resourceLocked(String resourceType, String resourceId) {
        return new BusinessException(
            ErrorCode.BIZ_RESOURCE_LOCKED,
            String.format("%s is currently locked", resourceType)
        ).withMetadata("resourceType", resourceType)
         .withMetadata("resourceId", resourceId);
    }

    /**
     * Get HTTP status as HttpStatus enum
     */
    public HttpStatus getHttpStatus() {
        return HttpStatus.valueOf(statusCode);
    }

    /**
     * Check if exception indicates a client error
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Check if exception indicates a server error
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    @Override
    public String toString() {
        return String.format("BusinessException[errorCode=%s, statusCode=%d, message=%s, retryable=%s]",
            errorCode, statusCode, getMessage(), retryable);
    }
}

