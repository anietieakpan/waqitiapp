package com.waqiti.common.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of executing a fraud rule action.
 * Contains execution status, results, and performance metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionExecutionResult {
    
    /**
     * Action that was executed
     */
    private String actionId;
    
    /**
     * Execution status
     */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.SUCCESS;
    
    /**
     * Success flag for quick status check
     */
    private boolean success;
    
    /**
     * Result message or description
     */
    private String message;
    
    /**
     * Error message if execution failed
     */
    private String errorMessage;
    
    /**
     * Exception details if applicable
     */
    private String exceptionDetails;
    
    /**
     * Execution start time
     */
    private LocalDateTime startTime;
    
    /**
     * Execution end time
     */
    private LocalDateTime endTime;
    
    /**
     * Execution duration in milliseconds
     */
    private long durationMs;
    
    /**
     * Return value from action execution
     */
    private Object returnValue;
    
    /**
     * Additional result data
     */
    private Map<String, Object> resultData;
    
    /**
     * Execution context that was used
     */
    private ActionExecutionContext executionContext;
    
    /**
     * Whether retry should be attempted
     */
    private boolean retryable;
    
    /**
     * Retry attempt number (0 for first attempt)
     */
    private int retryAttempt;
    
    /**
     * External system response if action called external service
     */
    private String externalResponse;
    
    /**
     * HTTP status code if action made HTTP call
     */
    private Integer httpStatusCode;
    
    /**
     * Resource usage metrics
     */
    private ResourceUsage resourceUsage;
    
    /**
     * Impact assessment of the action
     */
    private ActionImpact impact;
    
    /**
     * Whether action had side effects
     */
    private boolean hasSideEffects;
    
    /**
     * Rollback information if action needs to be undone
     */
    private RollbackInfo rollbackInfo;
    
    /**
     * Create successful execution result
     */
    public static ActionExecutionResult success(String actionId, String message) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.SUCCESS)
                .success(true)
                .message(message)
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create successful execution result with return value
     */
    public static ActionExecutionResult success(String actionId, String message, Object returnValue) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.SUCCESS)
                .success(true)
                .message(message)
                .returnValue(returnValue)
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error execution result
     */
    public static ActionExecutionResult error(String actionId, String errorMessage) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.ERROR)
                .success(false)
                .errorMessage(errorMessage)
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error execution result with exception
     */
    public static ActionExecutionResult error(String actionId, String errorMessage, Exception exception) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.ERROR)
                .success(false)
                .errorMessage(errorMessage)
                .exceptionDetails(exception.getMessage())
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create skipped execution result
     */
    public static ActionExecutionResult skipped(String actionId, String reason) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.SKIPPED)
                .success(false)
                .message(reason)
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create timeout execution result
     */
    public static ActionExecutionResult timeout(String actionId, long timeoutMs) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.TIMEOUT)
                .success(false)
                .errorMessage("Action execution timed out after " + timeoutMs + "ms")
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create partial success execution result
     */
    public static ActionExecutionResult partialSuccess(String actionId, String message, String warning) {
        return ActionExecutionResult.builder()
                .actionId(actionId)
                .status(ExecutionStatus.PARTIAL_SUCCESS)
                .success(true)
                .message(message)
                .errorMessage(warning)
                .endTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * Set execution duration from start time
     */
    public void setDurationFromStart() {
        if (startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
    
    /**
     * Check if execution was successful
     */
    public boolean isSuccess() {
        return success && (status == ExecutionStatus.SUCCESS || status == ExecutionStatus.PARTIAL_SUCCESS);
    }
    
    /**
     * Check if execution failed
     */
    public boolean isFailed() {
        return !success || status == ExecutionStatus.ERROR || status == ExecutionStatus.TIMEOUT;
    }
    
    /**
     * Check if execution was skipped
     */
    public boolean isSkipped() {
        return status == ExecutionStatus.SKIPPED;
    }
    
    /**
     * Check if action should be retried
     */
    public boolean shouldRetry() {
        return retryable && isFailed() && status != ExecutionStatus.TIMEOUT;
    }
    
    /**
     * Check if execution completed within expected time
     */
    public boolean isWithinExpectedTime(long expectedMaxMs) {
        return durationMs <= expectedMaxMs;
    }
    
    /**
     * Get result data value
     */
    public Object getResultData(String key) {
        if (resultData == null) {
            return null;
        }
        return resultData.get(key);
    }
    
    /**
     * Add result data
     */
    public void addResultData(String key, Object value) {
        if (resultData == null) {
            resultData = new java.util.HashMap<>();
        }
        resultData.put(key, value);
    }
    
    /**
     * Get execution summary for logging
     */
    public String getExecutionSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Action: %s, Status: %s, Duration: %dms", 
            actionId, status, durationMs));
        
        if (isSuccess()) {
            summary.append(", Result: ").append(message != null ? message : "Success");
        } else {
            summary.append(", Error: ").append(errorMessage != null ? errorMessage : "Unknown error");
        }
        
        if (httpStatusCode != null) {
            summary.append(", HTTP Status: ").append(httpStatusCode);
        }
        
        if (retryAttempt > 0) {
            summary.append(", Retry Attempt: ").append(retryAttempt);
        }
        
        return summary.toString();
    }
    
    /**
     * Get detailed execution report
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== ACTION EXECUTION RESULT ===\n");
        report.append(String.format("Action ID: %s\n", actionId));
        report.append(String.format("Status: %s\n", status));
        report.append(String.format("Success: %s\n", success));
        report.append(String.format("Start Time: %s\n", startTime));
        report.append(String.format("End Time: %s\n", endTime));
        report.append(String.format("Duration: %d ms\n", durationMs));
        
        if (message != null) {
            report.append(String.format("Message: %s\n", message));
        }
        
        if (errorMessage != null) {
            report.append(String.format("Error: %s\n", errorMessage));
        }
        
        if (exceptionDetails != null) {
            report.append(String.format("Exception: %s\n", exceptionDetails));
        }
        
        if (httpStatusCode != null) {
            report.append(String.format("HTTP Status: %d\n", httpStatusCode));
        }
        
        if (externalResponse != null) {
            report.append(String.format("External Response: %s\n", externalResponse));
        }
        
        if (retryAttempt > 0) {
            report.append(String.format("Retry Attempt: %d\n", retryAttempt));
        }
        
        if (returnValue != null) {
            report.append(String.format("Return Value: %s\n", returnValue));
        }
        
        if (resultData != null && !resultData.isEmpty()) {
            report.append("Result Data:\n");
            resultData.forEach((key, value) -> 
                report.append(String.format("  %s: %s\n", key, value)));
        }
        
        if (resourceUsage != null) {
            report.append(String.format("Resource Usage: %s\n", resourceUsage));
        }
        
        if (impact != null) {
            report.append(String.format("Impact: %s\n", impact));
        }
        
        return report.toString();
    }
    
    /**
     * Create a copy for retry attempt
     */
    public ActionExecutionResult forRetryAttempt(int attemptNumber) {
        return ActionExecutionResult.builder()
                .actionId(this.actionId)
                .executionContext(this.executionContext)
                .retryAttempt(attemptNumber)
                .retryable(this.retryable)
                .build();
    }
    
    // Supporting enums and classes
    
    public enum ExecutionStatus {
        SUCCESS,         // Action executed successfully
        PARTIAL_SUCCESS, // Action partially successful with warnings
        ERROR,           // Action failed with error
        TIMEOUT,         // Action execution timed out
        SKIPPED,         // Action was skipped
        CANCELLED,       // Action execution was cancelled
        RETRY_SCHEDULED  // Action scheduled for retry
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUsage {
        private long cpuTimeMs;
        private long memoryUsageBytes;
        private int networkCalls;
        private long networkTimeMs;
        private int databaseCalls;
        private long databaseTimeMs;
        
        @Override
        public String toString() {
            return String.format("CPU: %dms, Memory: %d bytes, Network: %d calls/%dms, DB: %d calls/%dms",
                cpuTimeMs, memoryUsageBytes, networkCalls, networkTimeMs, databaseCalls, databaseTimeMs);
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionImpact {
        private String impactType;
        private String impactLevel;
        private String description;
        private Map<String, Object> impactMetrics;
        
        @Override
        public String toString() {
            return String.format("%s (%s): %s", impactType, impactLevel, description);
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackInfo {
        private boolean rollbackSupported;
        private String rollbackMethod;
        private Map<String, Object> rollbackData;
        private LocalDateTime rollbackDeadline;
        
        public boolean canRollback() {
            return rollbackSupported && 
                   (rollbackDeadline == null || LocalDateTime.now().isBefore(rollbackDeadline));
        }
    }
}