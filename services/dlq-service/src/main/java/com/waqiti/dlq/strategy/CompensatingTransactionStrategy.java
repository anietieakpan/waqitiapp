package com.waqiti.dlq.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.dlq.model.DLQMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Compensating transaction strategy for DLQ recovery.
 *
 * This strategy reverses financial transactions that failed during processing.
 * Used for critical financial events where the system must undo partial changes.
 *
 * COMPENSATING ACTIONS:
 * - payment-authorized: Reverse payment, refund customer, credit merchant back
 * - balance-update: Reverse balance change
 * - ledger-entry-created: Create reversing journal entry
 * - fund-reservation: Release reservation
 * - transaction-completed: Void transaction
 *
 * FINANCIAL INTEGRITY:
 * - All compensations are atomic (all-or-nothing)
 * - Double-entry accounting maintained
 * - Full audit trail created
 * - Idempotency ensured
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CompensatingTransactionStrategy implements RecoveryStrategyHandler {

    private final PaymentCompensationService paymentCompensationService;
    private final WalletCompensationService walletCompensationService;
    private final LedgerCompensationService ledgerCompensationService;
    private final TransactionCompensationService transactionCompensationService;
    private final ObjectMapper objectMapper;

    @Override
    public RecoveryResult recover(DLQMessage message) {
        String topic = message.getOriginalTopic();

        log.info("Executing compensating transaction: topic={}, messageId={}",
                topic, message.getId());

        try {
            // Parse message payload
            Map<String, Object> payload = parsePayload(message.getMessagePayload());

            // Route to appropriate compensation handler
            CompensationResult result = switch (topic) {
                case "payment-authorized" -> compensatePaymentAuthorized(payload, message);
                case "balance-update" -> compensateBalanceUpdate(payload, message);
                case "ledger-entry-created" -> compensateLedgerEntry(payload, message);
                case "fund-reservation" -> compensateFundReservation(payload, message);
                case "transaction-completed" -> compensateTransaction(payload, message);
                case "wallet-debited" -> compensateWalletDebit(payload, message);
                case "wallet-credited" -> compensateWalletCredit(payload, message);
                default -> {
                    log.warn("No compensation logic for topic: {}", topic);
                    yield new CompensationResult(false, "No compensation logic for topic: " + topic);
                }
            };

            if (result.isSuccess()) {
                log.info("✅ Compensation successful: topic={}, messageId={}", topic, message.getId());
                return RecoveryResult.success("Compensation completed: " + result.getMessage());
            } else {
                log.warn("⚠️ Compensation failed: topic={}, reason={}", topic, result.getMessage());
                return RecoveryResult.retryLater("Compensation failed: " + result.getMessage(), 300);
            }

        } catch (Exception e) {
            log.error("❌ Compensation error: topic={}, messageId={}, error={}",
                    topic, message.getId(), e.getMessage(), e);
            return RecoveryResult.retryLater("Compensation error: " + e.getMessage(), 600);
        }
    }

    /**
     * Compensates a payment authorization failure.
     *
     * Steps:
     * 1. Reverse customer debit
     * 2. Reverse merchant credit
     * 3. Update payment status to REVERSED
     * 4. Create reversing ledger entries
     * 5. Send reversal notifications
     */
    private CompensationResult compensatePaymentAuthorized(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating payment authorization: paymentId={}", payload.get("paymentId"));

        try {
            UUID paymentId = UUID.fromString((String) payload.get("paymentId"));
            UUID customerId = UUID.fromString((String) payload.get("customerId"));
            UUID merchantId = UUID.fromString((String) payload.get("merchantId"));
            BigDecimal amount = new BigDecimal((String) payload.get("amount"));
            String currency = (String) payload.get("currency");

            // Check if already compensated (idempotency)
            if (paymentCompensationService.isAlreadyCompensated(paymentId)) {
                log.info("Payment already compensated: paymentId={}", paymentId);
                return new CompensationResult(true, "Already compensated");
            }

            // Execute compensation
            paymentCompensationService.compensatePayment(
                paymentId,
                customerId,
                merchantId,
                amount,
                currency,
                "DLQ_COMPENSATION",
                message.getId().toString()
            );

            log.info("✅ Payment compensation completed: paymentId={}", paymentId);
            return new CompensationResult(true, "Payment reversed successfully");

        } catch (Exception e) {
            log.error("Payment compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Payment compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensates a balance update failure.
     *
     * Reverses the balance change by creating an offsetting entry.
     */
    private CompensationResult compensateBalanceUpdate(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating balance update: transactionId={}", payload.get("transactionId"));

        try {
            UUID walletId = UUID.fromString((String) payload.get("walletId"));
            String operation = (String) payload.get("operation"); // CREDIT or DEBIT
            BigDecimal amount = new BigDecimal((String) payload.get("amount"));

            // Reverse operation
            String reverseOperation = "CREDIT".equals(operation) ? "DEBIT" : "CREDIT";

            walletCompensationService.compensateBalanceChange(
                walletId,
                amount,
                reverseOperation,
                "DLQ_BALANCE_REVERSAL",
                message.getId().toString()
            );

            log.info("✅ Balance update compensated: walletId={}", walletId);
            return new CompensationResult(true, "Balance reversed successfully");

        } catch (Exception e) {
            log.error("Balance compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Balance compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensates a ledger entry creation failure.
     *
     * Creates reversing journal entries to maintain double-entry accounting.
     */
    private CompensationResult compensateLedgerEntry(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating ledger entry: entryId={}", payload.get("entryId"));

        try {
            UUID entryId = UUID.fromString((String) payload.get("entryId"));

            ledgerCompensationService.createReversingEntry(
                entryId,
                "DLQ_LEDGER_REVERSAL",
                message.getId().toString()
            );

            log.info("✅ Ledger entry compensated: entryId={}", entryId);
            return new CompensationResult(true, "Ledger entry reversed successfully");

        } catch (Exception e) {
            log.error("Ledger compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Ledger compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensates a fund reservation failure.
     *
     * Releases the reservation to free up customer funds.
     */
    private CompensationResult compensateFundReservation(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating fund reservation: reservationId={}", payload.get("reservationId"));

        try {
            UUID reservationId = UUID.fromString((String) payload.get("reservationId"));
            UUID accountId = UUID.fromString((String) payload.get("accountId"));

            paymentCompensationService.releaseFundReservation(
                reservationId,
                accountId,
                "DLQ_RESERVATION_RELEASE"
            );

            log.info("✅ Fund reservation compensated: reservationId={}", reservationId);
            return new CompensationResult(true, "Reservation released successfully");

        } catch (Exception e) {
            log.error("Reservation compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Reservation compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensates a completed transaction failure.
     *
     * Voids the transaction and reverses all associated changes.
     */
    private CompensationResult compensateTransaction(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating transaction: transactionId={}", payload.get("transactionId"));

        try {
            UUID transactionId = UUID.fromString((String) payload.get("transactionId"));

            transactionCompensationService.voidTransaction(
                transactionId,
                "DLQ_TRANSACTION_VOID",
                message.getId().toString()
            );

            log.info("✅ Transaction compensated: transactionId={}", transactionId);
            return new CompensationResult(true, "Transaction voided successfully");

        } catch (Exception e) {
            log.error("Transaction compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Transaction compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensates a wallet debit failure.
     */
    private CompensationResult compensateWalletDebit(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating wallet debit: walletId={}", payload.get("walletId"));

        try {
            UUID walletId = UUID.fromString((String) payload.get("walletId"));
            BigDecimal amount = new BigDecimal((String) payload.get("amount"));

            // Credit back the amount that was debited
            walletCompensationService.creditWallet(
                walletId,
                amount,
                "DLQ_DEBIT_REVERSAL",
                message.getId().toString()
            );

            log.info("✅ Wallet debit compensated: walletId={}", walletId);
            return new CompensationResult(true, "Wallet debit reversed successfully");

        } catch (Exception e) {
            log.error("Wallet debit compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Wallet debit compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensates a wallet credit failure.
     */
    private CompensationResult compensateWalletCredit(
            Map<String, Object> payload,
            DLQMessage message) {

        log.info("Compensating wallet credit: walletId={}", payload.get("walletId"));

        try {
            UUID walletId = UUID.fromString((String) payload.get("walletId"));
            BigDecimal amount = new BigDecimal((String) payload.get("amount"));

            // Debit back the amount that was credited
            walletCompensationService.debitWallet(
                walletId,
                amount,
                "DLQ_CREDIT_REVERSAL",
                message.getId().toString()
            );

            log.info("✅ Wallet credit compensated: walletId={}", walletId);
            return new CompensationResult(true, "Wallet credit reversed successfully");

        } catch (Exception e) {
            log.error("Wallet credit compensation failed: error={}", e.getMessage(), e);
            return new CompensationResult(false, "Wallet credit compensation failed: " + e.getMessage());
        }
    }

    /**
     * Parses JSON payload.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payload) throws Exception {
        return objectMapper.readValue(payload, Map.class);
    }

    @Override
    public String getStrategyName() {
        return "COMPENSATING_TRANSACTION";
    }

    @Override
    public boolean canHandle(DLQMessage message) {
        // Can handle financial transaction topics
        String topic = message.getOriginalTopic();
        return topic.contains("payment") ||
               topic.contains("balance") ||
               topic.contains("ledger") ||
               topic.contains("transaction") ||
               topic.contains("wallet") ||
               topic.contains("fund-reservation");
    }

    /**
     * Internal result holder.
     */
    private record CompensationResult(boolean success, String message) {}
}
