package com.waqiti.dlq.strategy;

import com.waqiti.dlq.model.DLQMessage;

/**
 * Interface for DLQ recovery strategy handlers.
 *
 * Each strategy implements a different approach to recovering failed messages.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
public interface RecoveryStrategyHandler {

    /**
     * Attempts to recover a failed message.
     *
     * @param message The DLQ message to recover
     * @return Recovery result
     */
    RecoveryResult recover(DLQMessage message);

    /**
     * Gets the strategy name.
     */
    String getStrategyName();

    /**
     * Checks if this strategy can handle the given message.
     */
    boolean canHandle(DLQMessage message);

    /**
     * Result of recovery attempt.
     */
    record RecoveryResult(
        boolean success,
        String message,
        boolean retryable,
        Integer nextRetryDelaySeconds
    ) {
        public static RecoveryResult success(String message) {
            return new RecoveryResult(true, message, false, null);
        }

        public static RecoveryResult retryLater(String message, int delaySeconds) {
            return new RecoveryResult(false, message, true, delaySeconds);
        }

        public static RecoveryResult permanentFailure(String message) {
            return new RecoveryResult(false, message, false, null);
        }
    }
}
