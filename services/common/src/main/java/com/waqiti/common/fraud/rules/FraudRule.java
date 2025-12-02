package com.waqiti.common.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Represents a fraud detection rule with conditions, actions, and metadata.
 * Used by the fraud rules engine to evaluate transactions for suspicious patterns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRule {
    
    /**
     * Unique rule identifier
     */
    private String ruleId;
    
    /**
     * Human-readable rule name
     */
    private String name;
    
    /**
     * Detailed description of what this rule detects
     */
    private String description;
    
    /**
     * Rule category for organization
     */
    private RuleCategory category;
    
    /**
     * Rule severity level
     */
    private RuleSeverity severity;
    
    /**
     * Whether this rule is currently active
     */
    @Builder.Default
    private boolean active = true;
    
    /**
     * Rule priority (higher number = higher priority)
     */
    @Builder.Default
    private int priority = 1;
    
    /**
     * Rule conditions that must be met
     */
    private List<RuleCondition> conditions;
    
    /**
     * Actions to take when rule is triggered
     */
    private List<RuleAction> actions;
    
    /**
     * Risk score to assign when rule is triggered (0-100)
     */
    @Builder.Default
    private double riskScore = 50.0;
    
    /**
     * Rule execution weight (for weighted scoring)
     */
    @Builder.Default
    private double weight = 1.0;
    
    /**
     * Minimum confidence threshold for triggering (0.0-1.0)
     */
    @Builder.Default
    private double confidenceThreshold = 0.7;
    
    /**
     * Rule creation timestamp
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Last modification timestamp
     */
    @Builder.Default
    private LocalDateTime lastModified = LocalDateTime.now();
    
    /**
     * Rule creator/owner
     */
    private String createdBy;
    
    /**
     * Last person to modify the rule
     */
    private String modifiedBy;
    
    /**
     * Rule version for tracking changes
     */
    @Builder.Default
    private String version = "1.0";
    
    /**
     * Rule execution statistics
     */
    private RuleStatistics statistics;
    
    /**
     * Rule configuration parameters
     */
    private Map<String, Object> parameters;
    
    /**
     * Tags for rule categorization and filtering
     */
    private List<String> tags;
    
    /**
     * Effective date range for the rule
     */
    private DateRange effectivePeriod;
    
    /**
     * Geographic scope where rule applies
     */
    private List<String> geographicScope;
    
    /**
     * Business units where rule applies
     */
    private List<String> businessUnits;
    
    /**
     * Customer segments where rule applies
     */
    private List<String> customerSegments;
    
    /**
     * Rule dependencies (rules that must be evaluated first)
     */
    private List<String> dependencies;
    
    /**
     * Exception conditions that can override the rule
     */
    private List<RuleCondition> exceptions;
    
    /**
     * Machine learning enhancement settings
     */
    private MLEnhancementConfig mlConfig;
    
    /**
     * Evaluate this rule against transaction data
     */
    public RuleEvaluationResult evaluate(Map<String, Object> transactionData) {
        if (!isApplicable(transactionData)) {
            return RuleEvaluationResult.notApplicable(this.ruleId);
        }
        
        try {
            // Check if all conditions are met
            boolean conditionsMet = evaluateConditions(transactionData);
            
            // Check for exceptions
            boolean hasExceptions = evaluateExceptions(transactionData);
            
            // Calculate confidence score
            double confidence = calculateConfidence(transactionData);
            
            // Determine if rule should trigger
            boolean triggered = conditionsMet && !hasExceptions && confidence >= confidenceThreshold;
            
            RuleEvaluationResult result = RuleEvaluationResult.builder()
                    .ruleId(this.ruleId)
                    .ruleName(this.name)
                    .triggered(triggered)
                    .confidence(confidence)
                    .riskScore(triggered ? this.riskScore : 0.0)
                    .weight(this.weight)
                    .severity(this.severity)
                    .evaluationTime(LocalDateTime.now())
                    .conditionResults(getConditionResults(transactionData))
                    .exceptionResults(getExceptionResults(transactionData))
                    .build();
            
            // Update statistics
            updateStatistics(result);
            
            return result;
            
        } catch (Exception e) {
            return RuleEvaluationResult.error(this.ruleId, e.getMessage());
        }
    }
    
    /**
     * Check if rule is applicable to the transaction
     */
    private boolean isApplicable(Map<String, Object> transactionData) {
        if (!active) {
            return false;
        }
        
        // Check effective date range
        if (effectivePeriod != null && !effectivePeriod.isCurrentlyActive()) {
            return false;
        }
        
        // Check geographic scope
        if (geographicScope != null && !geographicScope.isEmpty()) {
            String country = (String) transactionData.get("country");
            if (country == null || !geographicScope.contains(country)) {
                return false;
            }
        }
        
        // Check business unit scope
        if (businessUnits != null && !businessUnits.isEmpty()) {
            String businessUnit = (String) transactionData.get("businessUnit");
            if (businessUnit == null || !businessUnits.contains(businessUnit)) {
                return false;
            }
        }
        
        // Check customer segment scope
        if (customerSegments != null && !customerSegments.isEmpty()) {
            String segment = (String) transactionData.get("customerSegment");
            if (segment == null || !customerSegments.contains(segment)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Evaluate all rule conditions
     */
    private boolean evaluateConditions(Map<String, Object> transactionData) {
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        
        return conditions.stream().allMatch(condition -> condition.evaluate(transactionData));
    }
    
    /**
     * Evaluate exception conditions
     */
    private boolean evaluateExceptions(Map<String, Object> transactionData) {
        if (exceptions == null || exceptions.isEmpty()) {
            return false;
        }
        
        return exceptions.stream().anyMatch(exception -> exception.evaluate(transactionData));
    }
    
    /**
     * Calculate confidence score for the rule match
     */
    private double calculateConfidence(Map<String, Object> transactionData) {
        if (conditions == null || conditions.isEmpty()) {
            return 0.0;
        }
        
        // Average confidence of all conditions
        double totalConfidence = conditions.stream()
                .mapToDouble(condition -> condition.getConfidence(transactionData))
                .average()
                .orElse(0.0);
        
        // Apply ML enhancement if configured
        if (mlConfig != null && mlConfig.isEnabled()) {
            totalConfidence = enhanceWithML(totalConfidence, transactionData);
        }
        
        return Math.min(1.0, Math.max(0.0, totalConfidence));
    }
    
    /**
     * Get detailed condition evaluation results
     */
    private Map<String, Boolean> getConditionResults(Map<String, Object> transactionData) {
        if (conditions == null) {
            return Map.of();
        }
        
        return conditions.stream()
                .collect(java.util.stream.Collectors.toMap(
                    condition -> condition.getName(),
                    condition -> condition.evaluate(transactionData)
                ));
    }
    
    /**
     * Get detailed exception evaluation results
     */
    private Map<String, Boolean> getExceptionResults(Map<String, Object> transactionData) {
        if (exceptions == null) {
            return Map.of();
        }
        
        return exceptions.stream()
                .collect(java.util.stream.Collectors.toMap(
                    exception -> exception.getName(),
                    exception -> exception.evaluate(transactionData)
                ));
    }
    
    /**
     * Update rule execution statistics
     */
    private void updateStatistics(RuleEvaluationResult result) {
        if (statistics == null) {
            statistics = new RuleStatistics();
        }
        statistics.recordExecution(result);
    }
    
    /**
     * Enhance confidence with machine learning
     */
    private double enhanceWithML(double baseConfidence, Map<String, Object> transactionData) {
        // Placeholder for ML enhancement logic
        // In production, this would call ML model for confidence adjustment
        return baseConfidence * mlConfig.getConfidenceMultiplier();
    }
    
    /**
     * Create a copy of this rule with modifications
     */
    public FraudRule copy() {
        return FraudRule.builder()
                .ruleId(this.ruleId + "_copy")
                .name(this.name + " (Copy)")
                .description(this.description)
                .category(this.category)
                .severity(this.severity)
                .active(false) // Copies start inactive
                .priority(this.priority)
                .conditions(this.conditions != null ? List.copyOf(this.conditions) : null)
                .actions(this.actions != null ? List.copyOf(this.actions) : null)
                .riskScore(this.riskScore)
                .weight(this.weight)
                .confidenceThreshold(this.confidenceThreshold)
                .parameters(this.parameters != null ? Map.copyOf(this.parameters) : null)
                .tags(this.tags != null ? List.copyOf(this.tags) : null)
                .geographicScope(this.geographicScope != null ? List.copyOf(this.geographicScope) : null)
                .businessUnits(this.businessUnits != null ? List.copyOf(this.businessUnits) : null)
                .customerSegments(this.customerSegments != null ? List.copyOf(this.customerSegments) : null)
                .createdAt(LocalDateTime.now())
                .createdBy("System")
                .version("1.0")
                .build();
    }
    
    /**
     * Validate rule configuration
     */
    public boolean isValid() {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            return false;
        }
        
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        
        if (riskScore < 0 || riskScore > 100) {
            return false;
        }
        
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            return false;
        }
        
        return conditions.stream().allMatch(RuleCondition::isValid);
    }
    
    /**
     * Get rule summary for display
     */
    public String getSummary() {
        return String.format("%s (%s) - %s - Risk: %.0f - Priority: %d - Status: %s",
                name, ruleId, severity, riskScore, priority, active ? "Active" : "Inactive");
    }
    
    // Supporting enums and classes
    
    public enum RuleCategory {
        AMOUNT_BASED,
        VELOCITY,
        GEOGRAPHIC,
        TEMPORAL,
        BEHAVIORAL,
        DEVICE_BASED,
        PATTERN_MATCHING,
        RISK_SCORING,
        COMPLIANCE,
        CUSTOM
    }
    
    public enum RuleSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        
        public boolean isCurrentlyActive() {
            LocalDateTime now = LocalDateTime.now();
            return (startDate == null || now.isAfter(startDate)) &&
                   (endDate == null || now.isBefore(endDate));
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLEnhancementConfig {
        private boolean enabled;
        private String modelId;
        private double confidenceMultiplier;
        private double weightAdjustment;
    }
    
    public static class RuleStatistics {
        private long totalExecutions = 0;
        private long totalTriggers = 0;
        private double averageConfidence = 0.0;
        private LocalDateTime lastExecution;
        
        public void recordExecution(RuleEvaluationResult result) {
            totalExecutions++;
            if (result.isTriggered()) {
                totalTriggers++;
            }
            averageConfidence = ((averageConfidence * (totalExecutions - 1)) + result.getConfidence()) / totalExecutions;
            lastExecution = LocalDateTime.now();
        }
        
        public double getTriggerRate() {
            return totalExecutions > 0 ? (double) totalTriggers / totalExecutions : 0.0;
        }
        
        // Getters
        public long getTotalExecutions() { return totalExecutions; }
        public long getTotalTriggers() { return totalTriggers; }
        public double getAverageConfidence() { return averageConfidence; }
        public LocalDateTime getLastExecution() { return lastExecution; }
    }

    /**
     * Convenience getter for rule name (backward compatibility)
     */
    public String getRuleName() {
        return name;
    }
}