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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #32: FraudAlertClearedConsumer
 * Notifies users when fraud alerts are cleared and accounts restored
 * Impact: Reduces customer support calls, improves user experience
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudAlertClearedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "fraud.alert.cleared", groupId = "notification-fraud-alert-cleared")
    public void handle(FraudAlertClearedEvent event, Acknowledgment ack) {
        try {
            log.info("✅ FRAUD ALERT CLEARED: userId={}, alertId={}, resolution={}",
                event.getUserId(), event.getAlertId(), event.getResolution());

            String key = "fraud:alert:cleared:" + event.getAlertId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            long hoursBlocked = ChronoUnit.HOURS.between(event.getAlertCreatedAt(), event.getClearedAt());

            String message = String.format("""
                ✅ Fraud Alert Cleared - Account Restored

                Good news! The fraud alert on your account has been cleared.

                Alert Details:
                - Alert Type: %s
                - Original Alert Date: %s
                - Cleared Date: %s
                - Duration: %s
                - Resolution: %s

                Original Transaction:
                - Amount: $%s
                - Merchant: %s
                - Date: %s
                - Status: %s

                Resolution Details:
                %s

                Your Account Status:
                %s

                %s

                Security Recommendations:
                • Review your recent transactions for any unauthorized activity
                • Enable two-factor authentication if not already active
                • Set up transaction alerts for real-time monitoring
                • Update your password regularly
                • Monitor your account for unusual activity

                If You Notice Suspicious Activity:
                🚨 Report immediately:
                Email: fraud@example.com
                Phone: 1-800-WAQITI-FRAUD (24/7)

                Thank You:
                Thank you for your patience while we investigated this alert.
                Your security is our top priority.

                Questions? Contact fraud prevention:
                Email: fraud@example.com
                Phone: 1-800-WAQITI-FRAUD
                Reference: Alert ID %s
                """,
                event.getAlertType(),
                event.getAlertCreatedAt(),
                event.getClearedAt(),
                formatDuration(hoursBlocked),
                event.getResolution(),
                event.getTransactionAmount(),
                event.getMerchantName(),
                event.getTransactionDate(),
                event.getTransactionStatus(),
                getResolutionExplanation(event.getResolutionCode()),
                getAccountStatus(event.isAccountReactivated(), event.isCardReactivated()),
                getNextSteps(event.getResolutionCode()),
                event.getAlertId());

            // Multi-channel notification
            notificationService.sendNotification(event.getUserId(), NotificationType.FRAUD_ALERT_CLEARED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Fraud Alert Cleared - Account Restored", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.FRAUD_ALERT_CLEARED,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                "Fraud Alert Cleared",
                String.format("Good news! Your fraud alert has been cleared. Your account is now %s.",
                    event.isAccountReactivated() ? "fully restored" : "partially restricted"), Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.FRAUD_ALERT_CLEARED,
                NotificationChannel.SMS, NotificationPriority.MEDIUM, null,
                String.format("Waqiti: Fraud alert cleared. Account status: %s. Check email for details.",
                    event.isAccountReactivated() ? "Active" : "Review required"), Map.of());

            metricsCollector.incrementCounter("notification.fraud.alert.cleared.sent");
            metricsCollector.incrementCounter("notification.fraud.alert.cleared." +
                event.getResolutionCode().toLowerCase().replace(" ", "_"));
            metricsCollector.recordHistogram("fraud.alert.duration.hours", hoursBlocked);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process fraud alert cleared event", e);
            dlqHandler.sendToDLQ("fraud.alert.cleared", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String formatDuration(long hours) {
        if (hours < 24) {
            return String.format("%d hour%s", hours, hours == 1 ? "" : "s");
        } else {
            long days = hours / 24;
            long remainingHours = hours % 24;
            if (remainingHours == 0) {
                return String.format("%d day%s", days, days == 1 ? "" : "s");
            }
            return String.format("%d day%s, %d hour%s",
                days, days == 1 ? "" : "s",
                remainingHours, remainingHours == 1 ? "" : "s");
        }
    }

    private String getResolutionExplanation(String resolutionCode) {
        return switch (resolutionCode.toLowerCase()) {
            case "user_verified_transaction" ->
                """
                You verified that this transaction was authorized. The alert has been cleared
                and the transaction has been approved.
                """;
            case "false_positive" ->
                """
                Our fraud detection system flagged this transaction, but after review, we've
                determined it was legitimate. We apologize for any inconvenience.
                """;
            case "fraud_confirmed_refunded" ->
                """
                ⚠️ Fraud was confirmed. The unauthorized transaction has been reversed and
                funds have been returned to your account. Your card has been replaced.
                """;
            case "insufficient_evidence" ->
                """
                After investigation, there was insufficient evidence of fraud. The transaction
                has been approved. If you believe this is incorrect, please contact support.
                """;
            case "merchant_verification" ->
                """
                We contacted the merchant and verified the transaction was legitimate.
                The alert has been cleared.
                """;
            case "user_dispute_resolved" ->
                """
                Your dispute has been resolved in your favor. Any holds have been removed
                and your account is fully restored.
                """;
            default ->
                "The fraud investigation has been completed and the alert has been cleared.";
        };
    }

    private String getAccountStatus(boolean accountReactivated, boolean cardReactivated) {
        StringBuilder status = new StringBuilder();

        if (accountReactivated && cardReactivated) {
            status.append("✅ Your account and card are FULLY ACTIVE\n");
            status.append("• All transactions are enabled\n");
            status.append("• No restrictions in place\n");
            status.append("• You can use your account normally");
        } else if (accountReactivated && !cardReactivated) {
            status.append("⚠️ Your account is ACTIVE but card remains blocked\n");
            status.append("• Account transfers and payments: ✅ Enabled\n");
            status.append("• Card transactions: ❌ Blocked\n");
            status.append("• A replacement card is being sent to you");
        } else if (!accountReactivated && cardReactivated) {
            status.append("⚠️ Your card is ACTIVE but account has restrictions\n");
            status.append("• Card transactions: ✅ Enabled\n");
            status.append("• Some account features: ❌ Limited\n");
            status.append("• Contact support to fully restore account");
        } else {
            status.append("⚠️ Your account requires additional verification\n");
            status.append("• Most features remain restricted\n");
            status.append("• Please contact support to complete verification\n");
            status.append("• Phone: 1-800-WAQITI-FRAUD");
        }

        return status.toString();
    }

    private String getNextSteps(String resolutionCode) {
        if ("fraud_confirmed_refunded".equals(resolutionCode)) {
            return """
                Next Steps:
                1. ✅ Funds have been refunded to your account
                2. 📬 A new card is being mailed to you (arrives in 5-7 business days)
                3. 🔒 Review your account for other unauthorized transactions
                4. 🔐 Update your password and enable 2FA
                5. 📧 File a police report if you believe identity theft occurred
                """;
        } else if ("user_verified_transaction".equals(resolutionCode) ||
                   "false_positive".equals(resolutionCode)) {
            return """
                Next Steps:
                1. ✅ No action needed - your account is fully restored
                2. 💡 Consider setting custom transaction limits to reduce false alerts
                3. 🌍 Notify us of travel plans to prevent future blocks
                4. 📱 Enable transaction notifications for real-time monitoring
                """;
        } else {
            return """
                Next Steps:
                1. Review your account activity thoroughly
                2. Report any suspicious transactions immediately
                3. Keep your contact information up to date
                4. Monitor your account regularly
                """;
        }
    }

    private static class FraudAlertClearedEvent {
        private UUID userId, alertId, transactionId;
        private String alertType, resolution, resolutionCode;
        private String merchantName, transactionStatus;
        private BigDecimal transactionAmount;
        private LocalDateTime alertCreatedAt, transactionDate, clearedAt;
        private boolean accountReactivated, cardReactivated;

        public UUID getUserId() { return userId; }
        public UUID getAlertId() { return alertId; }
        public UUID getTransactionId() { return transactionId; }
        public String getAlertType() { return alertType; }
        public String getResolution() { return resolution; }
        public String getResolutionCode() { return resolutionCode; }
        public String getMerchantName() { return merchantName; }
        public String getTransactionStatus() { return transactionStatus; }
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public LocalDateTime getAlertCreatedAt() { return alertCreatedAt; }
        public LocalDateTime getTransactionDate() { return transactionDate; }
        public LocalDateTime getClearedAt() { return clearedAt; }
        public boolean isAccountReactivated() { return accountReactivated; }
        public boolean isCardReactivated() { return cardReactivated; }
    }
}
