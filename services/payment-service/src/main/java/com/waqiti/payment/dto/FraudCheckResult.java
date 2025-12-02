package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result DTO for fraud detection checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudCheckResult {

    private String checkId;
    private String transactionId;
    private Instant checkedAt;
    
    // Risk assessment
    private BigDecimal riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private boolean isHighRisk;
    private boolean requiresReview;
    
    // Risk factors and analysis
    private List<String> riskFactors;
    private Map<String, Object> riskDetails;
    private List<String> triggeredRules;
    
    // Fraud indicators
    private boolean velocityViolation;
    private boolean locationAnomaly;
    private boolean deviceRiskFlag;
    private boolean patternAnomaly;
    private boolean blacklistMatch;
    
    // Device analysis
    private String deviceFingerprint;
    private boolean deviceTrusted;
    private boolean newDevice;
    private String deviceRiskScore;
    
    // Geographic analysis
    private String ipAddress;
    private String detectedCountry;
    private String detectedCity;
    private boolean vpnDetected;
    private boolean proxyDetected;
    private boolean torDetected;
    
    // Behavioral analysis
    private boolean behaviorAnomaly;
    private BigDecimal behaviorScore;
    private String behaviorProfile;
    
    // Velocity analysis
    private int transactionCount24h;
    private BigDecimal transactionAmount24h;
    private boolean velocityExceeded;
    
    // ML model results
    private BigDecimal mlScore;
    private String mlModel;
    private String mlVersion;
    private Map<String, BigDecimal> mlFeatureScores;
    
    // Actions and recommendations
    private String recommendedAction; // ALLOW, BLOCK, REVIEW, CHALLENGE
    private List<String> requiredActions;
    private boolean requiresStepUp;
    private String challengeType;
    
    // Additional information
    private String providerId;
    private String providerRequestId;
    private Long processingTimeMs;
    private Map<String, Object> metadata;
    
    /**
     * Creates a low risk fraud check result
     */
    public static FraudCheckResult lowRisk(String transactionId) {
        return FraudCheckResult.builder()
                .transactionId(transactionId)
                .riskScore(BigDecimal.valueOf(0.1))
                .riskLevel("LOW")
                .isHighRisk(false)
                .requiresReview(false)
                .recommendedAction("ALLOW")
                .checkedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a high risk fraud check result
     */
    public static FraudCheckResult highRisk(String transactionId, BigDecimal riskScore, List<String> riskFactors) {
        return FraudCheckResult.builder()
                .transactionId(transactionId)
                .riskScore(riskScore)
                .riskLevel("HIGH")
                .isHighRisk(true)
                .requiresReview(true)
                .riskFactors(riskFactors)
                .recommendedAction("BLOCK")
                .checkedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a medium risk fraud check result requiring review
     */
    public static FraudCheckResult mediumRisk(String transactionId, BigDecimal riskScore) {
        return FraudCheckResult.builder()
                .transactionId(transactionId)
                .riskScore(riskScore)
                .riskLevel("MEDIUM")
                .isHighRisk(false)
                .requiresReview(true)
                .recommendedAction("REVIEW")
                .checkedAt(Instant.now())
                .build();
    }
    
    /**
     * Checks if the transaction should be blocked
     */
    public boolean shouldBlock() {
        return isHighRisk || "BLOCK".equals(recommendedAction);
    }
    
    /**
     * Checks if step-up authentication is required
     */
    public boolean requiresStepUpAuth() {
        return requiresStepUp || "CHALLENGE".equals(recommendedAction);
    }
    
    /**
     * Gets the primary risk reason
     */
    public String getPrimaryRiskReason() {
        if (riskFactors != null && !riskFactors.isEmpty()) {
            return riskFactors.get(0);
        }
        return "No specific risk factors identified";
    }
}