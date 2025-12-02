package com.waqiti.user.kafka.dlq;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of DLQ recovery attempt
 * Provides full audit trail of recovery actions taken
 */
@Data
@Builder
public class DlqRecoveryResult {

    /**
     * Whether recovery was successful
     */
    private boolean success;

    /**
     * Status of the recovery attempt
     */
    private RecoveryStatus status;

    /**
     * Actions taken during recovery
     */
    @Builder.Default
    private List<RecoveryAction> actionsTaken = new ArrayList<>();

    /**
     * Error message if recovery failed
     */
    private String errorMessage;

    /**
     * Exception if recovery failed
     */
    private Exception exception;

    /**
     * Whether message should be retried
     */
    private boolean shouldRetry;

    /**
     * Delay before retry (milliseconds)
     */
    private long retryDelayMs;

    /**
     * Whether manual intervention is required
     */
    private boolean requiresManualIntervention;

    /**
     * Ticket/incident number created for tracking
     */
    private String ticketNumber;

    /**
     * Timestamp of recovery attempt
     */
    @Builder.Default
    private LocalDateTime recoveryTimestamp = LocalDateTime.now();

    /**
     * Duration of recovery attempt in milliseconds
     */
    private long durationMs;

    /**
     * Additional notes about the recovery
     */
    private String notes;

    public enum RecoveryStatus {
        /** Recovery succeeded, message processed */
        RECOVERED,

        /** Recovery in progress, requires more attempts */
        IN_PROGRESS,

        /** Recovery failed permanently, manual intervention needed */
        FAILED_PERMANENT,

        /** Recovery failed, but can be retried */
        FAILED_RETRY,

        /** Compensating action taken to fix data */
        COMPENSATED,

        /** Escalated to human operators */
        ESCALATED,

        /** Deferred for batch processing */
        DEFERRED
    }

    @Data
    @Builder
    public static class RecoveryAction {
        private String actionType;
        private String description;
        private LocalDateTime timestamp;
        private boolean successful;
        private String result;
    }

    /**
     * Add a recovery action to the audit trail
     */
    public void addAction(String actionType, String description, boolean successful, String result) {
        actionsTaken.add(RecoveryAction.builder()
                .actionType(actionType)
                .description(description)
                .timestamp(LocalDateTime.now())
                .successful(successful)
                .result(result)
                .build());
    }
}
