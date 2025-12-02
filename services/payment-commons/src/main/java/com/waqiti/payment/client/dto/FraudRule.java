package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud rule DTO
 * Represents a configurable fraud detection rule
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FraudRule {
    
    @NotNull
    private UUID ruleId;
    
    @NotNull
    @Size(min = 1, max = 100)
    private String ruleName;
    
    private String description;
    
    @NotNull
    private RuleType ruleType;
    
    @NotNull
    private RuleStatus status;
    
    @NotNull
    private RuleSeverity severity;
    
    // Rule configuration
    private RuleConfiguration configuration;
    
    // Rule conditions
    @Builder.Default
    private List<RuleCondition> conditions = List.of();
    
    // Rule actions
    @Builder.Default
    private List<RuleAction> actions = List.of();
    
    // Execution context
    private RuleExecutionContext executionContext;
    
    // Performance metrics
    private RulePerformanceMetrics performanceMetrics;
    
    // Metadata
    private String createdBy;
    private LocalDateTime createdAt;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    
    private String category;
    private String version;
    private Map<String, Object> metadata;
    
    public enum RuleType {
        VELOCITY_CHECK,      // Transaction velocity rules
        AMOUNT_THRESHOLD,    // Amount-based rules
        GEOGRAPHIC_RISK,     // Location-based rules
        BEHAVIORAL_ANOMALY,  // Behavioral pattern rules
        BLACKLIST_CHECK,     // Blacklist matching rules
        DEVICE_RISK,         // Device-based rules
        NETWORK_RISK,        // Network/IP-based rules
        TIME_PATTERN,        // Time-based pattern rules
        FREQUENCY_LIMIT,     // Frequency limitation rules
        COMPLIANCE_CHECK,    // Regulatory compliance rules
        ML_SCORE_THRESHOLD,  // ML model score thresholds
        COMPOSITE_RULE,      // Complex multi-condition rules
        CUSTOM              // Custom business logic rules
    }
    
    public enum RuleStatus {
        ACTIVE,
        INACTIVE,
        TESTING,
        DRAFT,
        DEPRECATED,
        SUSPENDED
    }
    
    public enum RuleSeverity {
        INFO(1),
        LOW(2),
        MEDIUM(3),
        HIGH(4),
        CRITICAL(5);
        
        private final int level;
        
        RuleSeverity(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleConfiguration {
        private Integer priority;          // 1-100, higher = more important
        private Double weight;            // 0.0-1.0 rule weight in scoring
        private Boolean isBlocking;       // Blocks transaction if triggered
        private Boolean generateAlert;    // Generates alert when triggered
        private Boolean auditExecution;   // Logs rule execution
        private Integer cooldownPeriod;   // Minutes before rule can trigger again
        private Integer maxExecutionsPerHour;
        private Map<String, Object> customConfiguration;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleCondition {
        private UUID conditionId;
        private String fieldName;
        private ConditionOperator operator;
        private Object expectedValue;
        private Object comparisonValue;
        private LogicalOperator logicalOperator; // AND, OR for multiple conditions
        private Integer conditionOrder;
        private Boolean isRequired;
        private String conditionExpression; // For complex conditions
        
        public enum ConditionOperator {
            EQUALS,
            NOT_EQUALS,
            GREATER_THAN,
            GREATER_THAN_OR_EQUAL,
            LESS_THAN,
            LESS_THAN_OR_EQUAL,
            CONTAINS,
            NOT_CONTAINS,
            STARTS_WITH,
            ENDS_WITH,
            IN_LIST,
            NOT_IN_LIST,
            MATCHES_PATTERN,
            IS_NULL,
            IS_NOT_NULL,
            BETWEEN,
            NOT_BETWEEN
        }
        
        public enum LogicalOperator {
            AND,
            OR,
            NOT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleAction {
        private UUID actionId;
        private ActionType actionType;
        private String actionDescription;
        private Map<String, Object> actionParameters;
        private Integer executionOrder;
        private Boolean isConditional;
        private String conditionalExpression;
        
        public enum ActionType {
            DECLINE_TRANSACTION,
            REQUIRE_MANUAL_REVIEW,
            GENERATE_ALERT,
            ADD_RISK_SCORE,
            SUBTRACT_RISK_SCORE,
            SET_RISK_LEVEL,
            TRIGGER_ADDITIONAL_VERIFICATION,
            LOG_EVENT,
            SEND_NOTIFICATION,
            UPDATE_USER_PROFILE,
            ADD_TO_MONITORING_LIST,
            EXECUTE_WORKFLOW,
            CUSTOM_ACTION
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleExecutionContext {
        private String applicableEntityTypes; // USER, TRANSACTION, MERCHANT
        private String applicableChannels;    // WEB, MOBILE, API
        private String applicableTransactionTypes;
        private String applicableGeographies;
        private String applicableCurrencies;
        private BigDecimal minTransactionAmount;
        private BigDecimal maxTransactionAmount;
        private String timeRestrictions;     // Business hours, weekends, etc.
        private Boolean enabledForTesting;
        private Double testingPercentage;    // 0.0-100.0 for A/B testing
        private Map<String, Object> contextFilters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RulePerformanceMetrics {
        private Long totalExecutions;
        private Long totalTriggers;
        private Long truePositives;
        private Long falsePositives;
        private Long trueNegatives;
        private Long falseNegatives;
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Double averageExecutionTimeMs;
        private LocalDateTime lastExecuted;
        private LocalDateTime lastTriggered;
        private LocalDateTime metricsLastUpdated;
    }
    
    // Business logic methods
    public boolean isActive() {
        return status == RuleStatus.ACTIVE;
    }
    
    public boolean isBlocking() {
        return configuration != null && 
               configuration.getIsBlocking() != null && 
               configuration.getIsBlocking();
    }
    
    public boolean generatesAlert() {
        return configuration != null && 
               configuration.getGenerateAlert() != null && 
               configuration.getGenerateAlert();
    }
    
    public boolean isHighSeverity() {
        return severity == RuleSeverity.HIGH || severity == RuleSeverity.CRITICAL;
    }
    
    public boolean requiresManualReview() {
        return actions != null && 
               actions.stream()
                   .anyMatch(action -> action.getActionType() == RuleAction.ActionType.REQUIRE_MANUAL_REVIEW);
    }
    
    public boolean declinesTransaction() {
        return actions != null && 
               actions.stream()
                   .anyMatch(action -> action.getActionType() == RuleAction.ActionType.DECLINE_TRANSACTION);
    }
    
    public Double getEffectiveness() {
        if (performanceMetrics == null || 
            performanceMetrics.getTotalExecutions() == null || 
            performanceMetrics.getTotalExecutions() == 0) {
            return null;
        }
        
        if (performanceMetrics.getAccuracy() != null) {
            return performanceMetrics.getAccuracy();
        }
        
        // Calculate basic effectiveness
        long totalTriggers = performanceMetrics.getTotalTriggers() != null ? 
                           performanceMetrics.getTotalTriggers() : 0;
        
        return (double) totalTriggers / performanceMetrics.getTotalExecutions() * 100.0;
    }
    
    public boolean hasGoodPerformance() {
        Double effectiveness = getEffectiveness();
        return effectiveness != null && effectiveness >= 70.0;
    }
    
    public boolean isInCooldown(LocalDateTime currentTime) {
        if (configuration == null || 
            configuration.getCooldownPeriod() == null || 
            performanceMetrics == null || 
            performanceMetrics.getLastTriggered() == null) {
            return false;
        }
        
        LocalDateTime cooldownExpiry = performanceMetrics.getLastTriggered()
            .plusMinutes(configuration.getCooldownPeriod());
        
        return currentTime.isBefore(cooldownExpiry);
    }
    
    public boolean exceedsHourlyLimit() {
        if (configuration == null || 
            configuration.getMaxExecutionsPerHour() == null || 
            performanceMetrics == null || 
            performanceMetrics.getLastExecuted() == null) {
            return false;
        }
        
        // This would require tracking executions in the last hour
        // Implementation would depend on how execution history is stored
        return false; // Placeholder implementation
    }
}