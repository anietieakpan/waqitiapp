package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.SubscriptionPaymentEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.SubscriptionPaymentService;
import com.waqiti.payment.model.SubscriptionPayment;
import com.waqiti.payment.repository.SubscriptionPaymentRepository;
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
 * Consumer for processing subscription payment events.
 * Handles subscription billing, renewals, and payment management.
 */
@Slf4j
@Component
public class SubscriptionPaymentConsumer extends BaseKafkaConsumer<SubscriptionPaymentEvent> {

    private static final String TOPIC = "subscription-payment-events";

    private final SubscriptionPaymentService subscriptionPaymentService;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter renewalCounter;
    private final Timer processingTimer;

    @Autowired
    public SubscriptionPaymentConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            SubscriptionPaymentService subscriptionPaymentService,
            SubscriptionPaymentRepository subscriptionPaymentRepository) {
        super(objectMapper, TOPIC);
        this.subscriptionPaymentService = subscriptionPaymentService;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;

        this.processedCounter = Counter.builder("subscription_payment_processed_total")
                .description("Total subscription payments processed")
                .register(meterRegistry);
        this.renewalCounter = Counter.builder("subscription_renewal_total")
                .description("Total subscription renewals processed")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("subscription_payment_processing_duration")
                .description("Time taken to process subscription payments")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-subscription-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing subscription payment event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            SubscriptionPaymentEvent event = deserializeEvent(record.value(), SubscriptionPaymentEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("Subscription payment already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the subscription payment
            processSubscriptionPayment(event);

            processedCounter.increment();
            if ("RENEWAL".equals(event.getPaymentType())) {
                renewalCounter.increment();
            }

            log.info("Successfully processed subscription payment: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing subscription payment event: {}", record.value(), e);
            throw new RuntimeException("Failed to process subscription payment event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processSubscriptionPayment(SubscriptionPaymentEvent event) {
        try {
            // Create subscription payment record
            SubscriptionPayment payment = createSubscriptionPayment(event);

            // Process the subscription payment
            boolean success = subscriptionPaymentService.processSubscriptionPayment(
                event.getTransactionId(), event.getSubscriptionId(),
                event.getAmount(), event.getPaymentType()
            );

            if (success) {
                payment.setStatus("COMPLETED");
                payment.setProcessedAt(LocalDateTime.now());

                // Handle subscription renewal
                if ("RENEWAL".equals(event.getPaymentType())) {
                    subscriptionPaymentService.renewSubscription(event.getSubscriptionId());
                }
            } else {
                payment.setStatus("FAILED");
                subscriptionPaymentService.handleSubscriptionPaymentFailure(
                    event.getSubscriptionId(), event.getTransactionId()
                );
            }

            // Save the payment
            subscriptionPaymentRepository.save(payment);

            // Send notifications
            subscriptionPaymentService.sendCustomerNotification(event.getCustomerId(),
                success ? "SUBSCRIPTION_PAYMENT_SUCCESS" : "SUBSCRIPTION_PAYMENT_FAILED", payment);

            log.info("Processed subscription payment: {} - Status: {} (Type: {})",
                    event.getTransactionId(), payment.getStatus(), event.getPaymentType());

        } catch (Exception e) {
            log.error("Error processing subscription payment: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process subscription payment", e);
        }
    }

    private SubscriptionPayment createSubscriptionPayment(SubscriptionPaymentEvent event) {
        return SubscriptionPayment.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .subscriptionId(event.getSubscriptionId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .paymentType(event.getPaymentType())
                .billingPeriod(event.getBillingPeriod())
                .serviceStartDate(event.getServiceStartDate())
                .serviceEndDate(event.getServiceEndDate())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return subscriptionPaymentRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(SubscriptionPaymentEvent event) {
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
        log.error("Subscription payment processing error: {}", message, error);
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed subscription payment event - Key: {}, Time: {}ms", key, processingTime);
    }
}