package com.waqiti.common.kafka.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of DLQ recovery attempt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQRecoveryResult {
    
    private boolean success;
    private RecoveryStatus status;
    private String messageKey;
    private String originalTopic;
    private int retryAttempt;
    private String message;
    private Exception exception;

    public static DLQRecoveryResult success(DLQRecord record) {
        return DLQRecoveryResult.builder()
            .success(true)
            .status(RecoveryStatus.RECOVERED)
            .messageKey(record.getMessageKey())
            .originalTopic(record.getOriginalTopic())
            .retryAttempt(record.getRetryAttempt())
            .message("Message successfully recovered")
            .build();
    }

    public static DLQRecoveryResult retryScheduled(DLQRecord record) {
        return DLQRecoveryResult.builder()
            .success(false)
            .status(RecoveryStatus.PENDING_RETRY)
            .messageKey(record.getMessageKey())
            .originalTopic(record.getOriginalTopic())
            .retryAttempt(record.getRetryAttempt())
            .message("Retry scheduled with backoff")
            .build();
    }

    public static DLQRecoveryResult manualInterventionRequired(DLQRecord record) {
        return DLQRecoveryResult.builder()
            .success(false)
            .status(RecoveryStatus.MANUAL_INTERVENTION_REQUIRED)
            .messageKey(record.getMessageKey())
            .originalTopic(record.getOriginalTopic())
            .retryAttempt(record.getRetryAttempt())
            .message("Max retries exceeded - manual intervention required")
            .build();
    }

    public static DLQRecoveryResult failed(DLQRecord record, Exception ex) {
        return DLQRecoveryResult.builder()
            .success(false)
            .status(RecoveryStatus.PENDING_RETRY)
            .messageKey(record.getMessageKey())
            .originalTopic(record.getOriginalTopic())
            .retryAttempt(record.getRetryAttempt())
            .message("Recovery failed: " + ex.getMessage())
            .exception(ex)
            .build();
    }

    public static DLQRecoveryResult interrupted(DLQRecord record) {
        return DLQRecoveryResult.builder()
            .success(false)
            .status(RecoveryStatus.PENDING_RETRY)
            .messageKey(record.getMessageKey())
            .originalTopic(record.getOriginalTopic())
            .retryAttempt(record.getRetryAttempt())
            .message("Recovery interrupted")
            .build();
    }
}
