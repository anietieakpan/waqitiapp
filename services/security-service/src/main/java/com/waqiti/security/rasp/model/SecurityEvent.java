package com.waqiti.security.rasp.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Security event model for RASP
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {
    
    private String requestId;
    private LocalDateTime timestamp;
    private String clientIp;
    private String userAgent;
    private String uri;
    private String method;
    private String threatType;
    private ThreatLevel threatLevel;
    private String description;
    private String userId;
    private String sessionId;
    private Map<String, Object> metadata;
    private String attackPayload;
    private String detectorName;
    private boolean blocked;
    private String action;
    private Long responseTime;
    
    // Geolocation data
    private String country;
    private String city;
    private String organization;
    
    // Request characteristics
    private long requestSize;
    private String contentType;
    private boolean isBot;
    private boolean isTor;
    private boolean isVpn;
    
    // Attack vectors
    private String sqlInjectionVector;
    private String xssVector;
    private String commandInjectionVector;
    private String pathTraversalVector;
    
    // Rate limiting info
    private int requestCount;
    private String rateLimitWindow;
    
    public boolean isCritical() {
        return threatLevel == ThreatLevel.CRITICAL;
    }
    
    public boolean isHigh() {
        return threatLevel == ThreatLevel.HIGH;
    }
    
    public boolean shouldBlock() {
        return isCritical() || isHigh();
    }
}