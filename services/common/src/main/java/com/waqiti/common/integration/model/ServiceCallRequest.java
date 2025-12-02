package com.waqiti.common.integration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Comprehensive service call request with full context and configuration
 */
@Data
@Builder
@Jacksonized
public class ServiceCallRequest {
    private String requestId;
    private String operation;
    private String httpMethod;
    private String path;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private Object body;
    private Duration timeout;
    private int maxRetries;
    private String correlationId;
    private String userId;
    private String sessionId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant requestTime;
    
    private Map<String, Object> context;
    private boolean cacheable;
    private Duration cacheTime;
    private String idempotencyKey;
    private Priority priority;
    
    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }
}