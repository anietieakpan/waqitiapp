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
 * CRITICAL FIX #53: TransactionDisputedConsumer
 * Notifies users when transaction disputes are filed
 * Impact: Dispute tracking, customer service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionDisputedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "transaction.disputed", groupId = "notification-transaction-disputed")
    public void handle(TransactionDisputedEvent event, Acknowledgment ack) {
        try {
            log.info("⚠️ TRANSACTION DISPUTED: userId={}, amount=${}, merchant={}, reason={}",
                event.getUserId(), event.getAmount(), event.getMerchantName(), event.getDisputeReason());

            String key = "transaction:disputed:" + event.getDisputeId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                ⚠️ Dispute Filed Successfully

                Your transaction dispute has been received and is under review.

                Disputed Transaction:
                - Merchant: %s
                - Amount: $%s
                - Date: %s
                - Card: %s
                - Transaction ID: %s

                Dispute Details:
                - Dispute Reason: %s
                - Filed: %s
                - Case #: %s
                - Expected Resolution: %s

                What Happens Next:
                1. We investigate the transaction (up to 45 days)
                2. Merchant is contacted for their response
                3. Temporary credit may be issued during investigation
                4. Final decision communicated to you

                %s

                Your Rights (Regulation E/Fair Credit Billing Act):
                • Right to dispute unauthorized or incorrect charges
                • Provisional credit during investigation
                • Written notification of investigation results
                • No liability for unauthorized transactions if reported promptly

                Track Your Dispute:
                https://example.com/disputes/%s

                Questions? Contact disputes team:
                Email: disputes@example.com
                Phone: 1-800-WAQITI-DISPUTE
                Reference: Case #%s
                """,
                event.getMerchantName(),
                event.getAmount(),
                event.getTransactionDate().toLocalDate(),
                maskCardNumber(event.getCardNumber()),
                event.getTransactionId(),
                event.getDisputeReason(),
                event.getDisputedAt(),
                event.getDisputeId(),
                event.getExpectedResolutionDate().toLocalDate(),
                getProvisionalCreditInfo(event.getAmount(), event.isProvisionalCreditIssued()),
                event.getDisputeId(),
                event.getDisputeId());

            notificationService.sendNotification(event.getUserId(), NotificationType.TRANSACTION_DISPUTED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                String.format("Dispute Filed: Case #%s", event.getDisputeId()), message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.TRANSACTION_DISPUTED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Dispute Filed",
                String.format("Your $%s dispute with %s has been filed. Case #%s",
                    event.getAmount(), event.getMerchantName(), event.getDisputeId()), Map.of());

            metricsCollector.incrementCounter("notification.transaction.disputed.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process transaction disputed event", e);
            dlqHandler.sendToDLQ("transaction.disputed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getProvisionalCreditInfo(BigDecimal amount, boolean credited) {
        if (credited) {
            return String.format("""
                ✅ Provisional Credit Issued:
                A temporary credit of $%s has been applied to your account
                while we investigate. If the dispute is resolved in the merchant's
                favor, this credit will be reversed.
                """, amount);
        }
        return """
            Provisional Credit:
            You may receive a temporary credit during the investigation.
            This typically occurs within 10 business days for debit card disputes.
            """;
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    private static class TransactionDisputedEvent {
        private UUID userId, transactionId, disputeId;
        private String merchantName, cardNumber, disputeReason;
        private BigDecimal amount;
        private LocalDateTime transactionDate, disputedAt, expectedResolutionDate;
        private boolean provisionalCreditIssued;

        public UUID getUserId() { return userId; }
        public UUID getTransactionId() { return transactionId; }
        public UUID getDisputeId() { return disputeId; }
        public String getMerchantName() { return merchantName; }
        public String getCardNumber() { return cardNumber; }
        public String getDisputeReason() { return disputeReason; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getTransactionDate() { return transactionDate; }
        public LocalDateTime getDisputedAt() { return disputedAt; }
        public LocalDateTime getExpectedResolutionDate() { return expectedResolutionDate; }
        public boolean isProvisionalCreditIssued() { return provisionalCreditIssued; }
    }
}
