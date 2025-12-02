package com.waqiti.ml.fraud.model;

import com.waqiti.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud analysis result containing all analysis outputs,
 * risk scores, and recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResult {

    private String transactionId;
    private String userId;
    private LocalDateTime timestamp;

    // Overall risk assessment
    private Double riskScore; // 0.0 - 1.0
    private RiskLevel riskLevel;
    private String riskCategory;

    // Individual analysis results
    @Builder.Default
    private Map<String, Object> analysisResults = new HashMap<>();

    // Detailed scores by category
    private Double velocityScore;
    private Double behaviorScore;
    private Double geolocationScore;
    private Double deviceScore;
    private Double networkScore;
    private Double patternScore;
    private Double mlScore;
    private Double graphScore;

    // Risk factors detected
    @Builder.Default
    private List<String> riskFactors = new ArrayList<>();

    // Recommendations
    @Builder.Default
    private List<FraudRecommendation> recommendations = new ArrayList<>();

    // Fraud indicators
    @Builder.Default
    private List<String> fraudIndicators = new ArrayList<>();

    // Decision
    private FraudDecision decision; // APPROVE, REVIEW, BLOCK
    private String decisionReason;
    private String decisionCode;

    // Additional context
    private String error;
    private Map<String, Object> metadata;

    // Analysis metrics
    private Long analysisTimeMs;
    private String modelVersion;
    private List<String> modelsUsed;

    /**
     * Add analysis result for a specific category
     */
    public void addAnalysis(String category, Object result) {
        if (analysisResults == null) {
            analysisResults = new HashMap<>();
        }
        analysisResults.put(category, result);
    }

    /**
     * Add a risk factor
     */
    public void addRiskFactor(String factor) {
        if (riskFactors == null) {
            riskFactors = new ArrayList<>();
        }
        riskFactors.add(factor);
    }

    /**
     * Add a fraud indicator
     */
    public void addFraudIndicator(String indicator) {
        if (fraudIndicators == null) {
            fraudIndicators = new ArrayList<>();
        }
        fraudIndicators.add(indicator);
    }

    /**
     * Check if transaction is high risk
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    /**
     * Check if manual review is recommended
     */
    public boolean requiresManualReview() {
        return riskLevel == RiskLevel.MEDIUM || riskLevel == RiskLevel.HIGH ||
               decision == FraudDecision.REVIEW;
    }

    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlock() {
        return riskLevel == RiskLevel.CRITICAL || decision == FraudDecision.BLOCK;
    }

    /**
     * Get analysis result for specific category
     */
    public <T> T getAnalysisResult(String category, Class<T> type) {
        if (analysisResults == null || !analysisResults.containsKey(category)) {
            return null;
        }
        Object result = analysisResults.get(category);
        return type.isInstance(result) ? type.cast(result) : null;
    }

    /**
     * Fraud decision enum
     */
    public enum FraudDecision {
        APPROVE,
        REVIEW,
        BLOCK,
        CHALLENGE // Require additional authentication
    }
}
