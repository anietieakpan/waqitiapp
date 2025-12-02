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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #50: CreditScoreUpdatedConsumer
 * Notifies users when their credit score is updated
 * Impact: Financial awareness, credit monitoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditScoreUpdatedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "credit.score.updated", groupId = "notification-credit-score-updated")
    public void handle(CreditScoreUpdatedEvent event, Acknowledgment ack) {
        try {
            log.info("📊 CREDIT SCORE UPDATED: userId={}, score={}, change={}, bureau={}",
                event.getUserId(), event.getNewScore(), event.getScoreChange(), event.getCreditBureau());

            String key = "credit:score:updated:" + event.getUserId() + ":" + event.getUpdatedAt().toLocalDate();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                📊 Your Credit Score Has Been Updated

                Your credit score has changed since your last update.

                Credit Score:
                - Current Score: %d
                - Previous Score: %d
                - Change: %s (%d points)
                - Credit Bureau: %s
                - Updated: %s
                - Score Range: %s

                %s

                Score Rating:
                %s

                Factors Affecting Your Score:
                %s

                %s

                What Your Score Means:
                %s

                Credit Score Ranges:
                📊 Credit Score Guide:
                • 800-850: Exceptional ⭐⭐⭐⭐⭐
                • 740-799: Very Good ⭐⭐⭐⭐
                • 670-739: Good ⭐⭐⭐
                • 580-669: Fair ⭐⭐
                • 300-579: Poor ⭐

                Your score of %d is in the %s range.

                How to Improve Your Credit Score:
                💡 Top Strategies:
                • Pay all bills on time (35%% of score)
                • Keep credit utilization below 30%% (30%% of score)
                • Don't close old credit accounts (15%% of score)
                • Limit new credit applications (10%% of score)
                • Maintain diverse credit mix (10%% of score)

                💡 Quick Wins:
                • Set up autopay for all bills
                • Pay down credit card balances
                • Dispute any errors on credit report
                • Become authorized user on old account
                • Ask for credit limit increases

                💡 Long-term Building:
                • Keep accounts open and active
                • Use credit regularly but responsibly
                • Monitor credit report quarterly
                • Maintain low debt-to-income ratio

                Credit Monitoring:
                ✅ What We Track:
                • Monthly credit score updates
                • New accounts opened in your name
                • Credit inquiries
                • Payment history changes
                • Credit utilization changes
                • Public records (bankruptcies, liens)

                🚨 Identity Theft Protection:
                We'll alert you if we detect:
                • New accounts you didn't open
                • Hard inquiries you didn't authorize
                • Address changes you didn't make
                • Sudden score drops

                View Full Credit Report:
                https://example.com/credit/report

                Credit Score History:
                https://example.com/credit/history

                Credit Simulator:
                See how actions affect your score:
                https://example.com/credit/simulator

                Questions? Contact credit monitoring support:
                Email: credit@example.com
                Phone: 1-800-WAQITI-CREDIT

                Important Information:
                Credit scores are calculated by credit bureaus using your credit history.
                Scores may vary slightly between bureaus due to different reporting.
                Your score updates approximately monthly as new information is reported.

                Powered by %s
                """,
                event.getNewScore(),
                event.getPreviousScore(),
                getScoreChangeDirection(event.getScoreChange()),
                Math.abs(event.getScoreChange()),
                event.getCreditBureau(),
                event.getUpdatedAt().toLocalDate(),
                getScoreRange(event.getCreditBureau()),
                getScoreChangeMessage(event.getScoreChange()),
                getRatingDescription(event.getNewScore()),
                formatFactors(event.getPrimaryFactors()),
                getRecommendations(event.getNewScore(), event.getScoreChange(), event.getPrimaryFactors()),
                getScoreMeaningDetail(event.getNewScore()),
                event.getNewScore(),
                getRatingCategory(event.getNewScore()),
                event.getCreditBureau());

            // Priority based on score change
            NotificationPriority priority = Math.abs(event.getScoreChange()) >= 20
                ? NotificationPriority.HIGH : NotificationPriority.MEDIUM;

            notificationService.sendNotification(event.getUserId(), NotificationType.CREDIT_SCORE_UPDATED,
                NotificationChannel.EMAIL, priority,
                String.format("Credit Score Update: %d (%s%d)", event.getNewScore(),
                    event.getScoreChange() >= 0 ? "+" : "", event.getScoreChange()),
                message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.CREDIT_SCORE_UPDATED,
                NotificationChannel.PUSH, priority,
                "Credit Score Updated",
                String.format("Your credit score is now %d (%s%d points). Tap to view details.",
                    event.getNewScore(), event.getScoreChange() >= 0 ? "+" : "", event.getScoreChange()),
                Map.of());

            metricsCollector.incrementCounter("notification.credit.score.updated.sent");
            metricsCollector.recordGauge("credit.score", event.getNewScore());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process credit score updated event", e);
            dlqHandler.sendToDLQ("credit.score.updated", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getScoreChangeDirection(int change) {
        if (change > 0) return "⬆️ Increased";
        if (change < 0) return "⬇️ Decreased";
        return "➡️ No change";
    }

    private String getScoreChangeMessage(int change) {
        if (change > 20) {
            return """
                🎉 Significant Improvement!
                Your credit score increased significantly! This is excellent progress.
                Keep up the good habits that led to this improvement.
                """;
        } else if (change > 0) {
            return """
                ✅ Positive Progress!
                Your credit score improved. You're moving in the right direction.
                """;
        } else if (change < -20) {
            return """
                ⚠️ Significant Decrease
                Your credit score dropped significantly. Review the factors below and
                take action to address any negative items.
                """;
        } else if (change < 0) {
            return """
                ⚠️ Slight Decrease
                Your credit score decreased slightly. This is normal and may be due to
                recent credit activity. Monitor for any unexpected changes.
                """;
        } else {
            return """
                ➡️ Steady Score
                Your credit score remained stable this month.
                """;
        }
    }

    private String getRatingDescription(int score) {
        if (score >= 800) return "Exceptional - You have excellent credit!";
        if (score >= 740) return "Very Good - You qualify for favorable rates";
        if (score >= 670) return "Good - You're above average";
        if (score >= 580) return "Fair - Room for improvement";
        return "Poor - Focus on rebuilding credit";
    }

    private String getRatingCategory(int score) {
        if (score >= 800) return "Exceptional";
        if (score >= 740) return "Very Good";
        if (score >= 670) return "Good";
        if (score >= 580) return "Fair";
        return "Poor";
    }

    private String getScoreRange(String bureau) {
        return "300-850 (FICO Score)";
    }

    private String formatFactors(String factors) {
        if (factors == null || factors.isEmpty()) {
            return "No specific factors reported this month.";
        }
        return factors;
    }

    private String getScoreMeaningDetail(int score) {
        if (score >= 740) {
            return """
                With your score, you can:
                • Qualify for the best interest rates
                • Get approved for premium credit cards
                • Secure favorable loan terms
                • Negotiate better rates with lenders
                • Rent apartments easily
                """;
        } else if (score >= 670) {
            return """
                With your score, you can:
                • Qualify for most loans and credit cards
                • Get reasonable interest rates
                • Rent apartments with ease
                • Some room to negotiate rates
                """;
        } else if (score >= 580) {
            return """
                With your score, you may:
                • Qualify for some loans with higher rates
                • Need cosigners for major purchases
                • Face higher insurance premiums
                • Have limited credit card options
                """;
        } else {
            return """
                With your score:
                • Loan approval may be difficult
                • Interest rates will be very high
                • Secured cards may be best option
                • Focus on credit rebuilding strategies
                """;
        }
    }

    private String getRecommendations(int score, int change, String factors) {
        if (score >= 740 && change >= 0) {
            return """
                💡 Maintain Your Excellent Credit:
                • Continue current payment habits
                • Keep utilization low
                • Monitor for identity theft
                • Consider credit line increases
                """;
        } else if (change < -10) {
            return """
                ⚠️ Take Action Now:
                • Review what caused the drop
                • Check credit report for errors
                • Catch up on any late payments
                • Reduce credit card balances
                """;
        } else {
            return """
                💡 Improve Your Score:
                • Focus on on-time payments
                • Pay down revolving debt
                • Avoid new credit applications
                • Keep old accounts open
                """;
        }
    }

    private static class CreditScoreUpdatedEvent {
        private UUID userId;
        private int newScore, previousScore, scoreChange;
        private String creditBureau, primaryFactors;
        private LocalDateTime updatedAt;

        public UUID getUserId() { return userId; }
        public int getNewScore() { return newScore; }
        public int getPreviousScore() { return previousScore; }
        public int getScoreChange() { return scoreChange; }
        public String getCreditBureau() { return creditBureau; }
        public String getPrimaryFactors() { return primaryFactors; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}
