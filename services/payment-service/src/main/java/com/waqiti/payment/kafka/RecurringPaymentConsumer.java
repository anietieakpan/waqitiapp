package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.RecurringPaymentEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import com.waqiti.payment.service.RecurringPaymentService;
import com.waqiti.payment.model.RecurringPayment;
import com.waqiti.payment.repository.RecurringPaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consumer for processing recurring payment events.
 * Handles subscription payments, automatic billing, and payment scheduling.
 */
@Slf4j
@Component
public class RecurringPaymentConsumer extends BaseKafkaConsumer<RecurringPaymentEvent> {

    private static final String TOPIC = "recurring-payment-events";

    private final RecurringPaymentService recurringPaymentService;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final RedisIdempotencyService idempotencyService;

    // Metrics
    private final Counter processedCounter;
    private final Counter failedPaymentCounter;
    private final Timer processingTimer;

    @Autowired
    public RecurringPaymentConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            RecurringPaymentService recurringPaymentService,
            RecurringPaymentRepository recurringPaymentRepository,
            RedisIdempotencyService idempotencyService) {
        super(objectMapper, TOPIC);
        this.recurringPaymentService = recurringPaymentService;
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.idempotencyService = idempotencyService;

        this.processedCounter = Counter.builder("recurring_payment_processed_total")
                .description("Total recurring payments processed")
                .register(meterRegistry);
        this.failedPaymentCounter = Counter.builder("recurring_payment_failed_total")
                .description("Total recurring payments failed")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("recurring_payment_processing_duration")
                .description("Time taken to process recurring payments")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-recurring-group")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing recurring payment event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            RecurringPaymentEvent event = deserializeEvent(record.value(), RecurringPaymentEvent.class);

            // Validate required fields
            validateEvent(event);

            // Build idempotency key
            String idempotencyKey = idempotencyService.buildIdempotencyKey(
                "payment-service",
                "RecurringPaymentEvent",
                event.getTransactionId()
            );

            // Universal idempotency check (30-day TTL for recurring payments)
            if (idempotencyService.isProcessed(idempotencyKey)) {
                log.info("⏭️ Recurring payment already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the recurring payment
            processRecurringPayment(event);

            // Mark as processed (30-day TTL for financial transactions)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            processedCounter.increment();
            log.info("Successfully processed recurring payment: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing recurring payment event: {}", record.value(), e);
            throw new RuntimeException("Failed to process recurring payment event", e);
        } finally {
            processingTimer.stop(sample);
        }
    }

    private void processRecurringPayment(RecurringPaymentEvent event) {
        try {
            // Create recurring payment record
            RecurringPayment payment = createRecurringPayment(event);

            // Check subscription status
            boolean subscriptionActive = recurringPaymentService.isSubscriptionActive(event.getSubscriptionId());
            if (!subscriptionActive) {
                handleInactiveSubscription(event, payment);
                return;
            }

            // Process the payment
            boolean success = recurringPaymentService.processRecurringPayment(
                event.getTransactionId(), event.getSubscriptionId(),
                event.getAmount(), event.getPaymentMethodId()
            );

            if (success) {
                payment.setStatus("COMPLETED");
                payment.setProcessedAt(LocalDateTime.now());

                // Schedule next payment
                recurringPaymentService.scheduleNextPayment(event.getSubscriptionId());
            } else {
                payment.setStatus("FAILED");
                failedPaymentCounter.increment();
                recurringPaymentService.handlePaymentFailure(event.getSubscriptionId(), event.getTransactionId());
            }

            // Save the payment
            recurringPaymentRepository.save(payment);

            // Send notifications
            recurringPaymentService.sendCustomerNotification(event.getCustomerId(),
                success ? "RECURRING_PAYMENT_SUCCESS" : "RECURRING_PAYMENT_FAILED", payment);

            log.info("Processed recurring payment: {} - Status: {}",
                    event.getTransactionId(), payment.getStatus());

        } catch (Exception e) {
            log.error("Error processing recurring payment: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process recurring payment", e);
        }
    }

    private RecurringPayment createRecurringPayment(RecurringPaymentEvent event) {
        return RecurringPayment.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .subscriptionId(event.getSubscriptionId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .paymentMethodId(event.getPaymentMethodId())
                .scheduledDate(event.getScheduledDate())
                .attemptNumber(event.getAttemptNumber())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private void handleInactiveSubscription(RecurringPaymentEvent event, RecurringPayment payment) {
        try {
            payment.setStatus("SUBSCRIPTION_INACTIVE");
            payment.setErrorMessage("Subscription is not active");

            recurringPaymentRepository.save(payment);

            log.warn("Recurring payment skipped - inactive subscription: {} for subscription: {}",
                    event.getTransactionId(), event.getSubscriptionId());

        } catch (Exception e) {
            log.error("Error handling inactive subscription: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle inactive subscription", e);
        }
    }

    private void validateEvent(RecurringPaymentEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getSubscriptionId() == null || event.getSubscriptionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Subscription ID cannot be null or empty");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Recurring payment processing error: {}", message, error);
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed recurring payment event - Key: {}, Time: {}ms", key, processingTime);
    }
}