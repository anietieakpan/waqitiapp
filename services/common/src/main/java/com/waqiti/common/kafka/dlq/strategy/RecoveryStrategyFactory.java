package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for selecting the appropriate DLQ recovery strategy.
 *
 * Routes DLQ messages to recovery strategies based on:
 * - Error type and classification
 * - Topic criticality
 * - Retry count
 * - Message age
 * - Business rules
 *
 * STRATEGY PRIORITY (in order):
 * 1. SkipStrategy - For invalid/unrecoverable messages
 * 2. AutomaticRetryStrategy - For transient errors with retries remaining
 * 3. CompensatingTransactionStrategy - For financial operations requiring reversal
 * 4. ManualInterventionStrategy - For complex failures requiring human review
 *
 * INTELLIGENT ROUTING:
 * - Validation errors → Skip
 * - Transient network errors → Automatic Retry
 * - Failed payments → Compensating Transaction
 * - Max retries exceeded → Manual Intervention
 * - Security incidents → Manual Intervention (escalate to security team)
 *
 * PRODUCTION BEHAVIOR:
 * - Each strategy's canHandle() method is consulted
 * - First matching strategy is selected
 * - Fallback to ManualIntervention if no strategy matches
 * - All routing decisions logged for audit trail
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecoveryStrategyFactory {

    private final SkipStrategy skipStrategy;
    private final AutomaticRetryStrategy automaticRetryStrategy;
    private final CompensatingTransactionStrategy compensatingTransactionStrategy;
    private final ManualInterventionStrategy manualInterventionStrategy;

    /**
     * Selects the appropriate recovery strategy for a DLQ record.
     *
     * @param dlqRecord The DLQ record to recover
     * @return The selected recovery strategy
     */
    public RecoveryStrategyHandler selectStrategy(DlqRecordEntity dlqRecord) {
        log.debug("Selecting recovery strategy for: messageId={}, topic={}, retryCount={}",
                dlqRecord.getMessageId(), dlqRecord.getTopic(), dlqRecord.getRetryCount());

        // Strategy selection order (priority-based)
        List<RecoveryStrategyHandler> strategies = List.of(
            skipStrategy,                       // 1. Skip invalid/unrecoverable messages
            compensatingTransactionStrategy,    // 2. Compensate financial transactions
            automaticRetryStrategy,             // 3. Retry transient errors
            manualInterventionStrategy          // 4. Escalate to humans (fallback)
        );

        // Find first strategy that can handle this record
        for (RecoveryStrategyHandler strategy : strategies) {
            if (strategy.canHandle(dlqRecord)) {
                log.info("📍 Selected strategy: {} for messageId={}, topic={}",
                        strategy.getStrategyName(),
                        dlqRecord.getMessageId(),
                        dlqRecord.getTopic());
                return strategy;
            }
        }

        // Fallback to manual intervention (should never reach here due to
        // ManualInterventionStrategy handling all messages)
        log.warn("⚠️ No strategy matched, falling back to ManualIntervention: messageId={}",
                dlqRecord.getMessageId());
        return manualInterventionStrategy;
    }

    /**
     * Gets a strategy by name (for explicit strategy selection).
     *
     * @param strategyName Strategy name
     * @return The recovery strategy
     * @throws IllegalArgumentException if strategy name is invalid
     */
    public RecoveryStrategyHandler getStrategyByName(String strategyName) {
        return switch (strategyName.toUpperCase()) {
            case "SKIP" -> skipStrategy;
            case "AUTOMATIC_RETRY" -> automaticRetryStrategy;
            case "COMPENSATING_TRANSACTION" -> compensatingTransactionStrategy;
            case "MANUAL_INTERVENTION" -> manualInterventionStrategy;
            default -> throw new IllegalArgumentException(
                    "Unknown strategy: " + strategyName);
        };
    }

    /**
     * Lists all available recovery strategies.
     *
     * @return List of strategy names
     */
    public List<String> getAvailableStrategies() {
        return List.of(
            skipStrategy.getStrategyName(),
            automaticRetryStrategy.getStrategyName(),
            compensatingTransactionStrategy.getStrategyName(),
            manualInterventionStrategy.getStrategyName()
        );
    }
}
