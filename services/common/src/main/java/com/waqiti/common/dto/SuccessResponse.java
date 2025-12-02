package com.waqiti.common.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard success response DTO
 * Used across all services for consistent success responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessResponse<T> {
    
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String traceId;
    private String spanId;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String operation;
    private String resourceId;
    private String version;
    
    // Pagination support
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    
    // Performance metrics
    private Long executionTimeMs;
    private String cacheStatus; // HIT, MISS, BYPASS
    
    // Static factory methods
    public static <T> SuccessResponse<T> of(T data) {
        return SuccessResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> SuccessResponse<T> of(T data, String message) {
        return SuccessResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> SuccessResponse<T> created(T data, String resourceId) {
        return SuccessResponse.<T>builder()
            .success(true)
            .message("Resource created successfully")
            .data(data)
            .resourceId(resourceId)
            .operation("CREATE")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> SuccessResponse<T> updated(T data, String resourceId) {
        return SuccessResponse.<T>builder()
            .success(true)
            .message("Resource updated successfully")
            .data(data)
            .resourceId(resourceId)
            .operation("UPDATE")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static SuccessResponse<Void> deleted(String resourceId) {
        return SuccessResponse.<Void>builder()
            .success(true)
            .message("Resource deleted successfully")
            .resourceId(resourceId)
            .operation("DELETE")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> SuccessResponse<T> paginated(T data, Integer page, Integer size, 
                                                   Long totalElements, Integer totalPages) {
        return SuccessResponse.<T>builder()
            .success(true)
            .data(data)
            .page(page)
            .size(size)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static SuccessResponse<String> accepted(String message) {
        return SuccessResponse.<String>builder()
            .success(true)
            .message(message)
            .data("Request accepted for processing")
            .operation("ASYNC")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    // Helper methods
    public SuccessResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
    
    public SuccessResponse<T> withSpanId(String spanId) {
        this.spanId = spanId;
        return this;
    }
    
    public SuccessResponse<T> withExecutionTime(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
        return this;
    }
    
    public SuccessResponse<T> withCacheStatus(String cacheStatus) {
        this.cacheStatus = cacheStatus;
        return this;
    }
    
    public SuccessResponse<T> withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    public SuccessResponse<T> withVersion(String version) {
        this.version = version;
        return this;
    }
    
    public boolean isPaginated() {
        return page != null && size != null && totalElements != null;
    }
    
    public boolean hasMetrics() {
        return executionTimeMs != null || cacheStatus != null;
    }
}