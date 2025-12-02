package com.waqiti.common.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of evaluating a fraud detection rule against transaction data.
 * Contains the outcome, confidence scores, and detailed evaluation metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEvaluationResult {
    
    /**
     * Rule that was evaluated
     */
    private String ruleId;
    
    /**
     * Rule name for display
     */
    private String ruleName;
    
    /**
     * Whether the rule was triggered
     */
    private boolean triggered;
    
    /**
     * Confidence score of the evaluation (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * Risk score assigned by this rule (0-100)
     */
    private double riskScore;
    
    /**
     * Weight of this rule in overall scoring
     */
    private double weight;
    
    /**
     * Severity level of the rule
     */
    private FraudRule.RuleSeverity severity;
    
    /**
     * Timestamp when evaluation was performed
     */
    @Builder.Default
    private LocalDateTime evaluationTime = LocalDateTime.now();
    
    /**
     * Time taken to evaluate the rule in milliseconds
     */
    private long evaluationDurationMs;
    
    /**
     * Results of individual condition evaluations
     */
    private Map<String, Boolean> conditionResults;
    
    /**
     * Results of exception condition evaluations
     */
    private Map<String, Boolean> exceptionResults;
    
    /**
     * Detailed explanation of why rule triggered or didn't trigger
     */
    private String explanation;
    
    /**
     * Additional context information
     */
    private Map<String, Object> context;
    
    /**
     * Error message if evaluation failed
     */
    private String errorMessage;
    
    /**
     * Status of the rule evaluation
     */
    @Builder.Default
    private EvaluationStatus status = EvaluationStatus.SUCCESS;
    
    /**
     * Actions that would be triggered by this rule
     */
    private List<String> triggeredActions;
    
    /**
     * Features that contributed to the rule decision
     */
    private Map<String, Double> contributingFeatures;
    
    /**
     * Rule version that was evaluated
     */
    private String ruleVersion;
    
    /**
     * Whether this rule result should bypass further evaluation
     */
    private boolean shortCircuit;
    
    /**
     * Priority of this rule result for conflict resolution
     */
    private int priority;
    
    /**
     * Calculate weighted risk score
     */
    public double getWeightedRiskScore() {
        return triggered ? riskScore * weight : 0.0;
    }
    
    /**
     * Get risk level based on risk score
     */
    public RiskLevel getRiskLevel() {
        if (!triggered) {
            return RiskLevel.NONE;
        }
        
        if (riskScore >= 80) {
            return RiskLevel.CRITICAL;
        } else if (riskScore >= 60) {
            return RiskLevel.HIGH;
        } else if (riskScore >= 40) {
            return RiskLevel.MEDIUM;
        } else if (riskScore >= 20) {
            return RiskLevel.LOW;
        } else {
            return RiskLevel.MINIMAL;
        }
    }
    
    /**
     * Check if this is a high-confidence result
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * Check if rule was violated (alias for triggered)
     */
    public boolean isViolated() {
        return triggered;
    }

    /**
     * Get description/explanation of evaluation
     */
    public String getDescription() {
        return explanation != null ? explanation : (triggered ? "Rule triggered" : "Rule not triggered");
    }

    /**
     * Check if rule evaluation was successful
     */
    public boolean isSuccess() {
        return status == EvaluationStatus.SUCCESS;
    }
    
    /**
     * Check if rule evaluation had errors
     */
    public boolean hasError() {
        return status == EvaluationStatus.ERROR;
    }
    
    /**
     * Check if evaluation was skipped
     */
    public boolean wasSkipped() {
        return status == EvaluationStatus.SKIPPED || status == EvaluationStatus.NOT_APPLICABLE;
    }
    
    /**
     * Get detailed evaluation summary
     */
    public String getEvaluationSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Rule: %s (%s)\n", ruleName, ruleId));
        summary.append(String.format("Status: %s\n", status));
        summary.append(String.format("Triggered: %s\n", triggered));
        summary.append(String.format("Confidence: %.3f (%.1f%%)\n", confidence, confidence * 100));
        summary.append(String.format("Risk Score: %.1f\n", riskScore));
        summary.append(String.format("Risk Level: %s\n", getRiskLevel()));
        summary.append(String.format("Severity: %s\n", severity));
        summary.append(String.format("Evaluation Time: %d ms\n", evaluationDurationMs));
        
        if (conditionResults != null && !conditionResults.isEmpty()) {
            summary.append("\nCondition Results:\n");
            conditionResults.forEach((condition, result) -> 
                summary.append(String.format("  %s: %s\n", condition, result ? "PASS" : "FAIL")));
        }
        
        if (exceptionResults != null && !exceptionResults.isEmpty()) {
            summary.append("\nException Results:\n");
            exceptionResults.forEach((exception, result) -> 
                summary.append(String.format("  %s: %s\n", exception, result ? "TRIGGERED" : "NOT_TRIGGERED")));
        }
        
        if (triggeredActions != null && !triggeredActions.isEmpty()) {
            summary.append(String.format("\nTriggered Actions: %s\n", String.join(", ", triggeredActions)));
        }
        
        if (explanation != null) {
            summary.append(String.format("\nExplanation: %s\n", explanation));
        }
        
        if (errorMessage != null) {
            summary.append(String.format("\nError: %s\n", errorMessage));
        }
        
        return summary.toString();
    }
    
    /**
     * Get short summary for logging
     */
    public String getShortSummary() {
        return String.format("Rule %s: %s (Confidence: %.2f, Risk: %.1f, Time: %dms)",
            ruleId, triggered ? "TRIGGERED" : "NOT_TRIGGERED", confidence, riskScore, evaluationDurationMs);
    }
    
    /**
     * Create successful evaluation result
     */
    public static RuleEvaluationResult success(String ruleId, String ruleName, boolean triggered, 
                                             double confidence, double riskScore) {
        return RuleEvaluationResult.builder()
                .ruleId(ruleId)
                .ruleName(ruleName)
                .triggered(triggered)
                .confidence(confidence)
                .riskScore(triggered ? riskScore : 0.0)
                .status(EvaluationStatus.SUCCESS)
                .evaluationTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error evaluation result
     */
    public static RuleEvaluationResult error(String ruleId, String errorMessage) {
        return RuleEvaluationResult.builder()
                .ruleId(ruleId)
                .triggered(false)
                .confidence(0.0)
                .riskScore(0.0)
                .status(EvaluationStatus.ERROR)
                .errorMessage(errorMessage)
                .evaluationTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create skipped evaluation result
     */
    public static RuleEvaluationResult skipped(String ruleId, String reason) {
        return RuleEvaluationResult.builder()
                .ruleId(ruleId)
                .triggered(false)
                .confidence(0.0)
                .riskScore(0.0)
                .status(EvaluationStatus.SKIPPED)
                .explanation(reason)
                .evaluationTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create not applicable evaluation result
     */
    public static RuleEvaluationResult notApplicable(String ruleId) {
        return RuleEvaluationResult.builder()
                .ruleId(ruleId)
                .triggered(false)
                .confidence(0.0)
                .riskScore(0.0)
                .status(EvaluationStatus.NOT_APPLICABLE)
                .explanation("Rule not applicable to this transaction")
                .evaluationTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Merge this result with another result (for rule combinations)
     */
    public RuleEvaluationResult mergeWith(RuleEvaluationResult other) {
        if (other == null) {
            return this;
        }
        
        return RuleEvaluationResult.builder()
                .ruleId(this.ruleId + "+" + other.ruleId)
                .ruleName(this.ruleName + " + " + other.ruleName)
                .triggered(this.triggered || other.triggered)
                .confidence(Math.max(this.confidence, other.confidence))
                .riskScore(Math.max(this.riskScore, other.riskScore))
                .weight(this.weight + other.weight)
                .severity(this.severity.ordinal() > other.severity.ordinal() ? this.severity : other.severity)
                .status(this.status == EvaluationStatus.SUCCESS && other.status == EvaluationStatus.SUCCESS ? 
                       EvaluationStatus.SUCCESS : EvaluationStatus.PARTIAL)
                .evaluationTime(LocalDateTime.now())
                .evaluationDurationMs(this.evaluationDurationMs + other.evaluationDurationMs)
                .build();
    }
    
    /**
     * Convert to fraud alert if rule triggered with high confidence
     */
    public com.waqiti.common.fraud.FraudAlert toFraudAlert() {
        if (!triggered || !isHighConfidence()) {
            return null;
        }
        
        return com.waqiti.common.fraud.FraudAlert.builder()
                .probability(confidence)
                .confidence(confidence)
                .riskLevel(mapToMLRiskLevel(getRiskLevel()))
                .alertLevel(mapToMLAlertLevel(severity))
                .modelUsed("Rule: " + ruleId)
                .timestamp(evaluationTime)
                .build();
    }
    
    private com.waqiti.common.fraud.ml.MLPredictionResult.RiskLevel mapToMLRiskLevel(RiskLevel riskLevel) {
        switch (riskLevel) {
            case CRITICAL: return com.waqiti.common.fraud.ml.MLPredictionResult.RiskLevel.CRITICAL;
            case HIGH: return com.waqiti.common.fraud.ml.MLPredictionResult.RiskLevel.HIGH;
            case MEDIUM: return com.waqiti.common.fraud.ml.MLPredictionResult.RiskLevel.MEDIUM;
            case LOW: return com.waqiti.common.fraud.ml.MLPredictionResult.RiskLevel.LOW;
            default: return com.waqiti.common.fraud.ml.MLPredictionResult.RiskLevel.MINIMAL;
        }
    }
    
    private com.waqiti.common.fraud.model.AlertLevel mapToMLAlertLevel(FraudRule.RuleSeverity severity) {
        switch (severity) {
            case CRITICAL: return com.waqiti.common.fraud.model.AlertLevel.CRITICAL;
            case HIGH: return com.waqiti.common.fraud.model.AlertLevel.HIGH;
            case MEDIUM: return com.waqiti.common.fraud.model.AlertLevel.MEDIUM;
            default: return com.waqiti.common.fraud.model.AlertLevel.LOW;
        }
    }
    
    // Supporting enums
    
    public enum EvaluationStatus {
        SUCCESS,        // Rule evaluated successfully
        ERROR,          // Error during evaluation
        SKIPPED,        // Rule was skipped due to conditions
        NOT_APPLICABLE, // Rule not applicable to this transaction
        TIMEOUT,        // Evaluation timed out
        PARTIAL         // Partial evaluation completed
    }
    
    public enum RiskLevel {
        NONE, MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
}