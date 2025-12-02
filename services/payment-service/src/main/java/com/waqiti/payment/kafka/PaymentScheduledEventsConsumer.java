package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentScheduledEvent;
import com.waqiti.payment.service.PaymentSchedulingService;
import com.waqiti.payment.service.PaymentExecutionService;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentScheduledEventsConsumer {

    private final PaymentSchedulingService schedulingService;
    private final PaymentExecutionService executionService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("payment_scheduled_events_processed_total")
            .description("Total number of successfully processed payment scheduled events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_scheduled_events_errors_total")
            .description("Total number of payment scheduled events processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_scheduled_events_processing_duration")
            .description("Time taken to process payment scheduled events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment-scheduled-events", "scheduled-payments", "payment-automation-events"},
        groupId = "payment-scheduled-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "payment-scheduled-events", fallbackMethod = "handlePaymentScheduledEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentScheduledEvent(
            @Payload PaymentScheduledEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("scheduled-%s-p%d-o%d", event.getPaymentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPaymentId(), event.getScheduledDate(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing payment scheduled event: paymentId={}, scheduledDate={}, amount={}",
                event.getPaymentId(), event.getScheduledDate(), event.getAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case PAYMENT_SCHEDULED:
                    processPaymentScheduled(event, correlationId);
                    break;

                case PAYMENT_DUE:
                    processPaymentDue(event, correlationId);
                    break;

                case PAYMENT_EXECUTION_READY:
                    processPaymentExecutionReady(event, correlationId);
                    break;

                case PAYMENT_EXECUTION_DELAYED:
                    processPaymentExecutionDelayed(event, correlationId);
                    break;

                case PAYMENT_RESCHEDULED:
                    processPaymentRescheduled(event, correlationId);
                    break;

                case PAYMENT_CANCELLED:
                    processPaymentCancelled(event, correlationId);
                    break;

                default:
                    log.warn("Unknown payment scheduled event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_SCHEDULED_EVENT_PROCESSED", event.getPaymentId(),
                Map.of("eventType", event.getEventType(), "scheduledDate", event.getScheduledDate(),
                    "amount", event.getAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment scheduled event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-scheduled-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handlePaymentScheduledEventFallback(
            PaymentScheduledEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("scheduled-fallback-%s-p%d-o%d", event.getPaymentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment scheduled event: paymentId={}, error={}",
            event.getPaymentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-scheduled-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Payment Scheduled Events Circuit Breaker Triggered",
                String.format("Payment %s scheduled event processing failed: %s", event.getPaymentId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentScheduledEvent(
            @Payload PaymentScheduledEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-scheduled-%s-%d", event.getPaymentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment scheduled event permanently failed: paymentId={}, topic={}, error={}",
            event.getPaymentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_SCHEDULED_EVENT_DLT", event.getPaymentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Scheduled Event Dead Letter",
                String.format("Payment %s scheduled event sent to DLT: %s", event.getPaymentId(), exceptionMessage),
                Map.of("paymentId", event.getPaymentId(), "topic", topic, "correlationId", correlationId)
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

    private void processPaymentScheduled(PaymentScheduledEvent event, String correlationId) {
        // Register the scheduled payment
        schedulingService.registerScheduledPayment(
            event.getPaymentId(),
            event.getCustomerId(),
            event.getMerchantId(),
            event.getAmount(),
            event.getCurrency(),
            event.getScheduledDate(),
            event.getPaymentMethodToken(),
            correlationId
        );

        // Send confirmation notification
        notificationService.sendNotification(
            event.getCustomerId(),
            "Payment Scheduled",
            String.format("Your payment of %s %s has been scheduled for %s",
                event.getAmount(), event.getCurrency(), event.getScheduledDate()),
            correlationId
        );

        // Update metrics
        metricsService.incrementScheduledPayments();

        // Schedule execution trigger
        schedulingService.scheduleExecutionTrigger(event.getPaymentId(), event.getScheduledDate(), correlationId);

        log.info("Payment scheduled successfully: paymentId={}, scheduledDate={}",
            event.getPaymentId(), event.getScheduledDate());
    }

    private void processPaymentDue(PaymentScheduledEvent event, String correlationId) {
        // Check if payment is ready for execution
        boolean readyForExecution = schedulingService.isPaymentReadyForExecution(event.getPaymentId());

        if (readyForExecution) {
            // Trigger payment execution
            kafkaTemplate.send("payment-execution-triggers", Map.of(
                "paymentId", event.getPaymentId(),
                "customerId", event.getCustomerId(),
                "amount", event.getAmount(),
                "currency", event.getCurrency(),
                "paymentMethodToken", event.getPaymentMethodToken(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Payment execution triggered: paymentId={}", event.getPaymentId());
        } else {
            // Payment not ready, send delay event
            kafkaTemplate.send("payment-scheduled-events", Map.of(
                "paymentId", event.getPaymentId(),
                "eventType", "PAYMENT_EXECUTION_DELAYED",
                "reason", "NOT_READY_FOR_EXECUTION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.warn("Payment execution delayed - not ready: paymentId={}", event.getPaymentId());
        }

        metricsService.incrementPaymentsDue();
    }

    private void processPaymentExecutionReady(PaymentScheduledEvent event, String correlationId) {
        // Validate payment prerequisites
        boolean validationPassed = executionService.validatePaymentPrerequisites(
            event.getPaymentId(),
            event.getCustomerId(),
            event.getPaymentMethodToken(),
            event.getAmount()
        );

        if (validationPassed) {
            // Execute the payment
            executionService.executeScheduledPayment(
                event.getPaymentId(),
                event.getCustomerId(),
                event.getMerchantId(),
                event.getAmount(),
                event.getCurrency(),
                event.getPaymentMethodToken(),
                correlationId
            );

            log.info("Scheduled payment executed: paymentId={}", event.getPaymentId());
        } else {
            // Validation failed, handle accordingly
            handlePaymentValidationFailure(event, correlationId);
        }

        metricsService.incrementPaymentsExecuted();
    }

    private void processPaymentExecutionDelayed(PaymentScheduledEvent event, String correlationId) {
        // Record the delay
        schedulingService.recordPaymentDelay(
            event.getPaymentId(),
            event.getDelayReason(),
            event.getNewScheduledDate(),
            correlationId
        );

        // Send delay notification to customer
        if (event.getNotifyCustomer()) {
            notificationService.sendNotification(
                event.getCustomerId(),
                "Payment Delayed",
                String.format("Your scheduled payment has been delayed. New execution date: %s. Reason: %s",
                    event.getNewScheduledDate(), event.getDelayReason()),
                correlationId
            );
        }

        // Schedule retry if applicable
        if (schedulingService.shouldRetryDelayedPayment(event.getDelayReason())) {
            schedulingService.schedulePaymentRetry(
                event.getPaymentId(),
                event.getNewScheduledDate(),
                correlationId
            );
        }

        metricsService.incrementPaymentDelays(event.getDelayReason());

        log.info("Payment execution delayed: paymentId={}, reason={}, newDate={}",
            event.getPaymentId(), event.getDelayReason(), event.getNewScheduledDate());
    }

    private void processPaymentRescheduled(PaymentScheduledEvent event, String correlationId) {
        // Update the payment schedule
        schedulingService.updatePaymentSchedule(
            event.getPaymentId(),
            event.getScheduledDate(),
            event.getRescheduleReason(),
            correlationId
        );

        // Send reschedule confirmation
        notificationService.sendNotification(
            event.getCustomerId(),
            "Payment Rescheduled",
            String.format("Your payment has been rescheduled to %s", event.getScheduledDate()),
            correlationId
        );

        // Update any related recurring payments
        if (event.getRecurringPaymentId() != null) {
            kafkaTemplate.send("recurring-payment-updates", Map.of(
                "recurringPaymentId", event.getRecurringPaymentId(),
                "paymentId", event.getPaymentId(),
                "newScheduledDate", event.getScheduledDate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementPaymentReschedules();

        log.info("Payment rescheduled: paymentId={}, newDate={}",
            event.getPaymentId(), event.getScheduledDate());
    }

    private void processPaymentCancelled(PaymentScheduledEvent event, String correlationId) {
        // Cancel the scheduled payment
        schedulingService.cancelScheduledPayment(
            event.getPaymentId(),
            event.getCancellationReason(),
            event.getCancelledBy(),
            correlationId
        );

        // Send cancellation confirmation
        notificationService.sendNotification(
            event.getCustomerId(),
            "Payment Cancelled",
            String.format("Your scheduled payment has been cancelled. Reason: %s", event.getCancellationReason()),
            correlationId
        );

        // Handle recurring payment cancellation if applicable
        if (event.getRecurringPaymentId() != null) {
            kafkaTemplate.send("recurring-payment-cancellations", Map.of(
                "recurringPaymentId", event.getRecurringPaymentId(),
                "paymentId", event.getPaymentId(),
                "cancellationReason", event.getCancellationReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementPaymentCancellations(event.getCancellationReason());

        log.info("Payment cancelled: paymentId={}, reason={}",
            event.getPaymentId(), event.getCancellationReason());
    }

    private void handlePaymentValidationFailure(PaymentScheduledEvent event, String correlationId) {
        // Log validation failure
        schedulingService.recordValidationFailure(
            event.getPaymentId(),
            event.getValidationErrors(),
            correlationId
        );

        // Determine if payment should be retried or failed
        if (schedulingService.shouldRetryAfterValidationFailure(event.getValidationErrors())) {
            // Schedule retry
            LocalDateTime retryDate = schedulingService.calculateRetryDate(event.getPaymentId());

            kafkaTemplate.send("payment-scheduled-events", Map.of(
                "paymentId", event.getPaymentId(),
                "eventType", "PAYMENT_RESCHEDULED",
                "scheduledDate", retryDate,
                "rescheduleReason", "VALIDATION_FAILURE_RETRY",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Fail the payment permanently
            kafkaTemplate.send("payment-failures", Map.of(
                "paymentId", event.getPaymentId(),
                "failureReason", "VALIDATION_FAILURE",
                "validationErrors", event.getValidationErrors(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Notify customer of failure
            notificationService.sendNotification(
                event.getCustomerId(),
                "Payment Failed",
                "Your scheduled payment could not be processed due to validation issues. Please update your payment method.",
                correlationId
            );
        }

        metricsService.incrementValidationFailures();

        log.warn("Payment validation failed: paymentId={}, errors={}",
            event.getPaymentId(), event.getValidationErrors());
    }
}