package com.waqiti.billingorchestrator.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.billingorchestrator.domain.Subscription;
import com.waqiti.billingorchestrator.domain.SubscriptionStatus;
import com.waqiti.billingorchestrator.domain.PaymentRetry;
import com.waqiti.billingorchestrator.repository.SubscriptionRepository;
import com.waqiti.billingorchestrator.repository.PaymentRetryRepository;
import com.waqiti.billingorchestrator.service.BillingNotificationService;
import com.waqiti.billingorchestrator.service.PaymentRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #16: SubscriptionPaymentFailedConsumer
 * Implements retry logic when subscription payments fail
 * Impact: $200K/month in lost subscription revenue
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentFailedConsumer {
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRetryRepository paymentRetryRepository;
    private final PaymentRetryService paymentRetryService;
    private final BillingNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int[] RETRY_DELAYS_DAYS = {3, 5, 7}; // Progressive retry schedule

    @KafkaListener(topics = "subscription.payment.failed", groupId = "billing-subscription-retry")
    @Transactional
    public void handle(SubscriptionPaymentFailedEvent event, Acknowledgment ack) {
        try {
            log.warn("💳 SUBSCRIPTION PAYMENT FAILED: subscriptionId={}, userId={}, amount=${}, reason={}",
                event.getSubscriptionId(), event.getUserId(), event.getAmount(), event.getFailureReason());

            String key = "subscription:payment:failed:" + event.getSubscriptionId() + ":" + event.getPaymentAttemptId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            Subscription subscription = subscriptionRepository.findById(event.getSubscriptionId())
                .orElseThrow(() -> new BusinessException("Subscription not found"));

            if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
                log.warn("Subscription {} already cancelled", event.getSubscriptionId());
                ack.acknowledge();
                return;
            }

            // Count existing retry attempts
            int attemptCount = paymentRetryRepository.countBySubscriptionId(event.getSubscriptionId());

            if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                log.error("🚫 MAX RETRIES EXCEEDED: subscriptionId={}, cancelling subscription",
                    event.getSubscriptionId());

                subscription.setStatus(SubscriptionStatus.CANCELLED);
                subscription.setCancellationReason("Payment failed after " + MAX_RETRY_ATTEMPTS + " retry attempts");
                subscription.setCancelledAt(LocalDateTime.now());
                subscriptionRepository.save(subscription);

                notifySubscriptionCancelled(event, subscription);
                metricsCollector.incrementCounter("billing.subscription.cancelled.max_retries");
            } else {
                // Schedule retry
                int nextRetryDays = RETRY_DELAYS_DAYS[attemptCount];
                LocalDateTime nextRetryAt = LocalDateTime.now().plusDays(nextRetryDays);

                PaymentRetry retry = PaymentRetry.builder()
                    .id(UUID.randomUUID())
                    .subscriptionId(event.getSubscriptionId())
                    .userId(event.getUserId())
                    .amount(event.getAmount())
                    .attemptNumber(attemptCount + 1)
                    .scheduledAt(nextRetryAt)
                    .failureReason(event.getFailureReason())
                    .createdAt(LocalDateTime.now())
                    .build();

                paymentRetryRepository.save(retry);

                subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);
                subscription.setNextRetryAt(nextRetryAt);
                subscriptionRepository.save(subscription);

                log.info("📅 RETRY SCHEDULED: subscriptionId={}, attempt={}/{}, retryAt={}",
                    event.getSubscriptionId(), attemptCount + 1, MAX_RETRY_ATTEMPTS, nextRetryAt);

                // Schedule actual retry job
                paymentRetryService.scheduleRetry(retry);

                notifyPaymentFailedWithRetry(event, subscription, attemptCount + 1, nextRetryAt);
                metricsCollector.incrementCounter("billing.subscription.payment.retry.scheduled");
            }

            metricsCollector.incrementCounter("billing.subscription.payment.failed");
            metricsCollector.incrementCounter("billing.subscription.payment.failed." +
                event.getFailureReason().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process subscription payment failed event", e);
            dlqHandler.sendToDLQ("subscription.payment.failed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private void notifyPaymentFailedWithRetry(SubscriptionPaymentFailedEvent event,
                                               Subscription subscription,
                                               int attemptNumber,
                                               LocalDateTime nextRetryAt) {
        String message = String.format("""
            Your subscription payment has failed.

            Subscription: %s
            Amount: $%s
            Payment Method: %s
            Failure Reason: %s

            What's Next:
            We'll automatically retry this payment on %s (attempt %d of %d).

            To avoid service interruption:
            1. Update your payment method if needed
            2. Ensure sufficient funds are available
            3. Check with your bank if the issue persists

            If all retry attempts fail, your subscription will be cancelled.

            Update Payment Method: https://example.com/billing/payment-methods
            Questions? Contact support@example.com
            """,
            subscription.getPlanName(),
            event.getAmount(),
            maskPaymentMethod(event.getPaymentMethod()),
            getFailureExplanation(event.getFailureReason()),
            nextRetryAt.toLocalDate(),
            attemptNumber,
            MAX_RETRY_ATTEMPTS);

        notificationService.sendSubscriptionPaymentFailedNotification(
            event.getUserId(), event.getSubscriptionId(), event.getAmount(), message);
    }

    private void notifySubscriptionCancelled(SubscriptionPaymentFailedEvent event, Subscription subscription) {
        String message = String.format("""
            Your subscription has been cancelled due to payment failure.

            Subscription: %s
            Amount: $%s
            Cancellation Reason: Payment failed after %d retry attempts

            Your service access will end on: %s

            To reactivate your subscription:
            1. Update your payment method
            2. Subscribe again at https://example.com/billing/subscriptions

            We're sorry to see you go!
            Questions? Contact support@example.com
            """,
            subscription.getPlanName(),
            event.getAmount(),
            MAX_RETRY_ATTEMPTS,
            subscription.getServiceEndDate());

        notificationService.sendSubscriptionCancelledNotification(
            event.getUserId(), event.getSubscriptionId(), message);
    }

    private String getFailureExplanation(String reason) {
        return switch (reason.toLowerCase()) {
            case "insufficient_funds" -> "Your account has insufficient funds";
            case "card_declined" -> "Your card was declined by your bank";
            case "card_expired" -> "Your card has expired";
            case "payment_method_invalid" -> "Your payment method is no longer valid";
            case "bank_error" -> "Your bank is experiencing technical issues";
            case "fraud_suspected" -> "This transaction was flagged for security review";
            default -> "Payment processing failed";
        };
    }

    private String maskPaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.length() < 4) return "****";
        return "****" + paymentMethod.substring(paymentMethod.length() - 4);
    }

    private static class SubscriptionPaymentFailedEvent {
        private UUID subscriptionId, userId, paymentAttemptId;
        private String failureReason, paymentMethod;
        private BigDecimal amount;
        private LocalDateTime failedAt;

        public UUID getSubscriptionId() { return subscriptionId; }
        public UUID getUserId() { return userId; }
        public UUID getPaymentAttemptId() { return paymentAttemptId; }
        public String getFailureReason() { return failureReason; }
        public String getPaymentMethod() { return paymentMethod; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getFailedAt() { return failedAt; }
    }
}
