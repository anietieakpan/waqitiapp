package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;

/**
 * Interface for DLQ recovery strategy handlers.
 *
 * Each strategy implements a different approach to recovering failed messages.
 * Strategies include:
 * - Automatic Retry: Exponential backoff for transient errors
 * - Compensating Transaction: Financial reversals and corrections
 * - Manual Intervention: Escalation to operations team
 * - Skip: Intentionally skip unrecoverable messages
 *
 * PRODUCTION USAGE:
 * All strategies must be idempotent and handle concurrent execution.
 * Recovery attempts are logged and metrics are emitted for monitoring.
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
public interface RecoveryStrategyHandler {

    /**
     * Attempts to recover a failed message.
     *
     * This method must be idempotent - calling it multiple times with the same
     * message should produce the same result without side effects.
     *
     * @param dlqRecord The DLQ record to recover
     * @return Recovery result indicating success, failure, or retry needed
     */
    RecoveryResult recover(DlqRecordEntity dlqRecord);

    /**
     * Gets the strategy name for logging and metrics.
     *
     * @return Strategy name (e.g., "AUTOMATIC_RETRY", "COMPENSATING_TRANSACTION")
     */
    String getStrategyName();

    /**
     * Checks if this strategy can handle the given DLQ record.
     *
     * This is used by the RecoveryStrategyFactory to route messages
     * to the appropriate recovery handler.
     *
     * @param dlqRecord The DLQ record to evaluate
     * @return true if this strategy can handle the record
     */
    boolean canHandle(DlqRecordEntity dlqRecord);

    /**
     * Result of recovery attempt.
     *
     * This is an immutable record class containing:
     * - success: Whether recovery succeeded
     * - message: Human-readable result message
     * - retryable: Whether the message can be retried later
     * - nextRetryDelaySeconds: Delay before next retry (if retryable)
     *
     * Factory methods:
     * - success(message): Successful recovery
     * - retryLater(message, delaySeconds): Failed but can retry
     * - permanentFailure(message): Failed permanently, no retry
     */
    record RecoveryResult(
        boolean success,
        String message,
        boolean retryable,
        Integer nextRetryDelaySeconds
    ) {
        /**
         * Creates a successful recovery result.
         */
        public static RecoveryResult success(String message) {
            return new RecoveryResult(true, message, false, null);
        }

        /**
         * Creates a retry-later result with specified delay.
         */
        public static RecoveryResult retryLater(String message, int delaySeconds) {
            return new RecoveryResult(false, message, true, delaySeconds);
        }

        /**
         * Creates a permanent failure result (no retry).
         */
        public static RecoveryResult permanentFailure(String message) {
            return new RecoveryResult(false, message, false, null);
        }
    }
}
