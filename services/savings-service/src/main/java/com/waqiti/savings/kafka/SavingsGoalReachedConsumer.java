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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * CRITICAL FIX #31: SavingsGoalReachedConsumer
 * Celebrates users when they reach their savings goals
 * Impact: Increases user engagement and satisfaction
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SavingsGoalReachedConsumer {
    private final SavingsNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "savings.goal.reached", groupId = "savings-goal-reached-notification")
    public void handle(SavingsGoalReachedEvent event, Acknowledgment ack) {
        try {
            log.info("🎉 SAVINGS GOAL REACHED: userId={}, goalName={}, amount=${}, daysToComplete={}",
                event.getUserId(), event.getGoalName(), event.getGoalAmount(),
                ChronoUnit.DAYS.between(event.getStartDate(), event.getReachedAt()));

            String key = "savings:goal:reached:" + event.getGoalId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            long daysToComplete = ChronoUnit.DAYS.between(event.getStartDate(), event.getReachedAt());
            BigDecimal averageMonthlySavings = event.getGoalAmount()
                .divide(BigDecimal.valueOf(Math.max(1, daysToComplete / 30)), 2, java.math.RoundingMode.HALF_UP);

            String message = String.format("""
                🎉 Congratulations! You've Reached Your Savings Goal!

                Your hard work and dedication have paid off!

                Goal Details:
                - Goal Name: %s
                - Target Amount: $%s
                - Current Balance: $%s
                - %s

                Your Savings Journey:
                - Started: %s
                - Completed: %s
                - Time Taken: %d days (%s)
                - Total Contributions: %d
                - Average Monthly Savings: $%s

                %s

                Celebration Stats:
                %s

                What's Next?
                %s

                Keep the Momentum Going:
                ✅ Set a new savings goal
                ✅ Increase your monthly contributions
                ✅ Explore investment opportunities
                ✅ Share your success with friends

                View Your Savings:
                https://example.com/savings/goals/%s

                You're building a strong financial future! 💪

                Questions? Contact savings support:
                Email: savings@example.com
                Phone: 1-800-WAQITI-SAVE
                """,
                event.getGoalName(),
                event.getGoalAmount(),
                event.getCurrentBalance(),
                event.getCurrentBalance().compareTo(event.getGoalAmount()) > 0
                    ? String.format("🌟 You exceeded your goal by $%s!",
                        event.getCurrentBalance().subtract(event.getGoalAmount()))
                    : "✅ Goal achieved!",
                event.getStartDate().toLocalDate(),
                event.getReachedAt().toLocalDate(),
                daysToComplete,
                formatDuration(daysToComplete),
                event.getContributionCount(),
                averageMonthlySavings,
                getMotivationalMessage(event.getGoalType(), event.getGoalAmount()),
                getCelebrationStats(event.getCompletedGoalsCount(), event.getTotalSaved()),
                getNextSteps(event.getGoalType(), event.getCurrentBalance()),
                event.getGoalId());

            notificationService.sendGoalReachedNotification(
                event.getUserId(), event.getGoalId(), event.getGoalName(),
                event.getGoalAmount(), message);

            metricsCollector.incrementCounter("savings.goal.reached.notification.sent");
            metricsCollector.incrementCounter("savings.goal.reached." +
                event.getGoalType().toLowerCase().replace(" ", "_"));
            metricsCollector.recordGauge("savings.goal.amount", event.getGoalAmount().doubleValue());
            metricsCollector.recordHistogram("savings.goal.days_to_complete", daysToComplete);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process savings goal reached event", e);
            dlqHandler.sendToDLQ("savings.goal.reached", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String formatDuration(long days) {
        if (days < 30) {
            return String.format("%d day%s", days, days == 1 ? "" : "s");
        } else if (days < 365) {
            long months = days / 30;
            return String.format("%d month%s", months, months == 1 ? "" : "s");
        } else {
            long years = days / 365;
            long months = (days % 365) / 30;
            return String.format("%d year%s, %d month%s",
                years, years == 1 ? "" : "s",
                months, months == 1 ? "" : "s");
        }
    }

    private String getMotivationalMessage(String goalType, BigDecimal amount) {
        return switch (goalType.toLowerCase()) {
            case "emergency_fund" ->
                String.format("""
                    💰 Financial Security Milestone:
                    You now have an emergency fund of $%s! This provides crucial protection
                    against unexpected expenses. Financial experts recommend 3-6 months of expenses.
                    """, amount);
            case "vacation" ->
                String.format("""
                    ✈️ Vacation Dreams Coming True:
                    You've saved $%s for your vacation! Time to start planning that trip
                    you've been dreaming about. You earned it!
                    """, amount);
            case "down_payment" ->
                String.format("""
                    🏠 Homeownership Goal Achieved:
                    You've saved $%s toward your down payment! This is a huge step toward
                    homeownership. Consider speaking with a mortgage advisor.
                    """, amount);
            case "retirement" ->
                String.format("""
                    🌅 Retirement Milestone:
                    You've reached $%s in retirement savings! Compound interest will continue
                    working in your favor. Consider increasing contributions to accelerate growth.
                    """, amount);
            case "education" ->
                String.format("""
                    📚 Education Investment:
                    You've saved $%s for education! Investing in education is one of the best
                    investments you can make in your future.
                    """, amount);
            default ->
                String.format("""
                    🎯 Goal Achieved:
                    You've successfully saved $%s! Your financial discipline is paying off.
                    """, amount);
        };
    }

    private String getCelebrationStats(int completedGoals, BigDecimal totalSaved) {
        StringBuilder stats = new StringBuilder();

        if (completedGoals > 1) {
            stats.append(String.format("🏆 This is your %s completed savings goal!\n",
                getOrdinal(completedGoals)));
        }

        if (totalSaved.compareTo(BigDecimal.ZERO) > 0) {
            stats.append(String.format("💵 Total saved across all goals: $%s\n", totalSaved));
        }

        if (completedGoals >= 5) {
            stats.append("⭐ Super Saver Badge Unlocked!\n");
        } else if (completedGoals >= 3) {
            stats.append("🌟 Consistent Saver Badge Unlocked!\n");
        }

        return stats.length() > 0 ? stats.toString() : "This is your first completed savings goal! 🎉";
    }

    private String getNextSteps(String goalType, BigDecimal currentBalance) {
        String baseSteps = String.format("""
            1. Celebrate your achievement! You earned it! 🎉
            2. Set a new, more ambitious goal
            3. Consider investing this money for growth
            4. Current balance: $%s - keep it working for you
            """, currentBalance);

        String typeSpecificSteps = switch (goalType.toLowerCase()) {
            case "emergency_fund" ->
                "\n5. Make sure this stays in a liquid, accessible account\n6. Only use for true emergencies";
            case "vacation" ->
                "\n5. Start planning your trip!\n6. Set up a new goal for your next adventure";
            case "down_payment" ->
                "\n5. Connect with a mortgage lender\n6. Start house hunting!";
            case "retirement" ->
                "\n5. Review your retirement plan with an advisor\n6. Consider increasing your contribution rate";
            default ->
                "\n5. Evaluate your next financial priority\n6. Keep building your wealth";
        };

        return baseSteps + typeSpecificSteps;
    }

    private String getOrdinal(int number) {
        if (number % 100 >= 11 && number % 100 <= 13) {
            return number + "th";
        }
        return switch (number % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }

    private static class SavingsGoalReachedEvent {
        private UUID userId, goalId;
        private String goalName, goalType;
        private BigDecimal goalAmount, currentBalance, totalSaved;
        private int contributionCount, completedGoalsCount;
        private LocalDateTime startDate, targetDate, reachedAt;

        public UUID getUserId() { return userId; }
        public UUID getGoalId() { return goalId; }
        public String getGoalName() { return goalName; }
        public String getGoalType() { return goalType; }
        public BigDecimal getGoalAmount() { return goalAmount; }
        public BigDecimal getCurrentBalance() { return currentBalance; }
        public BigDecimal getTotalSaved() { return totalSaved; }
        public int getContributionCount() { return contributionCount; }
        public int getCompletedGoalsCount() { return completedGoalsCount; }
        public LocalDateTime getStartDate() { return startDate; }
        public LocalDateTime getTargetDate() { return targetDate; }
        public LocalDateTime getReachedAt() { return reachedAt; }
    }
}
