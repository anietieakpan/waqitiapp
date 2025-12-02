package com.waqiti.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Standard API Response Wrapper
 * 
 * Provides consistent response structure across all microservices
 * Supports both successful responses and error handling
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardApiResponse<T> {

    // Response Metadata
    private ResponseStatus status;
    private String message;
    private LocalDateTime timestamp;
    private UUID requestId;
    private String path;
    
    // Response Data
    private T data;
    
    // Error Information
    private ErrorDetails error;
    
    // Pagination Information
    private PaginationInfo pagination;
    
    // Additional Metadata
    private ResponseMetadata metadata;

    /**
     * Factory method for successful response
     */
    public static <T> StandardApiResponse<T> success(T data) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.SUCCESS)
            .data(data)
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for successful response with message
     */
    public static <T> StandardApiResponse<T> success(T data, String message) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.SUCCESS)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for successful response with pagination
     */
    public static <T> StandardApiResponse<T> success(T data, PaginationInfo pagination) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.SUCCESS)
            .data(data)
            .pagination(pagination)
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for error response
     */
    public static <T> StandardApiResponse<T> error(ErrorDetails error) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.ERROR)
            .error(error)
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for error response with message
     */
    public static <T> StandardApiResponse<T> error(String errorCode, String message) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.ERROR)
            .message(message)
            .error(ErrorDetails.builder()
                .code(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build())
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for warning response
     */
    public static <T> StandardApiResponse<T> warning(T data, String message) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.WARNING)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for no content response
     */
    public static <T> StandardApiResponse<T> noContent() {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.SUCCESS)
            .message("No content")
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Factory method for accepted response (async processing)
     */
    public static <T> StandardApiResponse<T> accepted(String message, String trackingId) {
        return StandardApiResponse.<T>builder()
            .status(ResponseStatus.ACCEPTED)
            .message(message)
            .metadata(ResponseMetadata.builder()
                .trackingId(trackingId)
                .build())
            .timestamp(LocalDateTime.now())
            .requestId(UUID.randomUUID())
            .build();
    }

    /**
     * Get error code from error details (convenience method)
     */
    public String getErrorCode() {
        return error != null ? error.getCode() : null;
    }

    /**
     * Response Status Enum
     */
    public enum ResponseStatus {
        SUCCESS("Success"),
        ERROR("Error"),
        WARNING("Warning"),
        ACCEPTED("Accepted"),
        PARTIAL_SUCCESS("Partial Success");

        private final String description;

        ResponseStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Error Details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {
        private String code;
        private String message;
        private String field;
        private Object rejectedValue;
        private List<ValidationError> validationErrors;
        private String traceId;
        private String moreInfo;
        private LocalDateTime timestamp;
    }

    /**
     * Validation Error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
        private String code;
    }

    /**
     * Pagination Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
        private boolean hasNext;
        private boolean hasPrevious;
        
        /**
         * Create pagination info from Spring's Page object
         */
        public static PaginationInfo fromPage(org.springframework.data.domain.Page<?> page) {
            return PaginationInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
        }
    }

    /**
     * Response Metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseMetadata {
        private String version;
        private String trackingId;
        private Long processingTimeMs;
        private String serverRegion;
        private String dataCenter;
        private RateLimitInfo rateLimitInfo;
        private CacheInfo cacheInfo;
        private DebugInfo debugInfo;
    }

    /**
     * Rate Limit Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitInfo {
        private Integer limit;
        private Integer remaining;
        private Long resetTimestamp;
        private String resetTime;
    }

    /**
     * Cache Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheInfo {
        private boolean cached;
        private LocalDateTime cacheTime;
        private Integer cacheTtlSeconds;
        private String cacheKey;
    }

    /**
     * Debug Information (only in non-production)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DebugInfo {
        private String queryExecutionPlan;
        private Long databaseQueryTimeMs;
        private String sqlQuery;
        private String stackTrace;
        private Object requestPayload;
    }

    // Utility methods

    /**
     * Check if response is successful
     */
    public boolean isSuccess() {
        return status == ResponseStatus.SUCCESS || status == ResponseStatus.ACCEPTED;
    }

    /**
     * Check if response has error
     */
    public boolean hasError() {
        return status == ResponseStatus.ERROR;
    }

    /**
     * Check if response has warning
     */
    public boolean hasWarning() {
        return status == ResponseStatus.WARNING;
    }

    /**
     * Get response data
     */
    public T getData() {
        return data;
    }

    /**
     * Check if response has data
     */
    public boolean hasData() {
        return data != null;
    }

    /**
     * Check if response is paginated
     */
    public boolean isPaginated() {
        return pagination != null;
    }

    /**
     * Add request context
     */
    public StandardApiResponse<T> withRequestContext(UUID requestId, String path) {
        this.requestId = requestId;
        this.path = path;
        return this;
    }

    /**
     * Add processing time
     */
    public StandardApiResponse<T> withProcessingTime(long startTime) {
        if (this.metadata == null) {
            this.metadata = new ResponseMetadata();
        }
        this.metadata.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        return this;
    }

    /**
     * Add rate limit information
     */
    public StandardApiResponse<T> withRateLimitInfo(int limit, int remaining, long resetTimestamp) {
        if (this.metadata == null) {
            this.metadata = new ResponseMetadata();
        }
        this.metadata.setRateLimitInfo(RateLimitInfo.builder()
            .limit(limit)
            .remaining(remaining)
            .resetTimestamp(resetTimestamp)
            .resetTime(new java.util.Date(resetTimestamp).toString())
            .build());
        return this;
    }

    /**
     * Add cache information
     */
    public StandardApiResponse<T> withCacheInfo(boolean cached, int ttlSeconds) {
        if (this.metadata == null) {
            this.metadata = new ResponseMetadata();
        }
        this.metadata.setCacheInfo(CacheInfo.builder()
            .cached(cached)
            .cacheTime(cached ? LocalDateTime.now() : null)
            .cacheTtlSeconds(ttlSeconds)
            .build());
        return this;
    }
}