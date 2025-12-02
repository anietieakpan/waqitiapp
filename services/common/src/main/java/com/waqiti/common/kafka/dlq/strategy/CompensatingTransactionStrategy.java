package com.waqiti.common.kafka.dlq.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.compensation.*;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Compensating transaction strategy for DLQ recovery.
 *
 * This strategy reverses financial transactions that failed during processing.
 * Used for critical financial events where the system must undo partial changes.
 *
 * COMPENSATING ACTIONS:
 * - payment-authorized: Reverse payment, refund customer
 * - balance-update: Reverse balance change
 * - ledger-entry-created: Create reversing journal entry
 * - fund-reservation: Release reservation
 * - transaction-completed: Void transaction
 * - wallet-debited/credited: Reverse wallet operations
 *
 * FINANCIAL INTEGRITY:
 * - All compensations are atomic (all-or-nothing)
 * - Double-entry accounting maintained
 * - Full audit trail created
 * - Idempotency ensured via compensation IDs
 *
 * GRACEFUL DEGRADATION:
 * - If compensation services not available, escalates to manual review
 * - Never silently fails - all failures are logged and alerted
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CompensatingTransactionStrategy implements RecoveryStrategyHandler {

    // Compensation services (Optional to handle services not yet implemented)
    @Autowired(required = false)
    private final Optional<PaymentCompensationService> paymentCompensationService;

    @Autowired(required = false)
    private final Optional<WalletCompensationService> walletCompensationService;

    @Autowired(required = false)
    private final Optional<LedgerCompensationService> ledgerCompensationService;

    @Autowired(required = false)
    private final Optional<TransactionCompensationService> transactionCompensationService;

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    public RecoveryResult recover(DlqRecordEntity dlqRecord) {
        String topic = dlqRecord.getTopic();

        log.info("💰 Executing compensating transaction: topic={}, messageId={}",
                topic, dlqRecord.getMessageId());

        try {
            // Parse message payload
            Map<String, Object> payload = parsePayload(dlqRecord.getMessageValue());

            // Route to appropriate compensation handler
            CompensationService.CompensationResult result = routeToCompensationHandler(topic, payload);

            if (result.success()) {
                log.info("✅ Compensation successful: topic={}, compensationId={}",
                        topic, result.compensationId());

                recordMetric("dlq.compensation.success", topic);

                return RecoveryResult.success("Compensation completed: " + result.message());

            } else if (result.requiresManualReview()) {
                log.warn("⚠️ Compensation requires manual review: topic={}, reason={}",
                        topic, result.message());

                recordMetric("dlq.compensation.manual_review", topic);

                return RecoveryResult.permanentFailure(
                    "Requires manual review: " + result.message());

            } else {
                log.warn("⚠️ Compensation failed: topic={}, reason={}", topic, result.message());

                recordMetric("dlq.compensation.failure", topic);

                return RecoveryResult.retryLater("Compensation failed: " + result.message(), 300);
            }

        } catch (Exception e) {
            log.error("❌ Compensation error: topic={}, messageId={}, error={}",
                    topic, dlqRecord.getMessageId(), e.getMessage(), e);

            recordMetric("dlq.compensation.error", topic);

            return RecoveryResult.retryLater("Compensation error: " + e.getMessage(), 600);
        }
    }

    /**
     * Routes to the appropriate compensation handler based on topic.
     */
    private CompensationService.CompensationResult routeToCompensationHandler(
            String topic,
            Map<String, Object> payload) {

        return switch (topic) {
            case "payment-authorized", "payment-initiated", "payment-completed" ->
                compensatePayment(payload);

            case "balance-update", "wallet-debited", "wallet-credited" ->
                compensateWalletOperation(payload);

            case "ledger-entry-created" ->
                compensateLedgerEntry(payload);

            case "fund-reservation", "hold-created" ->
                compensateFundReservation(payload);

            case "transaction-completed", "transaction-initiated" ->
                compensateTransaction(payload);

            default -> {
                log.warn("No compensation logic for topic: {}", topic);
                yield CompensationService.CompensationResult.failed(
                    "No compensation logic defined for topic: " + topic);
            }
        };
    }

    /**
     * Compensates a payment operation.
     */
    private CompensationService.CompensationResult compensatePayment(Map<String, Object> payload) {
        if (paymentCompensationService.isEmpty()) {
            log.error("PaymentCompensationService not available");
            return CompensationService.CompensationResult.manualReview(
                "PaymentCompensationService not implemented - requires manual compensation");
        }

        try {
            UUID paymentId = extractUUID(payload, "paymentId");
            BigDecimal amount = extractBigDecimal(payload, "amount");
            String reason = "DLQ compensation for failed payment processing";

            return paymentCompensationService.get().reversePayment(paymentId, amount, reason);

        } catch (Exception e) {
            log.error("Payment compensation failed: {}", e.getMessage(), e);
            return CompensationService.CompensationResult.failed(
                "Payment compensation error: " + e.getMessage());
        }
    }

    /**
     * Compensates a wallet operation.
     */
    private CompensationService.CompensationResult compensateWalletOperation(Map<String, Object> payload) {
        if (walletCompensationService.isEmpty()) {
            log.error("WalletCompensationService not available");
            return CompensationService.CompensationResult.manualReview(
                "WalletCompensationService not implemented - requires manual compensation");
        }

        try {
            UUID transactionId = extractUUID(payload, "transactionId");
            String reason = "DLQ compensation for failed wallet operation";

            return walletCompensationService.get().reverseTransaction(transactionId, reason);

        } catch (Exception e) {
            log.error("Wallet compensation failed: {}", e.getMessage(), e);
            return CompensationService.CompensationResult.failed(
                "Wallet compensation error: " + e.getMessage());
        }
    }

    /**
     * Compensates a ledger entry.
     */
    private CompensationService.CompensationResult compensateLedgerEntry(Map<String, Object> payload) {
        if (ledgerCompensationService.isEmpty()) {
            log.error("LedgerCompensationService not available");
            return CompensationService.CompensationResult.manualReview(
                "LedgerCompensationService not implemented - requires manual compensation");
        }

        try {
            UUID entryId = extractUUID(payload, "entryId");
            String reason = "DLQ compensation for failed ledger entry";

            return ledgerCompensationService.get().createReversalEntry(entryId, reason);

        } catch (Exception e) {
            log.error("Ledger compensation failed: {}", e.getMessage(), e);
            return CompensationService.CompensationResult.failed(
                "Ledger compensation error: " + e.getMessage());
        }
    }

    /**
     * Compensates a fund reservation.
     */
    private CompensationService.CompensationResult compensateFundReservation(Map<String, Object> payload) {
        if (walletCompensationService.isEmpty()) {
            log.error("WalletCompensationService not available");
            return CompensationService.CompensationResult.manualReview(
                "WalletCompensationService not implemented - requires manual compensation");
        }

        try {
            UUID holdId = extractUUID(payload, "holdId");
            String reason = "DLQ compensation for failed fund reservation";

            return walletCompensationService.get().releaseHold(holdId, reason);

        } catch (Exception e) {
            log.error("Fund reservation compensation failed: {}", e.getMessage(), e);
            return CompensationService.CompensationResult.failed(
                "Fund reservation compensation error: " + e.getMessage());
        }
    }

    /**
     * Compensates a transaction.
     */
    private CompensationService.CompensationResult compensateTransaction(Map<String, Object> payload) {
        if (transactionCompensationService.isEmpty()) {
            log.error("TransactionCompensationService not available");
            return CompensationService.CompensationResult.manualReview(
                "TransactionCompensationService not implemented - requires manual compensation");
        }

        try {
            UUID transactionId = extractUUID(payload, "transactionId");
            String reason = "DLQ compensation for failed transaction";

            return transactionCompensationService.get().rollbackTransaction(transactionId, reason);

        } catch (Exception e) {
            log.error("Transaction compensation failed: {}", e.getMessage(), e);
            return CompensationService.CompensationResult.failed(
                "Transaction compensation error: " + e.getMessage());
        }
    }

    /**
     * Parses JSON payload.
     */
    private Map<String, Object> parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse payload: {}", e.getMessage());
            throw new RuntimeException("Invalid JSON payload", e);
        }
    }

    /**
     * Extracts UUID from payload.
     */
    private UUID extractUUID(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String) {
            return UUID.fromString((String) value);
        } else if (value instanceof UUID) {
            return (UUID) value;
        }
        throw new IllegalArgumentException("Missing or invalid UUID for key: " + key);
    }

    /**
     * Extracts BigDecimal from payload.
     */
    private BigDecimal extractBigDecimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String) {
            return new BigDecimal((String) value);
        } else if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        throw new IllegalArgumentException("Missing or invalid amount for key: " + key);
    }

    /**
     * Records metrics.
     */
    private void recordMetric(String metricName, String topic) {
        Counter.builder(metricName)
            .tag("topic", topic)
            .tag("strategy", "compensating_transaction")
            .description("DLQ compensating transaction strategy metrics")
            .register(meterRegistry)
            .increment();
    }

    @Override
    public String getStrategyName() {
        return "COMPENSATING_TRANSACTION";
    }

    @Override
    public boolean canHandle(DlqRecordEntity dlqRecord) {
        String topic = dlqRecord.getTopic();

        // Can handle financial transaction topics
        return topic != null && (
            topic.contains("payment") ||
            topic.contains("wallet") ||
            topic.contains("ledger") ||
            topic.contains("transaction") ||
            topic.contains("balance") ||
            topic.contains("fund") ||
            topic.contains("hold")
        );
    }
}
