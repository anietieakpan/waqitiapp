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
 * CRITICAL FIX #52: InterestEarnedConsumer
 * Credits wallet with earned interest and notifies users
 * Impact: Interest payment transparency, savings engagement
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InterestEarnedConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "account.interest.earned", groupId = "wallet-interest-processor")
    @Transactional
    public void handle(InterestEarnedEvent event, Acknowledgment ack) {
        String lockId = null;

        try {
            log.info("💰 INTEREST EARNED: userId={}, amount=${}, apy={}%, period={}",
                event.getUserId(), event.getInterestAmount(), event.getApy(), event.getInterestPeriod());

            String key = "interest:earned:" + event.getWalletId() + ":" + event.getInterestPeriod();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            lockId = lockService.acquireLock("wallet-" + event.getWalletId(), Duration.ofMinutes(5));
            if (lockId == null) {
                throw new BusinessException("Failed to acquire wallet lock");
            }

            Wallet wallet = walletRepository.findById(event.getWalletId())
                .orElseThrow(() -> new BusinessException("Wallet not found"));

            BigDecimal oldBalance = wallet.getBalance();
            wallet.setBalance(wallet.getBalance().add(event.getInterestAmount()));
            wallet.setTotalInterestEarned(wallet.getTotalInterestEarned().add(event.getInterestAmount()));
            walletRepository.save(wallet);

            Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .type(TransactionType.INTEREST_EARNED)
                .amount(event.getInterestAmount())
                .balanceBefore(oldBalance)
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.COMPLETED)
                .description(String.format("Interest earned (%s)", event.getInterestPeriod()))
                .createdAt(LocalDateTime.now())
                .build();

            transactionRepository.save(transaction);

            log.info("✅ INTEREST CREDITED: amount=${}, newBalance=${}, lifetimeInterest=${}",
                event.getInterestAmount(), wallet.getBalance(), wallet.getTotalInterestEarned());

            notifyInterestEarned(event, wallet, oldBalance);

            metricsCollector.incrementCounter("wallet.interest.earned");
            metricsCollector.recordGauge("wallet.interest.amount", event.getInterestAmount().doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process interest earned event", e);
            dlqHandler.sendToDLQ("account.interest.earned", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyInterestEarned(InterestEarnedEvent event, Wallet wallet, BigDecimal oldBalance) {
        String message = String.format("""
            💰 Interest Earned!

            Your account earned interest for %s.

            Interest Details:
            - Interest Amount: $%s
            - Annual Percentage Yield (APY): %.2f%%
            - Interest Period: %s
            - Calculation Method: %s

            Your Account:
            - Previous Balance: $%s
            - Interest Earned: +$%s
            - New Balance: $%s

            Lifetime Interest:
            - Total Interest Earned: $%s
            - This Year: $%s
            - This Month: $%s

            How Your Interest Was Calculated:
            - Average Daily Balance: $%s
            - Days in Period: %d
            - Daily Rate: %.4f%%
            - Calculation: $%s × %.4f%% × %d days = $%s

            Your APY Breakdown:
            • Current APY: %.2f%%
            • Tier: %s
            • Next Tier APY: %s

            Maximize Your Interest:
            💡 Earn More:
            • Maintain higher average balance
            • Keep funds in account longer
            • Consider tier upgrades
            • Enable autopay discounts

            Interest paid on or before the last business day of each month.
            Interest is compounded daily and paid monthly.

            Questions? Contact interest support:
            Email: interest@example.com
            Phone: 1-800-WAQITI-INT
            """,
            event.getInterestPeriod(),
            event.getInterestAmount(),
            event.getApy().doubleValue(),
            event.getInterestPeriod(),
            event.getCalculationMethod(),
            oldBalance,
            event.getInterestAmount(),
            wallet.getBalance(),
            wallet.getTotalInterestEarned(),
            event.getYtdInterest(),
            event.getMonthlyInterest(),
            event.getAverageDailyBalance(),
            event.getDaysInPeriod(),
            event.getDailyRate().doubleValue(),
            event.getAverageDailyBalance(),
            event.getDailyRate().doubleValue(),
            event.getDaysInPeriod(),
            event.getInterestAmount(),
            event.getApy().doubleValue(),
            event.getCurrentTier(),
            event.getNextTierApy() != null ? event.getNextTierApy() + "%" : "Max tier reached");

        notificationService.sendInterestEarnedNotification(
            event.getUserId(), event.getWalletId(), event.getInterestAmount(), message);
    }

    private static class InterestEarnedEvent {
        private UUID userId, walletId;
        private String interestPeriod, calculationMethod, currentTier;
        private BigDecimal interestAmount, apy, averageDailyBalance, dailyRate;
        private BigDecimal ytdInterest, monthlyInterest;
        private Integer daysInPeriod;
        private String nextTierApy;

        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public String getInterestPeriod() { return interestPeriod; }
        public String getCalculationMethod() { return calculationMethod; }
        public String getCurrentTier() { return currentTier; }
        public BigDecimal getInterestAmount() { return interestAmount; }
        public BigDecimal getApy() { return apy; }
        public BigDecimal getAverageDailyBalance() { return averageDailyBalance; }
        public BigDecimal getDailyRate() { return dailyRate; }
        public BigDecimal getYtdInterest() { return ytdInterest; }
        public BigDecimal getMonthlyInterest() { return monthlyInterest; }
        public Integer getDaysInPeriod() { return daysInPeriod; }
        public String getNextTierApy() { return nextTierApy; }
    }
}
