package com.waqiti.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for fraud detection checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResponse {
    
    // Basic response info
    private String transactionId;
    private String fraudCheckId;
    private LocalDateTime checkTimestamp;
    private long processingTimeMs;
    
    // Risk assessment
    private RiskLevel riskLevel;
    private Double riskScore;
    private FraudDecision decision;
    private String decisionReason;
    private Map<String, Double> componentScores;
    
    // Rule violations
    private List<String> triggeredRules;
    private List<FraudAlert> alerts;
    private List<String> warnings;
    
    // Analysis details
    private VelocityAnalysis velocityAnalysis;
    private GeolocationAnalysis geolocationAnalysis;
    private DeviceAnalysis deviceAnalysis;
    private BehavioralAnalysis behavioralAnalysis;
    private MLAnalysis mlAnalysis;
    private BlacklistAnalysis blacklistAnalysis;
    
    // Recommendations
    private List<String> recommendedActions;
    private String nextStepAction;
    private Integer reviewPriority;
    
    // Monitoring data
    private Map<String, Object> monitoringData;
    private String modelVersion;
    private String rulesVersion;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudAlert {
        private String alertId;
        private String alertType;
        private String severity;
        private String message;
        private Map<String, Object> context;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityAnalysis {
        private Double velocityScore;
        private Integer transactionCount24h;
        private Integer transactionCount7d;
        private Boolean velocityExceeded;
        private String velocityPattern;
        private List<String> velocityFlags;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationAnalysis {
        private Double geoScore;
        private String detectedCountry;
        private String detectedCity;
        private Boolean isNewLocation;
        private Boolean isHighRiskLocation;
        private Boolean isVpnDetected;
        private Boolean isProxyDetected;
        private Double distanceFromLastTransaction;
        private String impossibleTravelFlag;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAnalysis {
        private Double deviceScore;
        private Boolean isNewDevice;
        private Boolean isKnownDevice;
        private Boolean isTrustedDevice;
        private String deviceRiskCategory;
        private List<String> deviceFlags;
        private String deviceFingerprint;
        private Boolean fingerprintMatches;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralAnalysis {
        private Double behaviorScore;
        private String behaviorPattern;
        private Boolean isAnomalous;
        private List<String> behaviorFlags;
        private String userTypingPattern;
        private String transactionPattern;
        private Double confidenceScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLAnalysis {
        private Double mlScore;
        private String modelName;
        private String modelVersion;
        private Double confidence;
        private Map<String, Double> featureScores;
        private List<String> significantFeatures;
        private String prediction;
        private String explanation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlacklistAnalysis {
        private Boolean isBlacklisted;
        private String blacklistType;
        private String blacklistReason;
        private LocalDateTime blacklistDate;
        private String blacklistSource;
        private List<String> matchedEntries;
    }
    
    /**
     * Convenience methods
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean shouldBlock() {
        return decision == FraudDecision.BLOCK || decision == FraudDecision.REJECT;
    }
    
    public boolean shouldReview() {
        return decision == FraudDecision.REVIEW || decision == FraudDecision.MANUAL_REVIEW;
    }
    
    public boolean hasAlerts() {
        return alerts != null && !alerts.isEmpty();
    }
    
    public boolean hasHighSeverityAlerts() {
        return alerts != null && alerts.stream()
            .anyMatch(alert -> "HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()));
    }
}