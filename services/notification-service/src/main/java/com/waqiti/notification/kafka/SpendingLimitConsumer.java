package com.waqiti.notification.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #38: SpendingLimitConsumer
 * Notifies users when spending limits are reached
 * Impact: Budget management, financial wellness
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpendingLimitConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "spending.limit.reached", groupId = "notification-spending-limit")
    public void handle(SpendingLimitEvent event, Acknowledgment ack) {
        try {
            log.warn("⚠️ SPENDING LIMIT REACHED: userId={}, category={}, spent=${}, limit=${}",
                event.getUserId(), event.getCategory(), event.getCurrentSpending(), event.getSpendingLimit());

            String key = "spending:limit:" + event.getUserId() + ":" + event.getCategory() + ":" +
                event.getPeriodStart().toLocalDate();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            BigDecimal percentageUsed = event.getCurrentSpending()
                .divide(event.getSpendingLimit(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            String message = String.format("""
                ⚠️ Spending Limit Alert

                You've reached your spending limit for %s.

                Budget Status:
                - Category: %s
                - Spending Limit: $%s
                - Current Spending: $%s
                - Percentage Used: %.1f%%
                - %s

                Budget Period:
                - Period: %s to %s
                - Days Remaining: %d
                - %s

                Recent Transactions in This Category:
                %s

                %s

                Budget Management Tips:
                💡 Stay on Track:
                • Review your transactions regularly
                • Adjust spending habits if needed
                • Consider increasing limit if it's too restrictive
                • Use cash for discretionary purchases
                • Wait 24 hours before large purchases

                💡 Alternative Strategies:
                • Transfer from another budget category
                • Use a different payment method temporarily
                • Postpone non-essential purchases
                • Look for free or lower-cost alternatives

                Manage Your Budget:
                • View spending details: https://example.com/budget/%s
                • Adjust limits: https://example.com/budget/limits
                • Transaction history: https://example.com/transactions?category=%s
                • Budget insights: https://example.com/budget/insights

                Questions? Contact budget support:
                Email: budget@example.com
                Phone: 1-800-WAQITI-BUDGET
                """,
                event.getCategory(),
                event.getCategory(),
                event.getSpendingLimit(),
                event.getCurrentSpending(),
                percentageUsed,
                getOverageStatus(event.getCurrentSpending(), event.getSpendingLimit()),
                event.getPeriodStart().toLocalDate(),
                event.getPeriodEnd().toLocalDate(),
                getDaysRemaining(event.getPeriodEnd()),
                getPeriodProgress(event.getPeriodStart(), event.getPeriodEnd()),
                getRecentTransactions(event.getRecentTransactionCount(), event.getLastTransactionAmount(),
                    event.getLastTransactionMerchant()),
                getRecommendations(event.getCategory(), percentageUsed, getDaysRemaining(event.getPeriodEnd())),
                event.getCategory(),
                event.getCategory());

            // Notification priority based on overage
            NotificationPriority priority = percentageUsed.compareTo(BigDecimal.valueOf(100)) > 0
                ? NotificationPriority.HIGH : NotificationPriority.MEDIUM;

            notificationService.sendNotification(event.getUserId(), NotificationType.SPENDING_LIMIT_REACHED,
                NotificationChannel.EMAIL, priority,
                String.format("Budget Alert: %s Spending Limit Reached", event.getCategory()), message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.SPENDING_LIMIT_REACHED,
                NotificationChannel.PUSH, priority,
                "Budget Alert",
                String.format("You've reached your %s spending limit ($%s). %s left in budget period.",
                    event.getCategory(), event.getSpendingLimit(),
                    getDaysRemaining(event.getPeriodEnd()) + " days"), Map.of());

            metricsCollector.incrementCounter("notification.spending.limit.reached.sent");
            metricsCollector.incrementCounter("notification.spending.limit.reached." +
                event.getCategory().toLowerCase().replace(" ", "_"));
            metricsCollector.recordGauge("spending.limit.percentage", percentageUsed.doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process spending limit event", e);
            dlqHandler.sendToDLQ("spending.limit.reached", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getOverageStatus(BigDecimal currentSpending, BigDecimal limit) {
        BigDecimal overage = currentSpending.subtract(limit);
        if (overage.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("⚠️ OVER LIMIT by $%s", overage);
        } else if (overage.compareTo(BigDecimal.ZERO) == 0) {
            return "✅ Exactly at limit";
        } else {
            return String.format("✅ Within limit ($%s remaining)", overage.abs());
        }
    }

    private long getDaysRemaining(LocalDateTime periodEnd) {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), periodEnd);
    }

    private String getPeriodProgress(LocalDateTime periodStart, LocalDateTime periodEnd) {
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd);
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(periodStart, LocalDateTime.now());
        long percentageElapsed = (elapsedDays * 100) / totalDays;

        return String.format("%d%% of budget period elapsed", percentageElapsed);
    }

    private String getRecentTransactions(int count, BigDecimal lastAmount, String lastMerchant) {
        if (lastMerchant != null) {
            return String.format("""
                - Total transactions: %d
                - Most recent: $%s at %s
                - View all: https://example.com/transactions
                """, count, lastAmount, lastMerchant);
        }
        return String.format("Total transactions in this category: %d", count);
    }

    private String getRecommendations(String category, BigDecimal percentageUsed, long daysRemaining) {
        if (percentageUsed.compareTo(BigDecimal.valueOf(100)) > 0) {
            return String.format("""
                ⚠️ You're Over Budget:
                You've exceeded your %s budget. Consider:
                • Reviewing recent charges for errors
                • Cutting back on spending in this category
                • Increasing your budget limit if necessary
                • Using funds from other budget categories
                """, category);
        } else if (daysRemaining > 7) {
            return String.format("""
                💡 You've Hit Your Limit Early:
                With %d days left in the period, try to:
                • Avoid additional spending in this category
                • Use alternative categories when possible
                • Plan ahead for next period
                • Consider adjusting your budget allocation
                """, daysRemaining);
        } else {
            return String.format("""
                💡 Almost There:
                Only %d days left in this budget period!
                • You're close to the end of the period
                • Your budget resets soon
                • Try to hold off on new purchases
                • Plan your next period's budget now
                """, daysRemaining);
        }
    }

    private static class SpendingLimitEvent {
        private UUID userId;
        private String category, lastTransactionMerchant;
        private BigDecimal spendingLimit, currentSpending, lastTransactionAmount;
        private int recentTransactionCount;
        private LocalDateTime periodStart, periodEnd;

        public UUID getUserId() { return userId; }
        public String getCategory() { return category; }
        public String getLastTransactionMerchant() { return lastTransactionMerchant; }
        public BigDecimal getSpendingLimit() { return spendingLimit; }
        public BigDecimal getCurrentSpending() { return currentSpending; }
        public BigDecimal getLastTransactionAmount() { return lastTransactionAmount; }
        public int getRecentTransactionCount() { return recentTransactionCount; }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
    }
}
