package com.waqiti.billpayment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error response format for API errors
 * Provides consistent error information to clients
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Error code for programmatic error handling
     */
    private String errorCode;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Detailed error description (optional)
     */
    private String details;

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Timestamp when error occurred
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Request path that caused the error
     */
    private String path;

    /**
     * Validation errors (for 400 Bad Request)
     */
    private List<FieldError> fieldErrors;

    /**
     * Additional error metadata
     */
    private Map<String, Object> metadata;

    /**
     * Trace ID for distributed tracing
     */
    private String traceId;

    /**
     * Field-level validation error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String rejectedValue;
        private String message;
    }
}
