package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Token Metadata for security tracking and validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenMetadata {
    
    // Basic token information
    private String tokenId;
    private String userId;
    private String sessionId;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String tokenType;
    
    // Risk and security context
    private Double riskScore;
    private String riskLevel;
    private String deviceFingerprint;
    private String ipAddress;
    private String userAgent;
    
    // Geolocation context
    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    
    // Device context
    private String deviceType;
    private String osName;
    private String osVersion;
    private String browserName;
    private String browserVersion;
    
    // Authentication context
    private String authenticationMethod;
    private List<String> authenticationFactors;
    private Boolean mfaCompleted;
    private LocalDateTime lastAuthTime;
    
    // Behavioral context
    private Map<String, Object> behavioralSignatures;
    private String accessPattern;
    private String timeZone;
    
    // Network context
    private String isp;
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    
    // Session context
    private LocalDateTime sessionStartTime;
    private String sessionPattern;
    private Integer concurrentSessions;
    
    // Security constraints
    private List<String> allowedOperations;
    private List<String> restrictedOperations;
    private Boolean singleUse;
    private String ipBinding;
    private String deviceBinding;
    
    // Rate limiting
    private Integer maxRequests;
    private Long timeWindow;
    private Integer requestCount;
    
    // Compliance and audit
    private String regulatoryContext;
    private Map<String, Object> auditData;
    private String createdBy;
    private LocalDateTime lastValidated;
    
    // Token lifecycle
    private Boolean revoked;
    private LocalDateTime revokedAt;
    private String revocationReason;
    private Boolean refreshable;
    private Integer refreshCount;
    private Integer maxRefreshCount;
    
    // Creation timestamp
    private LocalDateTime createdAt;
}