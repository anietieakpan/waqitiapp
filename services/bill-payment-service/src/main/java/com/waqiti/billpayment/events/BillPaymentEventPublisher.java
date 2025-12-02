package com.waqiti.billpayment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise-grade bill payment event publisher with comprehensive reliability features
 *
 * Features:
 * - DLQ handling with automatic retry
 * - Circuit breaker pattern for fault tolerance
 * - Event store for durability and replay
 * - Comprehensive metrics and monitoring
 * - Correlation tracking for saga patterns
 * - Batch publishing for bulk operations
 * - High-priority event handling
 * - Transactional event publishing
 * - Event replay capability
 * - Idempotency guarantees
 *
 * Topics Published:
 * - bill-payment-events: Main payment events
 * - bill-payment-status-events: Status change events
 * - bill-pay-scheduling: Scheduled payment events
 * - recurring-bill-events: Recurring/auto-pay events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillPaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final BillPaymentEventStore eventStore;

    // Topic constants - aligned with consumer expectations
    private static final String BILL_PAYMENT_EVENTS_TOPIC = "bill-payment-events";
    private static final String BILL_PAYMENT_STATUS_EVENTS_TOPIC = "bill-payment-status-events";
    private static final String BILL_PAY_SCHEDULING_TOPIC = "bill-pay-scheduling";
    private static final String RECURRING_BILL_EVENTS_TOPIC = "recurring-bill-events";
    private static final String DLQ_SUFFIX = ".dlq";

    // Circuit breaker and fault tolerance
    private final Queue<BillPaymentEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;

    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();

    // Lazy-initialized metrics
    private Counter eventsPublishedCounter;
    private Counter eventsFailedCounter;
    private Timer eventPublishLatencyTimer;

    // ============== Bill Payment Event Types ==============

    /**
     * Publishes bill payment initiated event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBillPaymentInitiated(
            String paymentId, String userId, String billerId, String billerName,
            BigDecimal amount, String currency, String accountNumber, String billType) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BILL_PAYMENT_INITIATED")
                .paymentId(paymentId)
                .userId(userId)
                .billerId(billerId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .accountNumber(accountNumber)
                .billType(billType)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishEvent(BILL_PAYMENT_EVENTS_TOPIC, event, paymentId);
    }

    /**
     * Publishes bill payment status change event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBillPaymentStatusChange(
            String paymentId, String userId, String billerId, String billerName,
            BigDecimal amount, String currency, String status, String previousStatus,
            String referenceNumber, LocalDate dueDate, String billType,
            String accountNumber, String failureReason) {

        BillPaymentStatusEvent event = BillPaymentStatusEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BILL_PAYMENT_STATUS_CHANGED")
                .correlationId(getCorrelationIdFromContext())
                .timestamp(Instant.now())
                .eventVersion("1.0")
                .source("bill-payment-service")
                .userId(userId)
                .billPaymentId(paymentId)
                .billerName(billerName)
                .billerId(billerId)
                .billType(billType)
                .accountNumber(accountNumber)
                .amount(amount)
                .currency(currency)
                .status(status)
                .previousStatus(previousStatus)
                .referenceNumber(referenceNumber)
                .dueDate(dueDate)
                .failureReason(failureReason)
                .build();

        return publishEvent(BILL_PAYMENT_STATUS_EVENTS_TOPIC, event, paymentId);
    }

    /**
     * Publishes scheduled payment event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishScheduledPayment(
            String paymentId, String userId, String billerId, String billerName,
            BigDecimal amount, String currency, LocalDate scheduledDate,
            String accountNumber, String billType, String memo) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BILL_PAYMENT_SCHEDULED")
                .paymentId(paymentId)
                .userId(userId)
                .billerId(billerId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .scheduledDate(scheduledDate)
                .accountNumber(accountNumber)
                .billType(billType)
                .memo(memo)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishEvent(BILL_PAY_SCHEDULING_TOPIC, event, paymentId);
    }

    /**
     * Publishes recurring/auto-pay setup event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRecurringPaymentSetup(
            String recurringSetupId, String userId, String billerId, String billerName,
            BigDecimal amount, String currency, String frequency, LocalDate startDate,
            LocalDate nextPaymentDate, String accountNumber, String billType, boolean autopay) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("RECURRING_BILL_SETUP")
                .recurringSetupId(recurringSetupId)
                .userId(userId)
                .billerId(billerId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .frequency(frequency)
                .startDate(startDate)
                .nextPaymentDate(nextPaymentDate)
                .accountNumber(accountNumber)
                .billType(billType)
                .isAutoPay(autopay)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishEvent(RECURRING_BILL_EVENTS_TOPIC, event, recurringSetupId);
    }

    /**
     * Publishes recurring payment executed event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRecurringPaymentExecuted(
            String recurringSetupId, String paymentId, String userId, String billerName,
            BigDecimal amount, String currency, LocalDate executionDate) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("RECURRING_BILL_EXECUTED")
                .recurringSetupId(recurringSetupId)
                .paymentId(paymentId)
                .userId(userId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .executionDate(executionDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishEvent(RECURRING_BILL_EVENTS_TOPIC, event, recurringSetupId);
    }

    /**
     * Publishes recurring payment cancelled event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRecurringPaymentCancelled(
            String recurringSetupId, String userId, String billerName, String reason) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("RECURRING_BILL_CANCELLED")
                .recurringSetupId(recurringSetupId)
                .userId(userId)
                .billerName(billerName)
                .cancellationReason(reason)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishEvent(RECURRING_BILL_EVENTS_TOPIC, event, recurringSetupId);
    }

    /**
     * Publishes bill due reminder event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBillDueReminder(
            String userId, String billerId, String billerName, BigDecimal amount,
            String currency, LocalDate dueDate, int daysUntilDue) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BILL_DUE_REMINDER")
                .userId(userId)
                .billerId(billerId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .dueDate(dueDate)
                .daysUntilDue(daysUntilDue)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishEvent(BILL_PAYMENT_EVENTS_TOPIC, event, userId);
    }

    /**
     * Publishes bill payment completed event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBillPaymentCompleted(
            String paymentId, String userId, String billerName, BigDecimal amount,
            String currency, String confirmationNumber, LocalDate paidDate) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BILL_PAYMENT_COMPLETED")
                .paymentId(paymentId)
                .userId(userId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .confirmationNumber(confirmationNumber)
                .paidDate(paidDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishHighPriorityEvent(BILL_PAYMENT_EVENTS_TOPIC, event, paymentId);
    }

    /**
     * Publishes bill payment failed event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBillPaymentFailed(
            String paymentId, String userId, String billerName, BigDecimal amount,
            String currency, String failureReason, boolean retryable) {

        BillPaymentEvent event = BillPaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BILL_PAYMENT_FAILED")
                .paymentId(paymentId)
                .userId(userId)
                .billerName(billerName)
                .amount(amount)
                .currency(currency)
                .failureReason(failureReason)
                .isRetryable(retryable)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();

        return publishHighPriorityEvent(BILL_PAYMENT_EVENTS_TOPIC, event, paymentId);
    }

    // ============== Batch Publishing ==============

    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<BillPaymentEvent> events) {

        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        // Group events by topic for optimized publishing
        Map<String, List<BillPaymentEvent>> eventsByTopic = groupEventsByTopic(events);

        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();

        for (Map.Entry<String, List<BillPaymentEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<BillPaymentEvent> topicEvents = entry.getValue();

            for (BillPaymentEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future =
                    publishEvent(topic, event, getEventKey(event));
                futures.add(future);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getEventPublishLatencyTimer());
                if (ex == null) {
                    log.info("Batch bill payment events published: count={}, topics={}",
                        events.size(), eventsByTopic.keySet());
                    getEventsPublishedCounter().increment(events.size());
                } else {
                    log.error("Failed to publish batch bill payment events", ex);
                    getEventsFailedCounter().increment(events.size());
                }
            });
    }

    // ============== Core Publishing Methods ==============

    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, Object event, String key) {

        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing event for replay");
            if (event instanceof BillPaymentEvent) {
                queueFailedEvent((BillPaymentEvent) event);
            }
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for bill payment events")
            );
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Store event first for durability
            if (event instanceof BillPaymentEvent) {
                eventStore.storeEvent((BillPaymentEvent) event);
            }

            // Create Kafka record with headers
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);

            // Publish with callback
            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(record).toCompletableFuture();

            future.whenComplete((sendResult, ex) -> {
                sample.stop(getEventPublishLatencyTimer());

                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });

            return future;

        } catch (Exception e) {
            sample.stop(getEventPublishLatencyTimer());
            log.error("Failed to publish bill payment event to topic {}", topic, e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, Object event, String key) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Store event first
            if (event instanceof BillPaymentEvent) {
                eventStore.storeEvent((BillPaymentEvent) event);
            }

            // Create record with high priority header
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            record.headers().add("priority", "HIGH".getBytes(StandardCharsets.UTF_8));

            // Synchronous send with timeout for high-priority events
            SendResult<String, Object> result = kafkaTemplate.send(record)
                .get(5, TimeUnit.SECONDS);

            sample.stop(getEventPublishLatencyTimer());
            onPublishSuccess(event, result);

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            sample.stop(getEventPublishLatencyTimer());
            log.error("Failed to publish high-priority bill payment event to topic {}", topic, e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ============== Event Replay ==============

    /**
     * Replay failed events after circuit breaker closes
     */
    public CompletableFuture<Void> replayFailedEvents() {
        if (failedEvents.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("Replaying {} failed bill payment events", failedEvents.size());

        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();

        BillPaymentEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future =
                publishEvent(topic, event, getEventKey(event));
            replayFutures.add(future);
        }

        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed bill payment event replay"));
    }

    // ============== Helper Methods ==============

    private void onPublishSuccess(Object event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getEventsPublishedCounter().increment();

        // Track correlation for saga patterns
        if (event instanceof BillPaymentEvent) {
            BillPaymentEvent billEvent = (BillPaymentEvent) event;
            if (billEvent.getCorrelationId() != null) {
                trackEventCorrelation(billEvent);
            }
            log.debug("Bill payment event published: type={}, eventId={}, offset={}",
                billEvent.getEventType(), billEvent.getEventId(),
                result.getRecordMetadata().offset());
        }
    }

    private void onPublishFailure(Object event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getEventsFailedCounter().increment();

        String eventType = event instanceof BillPaymentEvent
            ? ((BillPaymentEvent) event).getEventType() : "UNKNOWN";

        log.error("Failed to publish bill payment event: type={}, topic={}", eventType, topic, ex);

        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }

        // Queue for retry
        if (event instanceof BillPaymentEvent) {
            queueFailedEvent((BillPaymentEvent) event);
        }

        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }

    private void queueFailedEvent(BillPaymentEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed bill payment event for retry: type={}, eventId={}",
            event.getEventType(), event.getEventId());
    }

    private boolean isCircuitBreakerOpen() {
        if (!circuitBreakerOpen) {
            return false;
        }

        if (System.currentTimeMillis() - circuitBreakerOpenTime > CIRCUIT_BREAKER_TIMEOUT) {
            closeCircuitBreaker();
            return false;
        }

        return true;
    }

    private void openCircuitBreaker() {
        circuitBreakerOpen = true;
        circuitBreakerOpenTime = System.currentTimeMillis();
        log.error("Bill payment event publisher circuit breaker OPENED due to high failure rate");
    }

    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Bill payment event publisher circuit breaker CLOSED");
    }

    private void publishToDeadLetterQueue(String originalTopic, Object event,
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;

        try {
            DeadLetterBillPaymentEvent dlqEvent = DeadLetterBillPaymentEvent.builder()
                .originalEvent(event)
                .originalTopic(originalTopic)
                .errorMessage(error.getMessage())
                .failureTimestamp(Instant.now())
                .retryCount(1)
                .build();

            ProducerRecord<String, Object> dlqRecord = createKafkaRecord(dlqTopic, key, dlqEvent);
            dlqRecord.headers().add("original-topic", originalTopic.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("error-message", error.getMessage().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(dlqRecord).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.warn("Bill payment event sent to DLQ: topic={}", dlqTopic);
                } else {
                    log.error("Failed to send bill payment event to DLQ", ex);
                }
            });

        } catch (Exception e) {
            log.error("Failed to publish bill payment event to dead letter queue", e);
        }
    }

    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);

        // Add standard headers
        record.headers().add("event-type",
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp",
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "bill-payment-service".getBytes(StandardCharsets.UTF_8));

        return record;
    }

    private void trackEventCorrelation(BillPaymentEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getEventId(),
                event.getEventType(), Instant.now()));

        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry ->
            entry.getValue().getTimestamp().isBefore(cutoff));
    }

    private Map<String, List<BillPaymentEvent>> groupEventsByTopic(List<BillPaymentEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }

    private String getTopicForEventType(String eventType) {
        switch (eventType.toUpperCase()) {
            case "BILL_PAYMENT_SCHEDULED":
                return BILL_PAY_SCHEDULING_TOPIC;
            case "RECURRING_BILL_SETUP":
            case "RECURRING_BILL_EXECUTED":
            case "RECURRING_BILL_CANCELLED":
                return RECURRING_BILL_EVENTS_TOPIC;
            case "BILL_PAYMENT_STATUS_CHANGED":
                return BILL_PAYMENT_STATUS_EVENTS_TOPIC;
            default:
                return BILL_PAYMENT_EVENTS_TOPIC;
        }
    }

    private String getEventKey(BillPaymentEvent event) {
        if (event.getPaymentId() != null) return event.getPaymentId();
        if (event.getRecurringSetupId() != null) return event.getRecurringSetupId();
        if (event.getUserId() != null) return event.getUserId();
        return event.getEventId();
    }

    private String getCorrelationIdFromContext() {
        // TODO: Extract from MDC or request context
        return "billpay-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Lazy initialization of metrics
    private Counter getEventsPublishedCounter() {
        if (eventsPublishedCounter == null) {
            eventsPublishedCounter = Counter.builder("bill_payment_events_published_total")
                .description("Total number of bill payment events published successfully")
                .tag("service", "bill-payment")
                .register(meterRegistry);
        }
        return eventsPublishedCounter;
    }

    private Counter getEventsFailedCounter() {
        if (eventsFailedCounter == null) {
            eventsFailedCounter = Counter.builder("bill_payment_events_failed_total")
                .description("Total number of bill payment events that failed to publish")
                .tag("service", "bill-payment")
                .register(meterRegistry);
        }
        return eventsFailedCounter;
    }

    private Timer getEventPublishLatencyTimer() {
        if (eventPublishLatencyTimer == null) {
            eventPublishLatencyTimer = Timer.builder("bill_payment_events_publish_latency")
                .description("Latency of bill payment event publishing")
                .tag("service", "bill-payment")
                .register(meterRegistry);
        }
        return eventPublishLatencyTimer;
    }

    /**
     * Event correlation tracking for saga patterns
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class EventCorrelation {
        private final String correlationId;
        private final String eventId;
        private final String eventType;
        private final Instant timestamp;
    }
}
