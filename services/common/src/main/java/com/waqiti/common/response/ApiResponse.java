package com.waqiti.common.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardized API response wrapper for all service endpoints
 * Provides consistent structure across all microservices
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private ErrorDetails error;
    private ResponseMetadata metadata;
    private List<ValidationError> validationErrors;
    private PaginationInfo pagination;

    /**
     * Create successful response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create successful response with data and message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create successful response with pagination
     */
    public static <T> ApiResponse<T> success(T data, PaginationInfo pagination) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .pagination(pagination)
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create error response
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorDetails.builder()
                        .code(errorCode)
                        .message(message)
                        .timestamp(Instant.now())
                        .build())
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create error response with HTTP status
     */
    public static <T> ApiResponse<T> error(String message, String errorCode, HttpStatus status) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorDetails.builder()
                        .code(errorCode)
                        .message(message)
                        .httpStatus(status.value())
                        .timestamp(Instant.now())
                        .build())
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create error response with exception
     */
    public static <T> ApiResponse<T> error(Exception exception, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(exception.getMessage())
                .error(ErrorDetails.builder()
                        .code(errorCode)
                        .message(exception.getMessage())
                        .exception(exception.getClass().getSimpleName())
                        .timestamp(Instant.now())
                        .build())
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create validation error response
     */
    public static <T> ApiResponse<T> validationError(List<ValidationError> validationErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message("Validation failed")
                .validationErrors(validationErrors)
                .error(ErrorDetails.builder()
                        .code("VALIDATION_FAILED")
                        .message("Request validation failed")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .timestamp(Instant.now())
                        .build())
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Create rate limit exceeded response
     */
    public static <T> ApiResponse<T> rateLimitExceeded(long retryAfterSeconds) {
        return ApiResponse.<T>builder()
                .success(false)
                .message("Rate limit exceeded")
                .error(ErrorDetails.builder()
                        .code("RATE_LIMIT_EXCEEDED")
                        .message("Too many requests. Try again later.")
                        .httpStatus(HttpStatus.TOO_MANY_REQUESTS.value())
                        .retryAfterSeconds(retryAfterSeconds)
                        .timestamp(Instant.now())
                        .build())
                .metadata(ResponseMetadata.builder()
                        .timestamp(Instant.now())
                        .version("1.0")
                        .build())
                .build();
    }

    /**
     * Error details nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {
        private String code;
        private String message;
        private String exception;
        private Integer httpStatus;
        private String traceId;
        private String requestId;
        private Map<String, Object> details;
        private Long retryAfterSeconds;
        private Instant timestamp;
    }

    /**
     * Response metadata nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseMetadata {
        private Instant timestamp;
        private String version;
        private String requestId;
        private String traceId;
        private String correlationId;
        private Long processingTimeMs;
        private String service;
        private String environment;
        private Map<String, Object> additionalInfo;
    }

    /**
     * Validation error nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
        private String code;
    }

    /**
     * Pagination information nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaginationInfo {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
        private boolean hasNext;
        private boolean hasPrevious;
        private int numberOfElements;
    }

    /**
     * Check if response contains data
     */
    public boolean hasData() {
        return data != null;
    }

    /**
     * Check if response has pagination
     */
    public boolean hasPagination() {
        return pagination != null;
    }

    /**
     * Check if response has validation errors
     */
    public boolean hasValidationErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }

    /**
     * Get HTTP status from error details
     */
    public int getHttpStatus() {
        if (error != null && error.getHttpStatus() != null) {
            return error.getHttpStatus();
        }
        return success ? HttpStatus.OK.value() : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    /**
     * Add metadata information
     */
    public ApiResponse<T> withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = ResponseMetadata.builder().build();
        }
        if (this.metadata.getAdditionalInfo() == null) {
            this.metadata.setAdditionalInfo(new java.util.HashMap<>());
        }
        this.metadata.getAdditionalInfo().put(key, value);
        return this;
    }

    /**
     * Set trace ID for distributed tracing
     */
    public ApiResponse<T> withTraceId(String traceId) {
        if (this.metadata == null) {
            this.metadata = ResponseMetadata.builder().build();
        }
        this.metadata.setTraceId(traceId);
        if (this.error != null) {
            this.error.setTraceId(traceId);
        }
        return this;
    }

    /**
     * Set request ID for request tracking
     */
    public ApiResponse<T> withRequestId(String requestId) {
        if (this.metadata == null) {
            this.metadata = ResponseMetadata.builder().build();
        }
        this.metadata.setRequestId(requestId);
        if (this.error != null) {
            this.error.setRequestId(requestId);
        }
        return this;
    }

    /**
     * Set processing time
     */
    public ApiResponse<T> withProcessingTime(long processingTimeMs) {
        if (this.metadata == null) {
            this.metadata = ResponseMetadata.builder().build();
        }
        this.metadata.setProcessingTimeMs(processingTimeMs);
        return this;
    }
}