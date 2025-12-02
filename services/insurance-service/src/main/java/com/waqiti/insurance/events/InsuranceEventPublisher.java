package com.waqiti.insurance.events;

import com.waqiti.insurance.events.model.InsuranceEvent;
import com.waqiti.insurance.events.model.DeadLetterInsuranceEvent;
import com.waqiti.insurance.events.store.InsuranceEventStore;
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
 * Enterprise-grade insurance event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InsuranceEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final InsuranceEventStore eventStore;
    
    // Topic constants
    private static final String INSURANCE_POLICY_CREATION_EVENTS_TOPIC = "insurance-policy-creation-events";
    private static final String INSURANCE_CLAIM_SUBMISSION_EVENTS_TOPIC = "insurance-claim-submission-events";
    private static final String INSURANCE_CLAIM_PROCESSING_EVENTS_TOPIC = "insurance-claim-processing-events";
    private static final String INSURANCE_PREMIUM_PAYMENT_EVENTS_TOPIC = "insurance-premium-payment-events";
    private static final String INSURANCE_POLICY_CANCELLATION_EVENTS_TOPIC = "insurance-policy-cancellation-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<InsuranceEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter insuranceEventsPublished;
    private Counter insuranceEventsFailure;
    private Timer insuranceEventPublishLatency;
    
    /**
     * Publishes policy created event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishPolicyCreated(
            String policyId, String userId, String policyType, String coverageType,
            BigDecimal coverageAmount, BigDecimal premiumAmount, String currency,
            Instant effectiveDate, Instant expirationDate, String underwritingResult) {
        
        InsuranceEvent event = InsuranceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("POLICY_CREATED")
                .policyId(policyId)
                .userId(userId)
                .policyType(policyType)
                .coverageType(coverageType)
                .coverageAmount(coverageAmount)
                .premiumAmount(premiumAmount)
                .currency(currency)
                .effectiveDate(effectiveDate)
                .expirationDate(expirationDate)
                .underwritingResult(underwritingResult)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("ACTIVE")
                .build();
        
        return publishEvent(INSURANCE_POLICY_CREATION_EVENTS_TOPIC, event, policyId);
    }
    
    /**
     * Publishes claim submitted event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishClaimSubmitted(
            String claimId, String policyId, String userId, String claimType,
            BigDecimal claimAmount, String currency, Instant incidentDate,
            String incidentDescription, String claimReason, String submissionMethod) {
        
        InsuranceEvent event = InsuranceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CLAIM_SUBMITTED")
                .claimId(claimId)
                .policyId(policyId)
                .userId(userId)
                .claimType(claimType)
                .claimAmount(claimAmount)
                .currency(currency)
                .incidentDate(incidentDate)
                .incidentDescription(incidentDescription)
                .claimReason(claimReason)
                .submissionMethod(submissionMethod)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("SUBMITTED")
                .build();
        
        return publishHighPriorityEvent(INSURANCE_CLAIM_SUBMISSION_EVENTS_TOPIC, event, claimId);
    }
    
    /**
     * Publishes claim processed event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishClaimProcessed(
            String claimId, String policyId, String userId, String claimStatus,
            BigDecimal approvedAmount, BigDecimal payoutAmount, String currency,
            String adjustorId, String decisionReason, Instant processedDate,
            String paymentMethod) {
        
        InsuranceEvent event = InsuranceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CLAIM_PROCESSED")
                .claimId(claimId)
                .policyId(policyId)
                .userId(userId)
                .status(claimStatus)
                .approvedAmount(approvedAmount)
                .payoutAmount(payoutAmount)
                .currency(currency)
                .adjustorId(adjustorId)
                .decisionReason(decisionReason)
                .processedDate(processedDate)
                .paymentMethod(paymentMethod)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(INSURANCE_CLAIM_PROCESSING_EVENTS_TOPIC, event, claimId);
    }
    
    /**
     * Publishes premium payment event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishPremiumPayment(
            String paymentId, String policyId, String userId, BigDecimal premiumAmount,
            String currency, String paymentMethod, String paymentStatus,
            Instant dueDate, Instant paidDate, String billingPeriod) {
        
        InsuranceEvent event = InsuranceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PREMIUM_PAYMENT")
                .paymentId(paymentId)
                .policyId(policyId)
                .userId(userId)
                .premiumAmount(premiumAmount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .status(paymentStatus)
                .dueDate(dueDate)
                .paidDate(paidDate)
                .billingPeriod(billingPeriod)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(INSURANCE_PREMIUM_PAYMENT_EVENTS_TOPIC, event, policyId);
    }
    
    /**
     * Publishes policy cancelled event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishPolicyCancelled(
            String policyId, String userId, String cancellationType, String cancellationReason,
            Instant cancellationDate, Instant effectiveDate, BigDecimal refundAmount,
            String currency, String initiatedBy) {
        
        InsuranceEvent event = InsuranceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("POLICY_CANCELLED")
                .policyId(policyId)
                .userId(userId)
                .cancellationType(cancellationType)
                .cancellationReason(cancellationReason)
                .cancellationDate(cancellationDate)
                .effectiveDate(effectiveDate)
                .refundAmount(refundAmount)
                .currency(currency)
                .initiatedBy(initiatedBy)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("CANCELLED")
                .build();
        
        return publishHighPriorityEvent(INSURANCE_POLICY_CANCELLATION_EVENTS_TOPIC, event, policyId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<InsuranceEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<InsuranceEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<InsuranceEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<InsuranceEvent> topicEvents = entry.getValue();
            
            for (InsuranceEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, getKeyForEvent(event));
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getInsuranceEventPublishLatency());
                if (ex == null) {
                    log.info("Batch insurance events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getInsuranceEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch insurance events", ex);
                    getInsuranceEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, InsuranceEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing insurance event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for insurance events")
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
                sample.stop(getInsuranceEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getInsuranceEventPublishLatency());
            log.error("Failed to publish insurance event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, InsuranceEvent event, String key) {
        
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
            
            sample.stop(getInsuranceEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getInsuranceEventPublishLatency());
            log.error("Failed to publish high-priority insurance event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed insurance events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        InsuranceEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, getKeyForEvent(event));
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed insurance event replay"));
    }
    
    // Helper methods (similar structure to previous publishers)
    
    private void onPublishSuccess(InsuranceEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getInsuranceEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Insurance event published successfully: type={}, policyId={}, offset={}", 
            event.getEventType(), event.getPolicyId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(InsuranceEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getInsuranceEventsFailure().increment();
        
        log.error("Failed to publish insurance event: type={}, policyId={}, topic={}", 
            event.getEventType(), event.getPolicyId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(InsuranceEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed insurance event for retry: type={}, policyId={}", 
            event.getEventType(), event.getPolicyId());
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
        log.error("Insurance event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Insurance event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, InsuranceEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterInsuranceEvent dlqEvent = DeadLetterInsuranceEvent.builder()
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
                    log.warn("Insurance event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send insurance event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish insurance event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "insurance-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(InsuranceEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getPolicyId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<InsuranceEvent>> groupEventsByTopic(List<InsuranceEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "POLICY_CREATED":
                return INSURANCE_POLICY_CREATION_EVENTS_TOPIC;
            case "CLAIM_SUBMITTED":
                return INSURANCE_CLAIM_SUBMISSION_EVENTS_TOPIC;
            case "CLAIM_PROCESSED":
                return INSURANCE_CLAIM_PROCESSING_EVENTS_TOPIC;
            case "PREMIUM_PAYMENT":
                return INSURANCE_PREMIUM_PAYMENT_EVENTS_TOPIC;
            case "POLICY_CANCELLED":
                return INSURANCE_POLICY_CANCELLATION_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown insurance event type: " + eventType);
        }
    }
    
    private String getKeyForEvent(InsuranceEvent event) {
        if (event.getClaimId() != null) return event.getClaimId();
        if (event.getPaymentId() != null) return event.getPaymentId();
        return event.getPolicyId();
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "insurance-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getInsuranceEventsPublished() {
        if (insuranceEventsPublished == null) {
            insuranceEventsPublished = Counter.builder("insurance.events.published")
                .description("Number of insurance events published")
                .register(meterRegistry);
        }
        return insuranceEventsPublished;
    }
    
    private Counter getInsuranceEventsFailure() {
        if (insuranceEventsFailure == null) {
            insuranceEventsFailure = Counter.builder("insurance.events.failure")
                .description("Number of insurance events that failed to publish")
                .register(meterRegistry);
        }
        return insuranceEventsFailure;
    }
    
    private Timer getInsuranceEventPublishLatency() {
        if (insuranceEventPublishLatency == null) {
            insuranceEventPublishLatency = Timer.builder("insurance.events.publish.latency")
                .description("Latency of insurance event publishing")
                .register(meterRegistry);
        }
        return insuranceEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String policyId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String policyId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.policyId = policyId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}