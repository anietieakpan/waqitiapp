package com.waqiti.risk.events;

import com.waqiti.risk.events.model.RiskEvent;
import com.waqiti.risk.events.model.DeadLetterRiskEvent;
import com.waqiti.risk.events.store.RiskEventStore;
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
 * Enterprise-grade risk event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final RiskEventStore eventStore;
    
    // Topic constants
    private static final String CREDIT_RISK_ASSESSMENT_EVENTS_TOPIC = "credit-risk-assessment-events";
    private static final String OPERATIONAL_RISK_EVENTS_TOPIC = "operational-risk-events";
    private static final String LIQUIDITY_RISK_EVENTS_TOPIC = "liquidity-risk-events";
    private static final String COUNTERPARTY_RISK_EVENTS_TOPIC = "counterparty-risk-events";
    private static final String CONCENTRATION_RISK_EVENTS_TOPIC = "concentration-risk-events";
    private static final String MODEL_RISK_VALIDATION_EVENTS_TOPIC = "model-risk-validation-events";
    private static final String STRESS_TESTING_EVENTS_TOPIC = "stress-testing-events";
    private static final String RISK_APPETITE_MONITORING_EVENTS_TOPIC = "risk-appetite-monitoring-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<RiskEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter riskEventsPublished;
    private Counter riskEventsFailure;
    private Timer riskEventPublishLatency;
    
    /**
     * Publishes credit risk assessment event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCreditRiskAssessment(
            String riskId, String assessmentId, String entityId, BigDecimal riskScore,
            String riskRating, String creditScore, BigDecimal exposure, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CREDIT_RISK_ASSESSMENT")
                .riskId(riskId)
                .assessmentId(assessmentId)
                .entityId(entityId)
                .riskScore(riskScore)
                .riskRating(riskRating)
                .creditScore(creditScore)
                .exposure(exposure)
                .userId(userId)
                .assessmentDate(Instant.now())
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CREDIT_RISK_ASSESSMENT_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes operational risk event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishOperationalRisk(
            String riskId, String operationalRiskType, String riskIndicator, String severity,
            BigDecimal probability, BigDecimal impact, String mitigationAction, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OPERATIONAL_RISK")
                .riskId(riskId)
                .operationalRiskType(operationalRiskType)
                .riskIndicator(riskIndicator)
                .severity(severity)
                .probability(probability)
                .impact(impact)
                .mitigationAction(mitigationAction)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(OPERATIONAL_RISK_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes liquidity risk event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishLiquidityRisk(
            String riskId, String entityId, BigDecimal liquidityRatio, BigDecimal exposure,
            String riskRating, String severity, String currency, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LIQUIDITY_RISK")
                .riskId(riskId)
                .entityId(entityId)
                .liquidityRatio(liquidityRatio)
                .exposure(exposure)
                .riskRating(riskRating)
                .severity(severity)
                .currency(currency)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(LIQUIDITY_RISK_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes counterparty risk event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCounterpartyRisk(
            String riskId, String counterpartyId, String counterpartyType, String counterpartyRating,
            BigDecimal exposure, BigDecimal riskScore, String currency, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("COUNTERPARTY_RISK")
                .riskId(riskId)
                .counterpartyId(counterpartyId)
                .counterpartyType(counterpartyType)
                .counterpartyRating(counterpartyRating)
                .exposure(exposure)
                .riskScore(riskScore)
                .currency(currency)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(COUNTERPARTY_RISK_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes concentration risk event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishConcentrationRisk(
            String riskId, String entityId, String riskCategory, BigDecimal concentrationLimit,
            BigDecimal concentrationRatio, BigDecimal exposure, String severity, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CONCENTRATION_RISK")
                .riskId(riskId)
                .entityId(entityId)
                .riskCategory(riskCategory)
                .concentrationLimit(concentrationLimit)
                .concentrationRatio(concentrationRatio)
                .exposure(exposure)
                .severity(severity)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CONCENTRATION_RISK_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes model risk validation event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishModelRiskValidation(
            String riskId, String modelId, String modelVersion, String validationStatus,
            BigDecimal riskScore, String complianceStatus, String userId, Instant reviewDate) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MODEL_RISK_VALIDATION")
                .riskId(riskId)
                .modelId(modelId)
                .modelVersion(modelVersion)
                .validationStatus(validationStatus)
                .riskScore(riskScore)
                .complianceStatus(complianceStatus)
                .userId(userId)
                .reviewDate(reviewDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(MODEL_RISK_VALIDATION_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes stress testing event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishStressTesting(
            String riskId, String stressTestScenario, BigDecimal stressTestResult,
            String severity, BigDecimal capitalRatio, String regulatoryFramework, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("STRESS_TESTING")
                .riskId(riskId)
                .stressTestScenario(stressTestScenario)
                .stressTestResult(stressTestResult)
                .severity(severity)
                .capitalRatio(capitalRatio)
                .regulatoryFramework(regulatoryFramework)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(STRESS_TESTING_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes risk appetite monitoring event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRiskAppetiteMonitoring(
            String riskId, String riskAppetiteMetric, BigDecimal riskAppetiteLimit,
            BigDecimal riskAppetiteUtilization, String status, String severity, String userId) {
        
        RiskEvent event = RiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("RISK_APPETITE_MONITORING")
                .riskId(riskId)
                .riskAppetiteMetric(riskAppetiteMetric)
                .riskAppetiteLimit(riskAppetiteLimit)
                .riskAppetiteUtilization(riskAppetiteUtilization)
                .status(status)
                .severity(severity)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(RISK_APPETITE_MONITORING_EVENTS_TOPIC, event, riskId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<RiskEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<RiskEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<RiskEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<RiskEvent> topicEvents = entry.getValue();
            
            for (RiskEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getRiskId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getRiskEventPublishLatency());
                if (ex == null) {
                    log.info("Batch risk events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getRiskEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch risk events", ex);
                    getRiskEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, RiskEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing risk event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for risk events")
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
                sample.stop(getRiskEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getRiskEventPublishLatency());
            log.error("Failed to publish risk event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, RiskEvent event, String key) {
        
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
            
            sample.stop(getRiskEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getRiskEventPublishLatency());
            log.error("Failed to publish high-priority risk event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed risk events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        RiskEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getRiskId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed risk event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(RiskEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getRiskEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Risk event published successfully: type={}, riskId={}, offset={}", 
            event.getEventType(), event.getRiskId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(RiskEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getRiskEventsFailure().increment();
        
        log.error("Failed to publish risk event: type={}, riskId={}, topic={}", 
            event.getEventType(), event.getRiskId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(RiskEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed risk event for retry: type={}, riskId={}", 
            event.getEventType(), event.getRiskId());
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
        log.error("Risk event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Risk event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, RiskEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterRiskEvent dlqEvent = DeadLetterRiskEvent.builder()
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
                    log.warn("Risk event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send risk event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish risk event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "risk-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(RiskEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getRiskId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<RiskEvent>> groupEventsByTopic(List<RiskEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "CREDIT_RISK_ASSESSMENT":
                return CREDIT_RISK_ASSESSMENT_EVENTS_TOPIC;
            case "OPERATIONAL_RISK":
                return OPERATIONAL_RISK_EVENTS_TOPIC;
            case "LIQUIDITY_RISK":
                return LIQUIDITY_RISK_EVENTS_TOPIC;
            case "COUNTERPARTY_RISK":
                return COUNTERPARTY_RISK_EVENTS_TOPIC;
            case "CONCENTRATION_RISK":
                return CONCENTRATION_RISK_EVENTS_TOPIC;
            case "MODEL_RISK_VALIDATION":
                return MODEL_RISK_VALIDATION_EVENTS_TOPIC;
            case "STRESS_TESTING":
                return STRESS_TESTING_EVENTS_TOPIC;
            case "RISK_APPETITE_MONITORING":
                return RISK_APPETITE_MONITORING_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown risk event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "risk-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getRiskEventsPublished() {
        if (riskEventsPublished == null) {
            riskEventsPublished = Counter.builder("risk.events.published")
                .description("Number of risk events published")
                .register(meterRegistry);
        }
        return riskEventsPublished;
    }
    
    private Counter getRiskEventsFailure() {
        if (riskEventsFailure == null) {
            riskEventsFailure = Counter.builder("risk.events.failure")
                .description("Number of risk events that failed to publish")
                .register(meterRegistry);
        }
        return riskEventsFailure;
    }
    
    private Timer getRiskEventPublishLatency() {
        if (riskEventPublishLatency == null) {
            riskEventPublishLatency = Timer.builder("risk.events.publish.latency")
                .description("Latency of risk event publishing")
                .register(meterRegistry);
        }
        return riskEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String riskId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String riskId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.riskId = riskId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}