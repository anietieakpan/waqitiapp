package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Result of scheduling a notification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledNotificationResult {
    
    /**
     * Schedule ID
     */
    private String scheduleId;
    
    /**
     * Status of the schedule
     */
    private ScheduleStatus status;
    
    /**
     * Next execution time
     */
    private Instant nextExecutionTime;
    
    /**
     * Previous execution time
     */
    private Instant previousExecutionTime;
    
    /**
     * Number of executions so far
     */
    private int executionCount;
    
    /**
     * Remaining executions
     */
    private Integer remainingExecutions;
    
    /**
     * Schedule created time
     */
    private Instant createdAt;
    
    /**
     * Schedule updated time
     */
    private Instant updatedAt;
    
    /**
     * Execution history
     */
    private List<ExecutionRecord> executionHistory;
    
    /**
     * Error details if failed
     */
    private String errorDetails;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionRecord {
        private Instant executionTime;
        private ExecutionStatus status;
        private String notificationId;
        private String errorMessage;
        private long executionDurationMs;
    }
    
    public enum ScheduleStatus {
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED,
        FAILED
    }
    
    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        PARTIAL
    }
}