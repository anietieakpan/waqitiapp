package com.waqiti.common.dto.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Standard Error Response for all Waqiti Platform APIs
 *
 * <p>This is the canonical error response format used across all microservices
 * in the Waqiti platform. It provides consistent error handling, comprehensive
 * error details, and support for distributed tracing.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>RFC 7807 Problem Details for HTTP APIs compliance</li>
 *   <li>Distributed tracing support (correlationId, traceId)</li>
 *   <li>Validation error details with field-level granularity</li>
 *   <li>Business rule violation support</li>
 *   <li>Security-aware (no sensitive data exposure)</li>
 *   <li>Environment-aware (stack traces only in dev/test)</li>
 *   <li>I18n support for error messages</li>
 * </ul>
 *
 * <p><b>HTTP Status Code Categories:</b>
 * <ul>
 *   <li>400 Bad Request - Validation errors, malformed requests</li>
 *   <li>401 Unauthorized - Authentication required</li>
 *   <li>403 Forbidden - Insufficient permissions</li>
 *   <li>404 Not Found - Resource not found</li>
 *   <li>409 Conflict - Resource conflict (e.g., duplicate)</li>
 *   <li>422 Unprocessable Entity - Business rule violation</li>
 *   <li>429 Too Many Requests - Rate limit exceeded</li>
 *   <li>500 Internal Server Error - Unexpected server error</li>
 *   <li>502 Bad Gateway - Downstream service error</li>
 *   <li>503 Service Unavailable - Service temporarily unavailable</li>
 *   <li>504 Gateway Timeout - Downstream service timeout</li>
 * </ul>
 *
 * <p><b>Error Code Format:</b>
 * Error codes follow the pattern: <code>{DOMAIN}_{CATEGORY}_{SPECIFIC}</code>
 * <ul>
 *   <li>PAYMENT_VALIDATION_AMOUNT_INVALID</li>
 *   <li>WALLET_BUSINESS_INSUFFICIENT_BALANCE</li>
 *   <li>USER_AUTH_INVALID_CREDENTIALS</li>
 *   <li>FRAUD_SECURITY_SUSPICIOUS_ACTIVITY</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * // Simple error
 * ErrorResponse error = ErrorResponse.builder()
 *     .errorCode("USER_NOT_FOUND")
 *     .message("User not found")
 *     .status(404)
 *     .path("/api/users/123")
 *     .correlationId(UUID.randomUUID().toString())
 *     .build();
 *
 * // Validation error
 * ErrorResponse error = ErrorResponse.validationError()
 *     .message("Invalid request")
 *     .path("/api/payments")
 *     .correlationId(correlationId)
 *     .addFieldError("amount", "Must be greater than 0", -100)
 *     .addFieldError("currency", "Invalid currency code", "XYZ")
 *     .build();
 *
 * // Business rule violation
 * ErrorResponse error = ErrorResponse.businessRuleViolation()
 *     .errorCode("WALLET_INSUFFICIENT_BALANCE")
 *     .message("Insufficient balance for transaction")
 *     .userFriendlyMessage("You don't have enough funds")
 *     .path("/api/wallet/transfer")
 *     .correlationId(correlationId)
 *     .retryable(false)
 *     .addDetail("currentBalance", 100.00)
 *     .addDetail("requiredAmount", 150.00)
 *     .build();
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 3.0.0
 * @since 2025-11-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response for all Waqiti APIs")
public class ErrorResponse {

    /**
     * Unique error code for categorization and client-side handling.
     * Format: {DOMAIN}_{CATEGORY}_{SPECIFIC}
     * Example: PAYMENT_VALIDATION_AMOUNT_INVALID
     */
    @Schema(description = "Error code for categorization", example = "PAYMENT_VALIDATION_AMOUNT_INVALID")
    @JsonProperty("errorCode")
    private String errorCode;

    /**
     * HTTP status code
     */
    @Schema(description = "HTTP status code", example = "400")
    @JsonProperty("status")
    private int status;

    /**
     * HTTP status text (e.g., "Bad Request", "Not Found")
     */
    @Schema(description = "HTTP status text", example = "Bad Request")
    @JsonProperty("error")
    private String error;

    /**
     * Technical error message for developers
     * Should be clear and actionable
     */
    @Schema(description = "Technical error message", example = "Amount must be greater than 0")
    @JsonProperty("message")
    private String message;

    /**
     * User-friendly error message for display to end users
     * Simplified, localized, and non-technical
     */
    @Schema(description = "User-friendly error message", example = "Please enter a valid amount")
    @JsonProperty("userFriendlyMessage")
    private String userFriendlyMessage;

    /**
     * Request path where error occurred
     */
    @Schema(description = "Request path", example = "/api/v1/payments")
    @JsonProperty("path")
    private String path;

    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    @Schema(description = "HTTP method", example = "POST")
    @JsonProperty("method")
    private String method;

