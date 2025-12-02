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
import java.util.UUID;

/**
 * CRITICAL FIX #43: OverdraftProtectionConsumer
 * Applies overdraft protection and notifies users
 * Impact: Prevents declined transactions, improves user experience
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverdraftProtectionConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    private static final BigDecimal OVERDRAFT_FEE = new BigDecimal("10.00");

    @KafkaListener(topics = "overdraft.protection.triggered", groupId = "wallet-overdraft-protection-processor")
    @Transactional
    public void handle(OverdraftProtectionEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.warn("⚠️ OVERDRAFT PROTECTION TRIGGERED: walletId={}, userId={}, overdraftAmount=${}, source={}",
                event.getWalletId(), event.getUserId(), event.getOverdraftAmount(), event.getProtectionSource());

            String key = "overdraft:protection:" + event.getTransactionId();
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

            // Transfer overdraft coverage amount
            BigDecimal oldBalance = wallet.getBalance();
            wallet.setBalance(wallet.getBalance().add(event.getOverdraftAmount()));
            wallet.setOverdraftUsed(wallet.getOverdraftUsed().add(event.getOverdraftAmount()));
            walletRepository.save(wallet);

            // Create overdraft protection transaction
            Transaction overdraftTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .type(TransactionType.OVERDRAFT_PROTECTION)
                .amount(event.getOverdraftAmount())
                .balanceBefore(oldBalance)
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.COMPLETED)
                .description(String.format("Overdraft protection from %s", event.getProtectionSource()))
                .fee(OVERDRAFT_FEE)
                .protectionSource(event.getProtectionSource())
                .linkedAccountId(event.getLinkedAccountId())
                .createdAt(LocalDateTime.now())
                .build();

            transactionRepository.save(overdraftTransaction);

            // Deduct overdraft fee
            wallet.setBalance(wallet.getBalance().subtract(OVERDRAFT_FEE));
            walletRepository.save(wallet);

            log.info("✅ OVERDRAFT PROTECTION APPLIED: amount=${}, fee=${}, newBalance=${}, totalOverdraftUsed=${}",
                event.getOverdraftAmount(), OVERDRAFT_FEE, wallet.getBalance(), wallet.getOverdraftUsed());

            // Notify user
            notifyOverdraftProtection(event, wallet, overdraftTransaction);

            metricsCollector.incrementCounter("wallet.overdraft.protection.triggered");
            metricsCollector.recordGauge("wallet.overdraft.amount", event.getOverdraftAmount().doubleValue());
            metricsCollector.recordHistogram("wallet.overdraft.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process overdraft protection event", e);
            dlqHandler.sendToDLQ("overdraft.protection.triggered", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyOverdraftProtection(OverdraftProtectionEvent event, Wallet wallet,
                                          Transaction overdraftTransaction) {
        String message = String.format("""
            ⚠️ Overdraft Protection Used

            Your overdraft protection was triggered to cover a transaction.

            Transaction Details:
            - Original Transaction: %s
            - Merchant: %s
            - Amount: $%s
            - Date: %s

            Overdraft Coverage:
            - Coverage Source: %s
            - Amount Covered: $%s
            - Overdraft Fee: $%s
            - Total Cost: $%s

            Your Wallet Status:
            - Previous Balance: $%s (insufficient)
            - Coverage Applied: +$%s
            - Overdraft Fee: -$%s
            - Current Balance: $%s
            - Total Overdraft Used: $%s
            - %s

            %s

            What This Means:
            • Your transaction was approved (not declined)
            • Funds were transferred from your %s
            • You need to repay the overdraft amount
            • An overdraft fee of $%s was charged

            Repayment Information:
            %s

            To Avoid Overdraft Fees:
            💡 Best Practices:
            • Monitor your balance regularly
            • Set up low balance alerts
            • Enable automatic transfers from savings
            • Use spending categories and budgets
            • Keep a buffer in your account

            💡 Manage Overdraft Protection:
            • View settings: https://example.com/settings/overdraft
            • Adjust coverage limits
            • Change linked account
            • Disable if not needed

            Overdraft Protection Details:
            • Protection Source: %s
            • Coverage Limit: %s
            • Used: $%s
            • Available: %s

            Questions? Contact overdraft support:
            Email: overdraft@example.com
            Phone: 1-800-WAQITI-OD
            """,
            event.getOriginalTransactionDescription(),
            event.getMerchantName(),
            event.getOriginalTransactionAmount(),
            event.getTransactionDate(),
            getProtectionSourceName(event.getProtectionSource()),
            event.getOverdraftAmount(),
            OVERDRAFT_FEE,
            event.getOverdraftAmount().add(OVERDRAFT_FEE),
            wallet.getBalance().subtract(event.getOverdraftAmount()).add(OVERDRAFT_FEE),
            event.getOverdraftAmount(),
            OVERDRAFT_FEE,
            wallet.getBalance(),
            wallet.getOverdraftUsed(),
            getOverdraftLimitStatus(wallet.getOverdraftUsed(), event.getOverdraftLimit()),
            getProtectionSourceDetails(event.getProtectionSource()),
            getProtectionSourceName(event.getProtectionSource()),
            OVERDRAFT_FEE,
            getRepaymentInfo(event.getProtectionSource(), event.getOverdraftAmount()),
            getProtectionSourceName(event.getProtectionSource()),
            event.getOverdraftLimit() != null ? "$" + event.getOverdraftLimit() : "No limit set",
            wallet.getOverdraftUsed(),
            event.getOverdraftLimit() != null
                ? "$" + event.getOverdraftLimit().subtract(wallet.getOverdraftUsed())
                : "Unlimited");

        notificationService.sendOverdraftProtectionNotification(
            event.getUserId(), event.getWalletId(), event.getOverdraftAmount(), OVERDRAFT_FEE, message);
    }

    private String getProtectionSourceName(String source) {
        return switch (source.toLowerCase()) {
            case "linked_savings" -> "Linked Savings Account";
            case "linked_checking" -> "Linked Checking Account";
            case "credit_line" -> "Overdraft Credit Line";
            case "backup_card" -> "Backup Debit/Credit Card";
            default -> source;
        };
    }

    private String getProtectionSourceDetails(String source) {
        return switch (source.toLowerCase()) {
            case "linked_savings", "linked_checking" -> """
                Coverage Method: Automatic Transfer
                Your overdraft was covered by transferring funds from your linked account.
                """;
            case "credit_line" -> """
                Coverage Method: Credit Line Draw
                Your overdraft was covered using your pre-approved overdraft credit line.
                This is a short-term loan that must be repaid.
                """;
            case "backup_card" -> """
                Coverage Method: Card Authorization
                Your overdraft was covered by charging your backup card.
                """;
            default -> "Funds were transferred from your overdraft protection source.";
        };
    }

    private String getRepaymentInfo(String source, BigDecimal amount) {
        if ("credit_line".equalsIgnoreCase(source)) {
            return String.format("""
                ⚠️ Repayment Required:
                • Amount owed: $%s + $%s fee = $%s total
                • Due date: Within 30 days
                • Payment options: https://example.com/repay-overdraft
                • Late fees may apply if not repaid on time

                Auto-repayment: When your balance allows, we'll automatically repay this overdraft.
                """, amount, OVERDRAFT_FEE, amount.add(OVERDRAFT_FEE));
        } else if ("linked_savings".equalsIgnoreCase(source) || "linked_checking".equalsIgnoreCase(source)) {
            return String.format("""
                ✅ Auto-Repayment Active:
                The $%s will be automatically repaid to your linked account when funds are available.
                The $%s fee is non-refundable and already deducted.
                """, amount, OVERDRAFT_FEE);
        } else {
            return String.format("""
                Repayment will be processed according to your overdraft protection terms.
                Check your agreement for specific repayment details.
                """);
        }
    }

    private String getOverdraftLimitStatus(BigDecimal used, BigDecimal limit) {
        if (limit == null) return "No limit set";

        BigDecimal remaining = limit.subtract(used);
        BigDecimal percentUsed = used.divide(limit, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        if (percentUsed.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return String.format("⚠️ WARNING: %.0f%% of overdraft limit used ($%s of $%s)",
                percentUsed, used, limit);
        } else if (percentUsed.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return String.format("⚠️ HIGH USAGE: %.0f%% of overdraft limit used",
                percentUsed);
        } else {
            return String.format("%.0f%% of overdraft limit used", percentUsed);
        }
    }

    private static class OverdraftProtectionEvent {
        private UUID userId, walletId, transactionId, linkedAccountId;
        private String protectionSource, merchantName, originalTransactionDescription;
        private BigDecimal overdraftAmount, originalTransactionAmount, overdraftLimit;
        private LocalDateTime transactionDate;

        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public UUID getTransactionId() { return transactionId; }
        public UUID getLinkedAccountId() { return linkedAccountId; }
        public String getProtectionSource() { return protectionSource; }
        public String getMerchantName() { return merchantName; }
        public String getOriginalTransactionDescription() { return originalTransactionDescription; }
        public BigDecimal getOverdraftAmount() { return overdraftAmount; }
        public BigDecimal getOriginalTransactionAmount() { return originalTransactionAmount; }
        public BigDecimal getOverdraftLimit() { return overdraftLimit; }
        public LocalDateTime getTransactionDate() { return transactionDate; }
    }
}
