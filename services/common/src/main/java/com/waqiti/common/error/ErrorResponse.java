package com.waqiti.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RFC 7807 compliant error response structure for all API endpoints.
 *
 * Provides consistent error information for client applications with:
 * - Problem Details for HTTP APIs (RFC 7807)
 * - Correlation ID tracking
 * - Distributed tracing integration
 * - I18n support
 * - PCI-DSS compliance (no sensitive data)
 *
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a>
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * RFC 7807: A URI reference that identifies the problem type
     * Example: "https://api.example.com/errors/validation-failed"
     */
    @JsonProperty("type")
    private String type;

    /**
     * RFC 7807: A short, human-readable summary of the problem type
     */
    @JsonProperty("title")
    private String title;

    /**
     * RFC 7807: The HTTP status code
     */
    @JsonProperty("status")
    private int status;

    /**
     * RFC 7807: A human-readable explanation specific to this occurrence
     */
    @JsonProperty("detail")
    private String detail;

    /**
     * RFC 7807: A URI reference that identifies the specific occurrence
     * Typically the request path
     */
    @JsonProperty("instance")
    private String instance;

    /**
     * Timestamp when the error occurred (ISO-8601 format)
     */
    @JsonProperty("timestamp")
    private ZonedDateTime timestamp;

    /**
     * Error category/type (legacy support)
     */
    @JsonProperty("error")
    private String error;

    /**
     * Human-readable error message (legacy support)
     */
    @JsonProperty("message")
    private String message;

    /**
     * Request path that caused the error (legacy support)
     */
    @JsonProperty("path")
    private String path;

    /**
     * Application-specific error code for programmatic handling
     * Format: MODULE_CATEGORY_SPECIFIC_ERROR (e.g., AUTH_001, PAYMENT_004)
     */
    @JsonProperty("errorCode")
    private String code;

    /**
     * Unique correlation ID for request tracking across microservices
     */
    @JsonProperty("correlationId")
    private String correlationId;

    /**
     * Distributed trace ID from OpenTelemetry/Jaeger
     */
    @JsonProperty("traceId")
    private String traceId;

    /**
     * Span ID from distributed tracing
     */
    @JsonProperty("spanId")
    private String spanId;

    /**
     * Unique error identifier for debugging and support
     */
    @JsonProperty("errorId")
    private String errorId;

    /**
     * Additional error details (validation errors, field-specific messages, etc.)
     */
    @JsonProperty("details")
    private Map<String, Object> details;

    /**
     * Stack trace (only included in development/debug mode)
     * Never included in production for security
     */
    @JsonProperty("stackTrace")
    private String stackTrace;

    /**
     * Request ID for correlation with logs
     */
    @JsonProperty("requestId")
    private String requestId;

    /**
     * Service name that generated the error
     */
    @JsonProperty("service")
    private String service;

    /**
     * Error severity level
     */
    @JsonProperty("severity")
    private ErrorSeverity severity;

    /**
     * Suggested actions for the client to resolve the error
     */
    @JsonProperty("suggestions")
    private List<String> suggestions;

    /**
     * Documentation URL for this error type
     */
    @JsonProperty("documentationUrl")
    private String documentationUrl;

    /**
     * Retry-after seconds for rate limiting errors
     */
    @JsonProperty("retryAfter")
    private Integer retryAfter;

    /**
     * Additional metadata specific to the error
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * Validation errors for field-level validation failures
     */
    @JsonProperty("validationErrors")
    private Map<String, List<String>> validationErrors;

    /**
     * Error severity enumeration
     */
    public enum ErrorSeverity {
        LOW,       // Informational errors, user can continue
        MEDIUM,    // Warnings that should be addressed
        HIGH,      // Errors that require attention
        CRITICAL   // Critical errors requiring immediate action
    }

    /**
     * Converts to legacy format for backward compatibility
     */
    public ErrorResponse toLegacyFormat() {
        ErrorResponse legacy = new ErrorResponse();
        legacy.timestamp = this.timestamp;
        legacy.status = this.status;
        legacy.error = this.title != null ? this.title : this.error;
        legacy.message = this.detail != null ? this.detail : this.message;
        legacy.path = this.instance != null ? this.instance : this.path;
        legacy.code = this.code;
        legacy.traceId = this.traceId;
        legacy.errorId = this.errorId;
        legacy.details = this.details;
        legacy.service = this.service;
        legacy.severity = this.severity;
        legacy.documentationUrl = this.documentationUrl;
        return legacy;
    }

    /**
     * Adds a suggestion to the response
     */
    public void addSuggestion(String suggestion) {
        if (this.suggestions == null) {
            this.suggestions = new ArrayList<>();
        }
        this.suggestions.add(suggestion);
    }

    /**
     * Adds a validation error for a specific field
     */
    public void addValidationError(String field, String error) {
        if (this.validationErrors == null) {
            this.validationErrors = new java.util.HashMap<>();
        }
        this.validationErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(error);
    }

    /**
     * Checks if this is a client error (4xx)
     */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }

    /**
     * Checks if this is a server error (5xx)
     */
    public boolean isServerError() {
        return status >= 500 && status < 600;
    }

    /**
     * Gets the error category based on status code
     */
    public String getErrorCategory() {
        if (status >= 400 && status < 500) {
            return "CLIENT_ERROR";
        } else if (status >= 500 && status < 600) {
            return "SERVER_ERROR";
        }
        return "UNKNOWN";
    }
}