package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for fraud detection checks.
 * Contains comprehensive fraud analysis results and recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResponse {

    private UUID transactionId;
    private UUID checkId;
    private LocalDateTime checkTimestamp;
    
    // Primary fraud detection result
    private FraudDecision decision;
    private Double riskScore; // 0-100 scale
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    // Detailed analysis
    private List<FraudIndicator> fraudIndicators;
    private List<String> triggeredRules;
    private Map<String, Double> riskFactors;
    
    // Recommendations
    private List<String> recommendedActions;
    private Boolean requiresManualReview;
    private Boolean requiresAdditionalVerification;
    private String verificationMethod; // SMS_OTP, EMAIL_OTP, BIOMETRIC, DOCUMENT
    
    // ML Model results
    private Double mlConfidenceScore;
    private String mlModelVersion;
    private Map<String, Double> mlFeatureImportance;
    
    // Historical context
    private Integer userHistoricalRiskScore;
    private Integer recentSuspiciousActivities;
    private LocalDateTime lastFraudAttempt;
    
    // Velocity check results
    private VelocityCheckResult velocityCheckResult;
    
    // Geo-location analysis
    private GeoLocationAnalysis geoLocationAnalysis;
    
    // Device fingerprint analysis
    private DeviceAnalysis deviceAnalysis;
    
    // Network analysis
    private NetworkAnalysis networkAnalysis;
    
    // Compliance checks
    private ComplianceCheckResult complianceResult;
    
    // Processing metadata
    private Long processingTimeMs;
    private String processingNode;
    private String checkVersion;

    /**
     * Enum for fraud detection decisions
     */
    public enum FraudDecision {
        APPROVE,           // Transaction is safe to proceed
        DECLINE,           // Transaction should be blocked
        REVIEW,            // Requires manual review
        CHALLENGE,         // Requires additional authentication
        HOLD,              // Temporarily hold for further checks
        ESCALATE          // Escalate to senior fraud analyst
    }

    /**
     * Individual fraud indicator
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudIndicator {
        private String indicatorType;
        private String description;
        private Double weight;
        private Double score;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Object> details;
    }

    /**
     * Velocity check results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityCheckResult {
        private Boolean passed;
        private Integer transactionsInLastHour;
        private Integer transactionsInLastDay;
        private BigDecimal amountInLastHour;
        private BigDecimal amountInLastDay;
        private List<String> violatedLimits;
        private Map<String, Object> velocityMetrics;
    }

    /**
     * Geo-location analysis results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocationAnalysis {
        private Boolean isHighRiskLocation;
        private Boolean isUnusualLocation;
        private Double distanceFromUsualLocation;
        private String countryRiskLevel;
        private Boolean isVpnDetected;
        private Boolean isTorDetected;
        private Boolean isProxyDetected;
        private String ipReputationScore;
        private List<String> geoAnomalies;
    }

    /**
     * Device analysis results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAnalysis {
        private Boolean isKnownDevice;
        private Boolean isCompromisedDevice;
        private Integer deviceTrustScore;
        private String deviceReputation;
        private Boolean hasJailbreakOrRoot;
        private Boolean hasEmulator;
        private List<String> deviceAnomalies;
        private LocalDateTime firstSeenDate;
        private Integer transactionCountOnDevice;
    }

    /**
     * Network analysis results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAnalysis {
        private Boolean isLinkedToFraudulentAccounts;
        private Integer fraudNetworkScore;
        private List<UUID> relatedSuspiciousAccounts;
        private Map<String, Integer> networkRiskFactors;
        private Boolean hasSharedPaymentMethods;
        private Boolean hasSharedDevices;
        private Boolean hasSharedIpAddresses;
    }

    /**
     * Compliance check results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceCheckResult {
        private Boolean isSanctioned;
        private Boolean isPep; // Politically Exposed Person
        private Boolean hasAmlFlags;
        private List<String> complianceViolations;
        private String kycStatus;
        private LocalDateTime kycExpiryDate;
        private Map<String, String> complianceMetadata;
    }

    /**
     * Determines if the transaction should be allowed based on the decision.
     */
    public boolean isApproved() {
        return decision == FraudDecision.APPROVE;
    }

    /**
     * Determines if the transaction requires immediate blocking.
     */
    public boolean shouldBlock() {
        return decision == FraudDecision.DECLINE;
    }

    /**
     * Determines if additional verification is needed.
     */
    public boolean needsVerification() {
        return decision == FraudDecision.CHALLENGE || requiresAdditionalVerification;
    }

    /**
     * Gets the overall risk classification.
     */
    public String getRiskClassification() {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore < 25) return "LOW";
        if (riskScore < 50) return "MEDIUM";
        if (riskScore < 75) return "HIGH";
        return "CRITICAL";
    }
}