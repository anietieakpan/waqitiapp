package com.waqiti.common.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents an action to be taken when a fraud detection rule is triggered.
 * Actions can include blocking transactions, sending alerts, or updating risk scores.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleAction {
    
    /**
     * Unique action identifier
     */
    private String actionId;
    
    /**
     * Action name for display
     */
    private String name;
    
    /**
     * Type of action to execute
     */
    private ActionType actionType;
    
    /**
     * Priority of this action (higher number = higher priority)
     */
    @Builder.Default
    private int priority = 1;
    
    /**
     * Whether this action should execute immediately or be queued
     */
    @Builder.Default
    private boolean immediate = true;
    
    /**
     * Action configuration parameters
     */
    private Map<String, Object> parameters;
    
    /**
     * Conditions that must be met for action to execute
     */
    private Map<String, Object> executionConditions;
    
    /**
     * Maximum number of times this action can execute for same transaction
     */
    @Builder.Default
    private int maxExecutions = 1;
    
    /**
     * Cooldown period in minutes before action can execute again
     */
    @Builder.Default
    private int cooldownMinutes = 0;
    
    /**
     * Timeout for action execution in milliseconds
     */
    @Builder.Default
    private long timeoutMs = 30000;
    
    /**
     * Retry configuration for failed actions
     */
    private RetryConfig retryConfig;
    
    /**
     * Notification configuration
     */
    private NotificationConfig notificationConfig;
    
    /**
     * Whether action execution should be logged
     */
    @Builder.Default
    private boolean logExecution = true;
    
    /**
     * Whether action should be executed asynchronously
     */
    @Builder.Default
    private boolean async = false;
    
    /**
     * Action execution statistics
     */
    private ActionStatistics statistics;
    
    /**
     * Execute this action with the provided context
     */
    public ActionExecutionResult execute(ActionExecutionContext context) {
        if (!canExecute(context)) {
            return ActionExecutionResult.skipped(this.actionId, "Execution conditions not met");
        }
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            ActionExecutionResult result = performAction(context);
            
            // Update statistics
            updateStatistics(result, startTime);
            
            return result;
            
        } catch (Exception e) {
            ActionExecutionResult errorResult = ActionExecutionResult.error(this.actionId, e.getMessage());
            updateStatistics(errorResult, startTime);
            return errorResult;
        }
    }
    
    /**
     * Check if action can be executed given current context
     */
    private boolean canExecute(ActionExecutionContext context) {
        // Check execution conditions
        if (executionConditions != null && !executionConditions.isEmpty()) {
            for (Map.Entry<String, Object> condition : executionConditions.entrySet()) {
                Object contextValue = context.getParameter(condition.getKey());
                if (!condition.getValue().equals(contextValue)) {
                    return false;
                }
            }
        }
        
        // Check cooldown period
        if (statistics != null && cooldownMinutes > 0) {
            LocalDateTime lastExecution = statistics.getLastExecution();
            if (lastExecution != null) {
                LocalDateTime cooldownEnd = lastExecution.plusMinutes(cooldownMinutes);
                if (LocalDateTime.now().isBefore(cooldownEnd)) {
                    return false;
                }
            }
        }
        
        // Check max executions
        if (statistics != null && maxExecutions > 0) {
            if (statistics.getExecutionCount() >= maxExecutions) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Perform the actual action based on action type
     */
    private ActionExecutionResult performAction(ActionExecutionContext context) {
        switch (actionType) {
            case BLOCK_TRANSACTION:
                return blockTransaction(context);
            case DECLINE_TRANSACTION:
                return declineTransaction(context);
            case REQUIRE_ADDITIONAL_AUTHENTICATION:
                return requireAdditionalAuth(context);
            case SEND_ALERT:
                return sendAlert(context);
            case SEND_EMAIL:
                return sendEmail(context);
            case SEND_SMS:
                return sendSMS(context);
            case UPDATE_RISK_SCORE:
                return updateRiskScore(context);
            case ADD_TO_WATCHLIST:
                return addToWatchlist(context);
            case FREEZE_ACCOUNT:
                return freezeAccount(context);
            case LOG_EVENT:
                return logEvent(context);
            case CALL_WEBHOOK:
                return callWebhook(context);
            case EXECUTE_CUSTOM_LOGIC:
                return executeCustomLogic(context);
            case SEND_TO_QUEUE:
                return sendToQueue(context);
            case UPDATE_USER_PROFILE:
                return updateUserProfile(context);
            case TRIGGER_INVESTIGATION:
                return triggerInvestigation(context);
            default:
                return ActionExecutionResult.error(this.actionId, "Unknown action type: " + actionType);
        }
    }
    
    // Action implementations
    
    private ActionExecutionResult blockTransaction(ActionExecutionContext context) {
        String transactionId = context.getTransactionId();
        String reason = getParameterAsString("reason", "Fraud risk detected");
        
        // Implementation would call transaction service to block
        return ActionExecutionResult.success(this.actionId, 
            "Transaction " + transactionId + " blocked: " + reason);
    }
    
    private ActionExecutionResult declineTransaction(ActionExecutionContext context) {
        String transactionId = context.getTransactionId();
        String declineCode = getParameterAsString("declineCode", "FRAUD_SUSPECTED");
        
        // Implementation would call transaction service to decline
        return ActionExecutionResult.success(this.actionId, 
            "Transaction " + transactionId + " declined with code: " + declineCode);
    }
    
    private ActionExecutionResult requireAdditionalAuth(ActionExecutionContext context) {
        String authMethod = getParameterAsString("authMethod", "SMS_OTP");
        
        // Implementation would trigger additional authentication
        return ActionExecutionResult.success(this.actionId, 
            "Additional authentication required: " + authMethod);
    }
    
    private ActionExecutionResult sendAlert(ActionExecutionContext context) {
        String alertType = getParameterAsString("alertType", "FRAUD_ALERT");
        String severity = getParameterAsString("severity", "HIGH");
        
        // Implementation would send alert to monitoring system
        return ActionExecutionResult.success(this.actionId, 
            "Alert sent: " + alertType + " with severity: " + severity);
    }
    
    private ActionExecutionResult sendEmail(ActionExecutionContext context) {
        String recipient = getParameterAsString("recipient", null);
        String template = getParameterAsString("template", "fraud_alert");
        
        if (recipient == null) {
            return ActionExecutionResult.error(this.actionId, "No email recipient specified");
        }
        
        // Implementation would call email service
        return ActionExecutionResult.success(this.actionId, 
            "Email sent to " + recipient + " using template: " + template);
    }
    
    private ActionExecutionResult sendSMS(ActionExecutionContext context) {
        String phoneNumber = getParameterAsString("phoneNumber", null);
        String message = getParameterAsString("message", "Security alert for your account");
        
        if (phoneNumber == null) {
            return ActionExecutionResult.error(this.actionId, "No phone number specified");
        }
        
        // Implementation would call SMS service
        return ActionExecutionResult.success(this.actionId, 
            "SMS sent to " + phoneNumber);
    }
    
    private ActionExecutionResult updateRiskScore(ActionExecutionContext context) {
        String userId = context.getUserId();
        double scoreAdjustment = getParameterAsDouble("scoreAdjustment", 10.0);
        
        // Implementation would update user risk score
        return ActionExecutionResult.success(this.actionId, 
            "Risk score for user " + userId + " increased by " + scoreAdjustment);
    }
    
    private ActionExecutionResult addToWatchlist(ActionExecutionContext context) {
        String userId = context.getUserId();
        String watchlistType = getParameterAsString("watchlistType", "FRAUD_RISK");
        
        // Implementation would add user to watchlist
        return ActionExecutionResult.success(this.actionId, 
            "User " + userId + " added to " + watchlistType + " watchlist");
    }
    
    private ActionExecutionResult freezeAccount(ActionExecutionContext context) {
        String userId = context.getUserId();
        String reason = getParameterAsString("reason", "Suspected fraudulent activity");
        
        // Implementation would freeze user account
        return ActionExecutionResult.success(this.actionId, 
            "Account " + userId + " frozen: " + reason);
    }
    
    private ActionExecutionResult logEvent(ActionExecutionContext context) {
        String eventType = getParameterAsString("eventType", "FRAUD_RULE_TRIGGERED");
        String logLevel = getParameterAsString("logLevel", "WARN");
        
        // Implementation would log the event
        return ActionExecutionResult.success(this.actionId, 
            "Event logged: " + eventType + " at level: " + logLevel);
    }
    
    private ActionExecutionResult callWebhook(ActionExecutionContext context) {
        String webhookUrl = getParameterAsString("webhookUrl", null);
        String method = getParameterAsString("method", "POST");
        
        if (webhookUrl == null) {
            return ActionExecutionResult.error(this.actionId, "No webhook URL specified");
        }
        
        // Implementation would make HTTP call to webhook
        return ActionExecutionResult.success(this.actionId, 
            "Webhook called: " + method + " " + webhookUrl);
    }
    
    private ActionExecutionResult executeCustomLogic(ActionExecutionContext context) {
        String logicName = getParameterAsString("logicName", null);
        
        if (logicName == null) {
            return ActionExecutionResult.error(this.actionId, "No custom logic specified");
        }
        
        // Implementation would execute custom business logic
        return ActionExecutionResult.success(this.actionId, 
            "Custom logic executed: " + logicName);
    }
    
    private ActionExecutionResult sendToQueue(ActionExecutionContext context) {
        String queueName = getParameterAsString("queueName", "fraud-investigation");
        
        // Implementation would send message to queue
        return ActionExecutionResult.success(this.actionId, 
            "Message sent to queue: " + queueName);
    }
    
    private ActionExecutionResult updateUserProfile(ActionExecutionContext context) {
        String userId = context.getUserId();
        String updateType = getParameterAsString("updateType", "FRAUD_FLAG");
        
        // Implementation would update user profile
        return ActionExecutionResult.success(this.actionId, 
            "User profile " + userId + " updated: " + updateType);
    }
    
    private ActionExecutionResult triggerInvestigation(ActionExecutionContext context) {
        String investigationType = getParameterAsString("investigationType", "FRAUD_REVIEW");
        String priority = getParameterAsString("priority", "MEDIUM");
        
        // Implementation would create investigation case
        return ActionExecutionResult.success(this.actionId, 
            "Investigation triggered: " + investigationType + " priority: " + priority);
    }
    
    // Helper methods
    
    private String getParameterAsString(String key, String defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private double getParameterAsDouble(String key, double defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private void updateStatistics(ActionExecutionResult result, LocalDateTime startTime) {
        if (statistics == null) {
            statistics = new ActionStatistics();
        }
        
        statistics.recordExecution(result, startTime);
    }
    
    /**
     * Validate action configuration
     */
    public boolean isValid() {
        if (actionId == null || actionId.trim().isEmpty()) {
            return false;
        }
        
        if (actionType == null) {
            return false;
        }
        
        if (timeoutMs <= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get action summary for display
     */
    public String getSummary() {
        return String.format("%s (%s) - Priority: %d - %s", 
            name != null ? name : actionId, 
            actionType, 
            priority, 
            immediate ? "Immediate" : "Queued");
    }
    
    // Supporting enums and classes
    
    public enum ActionType {
        BLOCK_TRANSACTION,
        DECLINE_TRANSACTION,
        REQUIRE_ADDITIONAL_AUTHENTICATION,
        SEND_ALERT,
        SEND_EMAIL,
        SEND_SMS,
        UPDATE_RISK_SCORE,
        ADD_TO_WATCHLIST,
        FREEZE_ACCOUNT,
        LOG_EVENT,
        CALL_WEBHOOK,
        EXECUTE_CUSTOM_LOGIC,
        SEND_TO_QUEUE,
        UPDATE_USER_PROFILE,
        TRIGGER_INVESTIGATION
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfig {
        private int maxRetries;
        private long retryDelayMs;
        private double backoffMultiplier;
        private long maxRetryDelayMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationConfig {
        private boolean notifyOnSuccess;
        private boolean notifyOnFailure;
        private String notificationChannel;
        private String notificationTemplate;
    }
    
    public static class ActionStatistics {
        private long executionCount = 0;
        private long successCount = 0;
        private long errorCount = 0;
        private double averageExecutionTimeMs = 0.0;
        private LocalDateTime lastExecution;
        private LocalDateTime lastSuccess;
        private LocalDateTime lastError;
        
        public void recordExecution(ActionExecutionResult result, LocalDateTime startTime) {
            executionCount++;
            lastExecution = LocalDateTime.now();
            
            long executionTime = java.time.Duration.between(startTime, lastExecution).toMillis();
            averageExecutionTimeMs = ((averageExecutionTimeMs * (executionCount - 1)) + executionTime) / executionCount;
            
            if (result.isSuccess()) {
                successCount++;
                lastSuccess = lastExecution;
            } else {
                errorCount++;
                lastError = lastExecution;
            }
        }
        
        public double getSuccessRate() {
            return executionCount > 0 ? (double) successCount / executionCount : 0.0;
        }
        
        public double getErrorRate() {
            return executionCount > 0 ? (double) errorCount / executionCount : 0.0;
        }
        
        // Getters
        public long getExecutionCount() { return executionCount; }
        public long getSuccessCount() { return successCount; }
        public long getErrorCount() { return errorCount; }
        public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public LocalDateTime getLastExecution() { return lastExecution; }
        public LocalDateTime getLastSuccess() { return lastSuccess; }
        public LocalDateTime getLastError() { return lastError; }
    }
}