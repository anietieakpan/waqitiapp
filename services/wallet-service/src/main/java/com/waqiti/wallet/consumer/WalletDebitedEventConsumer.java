package com.waqiti.wallet.consumer;

import com.waqiti.common.events.WalletDebitedEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.wallet.exception.InsufficientFundsException;
import com.waqiti.wallet.exception.WalletProcessingException;
import com.waqiti.wallet.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Production-grade consumer for wallet debit events.
 *
 * This consumer handles wallet debit operations with strict controls:
 * 1. Verify sufficient funds (including reservations)
 * 2. Update wallet balance
 * 3. Create transaction record
 * 4. Update ledger entries
 * 5. Verify reconciliation
 * 6. Send notifications
 * 7. Record analytics
 *
 * CRITICAL FEATURES:
 * - Idempotency protection (prevents duplicate debits)
 * - Insufficient funds validation
 * - Fund reservation consideration
 * - Real-time reconciliation
 * - Overdraft prevention
 * - Full audit trail
 *
 * FINANCIAL INTEGRITY:
 * - All debits are validated against available balance
 * - Fund reservations are considered
 * - Negative balances prevented (except authorized overdraft)
 * - Double-entry accounting maintained
 * - Automatic reconciliation on mismatch
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletDebitedEventConsumer {

    private final IdempotencyService idempotencyService;
    private final WalletBalanceService balanceService;
    private final FundReservationService reservationService;
    private final WalletTransactionService transactionService;
    private final LedgerIntegrationService ledgerService;
    private final ReconciliationService reconciliationService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final MeterRegistry meterRegistry;

    private static final String METRIC_PREFIX = "wallet.debited.consumer";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final BigDecimal RECONCILIATION_THRESHOLD = new BigDecimal("0.01");

    @KafkaListener(
        topics = "${kafka.topics.wallet-debited:wallet-debited}",
        groupId = "${kafka.consumer-groups.wallet-debited:wallet-debited-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumers.wallet-debited.concurrency:5}"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
    public void handleWalletDebited(
            @Payload WalletDebitedEvent event,
            Acknowledgment acknowledgment) {

        Timer.Sample timer = Timer.start(meterRegistry);
        String idempotencyKey = "wallet-debited:" + event.getTransactionId();
        UUID operationId = UUID.randomUUID();

        log.info("📥 Processing wallet debit: walletId={}, amount={}, currency={}, transactionId={}, reason={}",
                event.getWalletId(), event.getAmount(), event.getCurrency(),
                event.getTransactionId(), event.getReason());

        try {
            // Idempotency check
            if (!idempotencyService.startOperation(idempotencyKey, operationId, IDEMPOTENCY_TTL)) {
                log.warn("⚠️ DUPLICATE - Wallet debit already processed: transactionId={}, walletId={}",
                        event.getTransactionId(), event.getWalletId());
                recordMetric("duplicate", event);
                acknowledgment.acknowledge();
                return;
            }

            // Validate event
            validateEvent(event);

            // Get current balance before debit
            BigDecimal balanceBefore = balanceService.getBalance(event.getWalletId());

            // Verify sufficient funds (CRITICAL)
            verifySufficientFunds(event, balanceBefore);

            // Process wallet debit
            WalletDebitResult result = processWalletDebit(event, balanceBefore, operationId);

            // Verify reconciliation
            verifyReconciliation(event, balanceBefore, result.getBalanceAfter());

            // Complete operation
            idempotencyService.completeOperation(
                idempotencyKey,
                operationId,
                Map.of(
                    "status", "SUCCESS",
                    "walletId", event.getWalletId().toString(),
                    "transactionId", event.getTransactionId().toString(),
                    "amount", event.getAmount().toString(),
                    "balanceBefore", balanceBefore.toString(),
                    "balanceAfter", result.getBalanceAfter().toString()
                ),
                IDEMPOTENCY_TTL
            );

            acknowledgment.acknowledge();
            recordMetric("success", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "success"));

            log.info("✅ Wallet debit processed: walletId={}, transactionId={}, balanceAfter={}",
                    event.getWalletId(), event.getTransactionId(), result.getBalanceAfter());

        } catch (InsufficientFundsException e) {
            log.warn("❌ Insufficient funds: walletId={}, transactionId={}, required={}, available={}",
                    event.getWalletId(), event.getTransactionId(), event.getAmount(), e.getAvailableBalance());

            idempotencyService.failOperation(idempotencyKey, operationId, "Insufficient funds: " + e.getMessage());
            recordMetric("insufficient_funds", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "insufficient_funds"));

            // Acknowledge (don't retry insufficient funds)
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ CRITICAL: Wallet debit processing failed: walletId={}, transactionId={}, error={}",
                    event.getWalletId(), event.getTransactionId(), e.getMessage(), e);

            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            recordMetric("failure", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "failure"));

            throw new WalletProcessingException("Wallet debit processing failed", e);
        }
    }

    /**
     * Verifies sufficient funds are available for debit.
     *
     * CRITICAL: This check prevents overdrafts by considering:
     * - Current wallet balance
     * - Active fund reservations
     * - Available balance = balance - reservations
     */
    private void verifySufficientFunds(WalletDebitedEvent event, BigDecimal currentBalance) {
        // Get total reserved funds
        BigDecimal totalReserved = reservationService.getTotalReservedAmount(event.getWalletId());

        // Calculate available balance
        BigDecimal availableBalance = currentBalance.subtract(totalReserved);

        log.debug("Funds check: walletId={}, balance={}, reserved={}, available={}, required={}",
                event.getWalletId(), currentBalance, totalReserved, availableBalance, event.getAmount());

        // Check if sufficient funds available
        if (availableBalance.compareTo(event.getAmount()) < 0) {
            log.warn("🚨 INSUFFICIENT FUNDS: walletId={}, required={}, available={}, balance={}, reserved={}",
                    event.getWalletId(), event.getAmount(), availableBalance, currentBalance, totalReserved);

            throw new InsufficientFundsException(
                event.getWalletId(),
                event.getAmount(),
                availableBalance,
                currentBalance,
                totalReserved
            );
        }

        log.debug("✅ Sufficient funds verified: walletId={}, available={}", event.getWalletId(), availableBalance);
    }

    /**
     * Core wallet debit processing logic.
     */
    private WalletDebitResult processWalletDebit(
            WalletDebitedEvent event,
            BigDecimal balanceBefore,
            UUID operationId) {

        log.debug("Processing wallet debit workflow: walletId={}", event.getWalletId());

        WalletDebitResult result = new WalletDebitResult();

        try {
            // STEP 1: Calculate new balance
            BigDecimal newBalance = balanceBefore.subtract(event.getAmount());

            log.debug("Step 1/7: Calculated new balance: walletId={}, before={}, debit={}, after={}",
                    event.getWalletId(), balanceBefore, event.getAmount(), newBalance);

            // STEP 2: Validate new balance (prevent negative unless overdraft authorized)
            if (newBalance.signum() < 0 && !event.isOverdraftAuthorized()) {
                throw new InsufficientFundsException(
                    event.getWalletId(),
                    event.getAmount(),
                    balanceBefore,
                    balanceBefore,
                    BigDecimal.ZERO
                );
            }

            // STEP 3: Update wallet balance
            log.debug("Step 3/7: Updating wallet balance: walletId={}", event.getWalletId());

            balanceService.updateBalance(
                event.getWalletId(),
                newBalance,
                event.getTransactionId(),
                "DEBIT: " + event.getReason()
            );

            result.setBalanceAfter(newBalance);

            log.debug("✅ Wallet balance updated: walletId={}, newBalance={}", event.getWalletId(), newBalance);

            // STEP 4: Create transaction record
            log.debug("Step 4/7: Creating transaction record");

            UUID transactionRecordId = transactionService.createTransactionRecord(
                event.getWalletId(),
                event.getTransactionId(),
                "DEBIT",
                event.getAmount(),
                event.getCurrency(),
                balanceBefore,
                newBalance,
                event.getReason(),
                event.getDestinationType(),
                event.getDestinationId(),
                event.getMetadata()
            );

            result.setTransactionRecordId(transactionRecordId);

            log.debug("✅ Transaction record created: recordId={}", transactionRecordId);

            // STEP 5: Create ledger entry
            log.debug("Step 5/7: Creating ledger entry");

            UUID ledgerEntryId = ledgerService.createDebitEntry(
                event.getWalletId(),
                event.getCustomerId(),
                event.getAmount(),
                event.getCurrency(),
                event.getReason(),
                event.getTransactionId(),
                event.getDestinationType(),
                event.getDestinationId()
            );

            result.setLedgerEntryId(ledgerEntryId);

            log.debug("✅ Ledger entry created: ledgerEntryId={}", ledgerEntryId);

            // STEP 6: Send notifications
            log.debug("Step 6/7: Sending debit notification");

            notificationService.sendWalletDebitNotification(
                event.getCustomerId(),
                event.getWalletId(),
                event.getAmount(),
                event.getCurrency(),
                newBalance,
                event.getReason()
            );

            log.debug("✅ Notification sent");

            // STEP 7: Record analytics
            log.debug("Step 7/7: Recording analytics");

            analyticsService.recordWalletDebit(
                event.getWalletId(),
                event.getCustomerId(),
                event.getAmount(),
                event.getCurrency(),
                event.getDestinationType(),
                event.getReason(),
                LocalDateTime.now()
            );

            log.debug("✅ Analytics recorded");

            log.info("✅ All wallet debit steps completed: walletId={}, balanceAfter={}",
                    event.getWalletId(), newBalance);

            return result;

        } catch (Exception e) {
            log.error("❌ Wallet debit workflow failed: walletId={}, error={}", event.getWalletId(), e.getMessage(), e);
            throw new WalletProcessingException("Wallet debit workflow failed", e);
        }
    }

    /**
     * Verifies balance reconciliation after debit.
     */
    private void verifyReconciliation(
            WalletDebitedEvent event,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter) {

        BigDecimal expectedBalance = balanceBefore.subtract(event.getAmount());
        BigDecimal actualBalance = balanceService.getBalance(event.getWalletId());
        BigDecimal difference = actualBalance.subtract(expectedBalance).abs();

        if (difference.compareTo(RECONCILIATION_THRESHOLD) > 0) {
            log.error("🚨 RECONCILIATION MISMATCH: walletId={}, expected={}, actual={}, difference={}",
                    event.getWalletId(), expectedBalance, actualBalance, difference);

            reconciliationService.publishReconciliationFailure(
                event.getWalletId(),
                event.getCustomerId(),
                expectedBalance,
                actualBalance,
                difference,
                "WALLET_DEBIT",
                event.getTransactionId()
            );

            throw new WalletProcessingException(
                String.format("Balance reconciliation failed: expected=%s, actual=%s",
                    expectedBalance, actualBalance)
            );
        }

        log.debug("✅ Reconciliation verified: walletId={}", event.getWalletId());
    }

    private void validateEvent(WalletDebitedEvent event) {
        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID cannot be null");
        }
        if (event.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (event.getAmount().scale() > 2) {
            throw new IllegalArgumentException("Amount has too many decimal places (max 2)");
        }
    }

    private void recordMetric(String result, WalletDebitedEvent event) {
        Counter.builder(METRIC_PREFIX + ".processed")
            .tag("result", result)
            .tag("currency", event.getCurrency())
            .tag("destinationType", event.getDestinationType() != null ? event.getDestinationType() : "unknown")
            .description("Wallet debit events processed")
            .register(meterRegistry)
            .increment();

        meterRegistry.summary(METRIC_PREFIX + ".amount",
                "currency", event.getCurrency())
            .record(event.getAmount().doubleValue());
    }

    @lombok.Data
    private static class WalletDebitResult {
        private BigDecimal balanceAfter;
        private UUID transactionRecordId;
        private UUID ledgerEntryId;
    }
}
