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
 * CRITICAL FIX #22: CardDeclinedConsumer
 * Notifies users immediately when card transactions are declined
 * Impact: Improves UX, reduces customer support burden
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardDeclinedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "card.declined.insufficient_funds", groupId = "notification-card-declined")
    public void handle(CardDeclinedEvent event, Acknowledgment ack) {
        try {
            log.info("💳 CARD DECLINED: userId={}, amount=${}, merchant={}, reason={}",
                event.getUserId(), event.getAmount(), event.getMerchantName(), event.getDeclineReason());

            String key = "card:declined:" + event.getTransactionId();
            if (!idempotencyService.tryAcquire(key, Duration.ofMinutes(5))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Your card transaction was declined.

                Transaction Details:
                - Merchant: %s
                - Amount: $%s
                - Card: %s
                - Date/Time: %s
                - Location: %s

                Decline Reason: %s

                %s

                What You Can Do:
                %s

                Questions? Contact support@example.com | 1-800-WAQITI
                """,
                event.getMerchantName(),
                event.getAmount(),
                maskCardNumber(event.getCardNumber()),
                event.getAttemptedAt(),
                event.getMerchantLocation() != null ? event.getMerchantLocation() : "Online",
                event.getDeclineReason(),
                getDeclineExplanation(event.getDeclineCode()),
                getResolutionSteps(event.getDeclineCode(), event.getAvailableBalance()));

            // Send push notification (immediate)
            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_DECLINED,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                "Card Declined",
                String.format("Your card was declined at %s for $%s. Reason: %s",
                    event.getMerchantName(), event.getAmount(), event.getDeclineReason()), Map.of());

            // Send SMS for high-value declines
            if (event.getAmount().compareTo(new BigDecimal("100")) > 0) {
                notificationService.sendNotification(event.getUserId(), NotificationType.CARD_DECLINED,
                    NotificationChannel.SMS, NotificationPriority.MEDIUM, null,
                    String.format("Card declined at %s for $%s. Balance: $%s. Add funds or use another card.",
                        event.getMerchantName(), event.getAmount(), event.getAvailableBalance()), Map.of());
            }

            // Send email (detailed)
            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_DECLINED,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                "Card Transaction Declined", message, Map.of());

            metricsCollector.incrementCounter("notification.card.declined.sent");
            metricsCollector.incrementCounter("notification.card.declined." +
                event.getDeclineCode().toLowerCase());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process card declined event", e);
            dlqHandler.sendToDLQ("card.declined.insufficient_funds", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getDeclineExplanation(String declineCode) {
        return switch (declineCode.toLowerCase()) {
            case "insufficient_funds" ->
                "Your account does not have enough funds to complete this transaction.";
            case "card_expired" ->
                "Your card has expired and can no longer be used.";
            case "invalid_pin" ->
                "The PIN entered was incorrect.";
            case "exceeds_limit" ->
                "This transaction exceeds your daily spending limit.";
            case "suspected_fraud" ->
                "This transaction was flagged by our fraud detection system for your protection.";
            case "card_blocked" ->
                "Your card has been blocked. Contact support for assistance.";
            case "merchant_blocked" ->
                "Transactions with this merchant are restricted on your account.";
            case "international_blocked" ->
                "International transactions are not enabled on your card.";
            default ->
                "The transaction could not be processed at this time.";
        };
    }

    private String getResolutionSteps(String declineCode, BigDecimal availableBalance) {
        return switch (declineCode.toLowerCase()) {
            case "insufficient_funds" ->
                String.format("""
                    1. Add funds to your account (Current balance: $%s)
                    2. Use a different payment method
                    3. Reduce the transaction amount
                    """, availableBalance);
            case "card_expired" ->
                """
                    1. Use your new card (check your mail)
                    2. Request a replacement card in the app
                    3. Use a different payment method
                    """;
            case "invalid_pin" ->
                """
                    1. Try again with the correct PIN
                    2. Reset your PIN in the app: Settings > Card > Change PIN
                    3. Contact support if you've forgotten your PIN
                    """;
            case "exceeds_limit" ->
                """
                    1. Wait until tomorrow (limits reset at midnight)
                    2. Request a limit increase in the app
                    3. Split the purchase into smaller amounts
                    """;
            case "suspected_fraud" ->
                """
                    1. Confirm this transaction in the app: Security > Recent Alerts
                    2. If authorized, the transaction will be approved
                    3. If not authorized, your card will remain blocked for safety
                    """;
            case "card_blocked" ->
                """
                    1. Check for security alerts in the app
                    2. Contact support to unblock: 1-800-WAQITI
                    3. Use a different payment method in the meantime
                    """;
            case "international_blocked" ->
                """
                    1. Enable international transactions in the app: Settings > Card > International
                    2. Notify us of travel plans to prevent declines
                    3. Use a different card enabled for international use
                    """;
            default ->
                """
                    1. Check your account status in the app
                    2. Try again in a few minutes
                    3. Contact support if the issue persists
                    """;
        };
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    private static class CardDeclinedEvent {
        private UUID userId, transactionId;
        private String cardNumber, merchantName, merchantLocation;
        private String declineReason, declineCode;
        private BigDecimal amount, availableBalance;
        private LocalDateTime attemptedAt;

        public UUID getUserId() { return userId; }
        public UUID getTransactionId() { return transactionId; }
        public String getCardNumber() { return cardNumber; }
        public String getMerchantName() { return merchantName; }
        public String getMerchantLocation() { return merchantLocation; }
        public String getDeclineReason() { return declineReason; }
        public String getDeclineCode() { return declineCode; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getAvailableBalance() { return availableBalance; }
        public LocalDateTime getAttemptedAt() { return attemptedAt; }
    }
}
