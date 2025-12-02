package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Adaptive Token Result DTO
 * 
 * Contains the result of adaptive token generation with security metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveTokenResult {
    
    // Token information
    private String token; // Primary token field
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String tokenId;
    private Long expiresIn;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    
    // Security metadata
    private String deviceFingerprint;
    private String sessionId;
    private Double riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    // Authentication context
    private String authenticationMethod;
    private List<String> authenticationFactors;
    private String deviceTrustLevel; // TRUSTED, UNKNOWN, SUSPICIOUS, BLOCKED
    private String locationTrustLevel;
    
    // Adaptive features
    private Long adaptiveExpiryTime; // Dynamic expiry based on risk
    private Boolean requiresStepUp; // Requires additional authentication
    private Boolean requiresMfa; // Requires multi-factor authentication
    private Boolean restrictedAccess; // Access is restricted
    private List<String> allowedOperations;
    private List<String> restrictedOperations;
    
    // Security constraints
    private String ipBinding; // Token bound to specific IP
    private String deviceBinding; // Token bound to specific device
    private String locationBinding; // Token bound to geographic location
    private Boolean singleUse; // Token can only be used once
    
    // Rate limiting
    private Integer maxRequests; // Max requests per time window
    private Long timeWindow; // Time window in seconds
    private Integer remainingRequests;
    
    // Behavioral analysis
    private Map<String, Object> behavioralSignals;
    private Double anomalyScore;
    private List<String> anomalyReasons;
    
    // Geolocation context
    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private Boolean locationChanged;
    private Double locationRisk;
    
    // Device context
    private String deviceType;
    private String osName;
    private String osVersion;
    private String browserName;
    private String browserVersion;
    private Boolean newDevice;
    private LocalDateTime lastSeenDevice;
    
    // Network context
    private String ipAddress;
    private String isp;
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    
    // Session context
    private LocalDateTime sessionStartTime;
    private Integer sessionLength;
    private String sessionPattern;
    private Boolean concurrentSession;
    
    // Compliance and audit
    private String regulatoryRegion;
    private List<String> complianceFlags;
    private String auditTrailId;
    
    // Token refresh behavior
    private Boolean autoRefreshEnabled;
    private Long refreshThreshold; // Seconds before expiry to allow refresh
    private Integer maxRefreshCount;
    private Integer currentRefreshCount;
    
    // Security events
    private List<SecurityEvent> securityEvents;
    
    // Recommendations
    private List<String> securityRecommendations;
    private List<String> userActions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityEvent {
        private String eventType;
        private String severity;
        private String description;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }
}