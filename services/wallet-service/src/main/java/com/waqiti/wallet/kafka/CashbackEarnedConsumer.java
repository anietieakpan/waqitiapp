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
 * CRITICAL FIX #40: CashbackEarnedConsumer
 * Credits wallet with cashback rewards and notifies users
 * Impact: Rewards engagement, user satisfaction
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CashbackEarnedConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "cashback.earned", groupId = "wallet-cashback-processor")
    @Transactional
    public void handle(CashbackEarnedEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.info("💰 CASHBACK EARNED: rewardId={}, userId={}, amount=${}, rate={}%",
                event.getRewardId(), event.getUserId(), event.getCashbackAmount(), event.getCashbackRate());

            String key = "cashback:earned:" + event.getRewardId();
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

            // Credit cashback to wallet
            BigDecimal oldBalance = wallet.getBalance();
            wallet.setBalance(wallet.getBalance().add(event.getCashbackAmount()));
            wallet.setTotalCashbackEarned(wallet.getTotalCashbackEarned().add(event.getCashbackAmount()));
            walletRepository.save(wallet);

            // Create transaction record
            Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .type(TransactionType.CASHBACK_REWARD)
                .amount(event.getCashbackAmount())
                .balanceBefore(oldBalance)
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.COMPLETED)
                .description(String.format("Cashback reward: %.1f%% on %s purchase",
                    event.getCashbackRate().doubleValue(), event.getMerchantName()))
                .merchantName(event.getMerchantName())
                .merchantCategory(event.getMerchantCategory())
                .originalTransactionId(event.getOriginalTransactionId())
                .createdAt(LocalDateTime.now())
                .build();

            transactionRepository.save(transaction);

            log.info("✅ CASHBACK CREDITED: rewardId={}, amount=${}, newBalance=${}, lifetimeCashback=${}",
                event.getRewardId(), event.getCashbackAmount(), wallet.getBalance(),
                wallet.getTotalCashbackEarned());

            // Notify user
            notifyCashbackEarned(event, wallet, oldBalance);

            metricsCollector.incrementCounter("wallet.cashback.earned");
            metricsCollector.recordGauge("wallet.cashback.amount", event.getCashbackAmount().doubleValue());
            metricsCollector.recordHistogram("wallet.cashback.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process cashback earned event", e);
            dlqHandler.sendToDLQ("cashback.earned", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyCashbackEarned(CashbackEarnedEvent event, Wallet wallet, BigDecimal oldBalance) {
        String message = String.format("""
            🎉 You Earned Cashback!

            Congratulations! You've earned cashback on your recent purchase.

            Cashback Details:
            - Cashback Amount: $%s
            - Cashback Rate: %.1f%%
            - Original Purchase: $%s
            - Merchant: %s
            - Category: %s
            - Purchase Date: %s

            Your Wallet:
            - Previous Balance: $%s
            - Cashback Earned: +$%s
            - New Balance: $%s
            - Available Now: $%s

            Lifetime Cashback:
            💰 Total Cashback Earned: $%s
            %s

            How Cashback Works:
            • Earn cashback on eligible purchases
            • Cashback is automatically credited to your wallet
            • No minimum balance or redemption required
            • Use your cashback for any purchase or withdrawal

            %s

            Maximize Your Cashback:
            💡 Earn More:
            • Use your Waqiti card for all purchases
            • Look for bonus cashback categories
            • Shop at featured merchants for extra rewards
            • Refer friends to earn bonus cashback

            💡 Track Your Rewards:
            • View cashback history: https://example.com/rewards/cashback
            • Upcoming cashback: https://example.com/rewards/pending
            • Bonus opportunities: https://example.com/rewards/offers

            Current Cashback Rates:
            %s

            Questions? Contact rewards support:
            Email: cashback@example.com
            Phone: 1-800-WAQITI-REWARDS
            """,
            event.getCashbackAmount(),
            event.getCashbackRate().doubleValue(),
            event.getOriginalPurchaseAmount(),
            event.getMerchantName(),
            event.getMerchantCategory(),
            event.getPurchaseDate().toLocalDate(),
            oldBalance,
            event.getCashbackAmount(),
            wallet.getBalance(),
            wallet.getBalance(),
            wallet.getTotalCashbackEarned(),
            getMilestoneMessage(wallet.getTotalCashbackEarned()),
            getBonusInfo(event.getMerchantCategory(), event.getCashbackRate()),
            getCashbackRateInfo(event.getMerchantCategory()));

        notificationService.sendCashbackEarnedNotification(
            event.getUserId(), event.getWalletId(), event.getCashbackAmount(),
            event.getMerchantName(), message);
    }

    private String getMilestoneMessage(BigDecimal totalCashback) {
        if (totalCashback.compareTo(new BigDecimal("1000")) >= 0) {
            return "🏆 You've earned over $1,000 in lifetime cashback!";
        } else if (totalCashback.compareTo(new BigDecimal("500")) >= 0) {
            return "🌟 You've earned over $500 in lifetime cashback!";
        } else if (totalCashback.compareTo(new BigDecimal("100")) >= 0) {
            return "✨ You've earned over $100 in lifetime cashback!";
        }
        return "";
    }

    private String getBonusInfo(String category, BigDecimal rate) {
        BigDecimal baseRate = new BigDecimal("1.0");
        if (rate.compareTo(baseRate) > 0) {
            return String.format("""
                🎁 Bonus Cashback Active!
                You earned %.1f%% (instead of standard 1%%) because:
                • %s is a featured cashback category this month
                • Keep shopping in this category to maximize rewards!
                """, rate.doubleValue(), category);
        }
        return "";
    }

    private String getCashbackRateInfo(String category) {
        return """
            Standard Rates:
            • Groceries: 2% cashback
            • Gas Stations: 3% cashback
            • Restaurants: 2% cashback
            • Travel: 5% cashback
            • All Other Purchases: 1% cashback

            Bonus Categories (This Month):
            • Check https://example.com/rewards/offers for current bonuses

            Note: Cashback rates are subject to change. Check current rates anytime.
            """;
    }

    private static class CashbackEarnedEvent {
        private UUID rewardId, userId, walletId, originalTransactionId;
        private String merchantName, merchantCategory;
        private BigDecimal cashbackAmount, cashbackRate, originalPurchaseAmount;
        private LocalDateTime purchaseDate, earnedAt;

        public UUID getRewardId() { return rewardId; }
        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public UUID getOriginalTransactionId() { return originalTransactionId; }
        public String getMerchantName() { return merchantName; }
        public String getMerchantCategory() { return merchantCategory; }
        public BigDecimal getCashbackAmount() { return cashbackAmount; }
        public BigDecimal getCashbackRate() { return cashbackRate; }
        public BigDecimal getOriginalPurchaseAmount() { return originalPurchaseAmount; }
        public LocalDateTime getPurchaseDate() { return purchaseDate; }
        public LocalDateTime getEarnedAt() { return earnedAt; }
    }
}
