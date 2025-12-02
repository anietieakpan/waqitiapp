package com.waqiti.lending.events;

import com.waqiti.lending.events.model.LendingEvent;
import com.waqiti.lending.events.model.DeadLetterLendingEvent;
import com.waqiti.lending.events.store.LendingEventStore;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise-grade lending event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LendingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final LendingEventStore eventStore;
    
    // Topic constants
    private static final String LOAN_APPLICATION_EVENTS_TOPIC = "loan-application-events";
    private static final String CREDIT_DECISION_EVENTS_TOPIC = "credit-decision-events";
    private static final String LOAN_SERVICING_EVENTS_TOPIC = "loan-servicing-events";
    private static final String LOAN_DEFAULT_EVENTS_TOPIC = "loan-default-events";
    private static final String COLLATERAL_VALUATION_EVENTS_TOPIC = "collateral-valuation-events";
    private static final String LOAN_MODIFICATION_EVENTS_TOPIC = "loan-modification-events";
    private static final String FORECLOSURE_EVENTS_TOPIC = "foreclosure-events";
    private static final String STUDENT_LOAN_EVENTS_TOPIC = "student-loan-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<LendingEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter lendingEventsPublished;
    private Counter lendingEventsFailure;
    private Timer lendingEventPublishLatency;
    
    /**
     * Publishes loan application event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishLoanApplication(
            String applicationId, String borrowerId, String loanType, BigDecimal amount,
            String currency, BigDecimal interestRate, String term, String creditScore) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LOAN_APPLICATION")
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .loanType(loanType)
                .amount(amount)
                .currency(currency)
                .interestRate(interestRate)
                .term(term)
                .creditScore(creditScore)
                .status("SUBMITTED")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(LOAN_APPLICATION_EVENTS_TOPIC, event, applicationId);
    }
    
    /**
     * Publishes credit decision event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCreditDecision(
            String loanId, String applicationId, String borrowerId, String status,
            String decisionReason, String riskRating, String userId, BigDecimal approvedAmount) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CREDIT_DECISION")
                .loanId(loanId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .status(status)
                .decisionReason(decisionReason)
                .riskRating(riskRating)
                .userId(userId)
                .amount(approvedAmount)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CREDIT_DECISION_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes loan servicing event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishLoanServicing(
            String loanId, String borrowerId, String servicingType, BigDecimal paymentAmount,
            Instant paymentDueDate, Instant nextPaymentDate, BigDecimal outstandingBalance) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LOAN_SERVICING")
                .loanId(loanId)
                .borrowerId(borrowerId)
                .servicingType(servicingType)
                .paymentAmount(paymentAmount)
                .paymentDueDate(paymentDueDate)
                .nextPaymentDate(nextPaymentDate)
                .outstandingBalance(outstandingBalance)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(LOAN_SERVICING_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes loan default event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishLoanDefault(
            String loanId, String borrowerId, String defaultReason, String defaultStage,
            BigDecimal outstandingBalance, String currency, Instant defaultDate) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LOAN_DEFAULT")
                .loanId(loanId)
                .borrowerId(borrowerId)
                .defaultReason(defaultReason)
                .defaultStage(defaultStage)
                .outstandingBalance(outstandingBalance)
                .currency(currency)
                .status("DEFAULT")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(LOAN_DEFAULT_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes collateral valuation event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCollateralValuation(
            String loanId, String collateralId, String collateralType, BigDecimal collateralValue,
            String currency, String appraisalId, String userId, Instant valuationDate) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("COLLATERAL_VALUATION")
                .loanId(loanId)
                .collateralId(collateralId)
                .collateralType(collateralType)
                .collateralValue(collateralValue)
                .currency(currency)
                .appraisalId(appraisalId)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(COLLATERAL_VALUATION_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes loan modification event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishLoanModification(
            String loanId, String borrowerId, String modificationType, String modificationReason,
            BigDecimal previousAmount, BigDecimal newAmount, String userId) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LOAN_MODIFICATION")
                .loanId(loanId)
                .borrowerId(borrowerId)
                .modificationType(modificationType)
                .modificationReason(modificationReason)
                .previousAmount(previousAmount)
                .amount(newAmount)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(LOAN_MODIFICATION_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes foreclosure event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishForeclosure(
            String loanId, String borrowerId, String foreclosureStage, String collateralId,
            BigDecimal outstandingBalance, String currency, String userId) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("FORECLOSURE")
                .loanId(loanId)
                .borrowerId(borrowerId)
                .foreclosureStage(foreclosureStage)
                .collateralId(collateralId)
                .outstandingBalance(outstandingBalance)
                .currency(currency)
                .userId(userId)
                .status("FORECLOSURE")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(FORECLOSURE_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes student loan event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishStudentLoan(
            String loanId, String borrowerId, String studentLoanType, String repaymentPlan,
            String defermentType, String forbearanceType, BigDecimal amount, String currency) {
        
        LendingEvent event = LendingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("STUDENT_LOAN")
                .loanId(loanId)
                .borrowerId(borrowerId)
                .studentLoanType(studentLoanType)
                .repaymentPlan(repaymentPlan)
                .defermentType(defermentType)
                .forbearanceType(forbearanceType)
                .amount(amount)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(STUDENT_LOAN_EVENTS_TOPIC, event, loanId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<LendingEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<LendingEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<LendingEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<LendingEvent> topicEvents = entry.getValue();
            
            for (LendingEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getLoanId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getLendingEventPublishLatency());
                if (ex == null) {
                    log.info("Batch lending events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getLendingEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch lending events", ex);
                    getLendingEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, LendingEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing lending event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for lending events")
            );
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store event first for durability
            eventStore.storeEvent(event);
            
            // Create Kafka record with headers
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            
            // Publish with callback
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(record).toCompletableFuture();
            
            future.whenComplete((sendResult, ex) -> {
                sample.stop(getLendingEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getLendingEventPublishLatency());
            log.error("Failed to publish lending event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, LendingEvent event, String key) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store event first
            eventStore.storeEvent(event);
            
            // Create record with high priority header
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            record.headers().add("priority", "HIGH".getBytes(StandardCharsets.UTF_8));
            
            // Synchronous send with timeout for high-priority events
            SendResult<String, Object> result = kafkaTemplate.send(record)
                .get(5, TimeUnit.SECONDS);
            
            sample.stop(getLendingEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getLendingEventPublishLatency());
            log.error("Failed to publish high-priority lending event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Event replay capability for recovering failed events
     */
    public CompletableFuture<Void> replayFailedEvents() {
        if (failedEvents.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Replaying {} failed lending events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        LendingEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getLoanId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed lending event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(LendingEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getLendingEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Lending event published successfully: type={}, loanId={}, offset={}", 
            event.getEventType(), event.getLoanId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(LendingEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getLendingEventsFailure().increment();
        
        log.error("Failed to publish lending event: type={}, loanId={}, topic={}", 
            event.getEventType(), event.getLoanId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(LendingEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed lending event for retry: type={}, loanId={}", 
            event.getEventType(), event.getLoanId());
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
        log.error("Lending event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Lending event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, LendingEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterLendingEvent dlqEvent = DeadLetterLendingEvent.builder()
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
                    log.warn("Lending event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send lending event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish lending event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "lending-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(LendingEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getLoanId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<LendingEvent>> groupEventsByTopic(List<LendingEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "LOAN_APPLICATION":
                return LOAN_APPLICATION_EVENTS_TOPIC;
            case "CREDIT_DECISION":
                return CREDIT_DECISION_EVENTS_TOPIC;
            case "LOAN_SERVICING":
                return LOAN_SERVICING_EVENTS_TOPIC;
            case "LOAN_DEFAULT":
                return LOAN_DEFAULT_EVENTS_TOPIC;
            case "COLLATERAL_VALUATION":
                return COLLATERAL_VALUATION_EVENTS_TOPIC;
            case "LOAN_MODIFICATION":
                return LOAN_MODIFICATION_EVENTS_TOPIC;
            case "FORECLOSURE":
                return FORECLOSURE_EVENTS_TOPIC;
            case "STUDENT_LOAN":
                return STUDENT_LOAN_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown lending event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "lending-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getLendingEventsPublished() {
        if (lendingEventsPublished == null) {
            lendingEventsPublished = Counter.builder("lending.events.published")
                .description("Number of lending events published")
                .register(meterRegistry);
        }
        return lendingEventsPublished;
    }
    
    private Counter getLendingEventsFailure() {
        if (lendingEventsFailure == null) {
            lendingEventsFailure = Counter.builder("lending.events.failure")
                .description("Number of lending events that failed to publish")
                .register(meterRegistry);
        }
        return lendingEventsFailure;
    }
    
    private Timer getLendingEventPublishLatency() {
        if (lendingEventPublishLatency == null) {
            lendingEventPublishLatency = Timer.builder("lending.events.publish.latency")
                .description("Latency of lending event publishing")
                .register(meterRegistry);
        }
        return lendingEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String loanId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String loanId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.loanId = loanId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}