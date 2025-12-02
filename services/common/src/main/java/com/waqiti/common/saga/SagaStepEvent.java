package com.waqiti.common.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Saga Step Event
 *
 * Event published by saga orchestrator to participant services
 * instructing them to execute a specific step in the saga.
 *
 * @author Waqiti Engineering Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepEvent {

    /**
     * Unique identifier for the saga instance
     */
    private String sagaId;

    /**
     * Type of saga (P2P_TRANSFER, DEPOSIT, WITHDRAWAL, etc.)
     */
    private String sagaType;

    /**
     * Name of the step to execute
     */
    private String stepName;

    /**
     * Service responsible for executing this step
     */
    private String serviceName;

    /**
     * Operation to perform (debit, credit, reserve, etc.)
     */
    private String operation;

    /**
     * Step-specific data (walletIds, amounts, etc.)
     */
    private Map<String, Object> data;

    /**
     * Whether this is a compensation step (rollback)
     */
    @Builder.Default
    private boolean compensation = false;

    /**
     * Attempt number (for retries)
     */
    @Builder.Default
    private int attemptNumber = 1;

    /**
     * Maximum number of retry attempts
     */
    @Builder.Default
    private int maxAttempts = 3;

    /**
     * Timestamp when step was initiated
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Correlation ID for distributed tracing
     */
    private String correlationId;

    /**
     * Parent transaction ID
     */
    private String transactionId;
}
