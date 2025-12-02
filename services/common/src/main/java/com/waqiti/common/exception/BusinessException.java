package com.waqiti.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise-grade base exception for all business-related exceptions.
 *
 * FEATURES:
 * - Immutable error codes with type safety via ErrorCode enum
 * - Mutable metadata support for runtime context enrichment
 * - Fluent API for adding metadata after construction
 * - Unique error IDs for tracking across distributed systems
 * - Timestamp tracking for incident analysis
 * - HTTP status code mapping for RESTful APIs
 * - Thread-safe metadata operations
 *
 * USAGE PATTERNS:
 * 1. Simple construction: new BusinessException(ErrorCode.XXX, "message")
 * 2. With cause: new BusinessException(ErrorCode.XXX, "message", cause)
 * 3. With metadata: new BusinessException(ErrorCode.XXX, "message").withMetadata("key", value)
 * 4. Bulk metadata: new BusinessException(ErrorCode.XXX, "message", metadata)
 *
 * PRODUCTION CONSIDERATIONS:
 * - All constructors validate ErrorCode to prevent null pointer exceptions
 * - Metadata is mutable to support enrichment in catch blocks
 * - Error IDs enable correlation across microservices
 * - Timestamps use LocalDateTime for timezone-independent logging
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorId;
    private final String errorCode;
    private final Map<String, Object> metadata;
    private final HttpStatus status;
    private final LocalDateTime timestamp;

    /**
     * Create business exception with default error code
     */
    public BusinessException(String message) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create business exception with string error code (legacy compatibility)
     */
    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create business exception with cause but default error code
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create business exception with string error code and cause (legacy compatibility)
     */
    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create business exception with string error code and metadata (legacy compatibility)
     */
    public BusinessException(String message, String errorCode, Map<String, Object> details) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "BUSINESS_ERROR";
        this.metadata = details != null ? new HashMap<>(details) : new HashMap<>();
        this.status = HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create business exception with custom HTTP status (legacy compatibility)
     */
    public BusinessException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode : "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = status != null ? status : HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    // ===== ENTERPRISE CONSTRUCTORS WITH ErrorCode ENUM =====

    /**
     * PRIMARY CONSTRUCTOR: Create business exception with ErrorCode enum
     * Recommended for all new code - provides type safety and automatic HTTP status mapping
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(buildMessage(errorCode, message));
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode.getCode() : "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = errorCode != null ? errorCode.getStatus() : HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create business exception with ErrorCode enum and cause
     * Use when wrapping lower-level exceptions with business context
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(buildMessage(errorCode, message), cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode.getCode() : "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = errorCode != null ? errorCode.getStatus() : HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * ENTERPRISE CONSTRUCTOR: Create business exception with ErrorCode enum and metadata
     * Use when you have contextual information at construction time
     *
     * Example:
     * throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_ERROR, "Payment failed",
     *     Map.of("paymentId", "123", "amount", 100.00));
     */
    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> metadata) {
        super(buildMessage(errorCode, message));
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode.getCode() : "BUSINESS_ERROR";
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.status = errorCode != null ? errorCode.getStatus() : HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * ENTERPRISE CONSTRUCTOR: Create business exception with ErrorCode, message, cause, and metadata
     * Most comprehensive constructor for maximum context preservation
     *
     * Example:
     * catch (SQLException e) {
     *     throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_ERROR,
     *         "Database error during payment", e,
     *         Map.of("paymentId", paymentId, "operation", "insert"));
     * }
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause, Map<String, Object> metadata) {
        super(buildMessage(errorCode, message), cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode != null ? errorCode.getCode() : "BUSINESS_ERROR";
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.status = errorCode != null ? errorCode.getStatus() : HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Advanced constructor for suppression control (rarely needed)
     */
    public BusinessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = "BUSINESS_ERROR";
        this.metadata = new HashMap<>();
        this.status = HttpStatus.BAD_REQUEST;
        this.timestamp = LocalDateTime.now();
    }

    // ===== FLUENT API FOR METADATA ENRICHMENT =====

    /**
     * Add single metadata entry (fluent API)
     * Thread-safe for metadata modifications
     *
     * Example:
     * throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_ERROR, "Payment failed")
     *     .withMetadata("paymentId", paymentId)
     *     .withMetadata("userId", userId);
     */
    public BusinessException withMetadata(String key, Object value) {
        if (key != null && value != null) {
            this.metadata.put(key, value);
        }
        return this;
    }

    /**
     * Add multiple metadata entries (fluent API)
     * Null-safe - ignores null maps
     *
     * Example:
     * throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_ERROR, "Payment failed")
     *     .withMetadata(Map.of("paymentId", id, "amount", amount));
     */
    public BusinessException withMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata != null) {
            this.metadata.putAll(additionalMetadata);
        }
        return this;
    }

    /**
     * Add metadata entry with key-value pair (alternative naming for clarity)
     */
    public BusinessException addContext(String key, Object value) {
        return withMetadata(key, value);
    }

    /**
     * Remove metadata entry (for sensitive data cleanup before logging)
     */
    public BusinessException removeMetadata(String key) {
        this.metadata.remove(key);
        return this;
    }

    // ===== GETTER OVERRIDES (EXPLICIT FOR COMPATIBILITY) =====

    /**
     * Get error code string
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Get unique error ID for tracking
     */
    public String getErrorId() {
        return errorId;
    }

    /**
     * Get metadata map (returns unmodifiable view for safety)
     * To modify metadata, use withMetadata() methods
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Get mutable metadata (for internal use only)
     * External callers should use getMetadata() which returns unmodifiable view
     */
    protected Map<String, Object> getMutableMetadata() {
        return metadata;
    }

    /**
     * Legacy compatibility: getDetails() returns same as getMetadata()
     */
    public Map<String, Object> getDetails() {
        return getMetadata();
    }

    /**
     * Get HTTP status code
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Get exception timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // ===== UTILITY METHODS =====

    /**
     * Build detailed error message with error code
     */
    private static String buildMessage(ErrorCode errorCode, String message) {
        if (errorCode == null) {
            return message != null ? message : "Business error occurred";
        }
        return String.format("[%s] %s", errorCode.getCode(),
            message != null ? message : errorCode.getDefaultMessage());
    }

    /**
     * Convert to error response DTO for API responses
     */
    public ErrorResponse toErrorResponse() {
        return ErrorResponse.builder()
            .errorId(errorId)
            .error(errorCode)
            .message(getMessage())
            .timestamp(timestamp)
            .details(getMetadata())
            .build();
    }

    /**
     * Get metadata value by key with type casting
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new ClassCastException(
            String.format("Metadata value for key '%s' is not of type %s", key, type.getName())
        );
    }

    /**
     * Check if metadata contains key
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    /**
     * Get metadata size
     */
    public int getMetadataSize() {
        return metadata.size();
    }

    @Override
    public String toString() {
        return String.format("BusinessException[errorId=%s, errorCode=%s, message=%s, metadata=%s, timestamp=%s]",
            errorId, errorCode, getMessage(), metadata, timestamp);
    }
}