    /**
     * Timestamp when error occurred (ISO-8601 format)
     */
    @Schema(description = "Error timestamp", example = "2025-11-23T10:15:30.123Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Correlation ID for distributed tracing
     * Links related operations across microservices
     */
    @Schema(description = "Correlation ID for tracing", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    @JsonProperty("correlationId")
    private String correlationId;

    /**
     * Distributed trace ID (OpenTelemetry/Zipkin)
     */
    @Schema(description = "Distributed trace ID", example = "1234567890abcdef")
    @JsonProperty("traceId")
    private String traceId;

    /**
     * Span ID for distributed tracing
     */
    @Schema(description = "Span ID", example = "abcdef1234567890")
    @JsonProperty("spanId")
    private String spanId;

    /**
     * Service name that generated the error
     */
    @Schema(description = "Service name", example = "payment-service")
    @JsonProperty("service")
    private String service;

    /**
     * Instance ID of the service (for debugging)
     */
    @Schema(description = "Service instance ID", example = "payment-service-7d8f9g0h1i")
    @JsonProperty("instance")
    private String instance;

    /**
     * Validation field errors (for 400 Bad Request)
     * Contains detailed information about each invalid field
     */
    @Schema(description = "Field validation errors")
    @JsonProperty("fieldErrors")
    @Builder.Default
    private List<FieldError> fieldErrors = new ArrayList<>();

    /**
     * Additional error details and metadata
     * Can contain domain-specific information
     */
    @Schema(description = "Additional error details")
    @JsonProperty("details")
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Whether the operation can be retried
     */
    @Schema(description = "Whether operation is retryable", example = "true")
    @JsonProperty("retryable")
    private Boolean retryable;

    /**
     * Suggested retry delay in milliseconds
     */
    @Schema(description = "Suggested retry delay in ms", example = "5000")
    @JsonProperty("retryAfterMs")
    private Long retryAfterMs;

    /**
     * Support reference ID for customer support tickets
     */
    @Schema(description = "Support reference ID", example = "SUP-2025-11-23-12345")
    @JsonProperty("supportReferenceId")
    private String supportReferenceId;

    /**
     * Error severity (INFO, WARNING, ERROR, CRITICAL)
     */
    @Schema(description = "Error severity", example = "ERROR")
    @JsonProperty("severity")
    private ErrorSeverity severity;

    /**
     * Stack trace (only in development/test environments)
     * MUST NOT be included in production for security reasons
     */
    @Schema(description = "Stack trace (dev/test only)")
    @JsonProperty("stackTrace")
    private String stackTrace;

    /**
     * Help URL with more information about the error
     */
    @Schema(description = "Help documentation URL", example = "https://api.example.com/errors/PAYMENT_001")
    @JsonProperty("helpUrl")
    private String helpUrl;

    /**
     * Suggested actions for resolution
     */
    @Schema(description = "Suggested resolution actions")
    @JsonProperty("suggestedActions")
    private List<String> suggestedActions;

    // ========================================================================
    // Static Factory Methods
    // ========================================================================

    /**
     * Create builder for validation error (400 Bad Request)
     */
    public static ErrorResponseBuilder validationError() {
        return ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .status(400)
                .error("Bad Request")
                .severity(ErrorSeverity.WARNING)
                .retryable(false);
    }

    /**
     * Create builder for authentication error (401 Unauthorized)
     */
    public static ErrorResponseBuilder authenticationError() {
        return ErrorResponse.builder()
                .errorCode("AUTHENTICATION_REQUIRED")
                .status(401)
                .error("Unauthorized")
                .message("Authentication required")
                .userFriendlyMessage("Please sign in to continue")
                .severity(ErrorSeverity.WARNING)
                .retryable(false);
    }

    /**
     * Create builder for authorization error (403 Forbidden)
     */
    public static ErrorResponseBuilder authorizationError() {
        return ErrorResponse.builder()
                .errorCode("AUTHORIZATION_DENIED")
                .status(403)
                .error("Forbidden")
                .message("Insufficient permissions")
                .userFriendlyMessage("You don't have permission to perform this action")
                .severity(ErrorSeverity.WARNING)
                .retryable(false);
    }

    /**
     * Create builder for not found error (404 Not Found)
     */
    public static ErrorResponseBuilder notFound() {
        return ErrorResponse.builder()
                .errorCode("RESOURCE_NOT_FOUND")
                .status(404)
                .error("Not Found")
                .severity(ErrorSeverity.INFO)
                .retryable(false);
    }

    /**
     * Create builder for conflict error (409 Conflict)
     */
    public static ErrorResponseBuilder conflict() {
        return ErrorResponse.builder()
                .errorCode("RESOURCE_CONFLICT")
                .status(409)
                .error("Conflict")
                .severity(ErrorSeverity.WARNING)
                .retryable(false);
    }

    /**
     * Create builder for business rule violation (422 Unprocessable Entity)
     */
    public static ErrorResponseBuilder businessRuleViolation() {
        return ErrorResponse.builder()
                .errorCode("BUSINESS_RULE_VIOLATION")
                .status(422)
                .error("Unprocessable Entity")
                .severity(ErrorSeverity.WARNING)
                .retryable(false);
    }

    /**
     * Create builder for rate limit error (429 Too Many Requests)
     */
    public static ErrorResponseBuilder rateLimitExceeded() {
        return ErrorResponse.builder()
                .errorCode("RATE_LIMIT_EXCEEDED")
                .status(429)
                .error("Too Many Requests")
                .message("Rate limit exceeded")
                .userFriendlyMessage("Too many requests. Please try again later")
                .severity(ErrorSeverity.WARNING)
                .retryable(true)
                .retryAfterMs(60000L); // 1 minute
    }

    /**
     * Create builder for internal server error (500 Internal Server Error)
     */
    public static ErrorResponseBuilder internalError() {
        return ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .status(500)
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .userFriendlyMessage("Something went wrong. Please try again later")
                .severity(ErrorSeverity.ERROR)
                .retryable(true)
                .retryAfterMs(5000L); // 5 seconds
    }

    /**
     * Create builder for bad gateway error (502 Bad Gateway)
     */
    public static ErrorResponseBuilder badGateway() {
        return ErrorResponse.builder()
                .errorCode("DOWNSTREAM_SERVICE_ERROR")
                .status(502)
                .error("Bad Gateway")
                .message("Downstream service error")
                .userFriendlyMessage("Service temporarily unavailable")
                .severity(ErrorSeverity.ERROR)
                .retryable(true)
                .retryAfterMs(10000L); // 10 seconds
    }

    /**
     * Create builder for service unavailable error (503 Service Unavailable)
     */
    public static ErrorResponseBuilder serviceUnavailable() {
        return ErrorResponse.builder()
                .errorCode("SERVICE_UNAVAILABLE")
                .status(503)
                .error("Service Unavailable")
                .message("Service temporarily unavailable")
                .userFriendlyMessage("Service is temporarily down for maintenance")
                .severity(ErrorSeverity.ERROR)
                .retryable(true)
                .retryAfterMs(30000L); // 30 seconds
    }

    /**
     * Create builder for gateway timeout error (504 Gateway Timeout)
     */
    public static ErrorResponseBuilder gatewayTimeout() {
        return ErrorResponse.builder()
                .errorCode("DOWNSTREAM_TIMEOUT")
                .status(504)
                .error("Gateway Timeout")
                .message("Downstream service timeout")
                .userFriendlyMessage("Request timed out. Please try again")
                .severity(ErrorSeverity.ERROR)
                .retryable(true)
                .retryAfterMs(5000L); // 5 seconds
    }

    // ========================================================================
    // Fluent API Helper Methods
    // ========================================================================

    /**
     * Custom builder class with fluent API helpers
     */
    public static class ErrorResponseBuilder {

        /**
         * Add a field validation error
         */
        public ErrorResponseBuilder addFieldError(String field, String message, Object rejectedValue) {
            if (this.fieldErrors == null) {
                this.fieldErrors = new ArrayList<>();
            }
            this.fieldErrors.add(FieldError.builder()
                    .field(field)
                    .message(message)
                    .rejectedValue(rejectedValue)
                    .build());
            return this;
        }

        /**
         * Add a field validation error without rejected value
         */
        public ErrorResponseBuilder addFieldError(String field, String message) {
            return addFieldError(field, message, null);
        }

        /**
         * Add a detail entry
         */
        public ErrorResponseBuilder addDetail(String key, Object value) {
            if (this.details == null) {
                this.details = new HashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        /**
         * Add a suggested action
         */
        public ErrorResponseBuilder addSuggestedAction(String action) {
            if (this.suggestedActions == null) {
                this.suggestedActions = new ArrayList<>();
            }
            this.suggestedActions.add(action);
            return this;
        }
    }

    // ========================================================================
    // Nested Classes
    // ========================================================================

    /**
     * Field-level validation error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Field validation error")
    public static class FieldError {

        @Schema(description = "Field name", example = "amount")
        private String field;

        @Schema(description = "Error message", example = "Must be greater than 0")
        private String message;

        @Schema(description = "Rejected value", example = "-100")
        private Object rejectedValue;

        @Schema(description = "Field path (for nested objects)", example = "payment.amount")
        private String fieldPath;

        @Schema(description = "Validation constraint violated", example = "Min")
        private String constraint;
    }

    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        /**
         * Informational - expected behavior (e.g., 404 Not Found)
         */
        INFO,

        /**
         * Warning - client error but expected (e.g., validation failure)
         */
        WARNING,

        /**
         * Error - unexpected server error (e.g., 500 Internal Server Error)
         */
        ERROR,

        /**
         * Critical - system failure requiring immediate attention
         */
        CRITICAL
    }
}
