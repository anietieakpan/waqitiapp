package com.waqiti.wallet.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * CRITICAL FIX #35: CheckDepositClearedConsumer
 * Credits wallet when check deposits clear and funds become available
 * Impact: Critical for check deposit users, cash flow visibility
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckDepositClearedConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "check.deposit.cleared", groupId = "wallet-check-deposit-processor")
    @Transactional
    public void handle(CheckDepositClearedEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.info("✅ CHECK DEPOSIT CLEARED: depositId={}, userId={}, amount=${}, clearanceDays={}",
                event.getDepositId(), event.getUserId(), event.getAmount(),
                ChronoUnit.DAYS.between(event.getDepositedAt(), event.getClearedAt()));

            String key = "check:deposit:cleared:" + event.getDepositId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            // Acquire lock on wallet
            lockId = lockService.acquireLock("wallet-" + event.getWalletId(), Duration.ofMinutes(5));
            if (lockId == null) {
                throw new BusinessException("Failed to acquire wallet lock");
            }

            Wallet wallet = walletRepository.findById(event.getWalletId())
                .orElseThrow(() -> new BusinessException("Wallet not found"));

            // Credit wallet (remove hold and make available)
            BigDecimal oldBalance = wallet.getBalance();
            BigDecimal oldHeldBalance = wallet.getHeldBalance();

            wallet.setHeldBalance(wallet.getHeldBalance().subtract(event.getAmount()));
            wallet.setBalance(wallet.getBalance().add(event.getAmount()));
            walletRepository.save(wallet);

            // Update pending transaction to completed
            Transaction transaction = transactionRepository.findById(event.getTransactionId())
                .orElse(null);

            if (transaction != null) {
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setBalanceAfter(wallet.getBalance());
                transactionRepository.save(transaction);
            } else {
                // Create transaction if it doesn't exist
                transaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .walletId(event.getWalletId())
                    .userId(event.getUserId())
                    .type(TransactionType.CHECK_DEPOSIT)
                    .amount(event.getAmount())
                    .balanceBefore(oldBalance)
                    .balanceAfter(wallet.getBalance())
                    .status(TransactionStatus.COMPLETED)
                    .description("Check deposit cleared")
                    .checkNumber(event.getCheckNumber())
                    .createdAt(LocalDateTime.now())
                    .build();
                transactionRepository.save(transaction);
            }

            log.info("✅ CHECK DEPOSIT CLEARED: depositId={}, amount=${}, newBalance=${}, releasedHold=${}",
                event.getDepositId(), event.getAmount(), wallet.getBalance(),
                oldHeldBalance.subtract(wallet.getHeldBalance()));

            // Notify user
            notifyCheckCleared(event, wallet, oldBalance, oldHeldBalance);

            metricsCollector.incrementCounter("wallet.check.deposit.cleared");
            metricsCollector.recordGauge("wallet.check.deposit.amount", event.getAmount().doubleValue());
            metricsCollector.recordHistogram("wallet.check.deposit.clearance.days",
                ChronoUnit.DAYS.between(event.getDepositedAt(), event.getClearedAt()));
            metricsCollector.recordHistogram("wallet.check.deposit.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process check deposit cleared event", e);
            dlqHandler.sendToDLQ("check.deposit.cleared", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyCheckCleared(CheckDepositClearedEvent event, Wallet wallet,
                                    BigDecimal oldBalance, BigDecimal oldHeldBalance) {
        long clearanceDays = ChronoUnit.DAYS.between(event.getDepositedAt(), event.getClearedAt());

        String message = String.format("""
            ✅ Check Deposit Cleared - Funds Available!

            Your check deposit has cleared and funds are now available.

            Check Details:
            - Amount: $%s
            - Check Number: %s
            - Deposited: %s
            - Cleared: %s
            - Clearance Time: %d business day%s

            %s

            Your Wallet:
            - Previous Available Balance: $%s
            - Previous Held Balance: $%s
            - Deposit Amount: $%s
            - New Available Balance: $%s
            - New Held Balance: $%s

            Funds Status:
            ✅ Funds are IMMEDIATELY AVAILABLE
            • You can spend, transfer, or withdraw these funds now
            • No restrictions apply
            • This deposit has completed all bank verification

            Check Deposit Tips:
            💡 Future Deposits:
            • Mobile check deposit: Available 24/7 in our app
            • Typical clearance: 1-3 business days
            • Deposits before 5 PM clear faster
            • Larger checks may have extended holds

            💡 Avoid Delays:
            • Ensure check is endorsed properly
            • Take clear, well-lit photos for mobile deposits
            • Verify check amount matches deposit
            • Don't deposit damaged or altered checks

            View Transaction:
            https://example.com/wallet/transactions/%s

            Questions? Contact check deposit support:
            Email: checks@example.com
            Phone: 1-800-WAQITI-CHECK
            Reference: Deposit ID %s
            """,
            event.getAmount(),
            event.getCheckNumber() != null ? event.getCheckNumber() : "N/A",
            event.getDepositedAt().toLocalDate(),
            event.getClearedAt().toLocalDate(),
            clearanceDays,
            clearanceDays == 1 ? "" : "s",
            getClearanceNote(clearanceDays),
            oldBalance,
            oldHeldBalance,
            event.getAmount(),
            wallet.getBalance(),
            wallet.getHeldBalance(),
            event.getTransactionId(),
            event.getDepositId());

        notificationService.sendCheckDepositClearedNotification(
            event.getUserId(), event.getWalletId(), event.getAmount(), message);
    }

    private String getClearanceNote(long clearanceDays) {
        if (clearanceDays <= 1) {
            return """
                🚀 Fast Clearance:
                Your check cleared faster than usual! This typically happens with:
                • Checks from well-known banks
                • Smaller check amounts
                • Your good account history
                """;
        } else if (clearanceDays >= 5) {
            return """
                ⏱️ Extended Clearance:
                This check took longer than usual to clear. Extended holds can happen due to:
                • Large check amounts (risk management)
                • Out-of-state or foreign checks
                • New account (building history)
                • First-time check from this source

                To reduce future hold times:
                • Build a positive account history
                • Use direct deposit when possible
                • Maintain a healthy account balance
                """;
        } else {
            return """
                Standard clearance time (1-3 business days).
                """;
        }
    }

    private static class CheckDepositClearedEvent {
        private UUID depositId, userId, walletId, transactionId;
        private String checkNumber, depositMethod;
        private BigDecimal amount;
        private LocalDateTime depositedAt, clearedAt;

        public UUID getDepositId() { return depositId; }
        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public UUID getTransactionId() { return transactionId; }
        public String getCheckNumber() { return checkNumber; }
        public String getDepositMethod() { return depositMethod; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getDepositedAt() { return depositedAt; }
        public LocalDateTime getClearedAt() { return clearedAt; }
    }
}
