package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log for API access and operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAuditLog {
    
    private UUID id;
    private String userId;
    private String apiEndpoint;
    private String httpMethod;
    private String requestPath;
    private String queryParameters;
    private String requestBody;
    private String responseBody;
    private int statusCode;
    private Long responseTimeMs;
    private String userAgent;
    private String ipAddress;
    private String sessionId;
    private String apiKey;
    private String correlationId;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    
    /**
     * Create API audit log entry
     */
    public static ApiAuditLog create(String userId, String apiEndpoint, String httpMethod, 
                                   String requestPath, int statusCode, Long responseTimeMs) {
        return ApiAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .apiEndpoint(apiEndpoint)
                .httpMethod(httpMethod)
                .requestPath(requestPath)
                .statusCode(statusCode)
                .responseTimeMs(responseTimeMs)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Add request details
     */
    public ApiAuditLog withRequest(String queryParameters, String requestBody) {
        this.queryParameters = queryParameters;
        this.requestBody = requestBody;
        return this;
    }
    
    /**
     * Add response details
     */
    public ApiAuditLog withResponse(String responseBody) {
        this.responseBody = responseBody;
        return this;
    }
    
    /**
     * Add session details
     */
    public ApiAuditLog withSession(String sessionId, String userAgent, String ipAddress) {
        this.sessionId = sessionId;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        return this;
    }
    
    /**
     * Get start time for response time calculation
     */
    public Long getStartTime() {
        if (timestamp == null) return null;
        return timestamp.toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
    }
    
    /**
     * Get end time for response time calculation  
     */
    public Long getEndTime() {
        Long startTime = getStartTime();
        if (startTime == null || responseTimeMs == null || responseTimeMs == 0) return null;
        return startTime + responseTimeMs;
    }
    
    /**
     * Get API endpoint
     */
    public String getEndpoint() {
        return apiEndpoint;
    }
    
    /**
     * Get HTTP method (alias for httpMethod)
     */
    public String getMethod() {
        return httpMethod;
    }
    
    /**
     * Get request ID (use correlation ID or generate one)
     */
    public String getRequestId() {
        return correlationId != null ? correlationId : (id != null ? id.toString() : UUID.randomUUID().toString());
    }
    
    /**
     * Get response time in milliseconds
     */
    public long getResponseTime() {
        return responseTimeMs != null ? responseTimeMs : 0L;
    }
}