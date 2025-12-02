package com.waqiti.notification.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.notification.dto.PaymentCompletedEvent;
import com.waqiti.notification.service.EmailNotificationService;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.service.SMSNotificationService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Payment Completed Event Consumer
 *
 * PURPOSE: Send multi-channel notifications when payments complete
 *
 * NOTIFICATION CHANNELS:
 * 1. Push Notification (real-time, highest priority)
 * 2. Email (detailed receipt)
 * 3. SMS (optional, for high-value transactions)
 *
 * BUSINESS REQUIREMENTS:
 * - Sender receives confirmation within 5 seconds
 * - Recipient receives notification within 10 seconds
 * - Email receipt sent within 30 seconds
 * - SMS for transactions > $1,000
 *
 * USER EXPERIENCE:
 * - Immediate feedback builds trust
 * - Detailed receipts for record-keeping
 * - Reduces support inquiries ("Did my payment go through?")
 *
 * PERFORMANCE:
 * - Async notifications (don't block payment flow)
 * - Parallel delivery to multiple channels
 * - Circuit breaker for each channel (degradation not failure)
 *
 * @author Waqiti Product Team
 * @version 1.0.0
 * @since 2025-10-12
 */
@Service
@Slf4j
public class PaymentCompletedConsumer {

    private final PushNotificationService pushService;
    private final EmailNotificationService emailService;
    private final SMSNotificationService smsService;
    private final NotificationPreferenceService preferenceService;
    private final Counter notificationsSentCounter;
    private final Counter notificationsFailedCounter;

    @Autowired
    public PaymentCompletedConsumer(
            PushNotificationService pushService,
            EmailNotificationService emailService,
            SMSNotificationService smsService,
            NotificationPreferenceService preferenceService,
            MeterRegistry meterRegistry) {

        this.pushService = pushService;
        this.emailService = emailService;
        this.smsService = smsService;
        this.preferenceService = preferenceService;

        this.notificationsSentCounter = Counter.builder("payment.notifications.sent")
                .description("Number of payment notifications sent")
                .register(meterRegistry);

        this.notificationsFailedCounter = Counter.builder("payment.notifications.failed")
                .description("Number of payment notifications that failed")
                .register(meterRegistry);
    }

    /**
     * Process payment completed event and send notifications
     *
     * CRITICAL: Notifications must be sent quickly for good UX
     * Target: < 5 seconds for push notifications
     */
    @RetryableKafkaListener(
        topics = "payment-events",
        groupId = "notification-service-payment-events",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 3, // Fewer retries for notifications (not critical if delayed)
        backoffMultiplier = 2.0,
        initialBackoff = 2000L
    )
    public void handlePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing payment completion notification: paymentId={}, amount={}, sender={}, recipient={}",
                event.getPaymentId(),
                event.getAmount(),
                event.getSenderId(),
                event.getRecipientId());

        try {
            // Step 1: Validate event
            validateEvent(event);

            // Step 2: Check idempotency (prevent duplicate notifications)
            if (pushService.isPaymentNotificationAlreadySent(event.getPaymentId())) {
                log.info("Payment notification already sent (idempotent): paymentId={}",
                        event.getPaymentId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Get notification preferences for both users
            NotificationPreferences senderPrefs = preferenceService.getPreferences(event.getSenderId());
            NotificationPreferences recipientPrefs = preferenceService.getPreferences(event.getRecipientId());

            // Step 4: Send notifications in parallel (async for speed)
            CompletableFuture<Void> notificationsFuture = CompletableFuture.allOf(
                    // Sender notifications
                    sendSenderNotifications(event, senderPrefs),

                    // Recipient notifications
                    sendRecipientNotifications(event, recipientPrefs)
            );

            // Wait for all notifications to complete (with timeout)
            notificationsFuture.get(30, java.util.concurrent.TimeUnit.SECONDS);

            // Step 5: Mark notification as sent
            pushService.markPaymentNotificationSent(event.getPaymentId());

            // Step 6: Acknowledge message
            acknowledgment.acknowledge();

            // Metrics
            notificationsSentCounter.increment();

            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Payment notifications sent successfully: paymentId={}, processingTime={}ms",
                    event.getPaymentId(), processingTime);

            if (processingTime > 5000) {
                log.warn("Payment notification took longer than 5 seconds: paymentId={}, time={}ms",
                        event.getPaymentId(), processingTime);
            }

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Notification timeout after 30 seconds: paymentId={}", event.getPaymentId());
            // Acknowledge anyway - notifications are best-effort
            acknowledgment.acknowledge();
            notificationsFailedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to send payment notifications: paymentId={}, will retry",
                    event.getPaymentId(), e);

            notificationsFailedCounter.increment();

            throw new KafkaRetryException(
                    "Failed to send payment notifications",
                    e,
                    event.getPaymentId().toString()
            );
        }
    }

    /**
     * Send notifications to payment sender
     */
    private CompletableFuture<Void> sendSenderNotifications(
            PaymentCompletedEvent event,
            NotificationPreferences prefs) {

        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Push notification (immediate)
                if (prefs.isPushEnabled()) {
                    pushService.sendPaymentSentNotification(
                            event.getSenderId(),
                            event.getRecipientName(),
                            event.getAmount(),
                            event.getCurrency(),
                            event.getPaymentId()
                    );
                    log.debug("Push notification sent to sender: userId={}", event.getSenderId());
                }

                // 2. Email receipt (detailed)
                if (prefs.isEmailEnabled()) {
                    emailService.sendPaymentSentReceipt(
                            event.getSenderId(),
                            event.getRecipientName(),
                            event.getAmount(),
                            event.getCurrency(),
                            event.getPaymentId(),
                            event.getCompletedAt(),
                            event.getDescription()
                    );
                    log.debug("Email receipt sent to sender: userId={}", event.getSenderId());
                }

                // 3. SMS for high-value transactions
                if (prefs.isSmsEnabled() && isHighValueTransaction(event.getAmount())) {
                    smsService.sendPaymentConfirmation(
                            event.getSenderId(),
                            String.format("Payment of %s %s sent to %s. Ref: %s",
                                    event.getAmount(),
                                    event.getCurrency(),
                                    event.getRecipientName(),
                                    event.getPaymentId().toString().substring(0, 8))
                    );
                    log.debug("SMS sent to sender: userId={}", event.getSenderId());
                }

            } catch (Exception e) {
                log.error("Failed to send sender notifications: userId={}", event.getSenderId(), e);
                // Don't throw - best effort for notifications
            }
        });
    }

    /**
     * Send notifications to payment recipient
     */
    private CompletableFuture<Void> sendRecipientNotifications(
            PaymentCompletedEvent event,
            NotificationPreferences prefs) {

        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Push notification (immediate)
                if (prefs.isPushEnabled()) {
                    pushService.sendPaymentReceivedNotification(
                            event.getRecipientId(),
                            event.getSenderName(),
                            event.getAmount(),
                            event.getCurrency(),
                            event.getPaymentId()
                    );
                    log.debug("Push notification sent to recipient: userId={}", event.getRecipientId());
                }

                // 2. Email notification
                if (prefs.isEmailEnabled()) {
                    emailService.sendPaymentReceivedNotification(
                            event.getRecipientId(),
                            event.getSenderName(),
                            event.getAmount(),
                            event.getCurrency(),
                            event.getPaymentId(),
                            event.getCompletedAt(),
                            event.getDescription()
                    );
                    log.debug("Email sent to recipient: userId={}", event.getRecipientId());
                }

                // 3. SMS for high-value transactions
                if (prefs.isSmsEnabled() && isHighValueTransaction(event.getAmount())) {
                    smsService.sendPaymentReceived(
                            event.getRecipientId(),
                            String.format("You received %s %s from %s. Ref: %s",
                                    event.getAmount(),
                                    event.getCurrency(),
                                    event.getSenderName(),
                                    event.getPaymentId().toString().substring(0, 8))
                    );
                    log.debug("SMS sent to recipient: userId={}", event.getRecipientId());
                }

            } catch (Exception e) {
                log.error("Failed to send recipient notifications: userId={}", event.getRecipientId(), e);
                // Don't throw - best effort for notifications
            }
        });
    }

    /**
     * Check if transaction is high-value (requires SMS)
     */
    private boolean isHighValueTransaction(java.math.BigDecimal amount) {
        return amount.compareTo(new java.math.BigDecimal("1000.00")) > 0;
    }

    /**
     * Validate event
     */
    private void validateEvent(PaymentCompletedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getPaymentId() == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }

        if (event.getSenderId() == null) {
            throw new IllegalArgumentException("Sender ID cannot be null");
        }

        if (event.getRecipientId() == null) {
            throw new IllegalArgumentException("Recipient ID cannot be null");
        }

        if (event.getAmount() == null || event.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "payment-events-notification-service-dlq")
    public void handleDLQMessage(@Payload PaymentCompletedEvent event) {
        log.error("Payment notification in DLQ: paymentId={}", event.getPaymentId());

        try {
            // For notifications, DLQ is not critical - just log
            pushService.logDLQNotification(
                    event.getPaymentId(),
                    event,
                    "Payment notification failed after all retries"
            );

            // Create support ticket if high-value transaction
            if (isHighValueTransaction(event.getAmount())) {
                pushService.createSupportTicket(
                        event.getSenderId(),
                        "Payment Notification Failed",
                        String.format("High-value payment notification failed. PaymentId: %s, Amount: %s %s",
                                event.getPaymentId(), event.getAmount(), event.getCurrency())
                );
            }

        } catch (Exception e) {
            log.error("Failed to process DLQ notification: paymentId={}", event.getPaymentId(), e);
        }
    }

    // Data classes
    @lombok.Data
    public static class NotificationPreferences {
        private boolean pushEnabled = true;
        private boolean emailEnabled = true;
        private boolean smsEnabled = false;
    }
}
