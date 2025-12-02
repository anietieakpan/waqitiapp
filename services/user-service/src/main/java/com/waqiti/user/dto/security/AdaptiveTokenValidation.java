package com.waqiti.user.dto.security;

import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptive Token Validation DTO
 * 
 * Contains the result of adaptive token validation with security context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveTokenValidation {
    
    // Validation result
    private Boolean valid;
    private String validationStatus; // VALID, EXPIRED, REVOKED, INVALID, SUSPICIOUS
    private String failureReason;
    private List<String> validationErrors;
    
    // Token information
    private String tokenId;
    private String userId;
    private String sessionId;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Long remainingTtl; // Remaining time to live in seconds
    
    // Risk assessment
    private Double currentRiskScore;
    private Double originalRiskScore;
    private String riskLevel;
    private Boolean riskEscalated;
    private List<String> riskFactors;
    
    // Context validation
    private Boolean deviceMatch;
    private Boolean locationMatch;
    private Boolean ipMatch;
    private Boolean behaviorMatch;
    
    // Device validation
    private String currentDeviceFingerprint;
    private String originalDeviceFingerprint;
    private Double deviceSimilarity;
    private Boolean newDevice;
    private String deviceTrustLevel;
    
    // Location validation
    private String currentLocation;
    private String originalLocation;
    private Double locationDistance; // Distance in kilometers
    private Boolean suspiciousLocation;
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    
    // Behavioral validation
    private Map<String, Object> currentBehavior;
    private Map<String, Object> expectedBehavior;
    private Double behavioralDeviation;
    private List<String> behavioralAnomalies;
    
    // Rate limiting validation
    private Boolean rateLimitExceeded;
    private Integer currentRequests;
    private Integer maxRequests;
    private Long windowRemainingTime;
    
    // Security constraints validation
    private Boolean operationAllowed;
    private List<String> allowedOperations;
    private List<String> restrictedOperations;
    private String operationAttempted;
    
    // Concurrent session validation
    private Boolean concurrentSessionDetected;
    private Integer activeSessionCount;
    private Integer maxAllowedSessions;
    private List<String> activeSessions;
    
    // Adaptive decisions
    private Boolean requiresStepUp;
    private List<String> requiredAuthFactors;
    private Boolean shouldRefresh;
    private Boolean shouldRevoke;
    private Boolean shouldBlock;
    
    // Time-based validations
    private Boolean withinTimeWindow;
    private Boolean afterHours;
    private String timeZoneExpected;
    private String timeZoneCurrent;
    
    // Compliance validation
    private Boolean complianceViolation;
    private List<String> complianceIssues;
    private String regulatoryContext;
    
    // Fraud indicators
    private Boolean fraudSuspected;
    private Double fraudScore;
    private List<String> fraudIndicators;
    private String fraudType;
    
    // Security events
    private List<SecurityEvent> securityEvents;
    private Boolean securityEventTriggered;
    
    // Recommendations
    private List<String> securityActions;
    private List<String> userNotifications;
    private String nextValidationInterval;
    
    // Audit information
    private String validationId;
    private LocalDateTime validatedAt;
    private String validatedBy;
    private Map<String, Object> auditMetadata;
    
    /**
     * Creates a valid token validation result
     */
    public static AdaptiveTokenValidation valid(String tokenId, String userId) {
        return AdaptiveTokenValidation.builder()
            .valid(true)
            .validationStatus("VALID")
            .tokenId(tokenId)
            .userId(userId)
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates a valid token validation result from Claims
     */
    public static AdaptiveTokenValidation valid(Claims claims, AdaptiveValidationResult adaptiveResult) {
        String tokenId = claims.getId();
        String userId = (String) claims.get("userId");
        
        return AdaptiveTokenValidation.builder()
            .valid(true)
            .validationStatus("VALID")
            .tokenId(tokenId)
            .userId(userId)
            .issuedAt(claims.getIssuedAt() != null ? 
                claims.getIssuedAt().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null)
            .expiresAt(claims.getExpiration() != null ? 
                claims.getExpiration().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null)
            .currentRiskScore(adaptiveResult != null ? adaptiveResult.getRiskScore() : null)
            .riskLevel(adaptiveResult != null ? adaptiveResult.getRiskLevel() : null)
            .originalDeviceFingerprint((String) claims.get("deviceFingerprint"))
            .originalLocation((String) claims.get("location"))
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates an invalid token validation result
     */
    public static AdaptiveTokenValidation invalid(String reason) {
        return AdaptiveTokenValidation.builder()
            .valid(false)
            .validationStatus("INVALID")
            .failureReason(reason)
            .validatedAt(LocalDateTime.now())
            .securityEventTriggered(true)
            .build();
    }
    
    /**
     * Creates an invalid token validation result with adaptive validation details
     */
    public static AdaptiveTokenValidation invalid(String reason, AdaptiveValidationResult adaptiveResult) {
        return AdaptiveTokenValidation.builder()
            .valid(false)
            .validationStatus("INVALID")
            .failureReason(reason)
            .currentRiskScore(adaptiveResult != null ? adaptiveResult.getRiskScore() : null)
            .riskLevel(adaptiveResult != null ? adaptiveResult.getRiskLevel() : null)
            .validatedAt(LocalDateTime.now())
            .securityEventTriggered(true)
            .build();
    }
    
    /**
     * Creates a token validation result requiring step-up authentication
     */
    public static AdaptiveTokenValidation requiresStepUp(String tokenId, String userId, String reason) {
        return AdaptiveTokenValidation.builder()
            .valid(false)
            .validationStatus("REQUIRES_STEP_UP")
            .tokenId(tokenId)
            .userId(userId)
            .failureReason(reason)
            .requiresStepUp(true)
            .validatedAt(LocalDateTime.now())
            .securityEventTriggered(true)
            .build();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityEvent {
        private String eventType;
        private String severity;
        private String description;
        private LocalDateTime timestamp;
        private String source;
        private Map<String, Object> details;
    }
}