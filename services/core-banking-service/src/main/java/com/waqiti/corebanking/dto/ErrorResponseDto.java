package com.waqiti.corebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardized error response DTO for all API errors
 * Provides consistent error structure across the application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response structure")
public class ErrorResponseDto {

    @Schema(description = "Timestamp when the error occurred", example = "2024-01-15T10:30:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private Integer status;

    @Schema(description = "HTTP status text", example = "Bad Request")
    private String error;

    @Schema(description = "Error message for the user", example = "Invalid request parameters")
    private String message;

    @Schema(description = "Detailed error description", example = "Amount must be greater than zero")
    private String details;

    @Schema(description = "API path where the error occurred", example = "/api/core-banking/v1/transactions/transfer")
    private String path;

    @Schema(description = "Unique error correlation ID for tracking", example = "550e8400-e29b-41d4-a716-446655440000")
    private String correlationId;

    @Schema(description = "Error code for programmatic handling", example = "INSUFFICIENT_FUNDS")
    private String errorCode;

    @Schema(description = "List of validation errors")
    private List<FieldError> fieldErrors;

    @Schema(description = "Additional error metadata")
    private Map<String, Object> metadata;

    /**
     * Field-level validation error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Field-level validation error")
    public static class FieldError {

        @Schema(description = "Field name that failed validation", example = "amount")
        private String field;

        @Schema(description = "Rejected value", example = "-10.50")
        private Object rejectedValue;

        @Schema(description = "Validation error message", example = "Amount must be greater than zero")
        private String message;

        @Schema(description = "Error code for the validation failure", example = "MIN_VALUE")
        private String code;
    }

    /**
     * Creates a simple error response
     */
    public static ErrorResponseDto of(int status, String error, String message, String path) {
        return ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .correlationId(java.util.UUID.randomUUID().toString())
                .build();
    }

    /**
     * Creates an error response with details
     */
    public static ErrorResponseDto of(int status, String error, String message, String details, String path) {
        return ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .details(details)
                .path(path)
                .correlationId(java.util.UUID.randomUUID().toString())
                .build();
    }

    /**
     * Creates an error response with error code
     */
    public static ErrorResponseDto of(int status, String error, String message, String path, String errorCode) {
        return ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .errorCode(errorCode)
                .correlationId(java.util.UUID.randomUUID().toString())
                .build();
    }
}
