package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a comprehensive threat assessment result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAssessment {
    
    private UUID assessmentId;
    private UUID userId;
    private UUID sessionId;
    private String ipAddress;
    private String userAgent;
    private Instant timestamp;
    
    // Risk scoring
    private Integer riskScore; // 0-100
    private ThreatLevel threatLevel;
    private Double confidence; // 0.0-1.0
    
    // Assessment details
    private String assessmentType;
    private String context;
    private List<String> triggeredRules;
    private List<String> indicators;
    private Map<String, Object> evidenceData;
    
    // Threat categories
    private List<ThreatCategory> threatCategories;
    private String primaryThreatCategory;
    
    // Location and device analysis
    private GeolocationRisk geolocationRisk;
    private DeviceRisk deviceRisk;
    private BehavioralRisk behavioralRisk;
    
    // Historical context
    private Integer historicalIncidents;
    private Instant lastIncidentDate;
    private String accountAge;
    private Integer reputationScore;
    
    // Recommendations
    private List<String> recommendedActions;
    private String recommendedResponse;
    private boolean requiresManualReview;
    private boolean requiresImmediateAction;
    
    // Compliance flags
    private boolean isHighRiskJurisdiction;
    private boolean isPoliticallyExposed;
    private boolean isSanctionListed;
    private boolean requiresEnhancedDueDiligence;
    
    // Analysis metadata
    private String analysisModel;
    private String modelVersion;
    private Long processingTimeMs;
    private Map<String, Object> modelParameters;
    
    /**
     * Threat levels
     */
    public enum ThreatLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Threat categories
     */
    public enum ThreatCategory {
        FRAUD,
        IDENTITY_THEFT,
        ACCOUNT_TAKEOVER,
        MONEY_LAUNDERING,
        TERRORIST_FINANCING,
        INSIDER_THREAT,
        CYBERSECURITY,
        COMPLIANCE_VIOLATION,
        BEHAVIORAL_ANOMALY,
        GEOLOCATION_RISK,
        DEVICE_COMPROMISE
    }
    
    /**
     * Geolocation risk assessment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationRisk {
        private String country;
        private String region;
        private String city;
        private boolean isHighRiskLocation;
        private boolean isUnusualLocation;
        private boolean isVpnDetected;
        private boolean isTorDetected;
        private boolean isProxyDetected;
        private Integer locationRiskScore;
        private Double distanceFromUsualLocation;
    }
    
    /**
     * Device risk assessment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceRisk {
        private String deviceFingerprint;
        private boolean isKnownDevice;
        private boolean isCompromised;
        private boolean hasUnusualAttributes;
        private Integer deviceRiskScore;
        private String operatingSystem;
        private String browser;
        private boolean isEmulator;
        private boolean isRooted;
    }
    
    /**
     * Behavioral risk assessment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralRisk {
        private boolean isUnusualActivity;
        private boolean isRushingTransaction;
        private boolean hasUnusualPattern;
        private Integer behavioralScore;
        private List<String> anomalies;
        private Double velocityRisk;
        private boolean isAccountTakeoverSuspected;
    }
    
    /**
     * Check if assessment indicates high risk
     */
    public boolean isHighRisk() {
        return threatLevel == ThreatLevel.HIGH || threatLevel == ThreatLevel.CRITICAL ||
               riskScore >= 70;
    }
    
    /**
     * Check if assessment requires immediate action
     */
    public boolean requiresImmediateAction() {
        return requiresImmediateAction || threatLevel == ThreatLevel.CRITICAL;
    }
    
    /**
     * Check if assessment indicates potential fraud
     */
    public boolean indicatesFraud() {
        return threatCategories != null && 
               threatCategories.contains(ThreatCategory.FRAUD);
    }
    
    /**
     * Check if assessment indicates money laundering risk
     */
    public boolean indicatesMoneyLaundering() {
        return threatCategories != null && 
               threatCategories.contains(ThreatCategory.MONEY_LAUNDERING);
    }
    
    /**
     * Get risk score as percentage
     */
    public Double getRiskScorePercentage() {
        return riskScore != null ? riskScore.doubleValue() : 0.0;
    }
    
    /**
     * Check if confidence is high enough for automated action
     */
    public boolean hasHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }
}