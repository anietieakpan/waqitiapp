package com.waqiti.savings.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.savings.service.SavingsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #47: RecurringDepositConsumer
 * Notifies users when recurring deposits to savings are scheduled
 * Impact: Savings automation transparency, financial wellness
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringDepositConsumer {
    private final SavingsNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "recurring.deposit.scheduled", groupId = "savings-recurring-deposit")
    public void handle(RecurringDepositEvent event, Acknowledgment ack) {
        try {
            log.info("🔄 RECURRING DEPOSIT SCHEDULED: userId={}, amount=${}, frequency={}, goalName={}",
                event.getUserId(), event.getDepositAmount(), event.getFrequency(), event.getGoalName());

            String key = "recurring:deposit:" + event.getRecurringDepositId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                🔄 Automatic Savings Activated!

                Your recurring deposit has been set up successfully.

                Deposit Details:
                - Amount: $%s
                - Frequency: %s
                - Savings Goal: %s
                - Source Account: %s
                - First Deposit: %s
                - Status: Active

                Projected Savings:
                %s

                What Happens Now:
                • Funds will automatically transfer %s
                • You'll receive a confirmation after each deposit
                • No manual action needed from you
                • You can modify or cancel anytime

                Your Savings Plan:
                - Goal Amount: %s
                - Current Saved: $%s
                - Remaining: $%s
                - Projected Completion: %s

                %s

                Benefits of Automatic Savings:
                💰 Build Wealth Effortlessly:
                • Pay yourself first automatically
                • Never forget to save
                • Build consistent savings habit
                • Reach goals faster

                💰 Smart Money Management:
                • Dollar-cost averaging for investments
                • Emergency fund protection
                • Stress-free saving
                • Financial security

                Savings Tips:
                💡 Maximize Your Savings:
                • Start small and increase gradually
                • Align deposits with payday
                • Round up purchases for extra savings
                • Set specific goals for motivation
                • Review progress monthly

                💡 Common Strategies:
                • 50/30/20 rule (20%% to savings)
                • Save first, spend what's left
                • Automate before you see it
                • Treat savings like a bill

                Manage Recurring Deposits:
                • View schedule: https://example.com/savings/recurring
                • Modify amount or frequency
                • Pause temporarily
                • Cancel anytime (no penalties)

                Track Your Progress:
                • Goal dashboard: https://example.com/savings/goals/%s
                • Deposit history
                • Savings milestones
                • Achievement badges

                Questions? Contact savings support:
                Email: savings@example.com
                Phone: 1-800-WAQITI-SAVE
                Reference: Deposit ID %s
                """,
                event.getDepositAmount(),
                getFrequencyDescription(event.getFrequency()),
                event.getGoalName() != null ? event.getGoalName() : "General Savings",
                event.getSourceAccountName(),
                event.getNextDepositDate().toLocalDate(),
                getProjectedSavings(event.getDepositAmount(), event.getFrequency()),
                getFrequencyDescription(event.getFrequency()).toLowerCase(),
                event.getGoalAmount() != null ? "$" + event.getGoalAmount() : "No target set",
                event.getCurrentSaved(),
                event.getGoalAmount() != null
                    ? "$" + event.getGoalAmount().subtract(event.getCurrentSaved())
                    : "N/A",
                event.getProjectedCompletionDate() != null
                    ? event.getProjectedCompletionDate().toLocalDate().toString()
                    : "Based on your pace",
                getMotivationalMessage(event.getDepositAmount(), event.getFrequency()),
                event.getGoalId(),
                event.getRecurringDepositId());

            notificationService.sendRecurringDepositNotification(
                event.getUserId(), event.getGoalId(), event.getDepositAmount(),
                event.getFrequency(), message);

            metricsCollector.incrementCounter("savings.recurring.deposit.scheduled");
            metricsCollector.recordGauge("savings.recurring.amount", event.getDepositAmount().doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process recurring deposit event", e);
            dlqHandler.sendToDLQ("recurring.deposit.scheduled", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getFrequencyDescription(String frequency) {
        return switch (frequency.toLowerCase()) {
            case "daily" -> "Every day";
            case "weekly" -> "Every week";
            case "biweekly" -> "Every 2 weeks";
            case "monthly" -> "Every month";
            case "quarterly" -> "Every 3 months";
            default -> frequency;
        };
    }

    private String getProjectedSavings(BigDecimal amount, String frequency) {
        BigDecimal perMonth, perYear;

        switch (frequency.toLowerCase()) {
            case "daily" -> {
                perMonth = amount.multiply(BigDecimal.valueOf(30));
                perYear = amount.multiply(BigDecimal.valueOf(365));
            }
            case "weekly" -> {
                perMonth = amount.multiply(BigDecimal.valueOf(4.33));
                perYear = amount.multiply(BigDecimal.valueOf(52));
            }
            case "biweekly" -> {
                perMonth = amount.multiply(BigDecimal.valueOf(2.17));
                perYear = amount.multiply(BigDecimal.valueOf(26));
            }
            case "monthly" -> {
                perMonth = amount;
                perYear = amount.multiply(BigDecimal.valueOf(12));
            }
            case "quarterly" -> {
                perMonth = amount.divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
                perYear = amount.multiply(BigDecimal.valueOf(4));
            }
            default -> {
                perMonth = BigDecimal.ZERO;
                perYear = BigDecimal.ZERO;
            }
        }

        return String.format("""
            • Per Month: $%s
            • Per Year: $%s
            • In 5 Years: $%s
            • In 10 Years: $%s (not including interest)
            """,
            perMonth.setScale(2, java.math.RoundingMode.HALF_UP),
            perYear.setScale(2, java.math.RoundingMode.HALF_UP),
            perYear.multiply(BigDecimal.valueOf(5)).setScale(2, java.math.RoundingMode.HALF_UP),
            perYear.multiply(BigDecimal.valueOf(10)).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private String getMotivationalMessage(BigDecimal amount, String frequency) {
        BigDecimal yearlyAmount = switch (frequency.toLowerCase()) {
            case "daily" -> amount.multiply(BigDecimal.valueOf(365));
            case "weekly" -> amount.multiply(BigDecimal.valueOf(52));
            case "biweekly" -> amount.multiply(BigDecimal.valueOf(26));
            case "monthly" -> amount.multiply(BigDecimal.valueOf(12));
            case "quarterly" -> amount.multiply(BigDecimal.valueOf(4));
            default -> BigDecimal.ZERO;
        };

        return String.format("""
            🎯 You're on Track!
            By saving $%s %s, you'll save $%s per year.
            That's a significant step toward financial security!

            Small amounts add up to big results. Keep going! 💪
            """,
            amount,
            frequency.toLowerCase(),
            yearlyAmount.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private static class RecurringDepositEvent {
        private UUID userId, recurringDepositId, goalId;
        private String goalName, frequency, sourceAccountName;
        private BigDecimal depositAmount, currentSaved, goalAmount;
        private LocalDateTime nextDepositDate, projectedCompletionDate;

        public UUID getUserId() { return userId; }
        public UUID getRecurringDepositId() { return recurringDepositId; }
        public UUID getGoalId() { return goalId; }
        public String getGoalName() { return goalName; }
        public String getFrequency() { return frequency; }
        public String getSourceAccountName() { return sourceAccountName; }
        public BigDecimal getDepositAmount() { return depositAmount; }
        public BigDecimal getCurrentSaved() { return currentSaved; }
        public BigDecimal getGoalAmount() { return goalAmount; }
        public LocalDateTime getNextDepositDate() { return nextDepositDate; }
        public LocalDateTime getProjectedCompletionDate() { return projectedCompletionDate; }
    }
}
