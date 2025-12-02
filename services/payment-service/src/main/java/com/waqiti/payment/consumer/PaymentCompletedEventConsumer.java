package com.waqiti.payment.consumer;

import com.waqiti.common.events.PaymentCompletedEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.payment.exception.PaymentProcessingException;
import com.waqiti.payment.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Production-grade consumer for payment completion events.
 *
 * This consumer handles post-payment completion activities:
 * 1. Release fund reservations
 * 2. Generate receipts
 * 3. Update merchant settlement batches
 * 4. Trigger rewards/cashback
 * 5. Record analytics
 * 6. Send completion notifications
 *
 * CRITICAL FEATURES:
 * - Idempotency protection (prevents duplicate rewards/receipts)
 * - Transactional processing
 * - Audit trail for reconciliation
 * - Metrics for business intelligence
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final FundReservationService fundReservationService;
    private final ReceiptGenerationService receiptGenerationService;
    private final SettlementService settlementService;
    private final RewardsService rewardsService;
    private final AnalyticsService analyticsService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    private static final String METRIC_PREFIX = "payment.completed.consumer";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    @KafkaListener(
        topics = "${kafka.topics.payment-completed:payment-completed}",
        groupId = "${kafka.consumer-groups.payment-completed:payment-completed-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumers.payment-completed.concurrency:5}"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
    public void handlePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            Acknowledgment acknowledgment) {

        Timer.Sample timer = Timer.start(meterRegistry);
        String idempotencyKey = "payment-completed:" + event.getPaymentId();
        UUID operationId = UUID.randomUUID();

        log.info("📥 Processing payment completion: paymentId={}, amount={}, customerId={}, merchantId={}",
                event.getPaymentId(), event.getAmount(), event.getCustomerId(), event.getMerchantId());

        try {
            // Idempotency check
            if (!idempotencyService.startOperation(idempotencyKey, operationId, IDEMPOTENCY_TTL)) {
                log.warn("⚠️ DUPLICATE - Payment completion already processed: paymentId={}",
                        event.getPaymentId());
                recordMetric("duplicate", event);
                acknowledgment.acknowledge();
                return;
            }

            // Validate
            validateEvent(event);

            // Process completion
            processPaymentCompletion(event, operationId);

            // Complete operation
            idempotencyService.completeOperation(
                idempotencyKey,
                operationId,
                Map.of(
                    "status", "COMPLETED",
                    "paymentId", event.getPaymentId().toString(),
                    "amount", event.getAmount().toString(),
                    "receiptGenerated", "true",
                    "rewardsProcessed", "true"
                ),
                IDEMPOTENCY_TTL
            );

            acknowledgment.acknowledge();
            recordMetric("success", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "success"));

            log.info("✅ Payment completion processed: paymentId={}", event.getPaymentId());

        } catch (Exception e) {
            log.error("❌ Payment completion processing failed: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);

            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            recordMetric("failure", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "failure"));

            throw new PaymentProcessingException("Payment completion processing failed", e);
        }
    }

    /**
     * Processes payment completion with all post-payment activities.
     */
    private void processPaymentCompletion(PaymentCompletedEvent event, UUID operationId) {
        log.debug("Processing payment completion workflow: paymentId={}", event.getPaymentId());

        // STEP 1: Release fund reservations
        log.debug("Step 1/7: Releasing fund reservations: paymentId={}", event.getPaymentId());

        fundReservationService.releaseReservation(
            event.getPaymentId(),
            event.getCustomerId(),
            "PAYMENT_COMPLETED"
        );

        log.debug("✅ Fund reservations released: paymentId={}", event.getPaymentId());

        // STEP 2: Generate receipt
        log.debug("Step 2/7: Generating receipt: paymentId={}", event.getPaymentId());

        String receiptId = receiptGenerationService.generateReceipt(
            event.getPaymentId(),
            event.getCustomerId(),
            event.getMerchantId(),
            event.getAmount(),
            event.getCurrency(),
            event.getCompletedAt()
        );

        log.debug("✅ Receipt generated: paymentId={}, receiptId={}", event.getPaymentId(), receiptId);

        // STEP 3: Update merchant settlement batch
        log.debug("Step 3/7: Updating settlement batch: merchantId={}", event.getMerchantId());

        settlementService.addToSettlementBatch(
            event.getMerchantId(),
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency()
        );

        log.debug("✅ Settlement batch updated: merchantId={}", event.getMerchantId());

        // STEP 4: Process rewards/cashback
        log.debug("Step 4/7: Processing rewards: customerId={}", event.getCustomerId());

        rewardsService.processPaymentRewards(
            event.getCustomerId(),
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            event.getMerchantCategory()
        );

        log.debug("✅ Rewards processed: customerId={}", event.getCustomerId());

        // STEP 5: Record analytics
        log.debug("Step 5/7: Recording analytics: paymentId={}", event.getPaymentId());

        analyticsService.recordPaymentCompletion(
            event.getPaymentId(),
            event.getCustomerId(),
            event.getMerchantId(),
            event.getAmount(),
            event.getCurrency(),
            event.getPaymentMethod(),
            event.getMerchantCategory(),
            event.getCompletedAt()
        );

        log.debug("✅ Analytics recorded: paymentId={}", event.getPaymentId());

        // STEP 6: Update payment status
        log.debug("Step 6/7: Updating payment status: paymentId={}", event.getPaymentId());

        paymentService.updatePaymentStatus(
            event.getPaymentId(),
            "COMPLETED",
            "Payment successfully completed and settled"
        );

        log.debug("✅ Payment status updated: paymentId={}", event.getPaymentId());

        // STEP 7: Send notifications
        log.debug("Step 7/7: Sending completion notifications: paymentId={}", event.getPaymentId());

        notificationService.sendPaymentCompletedNotifications(
            event.getCustomerId(),
            event.getMerchantId(),
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            receiptId
        );

        log.debug("✅ Notifications sent: paymentId={}", event.getPaymentId());

        log.info("✅ All payment completion steps finished: paymentId={}", event.getPaymentId());
    }

    private void validateEvent(PaymentCompletedEvent event) {
        if (event.getPaymentId() == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }
        if (event.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (event.getMerchantId() == null) {
            throw new IllegalArgumentException("Merchant ID cannot be null");
        }
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private void recordMetric(String result, PaymentCompletedEvent event) {
        Counter.builder(METRIC_PREFIX + ".processed")
            .tag("result", result)
            .tag("currency", event.getCurrency())
            .description("Payment completed events processed")
            .register(meterRegistry)
            .increment();
    }
}
