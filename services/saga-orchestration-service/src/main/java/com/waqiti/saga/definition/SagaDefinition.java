package com.waqiti.saga.definition;

import com.waqiti.common.saga.SagaStepEvent;

import java.util.List;
import java.util.Map;

/**
 * Saga Definition Interface
 *
 * Defines the contract for all saga type definitions.
 * Each saga type (P2P_TRANSFER, DEPOSIT, WITHDRAWAL) implements this interface.
 *
 * @author Waqiti Engineering Team
 * @since 1.0.0
 */
public interface SagaDefinition {

    /**
     * Get the saga type identifier
     * @return Saga type (P2P_TRANSFER, DEPOSIT, WITHDRAWAL, etc.)
     */
    String getSagaType();

    /**
     * Define the sequence of steps for this saga
     *
     * @param sagaId Unique identifier for this saga instance
     * @param sagaData Data required for saga execution
     * @return Ordered list of saga steps to execute
     */
    List<SagaStepEvent> defineSteps(String sagaId, Map<String, Object> sagaData);

    /**
     * Define compensation steps (rollback) when saga fails
     *
     * @param sagaId Unique identifier for this saga instance
     * @param sagaData Saga execution data
     * @param failedStepIndex Index of the step that failed (0-based)
     * @return Ordered list of compensation steps to execute
     */
    List<SagaStepEvent> defineCompensationSteps(String sagaId, Map<String, Object> sagaData, int failedStepIndex);

    /**
     * Get total timeout for entire saga (seconds)
     * @return Timeout in seconds
     */
    int getTimeoutSeconds();

    /**
     * Get timeout for individual step (seconds)
     * @return Timeout in seconds
     */
    int getStepTimeoutSeconds();

    /**
     * Get maximum retry attempts per step
     * @return Max retry attempts
     */
    int getMaxRetryAttempts();
}
