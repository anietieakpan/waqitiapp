package com.waqiti.common.integration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Service call result with comprehensive response information and metrics
 */
@Data
@Builder
@Jacksonized
public class ServiceCallResult<T> {
    private String requestId;
    private int index; // Index in batch operations
    private boolean success;
    private T data;
    private T result; // Alias for data
    private String error;
    private Exception exception;
    private int statusCode;
    private Map<String, String> responseHeaders;
    private Duration responseTime;
    private String serviceName;
    private String operation;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant timestamp;
    
    private int retryAttempts;
    private boolean fromCache;
    private Map<String, Object> metadata;
    
    public boolean isSuccessful() {
        return success && statusCode >= 200 && statusCode < 300;
    }
    
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    public boolean isServerError() {
        return statusCode >= 500;
    }
}