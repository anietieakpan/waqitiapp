package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive fraud mitigation action with execution details
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudMitigationAction {
    
    // Static action constants
    public static final FraudMitigationAction BLOCK_TRANSACTION = createAction(ActionType.BLOCK_TRANSACTION);
    public static final FraudMitigationAction NOTIFY_SECURITY_TEAM = createAction(ActionType.NOTIFY_SECURITY_TEAM);
    public static final FraudMitigationAction FREEZE_ACCOUNT = createAction(ActionType.FREEZE_ACCOUNT);
    public static final FraudMitigationAction REQUIRE_ADDITIONAL_VERIFICATION = createAction(ActionType.REQUIRE_ADDITIONAL_VERIFICATION);
    public static final FraudMitigationAction FLAG_FOR_REVIEW = createAction(ActionType.FLAG_FOR_REVIEW);
    public static final FraudMitigationAction NOTIFY_USER = createAction(ActionType.NOTIFY_USER);
    public static final FraudMitigationAction REQUIRE_2FA = createAction(ActionType.REQUIRE_2FA);
    public static final FraudMitigationAction LOG_SUSPICIOUS_ACTIVITY = createAction(ActionType.LOG_SECURITY_EVENT);
    public static final FraudMitigationAction MONITOR = createAction(ActionType.ENABLE_ENHANCED_MONITORING);
    public static final FraudMitigationAction ALLOW = createAction(ActionType.ALLOW_TRANSACTION);
    public static final FraudMitigationAction MANUAL_REVIEW = createAction(ActionType.REQUIRE_MANUAL_REVIEW);
    public static final FraudMitigationAction ALLOW_WITH_MONITORING = createAction(ActionType.ENABLE_ENHANCED_MONITORING);
    public static final FraudMitigationAction ALLOW_TRANSACTION = createAction(ActionType.ALLOW_TRANSACTION);
    
    private String actionId;
    private String transactionId;
    private String userId;
    private String accountId;
    
    // Action details
    private ActionType actionType;
    private ActionSeverity severity;
    private String description;
    private String reason;
    private String riskScore;
    private String confidence;
    
    // Execution details
    private ActionStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime executedAt;
    private LocalDateTime completedAt;
    private String executedBy;
    private String executionResult;
    private String executionError;
    
    // Duration and timing
    private Long durationMillis;
    private LocalDateTime expiresAt;
    private Boolean isReversible;
    private String reversalProcedure;
    
    // Impact assessment
    private ImpactLevel impactLevel;
    private String affectedServices;
    private String userNotificationRequired;
    private String complianceImplications;
    
    // Additional metadata
    private Map<String, String> actionParameters;
    private String correlationId;
    private String parentActionId;
    private Integer retryCount;
    private String lastRetryError;
    
    /**
     * Action types for fraud mitigation
     */
    public enum ActionType {
        BLOCK_TRANSACTION,
        FREEZE_ACCOUNT,
        REQUIRE_ADDITIONAL_AUTH,
        REQUIRE_ADDITIONAL_VERIFICATION,
        LIMIT_TRANSACTION_AMOUNT,
        RESTRICT_PAYMENT_METHODS,
        ENABLE_ENHANCED_MONITORING,
        QUARANTINE_FUNDS,
        SUSPEND_ACCOUNT,
        REQUIRE_MANUAL_REVIEW,
        SEND_SECURITY_ALERT,
        LOG_SECURITY_EVENT,
        ESCALATE_TO_ANALYST,
        CONTACT_CUSTOMER,
        REQUEST_DOCUMENTATION,
        VERIFY_IDENTITY,
        TEMPORARY_HOLD,
        PERMANENT_BAN,
        WHITELIST_REMOVE,
        BLACKLIST_ADD,
        RATE_LIMIT_APPLY,
        NOTIFY_SECURITY_TEAM,
        FLAG_FOR_REVIEW,
        NOTIFY_USER,
        REQUIRE_2FA,
        ALLOW_TRANSACTION
    }
    
    /**
     * Action severity levels
     */
    public enum ActionSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
        EMERGENCY
    }
    
    /**
     * Action execution status
     */
    public enum ActionStatus {
        PENDING,
        SCHEDULED,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED,
        REVERSED
    }
    
    /**
     * Impact level of the mitigation action
     */
    public enum ImpactLevel {
        MINIMAL,      // No user impact
        LOW,          // Minor inconvenience
        MEDIUM,       // Noticeable impact
        HIGH,         // Significant disruption
        SEVERE        // Major business impact
    }
    
    /**
     * Check if action is currently active
     */
    public boolean isActive() {
        return status == ActionStatus.EXECUTING || status == ActionStatus.SCHEDULED;
    }
    
    /**
     * Check if action can be reversed
     */
    public boolean canBeReversed() {
        return isReversible != null && isReversible && 
               (status == ActionStatus.COMPLETED || status == ActionStatus.EXECUTING);
    }
    
    /**
     * Check if action requires immediate execution
     */
    public boolean requiresImmediateExecution() {
        return severity == ActionSeverity.CRITICAL || severity == ActionSeverity.EMERGENCY;
    }
    
    /**
     * Calculate action effectiveness based on completion time and results
     */
    public double calculateEffectiveness() {
        if (status != ActionStatus.COMPLETED || executedAt == null || completedAt == null) {
            return 0.0;
        }
        
        double baseEffectiveness = 0.8; // Base effectiveness
        
        // Adjust for execution time
        long executionTime = java.time.Duration.between(executedAt, completedAt).toMillis();
        if (executionTime < 1000) baseEffectiveness += 0.1; // Fast execution
        else if (executionTime > 30000) baseEffectiveness -= 0.1; // Slow execution
        
        // Adjust for retry attempts
        if (retryCount != null && retryCount > 0) {
            baseEffectiveness -= (retryCount * 0.05);
        }
        
        // Adjust for error-free execution
        if (executionError == null) {
            baseEffectiveness += 0.1;
        }
        
        return Math.max(0.0, Math.min(1.0, baseEffectiveness));
    }
    
    /**
     * Get human-readable action description
     */
    public String getHumanReadableDescription() {
        StringBuilder desc = new StringBuilder();
        
        switch (actionType) {
            case BLOCK_TRANSACTION:
                desc.append("Block transaction due to fraud risk");
                break;
            case FREEZE_ACCOUNT:
                desc.append("Temporarily freeze account for security review");
                break;
            case REQUIRE_ADDITIONAL_AUTH:
                desc.append("Require additional authentication for verification");
                break;
            case LIMIT_TRANSACTION_AMOUNT:
                desc.append("Apply transaction amount limits");
                break;
            case ENABLE_ENHANCED_MONITORING:
                desc.append("Enable enhanced monitoring for suspicious activity");
                break;
            default:
                desc.append(actionType.toString().toLowerCase().replace("_", " "));
        }
        
        if (severity != null) {
            desc.append(" (").append(severity.toString().toLowerCase()).append(" priority)");
        }
        
        return desc.toString();
    }
    
    /**
     * Create a fraud mitigation action with the specified type
     */
    public static FraudMitigationAction createAction(ActionType actionType) {
        return FraudMitigationAction.builder()
            .actionId("ACTION_" + System.currentTimeMillis())
            .actionType(actionType)
            .severity(ActionSeverity.MEDIUM)
            .description(getDefaultDescription(actionType))
            .status(ActionStatus.PENDING)
            .scheduledAt(LocalDateTime.now())
            .isReversible(isActionReversible(actionType))
            .impactLevel(getDefaultImpactLevel(actionType))
            .build();
    }
    
    /**
     * Get default description for action type
     */
    private static String getDefaultDescription(ActionType actionType) {
        return switch (actionType) {
            case BLOCK_TRANSACTION -> "Block transaction due to fraud risk";
            case FREEZE_ACCOUNT -> "Freeze account for security review";
            case REQUIRE_MANUAL_REVIEW -> "Transaction requires manual review";
            case ALLOW_TRANSACTION -> "Allow transaction to proceed";
            case ENABLE_ENHANCED_MONITORING -> "Enable enhanced monitoring";
            case REQUIRE_2FA -> "Require two-factor authentication";
            case NOTIFY_SECURITY_TEAM -> "Notify security team of suspicious activity";
            default -> actionType.toString().toLowerCase().replace("_", " ");
        };
    }
    
    /**
     * Determine if action type is reversible
     */
    private static Boolean isActionReversible(ActionType actionType) {
        return switch (actionType) {
            case BLOCK_TRANSACTION, FREEZE_ACCOUNT, TEMPORARY_HOLD, 
                 ENABLE_ENHANCED_MONITORING, REQUIRE_2FA -> true;
            case PERMANENT_BAN, BLACKLIST_ADD -> false;
            default -> true;
        };
    }
    
    /**
     * Get default impact level for action type
     */
    private static ImpactLevel getDefaultImpactLevel(ActionType actionType) {
        return switch (actionType) {
            case ALLOW_TRANSACTION -> ImpactLevel.MINIMAL;
            case ENABLE_ENHANCED_MONITORING, REQUIRE_2FA -> ImpactLevel.LOW;
            case REQUIRE_MANUAL_REVIEW, TEMPORARY_HOLD -> ImpactLevel.MEDIUM;
            case BLOCK_TRANSACTION, FREEZE_ACCOUNT -> ImpactLevel.HIGH;
            case PERMANENT_BAN, SUSPEND_ACCOUNT -> ImpactLevel.SEVERE;
            default -> ImpactLevel.MEDIUM;
        };
    }
}