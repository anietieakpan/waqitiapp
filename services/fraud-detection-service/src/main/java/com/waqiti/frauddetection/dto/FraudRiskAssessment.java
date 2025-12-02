package com.waqiti.frauddetection.dto;

import com.waqiti.frauddetection.dto.ml.FraudRiskScore;
import com.waqiti.frauddetection.dto.ml.ModelPrediction;
import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fraud Risk Assessment DTO
 *
 * Complete fraud risk assessment result combining ML predictions,
 * rule engine results, manual reviews, and final fraud decision.
 *
 * PRODUCTION-GRADE DTO
 * - Comprehensive fraud analysis results
 * - Multi-source risk scoring
 * - Explainability and audit trail
 * - Actionable recommendations
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskAssessment {

    /**
     * Assessment identification
     */
    @NotNull
    private String assessmentId;

    @NotNull
    private String transactionId;

    @NotNull
    private String userId;

    /**
     * Overall Assessment
     */
    @NotNull
    private FraudRiskScore riskScore;

    @NotNull
    private RiskLevel riskLevel;

    @NotNull
    private FraudDecision decision;

    /**
     * Decision reason and explanation
     */
    private String decisionReason;

    @Builder.Default
    private List<String> decisionFactors = new ArrayList<>();

    /**
     * ML Model Predictions
     */
    private ModelPrediction primaryModelPrediction;

    @Builder.Default
    private List<ModelPrediction> ensemblePredictions = new ArrayList<>();

    /**
     * Rule Engine Results
     */
    @Builder.Default
    private List<String> triggeredRules = new ArrayList<>();

    private Integer triggeredRulesCount;

    private Double ruleEngineScore;

    /**
     * Risk Factors
     */
    @Builder.Default
    private List<RiskFactor> identifiedRiskFactors = new ArrayList<>();

    private Integer highSeverityFactors;
    private Integer criticalFactors;

    /**
     * Actions Required
     */
    private Boolean requiresManualReview;
    private Boolean requiresAdditionalAuth;
    private Boolean blockedTransaction;

    @Builder.Default
    private List<String> recommendedActions = new ArrayList<>();

    /**
     * Monitoring and Alerts
     */
    private Boolean alertGenerated;
    private String alertSeverity; // "info", "warning", "high", "critical"
    private Boolean pagerDutyAlerted;
    private Boolean slackNotified;

    /**
     * Temporal Information
     */
    @Builder.Default
    private LocalDateTime assessedAt = LocalDateTime.now();

    private Long assessmentTimeMs;

    /**
     * Previous Assessment (for trend analysis)
     */
    private String previousAssessmentId;
    private Double previousRiskScore;
    private Boolean riskIncreasing;

    /**
     * Additional Context
     */
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Risk Factor Inner Class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorId;
        private String factorName;
        private String description;
        private String severity; // "low", "medium", "high", "critical"
        private Double impact; // 0.0 - 1.0
        private String category; // "behavior", "device", "location", "velocity"

        public boolean isCritical() {
            return "critical".equalsIgnoreCase(severity);
        }

        public boolean isHighSeverity() {
            return "high".equalsIgnoreCase(severity);
        }
    }

    /**
     * Check if transaction should be approved
     */
    public boolean isApproved() {
        return decision == FraudDecision.APPROVE ||
               decision == FraudDecision.APPROVE_WITH_MONITORING;
    }

    /**
     * Check if transaction should be rejected
     */
    public boolean isRejected() {
        return decision == FraudDecision.REJECT ||
               decision == FraudDecision.BLOCK;
    }

    /**
     * Check if requires review
     */
    public boolean requiresReview() {
        return Boolean.TRUE.equals(requiresManualReview) ||
               decision == FraudDecision.REVIEW ||
               decision == FraudDecision.MANUAL_REVIEW;
    }

    /**
     * Get risk score as percentage
     */
    public double getRiskScorePercentage() {
        return riskScore != null ? riskScore.getScorePercentage() : 0.0;
    }

    /**
     * Check if high risk
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    /**
     * Check if has critical factors
     */
    public boolean hasCriticalFactors() {
        return criticalFactors != null && criticalFactors > 0;
    }

    /**
     * Get assessment summary for logging
     */
    public String getSummary() {
        return String.format(
            "FraudAssessment[id=%s, tx=%s, risk=%.2f%%, level=%s, decision=%s, review=%s, blocked=%s, time=%dms]",
            assessmentId,
            transactionId,
            getRiskScorePercentage(),
            riskLevel,
            decision,
            requiresManualReview,
            blockedTransaction,
            assessmentTimeMs
        );
    }
}
