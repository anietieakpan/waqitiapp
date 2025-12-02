package com.waqiti.dlq.strategy;

import com.waqiti.dlq.model.DLQMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory for creating recovery strategies based on topic and error type.
 *
 * This determines the best recovery approach for each failed message.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecoveryStrategyFactory {

    private final AutomaticRetryStrategy automaticRetryStrategy;
    private final CompensatingTransactionStrategy compensatingTransactionStrategy;
    private final ManualInterventionStrategy manualInterventionStrategy;
    private final SkipStrategy skipStrategy;

    // Topic-to-priority mapping
    private static final Map<String, DLQMessage.DLQPriority> TOPIC_PRIORITIES = Map.ofEntries(
        // CRITICAL - Financial transactions
        Map.entry("payment-authorized", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("payment-completed", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("balance-update", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("balance-reconciliation-failed", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("fund-reservation", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("ledger-entry-created", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("sanctions-match-found", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("transaction-created", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("transaction-completed", DLQMessage.DLQPriority.CRITICAL),
        Map.entry("fraud-detection-alert", DLQMessage.DLQPriority.CRITICAL),

        // HIGH - User-facing features
        Map.entry("user-registered", DLQMessage.DLQPriority.HIGH),
        Map.entry("kyc-verification-completed", DLQMessage.DLQPriority.HIGH),
        Map.entry("notification-send", DLQMessage.DLQPriority.HIGH),
        Map.entry("wallet-created", DLQMessage.DLQPriority.HIGH),
        Map.entry("card-issued", DLQMessage.DLQPriority.HIGH),

        // MEDIUM - Background processes
        Map.entry("analytics-event", DLQMessage.DLQPriority.MEDIUM),
        Map.entry("audit-log-created", DLQMessage.DLQPriority.MEDIUM),
        Map.entry("metrics-updated", DLQMessage.DLQPriority.MEDIUM),

        // LOW - Non-critical
        Map.entry("email-sent", DLQMessage.DLQPriority.LOW),
        Map.entry("report-generated", DLQMessage.DLQPriority.LOW)
    );

    /**
     * Determines the appropriate recovery strategy for a DLQ message.
     */
    public RecoveryStrategyHandler getStrategy(DLQMessage message) {
        String topic = message.getOriginalTopic();
        String errorClass = message.getErrorClass();
        String errorMessage = message.getErrorMessage();

        log.debug("Determining recovery strategy for topic={}, errorClass={}", topic, errorClass);

        // 1. Check for transient errors (automatic retry)
        if (isTransientError(errorClass, errorMessage)) {
            log.info("Transient error detected - using automatic retry strategy");
            return automaticRetryStrategy;
        }

        // 2. Check for financial transactions (compensating transaction)
        if (isFinancialTransaction(topic)) {
            log.info("Financial transaction detected - using compensating transaction strategy");
            return compensatingTransactionStrategy;
        }

        // 3. Check for validation errors (skip)
        if (isValidationError(errorClass, errorMessage)) {
            log.info("Validation error detected - using skip strategy");
            return skipStrategy;
        }

        // 4. Check for deserialization errors (manual intervention)
        if (isDeserializationError(errorClass)) {
            log.info("Deserialization error detected - using manual intervention strategy");
            return manualInterventionStrategy;
        }

        // 5. Check for database errors (automatic retry with backoff)
        if (isDatabaseError(errorClass)) {
            log.info("Database error detected - using automatic retry strategy");
            return automaticRetryStrategy;
        }

        // 6. Default to manual intervention for unknown errors
        log.warn("Unknown error type - defaulting to manual intervention strategy");
        return manualInterventionStrategy;
    }

    /**
     * Determines priority for a topic.
     */
    public DLQMessage.DLQPriority determinePriority(String topic) {
        return TOPIC_PRIORITIES.getOrDefault(topic, DLQMessage.DLQPriority.MEDIUM);
    }

    /**
     * Checks if error is transient (network, timeout, etc.).
     */
    private boolean isTransientError(String errorClass, String errorMessage) {
        if (errorClass == null || errorMessage == null) {
            return false;
        }

        return errorClass.contains("TimeoutException") ||
               errorClass.contains("ConnectException") ||
               errorClass.contains("SocketTimeoutException") ||
               errorClass.contains("RetryableException") ||
               errorMessage.contains("Connection refused") ||
               errorMessage.contains("Connection reset") ||
               errorMessage.contains("Timeout") ||
               errorMessage.contains("temporarily unavailable");
    }

    /**
     * Checks if topic is a financial transaction.
     */
    private boolean isFinancialTransaction(String topic) {
        return topic.contains("payment") ||
               topic.contains("transaction") ||
               topic.contains("balance") ||
               topic.contains("ledger") ||
               topic.contains("fund-reservation");
    }

    /**
     * Checks if error is a validation error.
     */
    private boolean isValidationError(String errorClass, String errorMessage) {
        if (errorClass == null || errorMessage == null) {
            return false;
        }

        return errorClass.contains("ValidationException") ||
               errorClass.contains("IllegalArgumentException") ||
               errorClass.contains("ConstraintViolationException") ||
               errorMessage.contains("validation failed") ||
               errorMessage.contains("invalid") ||
               errorMessage.contains("cannot be null");
    }

    /**
     * Checks if error is a deserialization error.
     */
    private boolean isDeserializationError(String errorClass) {
        if (errorClass == null) {
            return false;
        }

        return errorClass.contains("SerializationException") ||
               errorClass.contains("JsonProcessingException") ||
               errorClass.contains("DeserializationException");
    }

    /**
     * Checks if error is a database error.
     */
    private boolean isDatabaseError(String errorClass) {
        if (errorClass == null) {
            return false;
        }

        return errorClass.contains("SQLException") ||
               errorClass.contains("DataAccessException") ||
               errorClass.contains("PersistenceException") ||
               errorClass.contains("QueryTimeoutException") ||
               errorClass.contains("LockTimeoutException");
    }
}
