package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud rule evaluation result with detailed analysis
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleEvaluation {
    
    private String evaluationId;
    private String ruleId;
    private String ruleName;
    private String transactionId;
    private String userId;
    private String sessionId;
    
    // Evaluation results
    private Boolean triggered;
    private Boolean violated; // PRODUCTION FIX: Alias for triggered for ComprehensiveFraudBlacklistService
    private Boolean passed; // Inverse of triggered for compatibility
    private Double confidence;
    private Double riskScore;
    private Double score; // Alias for riskScore for compatibility
    private Double violationScore; // PRODUCTION FIX: Alias for riskScore for builder
    private String triggerReason;
    private String violationReason; // PRODUCTION FIX: Alias for triggerReason
    private String evaluationResult;
    
    // Rule execution details
    private LocalDateTime evaluatedAt;
    private Long executionTimeMs;
    private String evaluationMethod;
    private String executionContext;
    private Boolean evaluationSuccessful;
    private String evaluationError;
    
    // Condition analysis
    private Map<String, Boolean> conditionResults;
    private List<String> triggeredConditions;
    private List<String> failedConditions;
    private String primaryCondition;
    private Double conditionConfidence;
    
    // Input data analysis
    private Map<String, Object> inputData;
    private List<String> missingRequiredData;
    private List<String> dataQualityIssues;
    private Double dataCompletenessScore;
    
    // Threshold analysis
    private Map<String, Object> thresholdValues;
    private Map<String, Object> actualValues;
    private Map<String, Double> thresholdViolationSeverity;
    private String mostSignificantViolation;
    
    // Historical context
    private Integer previousTriggerCount;
    private LocalDateTime lastTriggerTime;
    private Integer recentTriggerCount;
    private String triggerPattern;
    
    // Machine learning insights (if applicable)
    private Double mlScore;
    private String mlModelId;
    private Map<String, Double> featureImportance;
    private String mlExplanation;
    private Double mlConfidence;
    
    // Performance tracking
    private Boolean isValidEvaluation;
    private String validationNotes;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private ValidationOutcome outcome;
    
    // Action recommendations
    private List<String> recommendedActions;
    private String primaryAction;
    private ActionUrgency actionUrgency;
    private String actionReason;
    private Boolean requiresHumanReview;
    
    // Additional analysis
    private Map<String, Object> additionalMetrics;
    private List<String> riskFactors;
    private List<String> mitigatingFactors;
    private String overallAssessment;
    
    /**
     * Validation outcomes for rule evaluations
     */
    public enum ValidationOutcome {
        TRUE_POSITIVE,
        FALSE_POSITIVE,
        TRUE_NEGATIVE,
        FALSE_NEGATIVE,
        INCONCLUSIVE,
        PENDING_REVIEW
    }
    
    /**
     * Action urgency levels
     */
    public enum ActionUrgency {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        IMMEDIATE
    }
    
    /**
     * Calculate overall evaluation confidence
     */
    public double calculateOverallConfidence() {
        double overallConfidence = 0.0;
        double weightSum = 0.0;
        
        // Rule confidence weight (highest)
        if (confidence != null) {
            overallConfidence += confidence * 0.4;
            weightSum += 0.4;
        }
        
        // Data completeness weight
        if (dataCompletenessScore != null) {
            overallConfidence += dataCompletenessScore * 0.2;
            weightSum += 0.2;
        }
        
        // Condition confidence weight
        if (conditionConfidence != null) {
            overallConfidence += conditionConfidence * 0.2;
            weightSum += 0.2;
        }
        
        // ML confidence weight (if applicable)
        if (mlConfidence != null) {
            overallConfidence += mlConfidence * 0.2;
            weightSum += 0.2;
        }
        
        // Normalize by actual weights used
        return weightSum > 0 ? overallConfidence / weightSum : 0.0;
    }
    
    /**
     * Check if evaluation indicates high-risk scenario
     */
    public boolean isHighRisk() {
        return (riskScore != null && riskScore > 0.7) ||
               (confidence != null && confidence > 0.8 && triggered != null && triggered) ||
               (actionUrgency == ActionUrgency.IMMEDIATE || actionUrgency == ActionUrgency.HIGH);
    }
    
    /**
     * Generate detailed evaluation summary
     */
    public String generateEvaluationSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("=== FRAUD RULE EVALUATION SUMMARY ===\n");
        summary.append("Rule: ").append(ruleName).append(" (").append(ruleId).append(")\n");
        summary.append("Evaluation ID: ").append(evaluationId).append("\n");
        summary.append("Transaction: ").append(transactionId).append("\n");
        summary.append("Evaluated At: ").append(evaluatedAt).append("\n\n");
        
        summary.append("=== EVALUATION RESULTS ===\n");
        summary.append("Triggered: ").append(triggered != null ? triggered : "UNKNOWN").append("\n");
        if (confidence != null) {
            summary.append("Confidence: ").append(String.format("%.3f", confidence)).append("\n");
        }
        if (riskScore != null) {
            summary.append("Risk Score: ").append(String.format("%.3f", riskScore)).append("\n");
        }
        if (triggerReason != null) {
            summary.append("Trigger Reason: ").append(triggerReason).append("\n");
        }
        
        summary.append("\n=== CONDITION ANALYSIS ===\n");
        if (triggeredConditions != null && !triggeredConditions.isEmpty()) {
            summary.append("Triggered Conditions: ").append(String.join(", ", triggeredConditions)).append("\n");
        }
        if (failedConditions != null && !failedConditions.isEmpty()) {
            summary.append("Failed Conditions: ").append(String.join(", ", failedConditions)).append("\n");
        }
        if (primaryCondition != null) {
            summary.append("Primary Condition: ").append(primaryCondition).append("\n");
        }
        
        if (thresholdViolationSeverity != null && !thresholdViolationSeverity.isEmpty()) {
            summary.append("\n=== THRESHOLD VIOLATIONS ===\n");
            thresholdViolationSeverity.forEach((threshold, severity) -> 
                summary.append(threshold).append(": ").append(String.format("%.3f", severity)).append("\n"));
        }
        
        if (mlScore != null) {
            summary.append("\n=== MACHINE LEARNING INSIGHTS ===\n");
            summary.append("ML Score: ").append(String.format("%.3f", mlScore)).append("\n");
            if (mlExplanation != null) {
                summary.append("ML Explanation: ").append(mlExplanation).append("\n");
            }
        }
        
        if (recommendedActions != null && !recommendedActions.isEmpty()) {
            summary.append("\n=== RECOMMENDED ACTIONS ===\n");
            summary.append("Primary Action: ").append(primaryAction).append("\n");
            summary.append("All Actions: ").append(String.join(", ", recommendedActions)).append("\n");
            summary.append("Urgency: ").append(actionUrgency).append("\n");
        }
        
        if (executionTimeMs != null) {
            summary.append("\n=== PERFORMANCE ===\n");
            summary.append("Execution Time: ").append(executionTimeMs).append("ms\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Calculate evaluation quality score
     */
    public double calculateEvaluationQuality() {
        double quality = 0.5; // Base quality
        
        // Data completeness factor
        if (dataCompletenessScore != null) {
            quality += dataCompletenessScore * 0.3;
        }
        
        // Execution success factor
        if (evaluationSuccessful != null && evaluationSuccessful) {
            quality += 0.2;
        }
        
        // Missing data penalty
        if (missingRequiredData != null && !missingRequiredData.isEmpty()) {
            quality -= Math.min(0.3, missingRequiredData.size() * 0.05);
        }
        
        // Data quality issues penalty
        if (dataQualityIssues != null && !dataQualityIssues.isEmpty()) {
            quality -= Math.min(0.2, dataQualityIssues.size() * 0.03);
        }
        
        // Fast execution bonus
        if (executionTimeMs != null && executionTimeMs < 100) {
            quality += 0.1;
        }
        
        return Math.max(0.0, Math.min(1.0, quality));
    }
    
    /**
     * Get the most critical risk factor identified
     */
    public String getCriticalRiskFactor() {
        if (mostSignificantViolation != null) {
            return mostSignificantViolation;
        }
        
        if (primaryCondition != null) {
            return primaryCondition;
        }
        
        if (riskFactors != null && !riskFactors.isEmpty()) {
            return riskFactors.get(0);
        }
        
        return triggerReason;
    }
    
    /**
     * Check if evaluation requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return isHighRisk() ||
               (actionUrgency == ActionUrgency.IMMEDIATE) ||
               (requiresHumanReview != null && requiresHumanReview) ||
               (executionTimeMs != null && executionTimeMs > 5000); // Performance issue
    }
    
    /**
     * Get detailed explanation of the evaluation result
     */
    public String getDetailedExplanation() {
        StringBuilder explanation = new StringBuilder();
        
        if (triggered != null && triggered) {
            explanation.append("Rule triggered due to: ");
            
            if (triggerReason != null) {
                explanation.append(triggerReason);
            } else if (primaryCondition != null) {
                explanation.append(primaryCondition);
            } else {
                explanation.append("unspecified condition");
            }
            
            if (confidence != null) {
                explanation.append(" (confidence: ").append(String.format("%.1f%%", confidence * 100)).append(")");
            }
        } else {
            explanation.append("Rule did not trigger - all conditions passed within acceptable limits");
        }
        
        if (mlExplanation != null) {
            explanation.append(". ML analysis: ").append(mlExplanation);
        }
        
        if (riskScore != null && riskScore > 0.5) {
            explanation.append(". Risk level: ").append(riskScore > 0.8 ? "HIGH" : "MEDIUM");
        }
        
        return explanation.toString();
    }
    
    /**
     * Update evaluation outcome after human review
     */
    public void updateValidationOutcome(ValidationOutcome outcome, String notes, String reviewedBy) {
        this.outcome = outcome;
        this.validationNotes = notes;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        
        // Update validation flag
        this.isValidEvaluation = outcome == ValidationOutcome.TRUE_POSITIVE || 
                               outcome == ValidationOutcome.TRUE_NEGATIVE;
    }
    
    /**
     * PRODUCTION FIX: Check if rule was violated (alias for triggered)
     */
    public boolean isViolated() {
        if (violated != null) {
            return violated;
        }
        return triggered != null && triggered;
    }

    /**
     * PRODUCTION FIX: Get violated status (for ComprehensiveFraudBlacklistService)
     */
    public Boolean getViolated() {
        if (violated != null) {
            return violated;
        }
        return triggered;
    }

    /**
     * Get the violation reason (trigger reason)
     */
    public String getViolationReason() {
        if (violationReason != null) {
            return violationReason;
        }
        if (triggerReason != null) {
            return triggerReason;
        }
        if (triggered != null && triggered) {
            return primaryCondition != null ? primaryCondition : "Rule conditions violated";
        }
        return null;
    }

    /**
     * Get the evaluation score
     */
    public Double getScore() {
        return riskScore != null ? riskScore : (confidence != null ? confidence : 0.0);
    }
}