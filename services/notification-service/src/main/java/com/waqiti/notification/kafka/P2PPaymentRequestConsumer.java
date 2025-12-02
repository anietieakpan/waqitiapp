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
 * CRITICAL FIX #41: P2PPaymentRequestConsumer
 * Notifies users when they receive peer-to-peer payment requests
 * Impact: P2P payment flow completion, social payment engagement
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class P2PPaymentRequestConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "p2p.payment.request.received", groupId = "notification-p2p-payment-request")
    public void handle(P2PPaymentRequestEvent event, Acknowledgment ack) {
        try {
            log.info("💸 P2P PAYMENT REQUEST RECEIVED: requestId={}, from={}, to={}, amount=${}",
                event.getRequestId(), event.getRequesterName(), event.getRecipientUserId(), event.getAmount());

            String key = "p2p:request:" + event.getRequestId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                💸 Payment Request from %s

                You have a new payment request!

                Request Details:
                - From: %s
                - Amount: $%s
                - Request Date: %s
                - Expires: %s
                %s

                %s

                Payment Options:
                1. Pay Now (Instant):
                   https://example.com/p2p/pay/%s

                2. Pay via Mobile App:
                   Open app > Payments > Requests > Pay

                3. Decline Request:
                   https://example.com/p2p/decline/%s

                Request Details:
                - Request ID: %s
                - Status: Pending your action
                - Time to respond: %d days

                What You Can Do:
                ✅ Pay instantly from your Waqiti wallet
                ✅ Add a note when you pay
                ✅ Decline if this request is incorrect
                ✅ Contact sender if you have questions

                Payment Methods Available:
                • Waqiti Wallet Balance
                • Linked Bank Account
                • Debit Card

                Security Tips:
                🔒 Only pay people you know and trust
                ⚠️ Verify the request is legitimate
                ⚠️ Never send money to strangers
                ⚠️ Be cautious of unexpected requests
                ⚠️ Contact sender directly if suspicious

                %s

                Questions? Contact P2P support:
                Email: p2p@example.com
                Phone: 1-800-WAQITI-P2P
                """,
                event.getRequesterName(),
                event.getRequesterName(),
                event.getAmount(),
                event.getRequestedAt(),
                event.getExpiresAt().toLocalDate(),
                event.getNote() != null && !event.getNote().isEmpty()
                    ? String.format("- Note: \"%s\"", event.getNote()) : "",
                getRequestContext(event.getRequestReason()),
                event.getRequestId(),
                event.getRequestId(),
                event.getRequestId(),
                java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), event.getExpiresAt()),
                getCommonScenarios(event.getRequestReason()));

            // Multi-channel notification
            notificationService.sendNotification(event.getRecipientUserId(), NotificationType.P2P_PAYMENT_REQUEST,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                String.format("Payment Request: $%s", event.getAmount()),
                String.format("%s is requesting $%s. Tap to pay or decline.",
                    event.getRequesterName(), event.getAmount()), Map.of());

            notificationService.sendNotification(event.getRecipientUserId(), NotificationType.P2P_PAYMENT_REQUEST,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                String.format("Payment Request from %s ($%s)", event.getRequesterName(), event.getAmount()),
                message, Map.of());

            // SMS for large amounts
            if (event.getAmount().compareTo(new BigDecimal("100")) > 0) {
                notificationService.sendNotification(event.getRecipientUserId(), NotificationType.P2P_PAYMENT_REQUEST,
                    NotificationChannel.SMS, NotificationPriority.MEDIUM, null,
                    String.format("Payment request: %s wants $%s. Pay at waqiti.com/p2p/pay/%s",
                        event.getRequesterName(), event.getAmount(), event.getRequestId()), Map.of());
            }

            metricsCollector.incrementCounter("notification.p2p.payment.request.sent");
            metricsCollector.recordGauge("p2p.request.amount", event.getAmount().doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process P2P payment request event", e);
            dlqHandler.sendToDLQ("p2p.payment.request.received", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getRequestContext(String requestReason) {
        if (requestReason == null || requestReason.isEmpty()) {
            return "No additional context provided for this request.";
        }

        return switch (requestReason.toLowerCase()) {
            case "split_bill" -> """
                This appears to be for splitting a bill or shared expense.
                Make sure you agreed to split this cost.
                """;
            case "rent" -> """
                This appears to be a rent payment request.
                Verify the amount matches your rent agreement.
                """;
            case "utilities" -> """
                This appears to be for utilities or shared household expenses.
                """;
            case "loan_repayment" -> """
                This appears to be for loan or debt repayment.
                """;
            case "gift" -> """
                This appears to be a gift or contribution request.
                """;
            default -> String.format("Request reason: %s", requestReason);
        };
    }

    private String getCommonScenarios(String requestReason) {
        return """
            Common Payment Request Scenarios:
            ✅ Legitimate Requests:
            • Splitting dinner or drinks with friends
            • Roommate requesting their share of rent/utilities
            • Repaying a friend who covered your expenses
            • Contributing to a group gift or event

            🚫 Suspicious Requests - DO NOT PAY:
            • Requests from people you don't know
            • Urgent requests for "emergencies"
            • Requests claiming to be from companies/government
            • Requests with threats or pressure tactics
            • "Too good to be true" investment opportunities

            When in Doubt:
            • Contact the requester directly (call or text)
            • Don't use contact info from the payment request
            • Decline suspicious requests
            • Report scams to support@example.com
            """;
    }

    private static class P2PPaymentRequestEvent {
        private UUID requestId, requesterUserId, recipientUserId;
        private String requesterName, requestReason, note;
        private BigDecimal amount;
        private LocalDateTime requestedAt, expiresAt;

        public UUID getRequestId() { return requestId; }
        public UUID getRequesterUserId() { return requesterUserId; }
        public UUID getRecipientUserId() { return recipientUserId; }
        public String getRequesterName() { return requesterName; }
        public String getRequestReason() { return requestReason; }
        public String getNote() { return note; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getRequestedAt() { return requestedAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}
