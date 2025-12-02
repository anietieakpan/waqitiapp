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
 * CRITICAL FIX #49: CardReplacementConsumer
 * Notifies users when replacement cards are shipped
 * Impact: Card delivery tracking, security
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardReplacementConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "card.replacement.shipped", groupId = "notification-card-replacement")
    public void handle(CardReplacementEvent event, Acknowledgment ack) {
        try {
            log.info("📬 CARD REPLACEMENT SHIPPED: userId={}, cardType={}, reason={}, trackingNumber={}",
                event.getUserId(), event.getCardType(), event.getReplacementReason(), event.getTrackingNumber());

            String key = "card:replacement:" + event.getNewCardId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                📬 Your Replacement Card is On the Way!

                Your new Waqiti card has been shipped and is heading to you.

                Card Details:
                - Card Type: %s
                - Card Number: %s (new)
                - Cardholder: %s
                - Expected Delivery: %s

                Shipping Information:
                - Shipped: %s
                - Carrier: %s
                - Tracking Number: %s
                - Track Package: %s
                - Delivery Address:
                  %s

                Replacement Reason:
                %s

                What Happens Next:
                1. Your new card will arrive in %d business days
                2. Activate your card immediately upon receipt
                3. Your old card %s

                Old Card Status:
                - Old Card: %s
                - Status: %s
                - Expiration: %s

                When Your Card Arrives:
                ✅ Immediate Steps:
                1. Verify the package is sealed and unopened
                2. Confirm your name and address on the card
                3. Sign the back of the card
                4. Activate: https://example.com/cards/activate or call 1-800-WAQITI
                5. Add to mobile wallet (Apple Pay, Google Pay)
                6. Destroy old card by cutting through chip and magnetic stripe

                Activation Methods:
                • Website: https://example.com/cards/activate
                • Mobile App: Cards > Activate New Card
                • Phone: 1-800-WAQITI-CARD (automated)
                • SMS: Text ACTIVATE to 12345

                Your New Card Features:
                %s

                Security Information:
                🔒 Card Security:
                • New 16-digit card number (different from old card)
                • New CVV security code
                • Same PIN (or set new PIN during activation)
                • EMV chip for added security
                • Contactless payments enabled

                🔒 What if Card is Stolen in Mail:
                • Report immediately: 1-800-WAQITI-SEC
                • We'll cancel and send another
                • You're not liable for fraudulent charges
                • Monitor your account for suspicious activity

                Important Notes:
                ⚠️ Old Card:
                %s

                ⚠️ Recurring Payments:
                %s

                ⚠️ Package Not Received:
                If you don't receive your card by %s:
                • Check with household members
                • Verify delivery address
                • Contact us: cards@example.com | 1-800-WAQITI-CARD
                • We'll investigate and resend if needed

                Questions? Contact card services:
                Email: cards@example.com
                Phone: 1-800-WAQITI-CARD (24/7)
                Tracking: %s
                """,
                event.getCardType(),
                maskCardNumber(event.getNewCardNumber()),
                event.getCardholderName(),
                event.getExpectedDeliveryDate().toLocalDate(),
                event.getShippedAt().toLocalDate(),
                event.getShippingCarrier(),
                event.getTrackingNumber(),
                event.getTrackingUrl(),
                event.getDeliveryAddress(),
                getReplacementReasonDetail(event.getReplacementReason()),
                event.getEstimatedDeliveryDays(),
                event.getOldCardStatus().equalsIgnoreCase("active")
                    ? "will remain active until you activate the new card"
                    : "has already been deactivated",
                maskCardNumber(event.getOldCardNumber()),
                event.getOldCardStatus(),
                event.getOldCardExpirationDate() != null ? event.getOldCardExpirationDate().toLocalDate().toString() : "N/A",
                getCardFeatures(event.getCardType()),
                getOldCardInstructions(event.getReplacementReason(), event.getOldCardStatus()),
                getRecurringPaymentInfo(event.hasRecurringPayments(), event.getReplacementReason()),
                event.getExpectedDeliveryDate().plusDays(3).toLocalDate(),
                event.getTrackingUrl());

            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_REPLACEMENT_SHIPPED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Your Replacement Card Has Shipped!", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.CARD_REPLACEMENT_SHIPPED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Card Shipped",
                String.format("Your replacement %s card has shipped! Expected delivery: %s. Track: %s",
                    event.getCardType(), event.getExpectedDeliveryDate().toLocalDate(), event.getTrackingNumber()),
                Map.of());

            metricsCollector.incrementCounter("notification.card.replacement.shipped.sent");

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process card replacement event", e);
            dlqHandler.sendToDLQ("card.replacement.shipped", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getReplacementReasonDetail(String reason) {
        return switch (reason.toLowerCase()) {
            case "lost" -> "You reported this card as lost.";
            case "stolen" -> "You reported this card as stolen. We've deactivated the old card for your protection.";
            case "damaged" -> "Your card was damaged and needs replacement.";
            case "expired" -> "Your previous card has expired. This is your renewed card.";
            case "compromised" -> "Suspicious activity was detected. We've issued a new card for your security.";
            case "fraud" -> "Fraudulent activity was detected. We've issued a new card and old card is deactivated.";
            case "upgrade" -> "You requested a card upgrade.";
            default -> reason;
        };
    }

    private String getCardFeatures(String cardType) {
        return switch (cardType.toLowerCase()) {
            case "debit" -> """
                • Direct access to your account balance
                • No interest charges
                • ATM withdrawals worldwide
                • Contactless payments
                • Virtual card available
                """;
            case "credit" -> """
                • Credit line access
                • Rewards/cashback program
                • Purchase protection
                • Extended warranty
                • Travel benefits
                """;
            case "prepaid" -> """
                • Preloaded funds only
                • No credit check
                • Budget control
                • Reloadable
                """;
            default -> "All standard card features included.";
        };
    }

    private String getOldCardInstructions(String reason, String oldCardStatus) {
        if ("fraud".equalsIgnoreCase(reason) || "compromised".equalsIgnoreCase(reason) || "stolen".equalsIgnoreCase(reason)) {
            return """
                Your old card has been permanently deactivated and cannot be used.
                Destroy it immediately by cutting through the chip and magnetic stripe.
                Do NOT attempt to use the old card.
                """;
        } else if ("active".equalsIgnoreCase(oldCardStatus)) {
            return """
                Your old card will remain active until you activate your new card.
                You can continue using the old card until the new one is activated.
                Once new card is activated, destroy the old card immediately.
                """;
        } else {
            return """
                Your old card has been deactivated.
                Destroy it by cutting through the chip and magnetic stripe.
                """;
        }
    }

    private String getRecurringPaymentInfo(boolean hasRecurringPayments, String reason) {
        if (hasRecurringPayments) {
            if ("expired".equalsIgnoreCase(reason)) {
                return """
                ✅ Recurring Payments Updated Automatically:
                Your recurring payments will automatically use your new card number.
                No action needed - all subscriptions will continue without interruption.
                """;
            } else {
                return """
                ⚠️ Update Recurring Payments:
                You have recurring payments set up with your old card.
                You'll need to update your card information with:
                • Subscription services (Netflix, Spotify, etc.)
                • Utility companies
                • Gym memberships
                • Any other recurring charges

                View recurring payments: https://example.com/cards/recurring
                """;
            }
        }
        return "";
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    private static class CardReplacementEvent {
        private UUID userId, oldCardId, newCardId;
        private String cardType, cardholderName, replacementReason;
        private String oldCardNumber, newCardNumber, oldCardStatus;
        private String trackingNumber, trackingUrl, shippingCarrier, deliveryAddress;
        private LocalDateTime shippedAt, expectedDeliveryDate, oldCardExpirationDate;
        private int estimatedDeliveryDays;
        private boolean recurringPayments;

        public UUID getUserId() { return userId; }
        public UUID getOldCardId() { return oldCardId; }
        public UUID getNewCardId() { return newCardId; }
        public String getCardType() { return cardType; }
        public String getCardholderName() { return cardholderName; }
        public String getReplacementReason() { return replacementReason; }
        public String getOldCardNumber() { return oldCardNumber; }
        public String getNewCardNumber() { return newCardNumber; }
        public String getOldCardStatus() { return oldCardStatus; }
        public String getTrackingNumber() { return trackingNumber; }
        public String getTrackingUrl() { return trackingUrl; }
        public String getShippingCarrier() { return shippingCarrier; }
        public String getDeliveryAddress() { return deliveryAddress; }
        public LocalDateTime getShippedAt() { return shippedAt; }
        public LocalDateTime getExpectedDeliveryDate() { return expectedDeliveryDate; }
        public LocalDateTime getOldCardExpirationDate() { return oldCardExpirationDate; }
        public int getEstimatedDeliveryDays() { return estimatedDeliveryDays; }
        public boolean hasRecurringPayments() { return recurringPayments; }
    }
}
