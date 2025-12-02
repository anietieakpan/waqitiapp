package com.waqiti.common.kafka.dlq.compensation;

import java.util.UUID;

/**
 * Transaction compensation service interface.
 *
 * Handles compensation for failed transaction-service operations including:
 * - Transaction state rollbacks
 * - Multi-step transaction reversals
 * - Saga compensation
 *
 * SAGA PATTERN:
 * - Implements compensating transactions for distributed sagas
 * - Must maintain saga state consistency
 * - Compensation must be idempotent
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
public interface TransactionCompensationService extends CompensationService {

    /**
     * Rolls back a transaction to previous state.
     *
     * @param transactionId Transaction ID to rollback
     * @param reason Rollback reason
     * @return Compensation result
     */
    CompensationResult rollbackTransaction(UUID transactionId, String reason);

    /**
     * Compensates a saga step.
     *
     * @param sagaId Saga ID
     * @param stepId Step ID to compensate
     * @param reason Compensation reason
     * @return Compensation result
     */
    CompensationResult compensateSagaStep(UUID sagaId, String stepId, String reason);
}
