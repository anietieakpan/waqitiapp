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
 * CRITICAL FIX #33: DirectDepositConsumer
 * Credits wallet and notifies users when direct deposits (payroll) are received
 * Impact: Critical for payroll users, improves cash flow visibility
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DirectDepositConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "direct.deposit.received", groupId = "wallet-direct-deposit-processor")
    @Transactional
    public void handle(DirectDepositEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.info("💰 DIRECT DEPOSIT RECEIVED: depositId={}, userId={}, amount=${}, employer={}",
                event.getDepositId(), event.getUserId(), event.getAmount(), event.getEmployerName());

            String key = "direct:deposit:" + event.getDepositId();
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

            // Credit wallet
            BigDecimal oldBalance = wallet.getBalance();
            wallet.setBalance(wallet.getBalance().add(event.getAmount()));
            walletRepository.save(wallet);

            // Create transaction record
            Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .type(TransactionType.DIRECT_DEPOSIT)
                .amount(event.getAmount())
                .balanceBefore(oldBalance)
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.COMPLETED)
                .description(String.format("Direct deposit from %s", event.getEmployerName()))
                .externalReference(event.getAchTraceNumber())
                .employerName(event.getEmployerName())
                .payPeriodStart(event.getPayPeriodStart())
                .payPeriodEnd(event.getPayPeriodEnd())
                .createdAt(LocalDateTime.now())
                .build();

            transactionRepository.save(transaction);

            log.info("✅ DIRECT DEPOSIT CREDITED: depositId={}, amount=${}, newBalance=${}",
                event.getDepositId(), event.getAmount(), wallet.getBalance());

            // Notify user
            notifyDirectDeposit(event, wallet, oldBalance);

            metricsCollector.incrementCounter("wallet.direct.deposit.received");
            metricsCollector.recordGauge("wallet.direct.deposit.amount", event.getAmount().doubleValue());
            metricsCollector.recordHistogram("wallet.direct.deposit.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process direct deposit received event", e);
            dlqHandler.sendToDLQ("direct.deposit.received", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyDirectDeposit(DirectDepositEvent event, Wallet wallet, BigDecimal oldBalance) {
        String message = String.format("""
            💰 Direct Deposit Received!

            Your paycheck has been deposited and is now available.

            Deposit Details:
            - Amount: $%s
            - Employer: %s
            - Pay Period: %s to %s
            - Deposit Date: %s
            - ACH Trace: %s

            Your Wallet:
            - Previous Balance: $%s
            - Deposit Amount: $%s
            - New Balance: $%s
            - Available Now: $%s

            %s

            Smart Money Tips:
            💡 Automate your finances:
            • Set up automatic savings transfers
            • Schedule bill payments
            • Invest a percentage automatically

            💡 Budget your paycheck:
            • 50%% needs (housing, food, utilities)
            • 30%% wants (entertainment, dining)
            • 20%% savings and debt payoff

            Manage Your Money:
            • View budget: https://example.com/budget
            • Set savings goals: https://example.com/savings/goals
            • Schedule transfers: https://example.com/transfers

            Questions? Contact direct deposit support:
            Email: directdeposit@example.com
            Phone: 1-800-WAQITI-DD
            """,
            event.getAmount(),
            event.getEmployerName(),
            event.getPayPeriodStart().toLocalDate(),
            event.getPayPeriodEnd().toLocalDate(),
            LocalDateTime.now().toLocalDate(),
            maskAchTrace(event.getAchTraceNumber()),
            oldBalance,
            event.getAmount(),
            wallet.getBalance(),
            wallet.getBalance(),
            getPaycheckInsights(event.getAmount(), event.isRecurring(), event.getDepositCount()));

        notificationService.sendDirectDepositNotification(
            event.getUserId(), event.getWalletId(), event.getAmount(),
            event.getEmployerName(), message);
    }

    private String getPaycheckInsights(BigDecimal amount, boolean isRecurring, int depositCount) {
        StringBuilder insights = new StringBuilder();

        if (isRecurring && depositCount > 1) {
            insights.append(String.format("""
                📊 Paycheck History:
                This is deposit #%d from this employer.
                Your direct deposit is set up for automatic recurring payments.
                """, depositCount));
        }

        // Suggest allocation
        BigDecimal needs = amount.multiply(new BigDecimal("0.50"));
        BigDecimal wants = amount.multiply(new BigDecimal("0.30"));
        BigDecimal savings = amount.multiply(new BigDecimal("0.20"));

        insights.append(String.format("""

            📈 Suggested Budget Allocation (50/30/20 rule):
            • Needs (50%%): $%s - Essential expenses
            • Wants (30%%): $%s - Discretionary spending
            • Savings (20%%): $%s - Future & emergency fund
            """, needs.setScale(2, java.math.RoundingMode.HALF_UP),
            wants.setScale(2, java.math.RoundingMode.HALF_UP),
            savings.setScale(2, java.math.RoundingMode.HALF_UP)));

        return insights.toString();
    }

    private String maskAchTrace(String achTrace) {
        if (achTrace == null || achTrace.length() < 8) return "****";
        return achTrace.substring(0, 4) + "****" + achTrace.substring(achTrace.length() - 4);
    }

    private static class DirectDepositEvent {
        private UUID depositId, userId, walletId;
        private String employerName, achTraceNumber;
        private BigDecimal amount;
        private LocalDateTime payPeriodStart, payPeriodEnd, depositedAt;
        private boolean recurring;
        private int depositCount;

        public UUID getDepositId() { return depositId; }
        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public String getEmployerName() { return employerName; }
        public String getAchTraceNumber() { return achTraceNumber; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getPayPeriodStart() { return payPeriodStart; }
        public LocalDateTime getPayPeriodEnd() { return payPeriodEnd; }
        public LocalDateTime getDepositedAt() { return depositedAt; }
        public boolean isRecurring() { return recurring; }
        public int getDepositCount() { return depositCount; }
    }
}
