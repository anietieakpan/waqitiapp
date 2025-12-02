package com.waqiti.common.kafka.dlq;

import lombok.Builder;
import lombok.Data;

/**
 * Result of a DLQ recovery attempt
 * Indicates whether recovery succeeded, failed (retryable), or permanently failed
 */
@Data
@Builder
public class RecoveryResult {
    private boolean success;
    private boolean retryable;
    private String failureReason;
    private String recoveryMethod;

    /**
     * Create a successful recovery result
     */
    public static RecoveryResult success(String recoveryMethod) {
        return RecoveryResult.builder()
                .success(true)
                .retryable(false)
                .recoveryMethod(recoveryMethod)
                .build();
    }

    /**
     * Create a retryable failure result
     */
    public static RecoveryResult retryableFailure(String reason) {
        return RecoveryResult.builder()
                .success(false)
                .retryable(true)
                .failureReason(reason)
                .build();
    }

    /**
     * Create a permanent failure result
     */
    public static RecoveryResult permanentFailure(String reason) {
        return RecoveryResult.builder()
                .success(false)
                .retryable(false)
                .failureReason(reason)
                .build();
    }
}
