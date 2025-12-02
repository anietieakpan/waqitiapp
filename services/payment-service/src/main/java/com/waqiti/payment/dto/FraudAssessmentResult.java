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
 * Result DTO for comprehensive fraud assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudAssessmentResult {

    private String assessmentId;
    private String transactionId;
    private Instant assessedAt;
    private Long processingTimeMs;
    
    // Overall risk assessment
    private BigDecimal riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private boolean isHighRisk;
    private boolean requiresReview;
    private String finalDecision; // APPROVE, DECLINE, REVIEW, CHALLENGE
    
    // Risk breakdown
    private List<String> riskFactors;
    private Map<String, BigDecimal> riskScoreBreakdown;
    private List<String> triggeredRules;
    private Map<String, Object> riskDetails;
    
    // Specific risk indicators
    private VelocityRiskResult velocityRisk;
    private DeviceRiskResult deviceRisk;
    private LocationRiskResult locationRisk;
    private BehavioralRiskResult behavioralRisk;
    private AccountRiskResult accountRisk;
    
    // ML model results
    private MLFraudResult mlResult;
    private Map<String, BigDecimal> modelScores;
    private String primaryModel;
    private String modelVersion;
    
    // Blacklist and watchlist results
    private BlacklistResult blacklistResult;
    private WatchlistResult watchlistResult;
    
    // Recommendations
    private String recommendedAction;
    private List<String> requiredActions;
    private String challengeType;
    private boolean requiresStepUpAuth;
    private String stepUpMethod;
    
    // Monitoring and alerting
    private boolean generateAlert;
    private String alertLevel;
    private List<String> alertRecipients;
    
    // Additional context
    private Map<String, Object> metadata;
    private String providerId;
    private String providerCorrelationId;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityRiskResult {
        private BigDecimal velocityScore;
        private boolean velocityViolation;
        private Integer transactionCount24h;
        private BigDecimal transactionAmount24h;
        private Integer failedAttempts24h;
        private boolean exceedsVelocityLimits;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceRiskResult {
        private BigDecimal deviceScore;
        private boolean deviceTrusted;
        private boolean newDevice;
        private String deviceReputation;
        private boolean deviceCompromised;
        private String deviceRiskFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationRiskResult {
        private BigDecimal locationScore;
        private boolean locationAnomaly;
        private String detectedCountry;
        private String detectedCity;
        private boolean vpnDetected;
        private boolean proxyDetected;
        private boolean torDetected;
        private boolean highRiskLocation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralRiskResult {
        private BigDecimal behaviorScore;
        private boolean behaviorAnomaly;
        private String behaviorProfile;
        private List<String> anomalyTypes;
        private String deviationLevel;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountRiskResult {
        private BigDecimal accountScore;
        private String accountRiskProfile;
        private boolean newAccount;
        private boolean suspiciousActivity;
        private String kycStatus;
        private List<String> accountFlags;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLFraudResult {
        private BigDecimal mlScore;
        private String modelName;
        private String modelVersion;
        private Map<String, BigDecimal> featureImportances;
        private String predictionConfidence;
        private List<String> significantFeatures;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlacklistResult {
        private boolean isBlacklisted;
        private List<String> blacklistMatches;
        private String blacklistType;
        private String matchReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatchlistResult {
        private boolean isWatchlisted;
        private List<String> watchlistMatches;
        private String watchlistType;
        private String alertLevel;
    }
    
    /**
     * Creates a low risk assessment result
     */
    public static FraudAssessmentResult lowRisk() {
        return FraudAssessmentResult.builder()
                .riskScore(BigDecimal.valueOf(0.15))
                .riskLevel("LOW")
                .isHighRisk(false)
                .requiresReview(false)
                .finalDecision("APPROVE")
                .recommendedAction("APPROVE")
                .assessedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a high risk assessment result
     */
    public static FraudAssessmentResult highRisk(BigDecimal riskScore, List<String> riskFactors) {
        return FraudAssessmentResult.builder()
                .riskScore(riskScore)
                .riskLevel("HIGH")
                .isHighRisk(true)
                .requiresReview(true)
                .finalDecision("DECLINE")
                .recommendedAction("DECLINE")
                .riskFactors(riskFactors)
                .generateAlert(true)
                .alertLevel("HIGH")
                .assessedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a medium risk assessment result requiring review
     */
    public static FraudAssessmentResult mediumRisk(BigDecimal riskScore) {
        return FraudAssessmentResult.builder()
                .riskScore(riskScore)
                .riskLevel("MEDIUM")
                .isHighRisk(false)
                .requiresReview(true)
                .finalDecision("REVIEW")
                .recommendedAction("REVIEW")
                .generateAlert(true)
                .alertLevel("MEDIUM")
                .assessedAt(Instant.now())
                .build();
    }
    
    /**
     * Checks if the transaction should be declined based on risk
     */
    public boolean shouldDecline() {
        return "DECLINE".equals(finalDecision) || isHighRisk;
    }
    
    /**
     * Checks if manual review is required
     */
    public boolean requiresManualReview() {
        return requiresReview || "REVIEW".equals(finalDecision);
    }
    
    /**
     * Gets the primary risk factor
     */
    public String getPrimaryRiskFactor() {
        if (riskFactors != null && !riskFactors.isEmpty()) {
            return riskFactors.get(0);
        }
        return "No specific risk factors identified";
    }
    
    /**
     * Checks if the result indicates potential fraud
     */
    public boolean indicatesFraud() {
        return isHighRisk || riskScore.compareTo(BigDecimal.valueOf(0.7)) >= 0;
    }
}