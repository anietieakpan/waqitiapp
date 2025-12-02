package com.waqiti.payment.kafka;

import com.waqiti.common.events.RecurringPaymentCreatedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.service.RecurringPaymentService;
import com.waqiti.payment.service.PaymentSchedulingService;
import com.waqiti.payment.service.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecurringPaymentCreatedConsumer {

    private final RecurringPaymentService recurringPaymentService;
    private final PaymentSchedulingService schedulingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("recurring_payment_created_processed_total")
            .description("Total number of successfully processed recurring payment created events")
            .register(meterRegistry);
        errorCounter = Counter.builder("recurring_payment_created_errors_total")
            .description("Total number of recurring payment created processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("recurring_payment_created_processing_duration")
            .description("Time taken to process recurring payment created events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"recurring-payment-created", "subscription-created", "auto-pay-setup"},
        groupId = "recurring-payment-created-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "recurring-payment-created", fallbackMethod = "handleRecurringPaymentCreatedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleRecurringPaymentCreatedEvent(
            @Payload RecurringPaymentCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("recurring-%s-p%d-o%d", event.getRecurringPaymentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getRecurringPaymentId(), event.getCustomerId(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing recurring payment created: recurringPaymentId={}, customerId={}, amount={}, frequency={}",
                event.getRecurringPaymentId(), event.getCustomerId(), event.getAmount(), event.getFrequency());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Create recurring payment record
            createRecurringPaymentRecord(event, correlationId);

            // Schedule first payment
            scheduleFirstPayment(event, correlationId);

            // Setup payment schedule
            setupPaymentSchedule(event, correlationId);

            // Validate payment method
            validatePaymentMethod(event, correlationId);

            // Send confirmation notification
            sendConfirmationNotification(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("RECURRING_PAYMENT_CREATED_PROCESSED", event.getRecurringPaymentId(),
                Map.of("customerId", event.getCustomerId(), "amount", event.getAmount(),
                    "frequency", event.getFrequency(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing recurring payment created event: partition={}, offset={}, error={}",
                partition, offset, e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("recurring-payment-created-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: offset={}, destination={}, category={}",
                        offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Recurring payment created event processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleRecurringPaymentCreatedEventFallback(
            RecurringPaymentCreatedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("recurring-fallback-%s-p%d-o%d", event.getRecurringPaymentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for recurring payment created: recurringPaymentId={}, error={}",
            event.getRecurringPaymentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("recurring-payment-created-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Recurring Payment Created Circuit Breaker Triggered",
                String.format("Recurring payment %s creation failed: %s", event.getRecurringPaymentId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltRecurringPaymentCreatedEvent(
            @Payload RecurringPaymentCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-recurring-%s-%d", event.getRecurringPaymentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Recurring payment created permanently failed: recurringPaymentId={}, topic={}, error={}",
            event.getRecurringPaymentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("RECURRING_PAYMENT_CREATED_DLT_EVENT", event.getRecurringPaymentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "customerId", event.getCustomerId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Recurring Payment Created Dead Letter Event",
                String.format("Recurring payment %s creation sent to DLT: %s", event.getRecurringPaymentId(), exceptionMessage),
                Map.of("recurringPaymentId", event.getRecurringPaymentId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void createRecurringPaymentRecord(RecurringPaymentCreatedEvent event, String correlationId) {
        recurringPaymentService.createRecurringPayment(
            event.getRecurringPaymentId(),
            event.getCustomerId(),
            event.getMerchantId(),
            event.getAmount(),
            event.getCurrency(),
            event.getFrequency(),
            event.getStartDate(),
            event.getEndDate(),
            event.getPaymentMethodToken(),
            event.getDescription(),
            event.getMetadata()
        );

        // Record metrics
        metricsService.incrementRecurringPaymentsCreated(event.getFrequency());
        metricsService.recordRecurringPaymentAmount(event.getAmount(), event.getCurrency());

        log.info("Recurring payment record created: recurringPaymentId={}, amount={}, frequency={}",
            event.getRecurringPaymentId(), event.getAmount(), event.getFrequency());
    }

    private void scheduleFirstPayment(RecurringPaymentCreatedEvent event, String correlationId) {
        // Calculate next payment date based on start date and frequency
        var nextPaymentDate = schedulingService.calculateNextPaymentDate(
            event.getStartDate(),
            event.getFrequency()
        );

        // Schedule the first payment
        schedulingService.schedulePayment(
            event.getRecurringPaymentId(),
            nextPaymentDate,
            event.getAmount(),
            event.getCurrency(),
            event.getPaymentMethodToken(),
            correlationId
        );

        // Send event to payment scheduler
        kafkaTemplate.send("payment-scheduled-events", Map.of(
            "recurringPaymentId", event.getRecurringPaymentId(),
            "paymentDate", nextPaymentDate,
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("First payment scheduled: recurringPaymentId={}, nextPaymentDate={}",
            event.getRecurringPaymentId(), nextPaymentDate);
    }

    private void setupPaymentSchedule(RecurringPaymentCreatedEvent event, String correlationId) {
        // Create payment schedule for the entire period
        schedulingService.createPaymentSchedule(
            event.getRecurringPaymentId(),
            event.getStartDate(),
            event.getEndDate(),
            event.getFrequency(),
            event.getAmount(),
            event.getCurrency()
        );

        // If this is a subscription, set up billing cycle
        if (event.getSubscriptionId() != null) {
            kafkaTemplate.send("subscription-billing-events", Map.of(
                "subscriptionId", event.getSubscriptionId(),
                "recurringPaymentId", event.getRecurringPaymentId(),
                "billingCycle", event.getFrequency(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Payment schedule setup: recurringPaymentId={}, frequency={}",
            event.getRecurringPaymentId(), event.getFrequency());
    }

    private void validatePaymentMethod(RecurringPaymentCreatedEvent event, String correlationId) {
        // Validate payment method is active and valid for recurring payments
        if (!recurringPaymentService.isPaymentMethodValidForRecurring(event.getPaymentMethodToken())) {
            log.warn("Invalid payment method for recurring payment: recurringPaymentId={}, paymentMethodToken={}",
                event.getRecurringPaymentId(), event.getPaymentMethodToken());

            // Send validation failure event
            kafkaTemplate.send("payment-validation-events", Map.of(
                "type", "INVALID_PAYMENT_METHOD",
                "recurringPaymentId", event.getRecurringPaymentId(),
                "paymentMethodToken", event.getPaymentMethodToken(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Notify customer
            kafkaTemplate.send("customer-notifications", Map.of(
                "type", "PAYMENT_METHOD_INVALID",
                "customerId", event.getCustomerId(),
                "recurringPaymentId", event.getRecurringPaymentId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            throw new IllegalStateException("Invalid payment method for recurring payment");
        }

        // Check if payment method will expire soon
        if (recurringPaymentService.willPaymentMethodExpireSoon(event.getPaymentMethodToken(), event.getEndDate())) {
            kafkaTemplate.send("customer-notifications", Map.of(
                "type", "PAYMENT_METHOD_EXPIRING",
                "customerId", event.getCustomerId(),
                "recurringPaymentId", event.getRecurringPaymentId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Payment method validated for recurring payment: recurringPaymentId={}",
            event.getRecurringPaymentId());
    }

    private void sendConfirmationNotification(RecurringPaymentCreatedEvent event, String correlationId) {
        // Send confirmation to customer
        notificationService.sendNotification(
            event.getCustomerId(),
            "Recurring Payment Setup Complete",
            String.format("Your recurring payment of %s %s to %s has been set up successfully. " +
                    "The first payment will be processed on %s.",
                event.getAmount(), event.getCurrency(), event.getMerchantName(), event.getStartDate()),
            correlationId
        );

        // Send confirmation email
        kafkaTemplate.send("email-notifications", Map.of(
            "type", "RECURRING_PAYMENT_CONFIRMATION",
            "customerId", event.getCustomerId(),
            "recurringPaymentId", event.getRecurringPaymentId(),
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "frequency", event.getFrequency(),
            "merchantName", event.getMerchantName(),
            "startDate", event.getStartDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // If this is a subscription, send subscription welcome
        if (event.getSubscriptionId() != null) {
            kafkaTemplate.send("subscription-notifications", Map.of(
                "type", "SUBSCRIPTION_WELCOME",
                "customerId", event.getCustomerId(),
                "subscriptionId", event.getSubscriptionId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Confirmation notification sent: recurringPaymentId={}, customerId={}",
            event.getRecurringPaymentId(), event.getCustomerId());
    }
}