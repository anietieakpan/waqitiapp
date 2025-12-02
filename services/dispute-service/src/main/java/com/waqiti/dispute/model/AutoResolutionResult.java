package com.waqiti.dispute.model;

import com.waqiti.dispute.entity.ResolutionDecision;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Result of automated dispute resolution attempt
 */
@Data
@Builder
public class AutoResolutionResult {
    
    private boolean resolvable;
    private ResolutionDecision decision;
    private String reason;
    private Double confidenceScore;
    private Integer refundPercentage;
    private BigDecimal refundAmount;
    private String ineligibilityReason;
    
    // Rule evaluation results
    private Map<String, RuleEvaluation> ruleEvaluations;
    private List<String> appliedRules;
    private List<String> failedRules;
    
    // Risk assessment
    private Double fraudRiskScore;
    private Double customerRiskScore;
    private Double merchantRiskScore;
    
    // Evidence analysis
    private Map<String, Double> evidenceScores;
    private String strongestEvidence;
    private String weakestEvidence;
    
    /**
     * Create resolvable result
     */
    public static AutoResolutionResult resolvable(ResolutionDecision decision, String reason, double confidence) {
        return AutoResolutionResult.builder()
            .resolvable(true)
            .decision(decision)
            .reason(reason)
            .confidenceScore(confidence)
            .build();
    }
    
    /**
     * Create non-resolvable result
     */
    public static AutoResolutionResult notResolvable(String ineligibilityReason) {
        return AutoResolutionResult.builder()
            .resolvable(false)
            .ineligibilityReason(ineligibilityReason)
            .build();
    }
    
    /**
     * Check if high confidence resolution
     */
    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore >= 0.8;
    }
    
    /**
     * Check if requires manual review
     */
    public boolean requiresManualReview() {
        return !resolvable || (confidenceScore != null && confidenceScore < 0.6);
    }
    
    /**
     * Rule evaluation result
     */
    @Data
    @Builder
    public static class RuleEvaluation {
        private String ruleName;
        private boolean passed;
        private double score;
        private String failureReason;
        private Map<String, Object> metadata;
    }
}