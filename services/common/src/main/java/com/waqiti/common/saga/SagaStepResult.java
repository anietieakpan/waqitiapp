package com.waqiti.common.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Saga Step Result
 *
 * Result published by participant services back to saga orchestrator
 * after executing a saga step.
 *
 * @author Waqiti Engineering Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepResult {

    /**
     * Saga instance identifier
     */
    private String sagaId;

    /**
     * Name of the executed step
     */
    private String stepName;

    /**
     * Execution status (SUCCESS, FAILED, RETRY)
     */
    private String status;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Error message (if failed)
     */
    private String errorMessage;

    /**
     * Error code (if failed)
     */
    private String errorCode;

    /**
     * Result data (updated wallet balances, transaction IDs, etc.)
     */
    private Map<String, Object> data;

    /**
     * Timestamp when step completed
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Execution duration in milliseconds
     */
    private Long durationMs;

    /**
     * Service that executed the step
     */
    private String executedBy;

    /**
     * Whether retry is recommended (for transient failures)
     */
    @Builder.Default
    private boolean retryable = false;
}
