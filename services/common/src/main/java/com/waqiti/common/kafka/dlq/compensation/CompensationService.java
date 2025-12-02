package com.waqiti.common.kafka.dlq.compensation;

import java.util.UUID;

/**
 * Base interface for compensation services.
 *
 * Compensation services execute reversing transactions when a financial
 * operation fails and needs to be undone. This is critical for maintaining
 * financial integrity in distributed systems.
 *
 * FINANCIAL COMPLIANCE:
 * - All compensations must be audited
 * - Compensations must be idempotent
 * - Failed compensations must escalate to manual review
 * - Compensation amounts must match original transaction amounts
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
public interface CompensationService {

    /**
     * Executes a compensation for a failed transaction.
     *
     * This method must be idempotent - calling it multiple times with the
     * same transaction ID should not create duplicate compensations.
     *
     * @param transactionId The ID of the failed transaction
     * @param reason Reason for compensation
     * @return Compensation result
     */
    CompensationResult compensate(UUID transactionId, String reason);

    /**
     * Gets the service name for logging and metrics.
     */
    String getServiceName();

    /**
     * Result of compensation operation.
     */
    record CompensationResult(
        boolean success,
        String message,
        String compensationId,
        boolean requiresManualReview
    ) {
        public static CompensationResult success(String compensationId, String message) {
            return new CompensationResult(true, message, compensationId, false);
        }

        public static CompensationResult failed(String message) {
            return new CompensationResult(false, message, null, true);
        }

        public static CompensationResult manualReview(String message) {
            return new CompensationResult(false, message, null, true);
        }
    }
}
