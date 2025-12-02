package com.waqiti.rewards.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardized error response DTO
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Error code for categorization
     */
    private String errorCode;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Timestamp of error
     */
    private Instant timestamp;

    /**
     * Request path where error occurred
     */
    private String path;

    /**
     * Correlation ID for tracing
     */
    private String correlationId;

    /**
     * Detailed validation errors (for 400 Bad Request)
     */
    private List<FieldError> fieldErrors;

    /**
     * Additional error details
     */
    private Map<String, Object> details;

    /**
     * Stack trace (only in dev/test environments)
     */
    private String stackTrace;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    /**
     * Create error response for validation failures
     */
    public static ErrorResponse validationError(String message, List<FieldError> fieldErrors,
                                                String path, String correlationId) {
        return ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .status(400)
                .timestamp(Instant.now())
                .path(path)
                .correlationId(correlationId)
                .fieldErrors(fieldErrors)
                .build();
    }

    /**
     * Create error response for not found
     */
    public static ErrorResponse notFound(String message, String path, String correlationId) {
        return ErrorResponse.builder()
                .errorCode("NOT_FOUND")
                .message(message)
                .status(404)
                .timestamp(Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();
    }

    /**
     * Create error response for business rule violation
     */
    public static ErrorResponse businessRuleViolation(String errorCode, String message,
                                                      String path, String correlationId) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .status(422)
                .timestamp(Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();
    }

    /**
     * Create error response for internal server error
     */
    public static ErrorResponse internalError(String message, String path, String correlationId) {
        return ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message(message)
                .status(500)
                .timestamp(Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();
    }
}
