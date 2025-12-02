package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud rule creation/modification request DTO
 * For creating, updating, or managing fraud detection rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FraudRuleRequest {
    
    private UUID ruleId; // For updates, null for new rules
    
    @NotNull
    @Size(min = 1, max = 100)
    private String ruleName;
    
    @Size(max = 500)
    private String description;
    
    @NotNull
    private FraudRule.RuleType ruleType;
    
    @NotNull
    private FraudRule.RuleSeverity severity;
    
    @NotNull
    private RequestType requestType;
    
    // Rule specification
    private RuleSpecification specification;
    
    // Rule conditions to create/modify
    @Builder.Default
    private List<ConditionRequest> conditions = List.of();
    
    // Rule actions to create/modify
    @Builder.Default
    private List<ActionRequest> actions = List.of();
    
    // Execution parameters
    private ExecutionParameters executionParameters;
    
    // Testing configuration
    private TestingConfiguration testingConfiguration;
    
    // Approval workflow
    private ApprovalRequest approvalRequest;
    
    // Request metadata
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String businessJustification;
    private String impactAssessment;
    
    private Map<String, Object> additionalMetadata;
    
    public enum RequestType {
        CREATE,
        UPDATE,
        ACTIVATE,
        DEACTIVATE,
        DELETE,
        CLONE,
        TEST,
        VALIDATE
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleSpecification {
        @NotNull
        @Min(1) @Max(100)
        private Integer priority;
        
        @NotNull
        @DecimalMin("0.0") @DecimalMax("1.0")
        private Double weight;
        
        @NotNull
        private Boolean isBlocking;
        
        @NotNull
        private Boolean generateAlert;
        
        @Builder.Default
        private Boolean auditExecution = true;
        
        private Integer cooldownPeriodMinutes;
        
        @Min(1)
        private Integer maxExecutionsPerHour;
        
        private String category;
        
        private String version;
        
        private Map<String, Object> customConfiguration;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionRequest {
        private UUID conditionId; // For updates
        
        @NotNull
        private String fieldName;
        
        @NotNull
        private FraudRule.RuleCondition.ConditionOperator operator;
        
        @NotNull
        private Object expectedValue;
        
        private Object comparisonValue; // For BETWEEN operations
        
        @Builder.Default
        private FraudRule.RuleCondition.LogicalOperator logicalOperator = FraudRule.RuleCondition.LogicalOperator.AND;
        
        @Min(1)
        private Integer conditionOrder;
        
        @Builder.Default
        private Boolean isRequired = true;
        
        private String conditionExpression; // For complex conditions
        
        private String conditionDescription;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionRequest {
        private UUID actionId; // For updates
        
        @NotNull
        private FraudRule.RuleAction.ActionType actionType;
        
        @NotNull
        private String actionDescription;
        
        private Map<String, Object> actionParameters;
        
        @Min(1)
        private Integer executionOrder;
        
        @Builder.Default
        private Boolean isConditional = false;
        
        private String conditionalExpression;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionParameters {
        @Builder.Default
        private List<String> applicableEntityTypes = List.of("TRANSACTION");
        
        @Builder.Default
        private List<String> applicableChannels = List.of();
        
        @Builder.Default
        private List<String> applicableTransactionTypes = List.of();
        
        @Builder.Default
        private List<String> applicableGeographies = List.of();
        
        @Builder.Default
        private List<String> applicableCurrencies = List.of();
        
        @DecimalMin("0.0")
        private BigDecimal minTransactionAmount;
        
        private BigDecimal maxTransactionAmount;
        
        private String timeRestrictions;
        
        private Map<String, Object> contextFilters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestingConfiguration {
        @Builder.Default
        private Boolean enableTesting = false;
        
        @DecimalMin("0.0") @DecimalMax("100.0")
        private Double testingPercentage;
        
        private Integer testDurationDays;
        
        private LocalDateTime testStartDate;
        
        private LocalDateTime testEndDate;
        
        private String testDescription;
        
        @Builder.Default
        private List<String> testCriteria = List.of();
        
        private Map<String, Object> testParameters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        @Builder.Default
        private Boolean requiresApproval = true;
        
        private String approverRole;
        
        private String approverUserId;
        
        private String approvalReason;
        
        private String riskAssessment;
        
        private String businessImpact;
        
        private Integer urgencyLevel; // 1-5
        
        private LocalDateTime requestedApprovalDate;
        
        private Map<String, Object> approvalMetadata;
    }
    
    // Business logic methods
    public boolean isNewRule() {
        return requestType == RequestType.CREATE && ruleId == null;
    }
    
    public boolean isRuleUpdate() {
        return requestType == RequestType.UPDATE && ruleId != null;
    }
    
    public boolean isHighImpactRule() {
        return specification != null && 
               (specification.getIsBlocking() ||
                severity == FraudRule.RuleSeverity.HIGH ||
                severity == FraudRule.RuleSeverity.CRITICAL);
    }
    
    public boolean requiresApproval() {
        return approvalRequest != null && 
               approvalRequest.getRequiresApproval() != null && 
               approvalRequest.getRequiresApproval();
    }
    
    public boolean isTestingEnabled() {
        return testingConfiguration != null && 
               testingConfiguration.getEnableTesting() != null && 
               testingConfiguration.getEnableTesting();
    }
    
    public boolean hasComplexConditions() {
        return conditions != null && 
               conditions.stream()
                   .anyMatch(condition -> condition.getConditionExpression() != null ||
                           condition.getOperator() == FraudRule.RuleCondition.ConditionOperator.MATCHES_PATTERN);
    }
    
    public boolean declinesTransactions() {
        return actions != null && 
               actions.stream()
                   .anyMatch(action -> action.getActionType() == FraudRule.RuleAction.ActionType.DECLINE_TRANSACTION);
    }
    
    public boolean generatesAlerts() {
        return (specification != null && 
                specification.getGenerateAlert() != null && 
                specification.getGenerateAlert()) ||
               (actions != null && 
                actions.stream()
                    .anyMatch(action -> action.getActionType() == FraudRule.RuleAction.ActionType.GENERATE_ALERT));
    }
    
    public Integer getEffectivePriority() {
        if (specification != null && specification.getPriority() != null) {
            return specification.getPriority();
        }
        
        // Default priorities based on severity
        return switch (severity) {
            case CRITICAL -> 90;
            case HIGH -> 70;
            case MEDIUM -> 50;
            case LOW -> 30;
            case INFO -> 10;
        };
    }
    
    public Double getEffectiveWeight() {
        if (specification != null && specification.getWeight() != null) {
            return specification.getWeight();
        }
        
        // Default weights based on severity
        return switch (severity) {
            case CRITICAL -> 0.9;
            case HIGH -> 0.7;
            case MEDIUM -> 0.5;
            case LOW -> 0.3;
            case INFO -> 0.1;
        };
    }
    
    public boolean isUrgent() {
        return approvalRequest != null && 
               approvalRequest.getUrgencyLevel() != null && 
               approvalRequest.getUrgencyLevel() >= 4;
    }
    
    public boolean hasGeographicRestrictions() {
        return executionParameters != null && 
               executionParameters.getApplicableGeographies() != null && 
               !executionParameters.getApplicableGeographies().isEmpty();
    }
    
    public boolean hasAmountRestrictions() {
        return executionParameters != null && 
               (executionParameters.getMinTransactionAmount() != null || 
                executionParameters.getMaxTransactionAmount() != null);
    }
}