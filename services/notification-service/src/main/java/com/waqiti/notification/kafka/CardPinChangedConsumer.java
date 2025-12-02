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
 * CRITICAL FIX #27: CardPinChangedConsumer
 * Notifies users when card PIN is changed (security alert)
 * Impact: Fraud detection, account security
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardPinChangedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "card.pin.changed", groupId = "notification-card-pin-changed")
    public void handle(CardPinChangedEvent event, Acknowledgment ack) {
        try {
            log.info("🔒 CARD PIN CHANGED: userId={}, cardId={}, changeMethod={}, location={}",
                event.getUserId(), event.getCardId(), event.getChangeMethod(), event.getLocation());

            String key = "card:pin:changed:" + event.getCardId() + ":" + event.getChangedAt().toString();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                🔒 Security Alert: Card PIN Changed

                Your card PIN was recently changed.

                Card Details:
                - Card: %s
                - Change Date/Time: %s
                - Change Method: %s
                - Location: %s
                - IP Address: %s
                - Device: %s

                If You Made This Change:
                ✅ No action needed. Your new PIN is now active.

                If You Did NOT Make This Change:
                🚨 IMMEDIATE ACTION REQUIRED:
                1. Lock your card immediately in the app: Cards > Lock Card
                2. Contact security: security@example.com | 1-800-WAQITI-SEC
                3. Review recent transactions for unauthorized activity
                4. Request a replacement card

                Security Tips:
                • Never share your PIN with anyone
                • Use a unique PIN that's different from other accounts
                • Change your PIN regularly
                • Enable transaction alerts for real-time monitoring

                Questions? Contact card support:
                Email: cards@example.com
                Phone: 1-800-WAQITI-CARD
                Reference: Card ID %s
                """,
                maskCardNumber(event.getCardNumber()),
                event.getChangedAt(),
                getChangeMethodDescription(event.getChangeMethod()),
                event.getLocation() != null ? event.getLocation() : "Unknown",
                maskIpAddress(event.getIpAddress()),
                event.getDeviceType() != null ? event.getDeviceType() : "Unknown device",
                event.getCardId());

            // Multi-channel security notification
            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_PIN_CHANGED,
                NotificationChannel.PUSH, NotificationPriority.URGENT,
                "Security Alert: Card PIN Changed",
                String.format("Your PIN for card %s was changed. If this wasn't you, lock your card immediately.",
                    maskCardNumber(event.getCardNumber())), Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_PIN_CHANGED,
                NotificationChannel.SMS, NotificationPriority.HIGH, null,
                String.format("Security Alert: PIN changed for card %s at %s. Not you? Lock card in app or call 1-800-WAQITI-SEC",
                    maskCardNumber(event.getCardNumber()), event.getChangedAt().toLocalTime()), Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_PIN_CHANGED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Security Alert: Card PIN Changed", message, Map.of());

            metricsCollector.incrementCounter("notification.card.pin.changed.sent");
            metricsCollector.incrementCounter("notification.card.pin.changed." +
                event.getChangeMethod().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process card PIN changed event", e);
            dlqHandler.sendToDLQ("card.pin.changed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getChangeMethodDescription(String changeMethod) {
        return switch (changeMethod.toLowerCase()) {
            case "atm" -> "ATM Machine";
            case "mobile_app" -> "Mobile App";
            case "website" -> "Website";
            case "customer_service" -> "Customer Service";
            case "branch" -> "Bank Branch";
            default -> changeMethod;
        };
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return "Unknown";
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        return ipAddress;
    }

    private static class CardPinChangedEvent {
        private UUID userId, cardId;
        private String cardNumber, changeMethod, location, ipAddress, deviceType;
        private LocalDateTime changedAt;

        public UUID getUserId() { return userId; }
        public UUID getCardId() { return cardId; }
        public String getCardNumber() { return cardNumber; }
        public String getChangeMethod() { return changeMethod; }
        public String getLocation() { return location; }
        public String getIpAddress() { return ipAddress; }
        public String getDeviceType() { return deviceType; }
        public LocalDateTime getChangedAt() { return changedAt; }
    }
}
