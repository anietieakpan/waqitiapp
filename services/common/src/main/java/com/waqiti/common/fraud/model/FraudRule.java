package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud detection rule with advanced logic and conditions
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudRule {
    
    private String ruleId;
    private String ruleName;
    private String description;
    private String category;
    private String subcategory;
    
    // Rule configuration
    private RuleType ruleType;
    private RuleSeverity severity;
    private Boolean isActive;
    private Boolean isEnabled;
    private Boolean enabled; // PRODUCTION FIX: Alias for isEnabled for builder compatibility
    private Integer priority;
    
    // Rule logic
    private String condition;
    private String logicExpression;
    private Map<String, Object> parameters;
    private String triggerEvents;
    private String evaluationFrequency;
    
    // Thresholds and limits
    private BigDecimal threshold; // PRODUCTION FIX: Generic threshold field for ComprehensiveFraudBlacklistService
    private BigDecimal amountThreshold;
    private Integer countThreshold;
    private Long timeWindowMs;
    private Double confidenceThreshold;
    private Map<String, Object> dynamicThresholds;
    
    // Rule targeting
    private List<String> applicableTransactionTypes;
    private List<String> applicableUserTypes;
    private List<String> applicableRegions;
    private List<String> excludedUsers;
    private List<String> excludedMerchants;
    
    // Actions and responses
    private List<String> triggeredActions;
    private String defaultAction;
    private Map<String, String> conditionalActions;
    private String escalationAction;
    private Boolean requiresManualReview;
    
    // Performance metrics
    private Integer executionCount;
    private Integer truePositiveCount;
    private Integer falsePositiveCount;
    private Double accuracy;
    private Double precision;
    private Double recall;
    private Double f1Score;
    
    // Rule lifecycle
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
    private LocalDateTime lastExecutedAt;
    private String createdBy;
    private String lastModifiedBy;
    private String version;
    
    // Rule effectiveness
    private Double effectivenessScore;
    private Long averageExecutionTimeMs;
    private Integer performanceRank;
    private LocalDateTime lastOptimizedAt;
    
    // Compliance and regulatory
    private List<String> regulatoryRequirements;
    private Boolean isRegulatoryRequired;
    private String complianceNotes;
    private LocalDateTime complianceReviewDate;
    
    // Advanced features
    private Boolean usesMachineLearning;
    private String mlModelId;
    private Map<String, Double> featureWeights;
    private Boolean isAdaptive;
    private String adaptationStrategy;
    
    // Dependencies and relationships
    private List<String> dependsOnRules;
    private List<String> conflictsWith;
    private String ruleGroup;
    private Boolean isCompositeRule;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private List<String> tags;
    private String notes;
    
    /**
     * Types of fraud rules
     */
    public enum RuleType {
        THRESHOLD,           // Simple threshold-based rules
        VELOCITY,           // Rate/velocity based rules
        PATTERN,            // Pattern detection rules
        BEHAVIORAL,         // Behavioral analysis rules
        NETWORK,            // Network/graph analysis rules
        STATISTICAL,        // Statistical anomaly detection
        MACHINE_LEARNING,   // ML-based rules
        COMPOSITE,          // Rules combining multiple conditions
        TEMPORAL,           // Time-based rules
        GEOGRAPHIC,         // Location-based rules
        CONTEXTUAL,         // Context-aware rules
        REGULATORY,         // Compliance-driven rules
        AMOUNT_CHECK,       // Amount-based validation rules
        VELOCITY_CHECK,     // Velocity checking rules
        BLACKLIST_CHECK     // Blacklist validation rules
    }
    
    /**
     * Rule severity levels
     */
    public enum RuleSeverity {
        INFORMATIONAL(0.1),
        LOW(0.3),
        MEDIUM(0.5),
        HIGH(0.7),
        CRITICAL(0.9),
        BLOCKER(0.95),      // PRODUCTION FIX: Blocker severity level
        EMERGENCY(1.0);

        private final double baseScore;

        RuleSeverity(double baseScore) {
            this.baseScore = baseScore;
        }

        public double getBaseScore() {
            return baseScore;
        }
    }
    
    /**
     * Calculate rule effectiveness based on performance metrics
     */
    public double calculateEffectiveness() {
        if (truePositiveCount == null || falsePositiveCount == null || executionCount == null) {
            return 0.0;
        }
        
        if (executionCount == 0) return 0.0;
        
        // Base effectiveness from accuracy
        double effectiveness = accuracy != null ? accuracy : 0.0;
        
        // Adjust for precision and recall balance
        if (precision != null && recall != null) {
            double f1 = f1Score != null ? f1Score : (2 * precision * recall) / (precision + recall);
            effectiveness = (effectiveness + f1) / 2.0;
        }
        
        // Penalize for low execution count (insufficient data)
        if (executionCount < 100) {
            effectiveness *= (executionCount / 100.0);
        }
        
        // Boost for low false positive rate
        if (falsePositiveCount == 0 && truePositiveCount > 0) {
            effectiveness += 0.1;
        }
        
        // Performance adjustment
        if (averageExecutionTimeMs != null && averageExecutionTimeMs < 100) {
            effectiveness += 0.05; // Bonus for fast execution
        }
        
        return Math.min(1.0, effectiveness);
    }
    
    /**
     * Check if rule should be triggered based on current performance
     */
    public boolean shouldExecute() {
        if (!isActive || !isEnabled) {
            return false;
        }
        
        // Check if rule is performing poorly and should be disabled
        if (accuracy != null && accuracy < 0.3 && executionCount > 100) {
            return false; // Poor performance
        }
        
        // Check if false positive rate is too high
        if (truePositiveCount != null && falsePositiveCount != null && 
            truePositiveCount + falsePositiveCount > 50) {
            double fpRate = (double) falsePositiveCount / (truePositiveCount + falsePositiveCount);
            if (fpRate > 0.8) {
                return false; // Too many false positives
            }
        }
        
        return true;
    }
    
    /**
     * Calculate rule risk score based on severity and confidence
     */
    public double calculateRiskScore(double confidence) {
        double baseScore = severity.getBaseScore();
        
        // Apply confidence multiplier
        double riskScore = baseScore * confidence;
        
        // Adjust based on rule effectiveness
        double effectiveness = calculateEffectiveness();
        if (effectiveness > 0) {
            riskScore *= (0.7 + (effectiveness * 0.3)); // 70-100% based on effectiveness
        }
        
        // Boost for regulatory required rules
        if (isRegulatoryRequired != null && isRegulatoryRequired) {
            riskScore *= 1.2;
        }
        
        return Math.min(1.0, riskScore);
    }
    
    /**
     * Get recommended actions based on rule configuration
     */
    public List<String> getRecommendedActions() {
        if (triggeredActions != null && !triggeredActions.isEmpty()) {
            return triggeredActions;
        }
        
        // Default actions based on severity
        switch (severity) {
            case EMERGENCY:
            case CRITICAL:
                return List.of("BLOCK_TRANSACTION", "FREEZE_ACCOUNT", "ESCALATE_TO_ANALYST", "NOTIFY_COMPLIANCE");
                
            case HIGH:
                return List.of("REQUIRE_ADDITIONAL_AUTH", "ENABLE_ENHANCED_MONITORING", "LOG_SECURITY_EVENT");
                
            case MEDIUM:
                return List.of("ENABLE_ENHANCED_MONITORING", "LOG_SECURITY_EVENT");
                
            case LOW:
            case INFORMATIONAL:
                return List.of("LOG_SECURITY_EVENT");
                
            default:
                return List.of("LOG_SECURITY_EVENT");
        }
    }
    
    /**
     * Check if rule needs performance optimization
     */
    public boolean needsOptimization() {
        // Check various optimization criteria
        
        // Poor accuracy
        if (accuracy != null && accuracy < 0.6 && executionCount > 100) {
            return true;
        }
        
        // High false positive rate
        if (truePositiveCount != null && falsePositiveCount != null && 
            truePositiveCount + falsePositiveCount > 50) {
            double fpRate = (double) falsePositiveCount / (truePositiveCount + falsePositiveCount);
            if (fpRate > 0.5) {
                return true;
            }
        }
        
        // Slow execution
        if (averageExecutionTimeMs != null && averageExecutionTimeMs > 1000) {
            return true;
        }
        
        // Not optimized recently
        if (lastOptimizedAt != null) {
            return lastOptimizedAt.isBefore(LocalDateTime.now().minusMonths(3));
        }
        
        return false;
    }
    
    /**
     * Generate rule performance report
     */
    public String generatePerformanceReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== FRAUD RULE PERFORMANCE REPORT ===\n");
        report.append("Rule: ").append(ruleName).append(" (").append(ruleId).append(")\n");
        report.append("Type: ").append(ruleType).append("\n");
        report.append("Severity: ").append(severity).append("\n");
        report.append("Status: ").append(isActive && isEnabled ? "ACTIVE" : "INACTIVE").append("\n\n");
        
        report.append("=== EXECUTION STATISTICS ===\n");
        if (executionCount != null) {
            report.append("Total Executions: ").append(executionCount).append("\n");
        }
        if (truePositiveCount != null) {
            report.append("True Positives: ").append(truePositiveCount).append("\n");
        }
        if (falsePositiveCount != null) {
            report.append("False Positives: ").append(falsePositiveCount).append("\n");
        }
        
        report.append("\n=== PERFORMANCE METRICS ===\n");
        if (accuracy != null) {
            report.append("Accuracy: ").append(String.format("%.3f", accuracy)).append("\n");
        }
        if (precision != null) {
            report.append("Precision: ").append(String.format("%.3f", precision)).append("\n");
        }
        if (recall != null) {
            report.append("Recall: ").append(String.format("%.3f", recall)).append("\n");
        }
        if (f1Score != null) {
            report.append("F1 Score: ").append(String.format("%.3f", f1Score)).append("\n");
        }
        
        report.append("Effectiveness Score: ").append(String.format("%.3f", calculateEffectiveness())).append("\n");
        
        if (averageExecutionTimeMs != null) {
            report.append("Average Execution Time: ").append(averageExecutionTimeMs).append("ms\n");
        }
        
        if (needsOptimization()) {
            report.append("\n=== OPTIMIZATION NEEDED ===\n");
            report.append("This rule requires performance optimization.\n");
        }
        
        return report.toString();
    }
    
    /**
     * Update rule performance metrics
     */
    public void updatePerformanceMetrics(boolean wasCorrect, long executionTimeMs) {
        if (executionCount == null) executionCount = 0;
        if (truePositiveCount == null) truePositiveCount = 0;
        if (falsePositiveCount == null) falsePositiveCount = 0;
        
        executionCount++;
        
        if (wasCorrect) {
            truePositiveCount++;
        } else {
            falsePositiveCount++;
        }
        
        // Update accuracy
        accuracy = (double) truePositiveCount / executionCount;
        
        // Update average execution time
        if (averageExecutionTimeMs == null) {
            averageExecutionTimeMs = executionTimeMs;
        } else {
            averageExecutionTimeMs = (averageExecutionTimeMs + executionTimeMs) / 2;
        }
        
        // Update effectiveness score
        effectivenessScore = calculateEffectiveness();
        
        lastExecutedAt = LocalDateTime.now();
    }
    
    /**
     * Get the rule ID
     */
    public String getId() {
        return ruleId;
    }

    /**
     * Get the rule name
     */
    public String getName() {
        return ruleName;
    }

    /**
     * Get the rule type as string
     */
    public String getType() {
        return ruleType != null ? ruleType.toString() : "UNKNOWN";
    }

    /**
     * PRODUCTION FIX: Get enabled status (for ComprehensiveFraudBlacklistService)
     */
    public Boolean getEnabled() {
        return isEnabled != null ? isEnabled : (isActive != null ? isActive : true);
    }

    /**
     * Get the recommended action for this rule
     */
    public String getRecommendedAction() {
        if (defaultAction != null) {
            return defaultAction;
        }

        // Return primary action from triggered actions
        if (triggeredActions != null && !triggeredActions.isEmpty()) {
            return triggeredActions.get(0);
        }

        // Return based on severity
        switch (severity) {
            case EMERGENCY:
            case CRITICAL:
                return "BLOCK_TRANSACTION";
            case HIGH:
                return "REQUIRE_ADDITIONAL_AUTH";
            case MEDIUM:
                return "ENABLE_ENHANCED_MONITORING";
            default:
                return "LOG_SECURITY_EVENT";
        }
    }
}