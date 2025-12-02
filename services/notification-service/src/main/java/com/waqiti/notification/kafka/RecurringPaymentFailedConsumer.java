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
 * CRITICAL FIX #10: RecurringPaymentFailedConsumer
 * Notifies merchants/users when recurring payments fail
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringPaymentFailedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "recurring.payment.failed", groupId = "notification-recurring-failed")
    public void handle(RecurringFailedEvent event, Acknowledgment ack) {
        try {
            String key = "recurring:failed:" + event.getRecurringPaymentId() + ":" + event.getAttemptNumber();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            // Notify customer
            String customerMessage = String.format("""
                Your recurring payment could not be processed.

                Merchant: %s
                Amount: $%s
                Due Date: %s
                Failure Reason: %s
                Attempt: %d of %d

                Action Required: Please update your payment method or ensure sufficient funds.
                Next Attempt: %s

                To avoid service interruption, update your payment information in the app.
                """, event.getMerchantName(), event.getAmount(), event.getDueDate(),
                event.getFailureReason(), event.getAttemptNumber(), event.getMaxAttempts(),
                event.getNextRetryAt());

            notificationService.sendNotification(event.getCustomerId(), NotificationType.RECURRING_PAYMENT_FAILED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Action Required: Payment Failed", customerMessage, Map.of());

            // Notify merchant
            String merchantMessage = String.format("""
                Recurring payment failed for customer account.

                Customer ID: %s
                Amount: $%s
                Subscription ID: %s
                Failure Reason: %s
                Attempt: %d of %d

                Next automatic retry: %s

                The customer has been notified to update their payment information.
                """, event.getCustomerId(), event.getAmount(), event.getRecurringPaymentId(),
                event.getFailureReason(), event.getAttemptNumber(), event.getMaxAttempts(),
                event.getNextRetryAt());

            notificationService.sendNotification(event.getMerchantId(), NotificationType.RECURRING_PAYMENT_FAILED,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                "Recurring Payment Failed", merchantMessage, Map.of());

            // If final attempt, send urgent notification
            if (event.getAttemptNumber() >= event.getMaxAttempts()) {
                notificationService.sendNotification(event.getCustomerId(), NotificationType.SUBSCRIPTION_CANCELLED,
                    NotificationChannel.SMS, NotificationPriority.CRITICAL, null,
                    String.format("URGENT: Your %s subscription may be cancelled due to payment failure. Update payment method immediately.",
                        event.getMerchantName()), Map.of());
            }

            metricsCollector.incrementCounter("notification.recurring.payment.failed.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process recurring payment failed event", e);
            dlqHandler.sendToDLQ("recurring.payment.failed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private static class RecurringFailedEvent {
        private UUID recurringPaymentId, customerId, merchantId;
        private String merchantName, failureReason;
        private BigDecimal amount;
        private LocalDateTime dueDate, nextRetryAt;
        private int attemptNumber, maxAttempts;
        public UUID getRecurringPaymentId() { return recurringPaymentId; }
        public UUID getCustomerId() { return customerId; }
        public UUID getMerchantId() { return merchantId; }
        public String getMerchantName() { return merchantName; }
        public String getFailureReason() { return failureReason; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getDueDate() { return dueDate; }
        public LocalDateTime getNextRetryAt() { return nextRetryAt; }
        public int getAttemptNumber() { return attemptNumber; }
        public int getMaxAttempts() { return maxAttempts; }
    }
}
