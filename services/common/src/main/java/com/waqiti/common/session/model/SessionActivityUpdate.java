package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Session activity update model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionActivityUpdate {
    
    private String sessionId;
    private String activityType;
    private String activityDescription;
    private Instant timestamp;
    private String ipAddress;
    private String userAgent;
    private String endpoint;
    private String httpMethod;
    private Integer responseCode;
    private Long responseTimeMs;
    private Map<String, Object> metadata;
    private GeoLocation location;
    private boolean suspicious;
    private String riskLevel;
    
    /**
     * Create activity update for API call
     */
    public static SessionActivityUpdate apiCall(String sessionId, String endpoint, String method) {
        return SessionActivityUpdate.builder()
            .sessionId(sessionId)
            .activityType("API_CALL")
            .endpoint(endpoint)
            .httpMethod(method)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create activity update for login
     */
    public static SessionActivityUpdate login(String sessionId, String ipAddress, boolean success) {
        return SessionActivityUpdate.builder()
            .sessionId(sessionId)
            .activityType(success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE")
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create activity update for security event
     */
    public static SessionActivityUpdate securityEvent(String sessionId, String event, String riskLevel) {
        return SessionActivityUpdate.builder()
            .sessionId(sessionId)
            .activityType("SECURITY_EVENT")
            .activityDescription(event)
            .riskLevel(riskLevel)
            .suspicious(true)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Check if activity is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskLevel) || 
               "CRITICAL".equalsIgnoreCase(riskLevel) ||
               suspicious;
    }
}