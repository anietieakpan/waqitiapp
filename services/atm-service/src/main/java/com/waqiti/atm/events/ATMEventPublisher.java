package com.waqiti.atm.events;

import com.waqiti.atm.events.model.ATMEvent;
import com.waqiti.atm.events.model.DeadLetterATMEvent;
import com.waqiti.atm.events.store.ATMEventStore;
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
 * Enterprise-grade ATM event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ATMEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ATMEventStore eventStore;
    
    // Topic constants
    private static final String ATM_WITHDRAWAL_EVENTS_TOPIC = "atm-withdrawal-events";
    private static final String ATM_DEPOSIT_EVENTS_TOPIC = "atm-deposit-events";
    private static final String ATM_BALANCE_INQUIRY_EVENTS_TOPIC = "atm-balance-inquiry-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<ATMEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter atmEventsPublished;
    private Counter atmEventsFailure;
    private Timer atmEventPublishLatency;
    
    /**
     * Publishes ATM withdrawal event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishATMWithdrawal(
            String atmId, String userId, String accountId, String cardNumber, BigDecimal amount, 
            String currency, String transactionId, String authorizationCode, String terminalId) {
        
        ATMEvent event = ATMEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ATM_WITHDRAWAL")
                .atmId(atmId)
                .userId(userId)
                .accountId(accountId)
                .cardNumber(cardNumber)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .authorizationCode(authorizationCode)
                .terminalId(terminalId)
                .isCashWithdrawal(true)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(ATM_WITHDRAWAL_EVENTS_TOPIC, event, atmId);
    }
    
    /**
     * Publishes ATM deposit event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishATMDeposit(
            String atmId, String userId, String accountId, String cardNumber, BigDecimal amount,
            String currency, String transactionId, String authorizationCode, String terminalId,
            String depositType) {
        
        ATMEvent event = ATMEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ATM_DEPOSIT")
                .atmId(atmId)
                .userId(userId)
                .accountId(accountId)
                .cardNumber(cardNumber)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .authorizationCode(authorizationCode)
                .terminalId(terminalId)
                .transactionType(depositType)
                .isDeposit(true)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(ATM_DEPOSIT_EVENTS_TOPIC, event, atmId);
    }
    
    /**
     * Publishes ATM balance inquiry event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishATMBalanceInquiry(
            String atmId, String userId, String accountId, String cardNumber, String transactionId,
            BigDecimal availableBalance, BigDecimal accountBalance, String terminalId) {
        
        ATMEvent event = ATMEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ATM_BALANCE_INQUIRY")
                .atmId(atmId)
                .userId(userId)
                .accountId(accountId)
                .cardNumber(cardNumber)
                .transactionId(transactionId)
                .availableBalance(availableBalance)
                .accountBalance(accountBalance)
                .terminalId(terminalId)
                .isBalanceInquiry(true)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(ATM_BALANCE_INQUIRY_EVENTS_TOPIC, event, atmId);
    }
    
    /**
     * Publishes ATM fraud detected event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishATMFraudDetected(
            String atmId, String userId, String accountId, String cardNumber, String transactionId,
            String fraudScore, String riskAssessment, String suspiciousActivity) {
        
        ATMEvent event = ATMEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ATM_FRAUD_DETECTED")
                .atmId(atmId)
                .userId(userId)
                .accountId(accountId)
                .cardNumber(cardNumber)
                .transactionId(transactionId)
                .fraudScore(fraudScore)
                .riskAssessment(riskAssessment)
                .description(suspiciousActivity)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(ATM_WITHDRAWAL_EVENTS_TOPIC, event, atmId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<ATMEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<ATMEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<ATMEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<ATMEvent> topicEvents = entry.getValue();
            
            for (ATMEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getAtmId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getATMEventPublishLatency());
                if (ex == null) {
                    log.info("Batch ATM events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getATMEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch ATM events", ex);
                    getATMEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, ATMEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing ATM event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for ATM events")
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
                sample.stop(getATMEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getATMEventPublishLatency());
            log.error("Failed to publish ATM event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, ATMEvent event, String key) {
        
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
            
            sample.stop(getATMEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getATMEventPublishLatency());
            log.error("Failed to publish high-priority ATM event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed ATM events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        ATMEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getAtmId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed ATM event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(ATMEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getATMEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("ATM event published successfully: type={}, atmId={}, offset={}", 
            event.getEventType(), event.getAtmId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(ATMEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getATMEventsFailure().increment();
        
        log.error("Failed to publish ATM event: type={}, atmId={}, topic={}", 
            event.getEventType(), event.getAtmId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(ATMEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed ATM event for retry: type={}, atmId={}", 
            event.getEventType(), event.getAtmId());
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
        log.error("ATM event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("ATM event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, ATMEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterATMEvent dlqEvent = DeadLetterATMEvent.builder()
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
                    log.warn("ATM event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send ATM event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish ATM event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "atm-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(ATMEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getAtmId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<ATMEvent>> groupEventsByTopic(List<ATMEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "ATM_WITHDRAWAL":
            case "ATM_FRAUD_DETECTED":
            case "ATM_CARD_CAPTURED":
                return ATM_WITHDRAWAL_EVENTS_TOPIC;
            case "ATM_DEPOSIT":
                return ATM_DEPOSIT_EVENTS_TOPIC;
            case "ATM_BALANCE_INQUIRY":
                return ATM_BALANCE_INQUIRY_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown ATM event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "atm-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getATMEventsPublished() {
        if (atmEventsPublished == null) {
            atmEventsPublished = Counter.builder("atm.events.published")
                .description("Number of ATM events published")
                .register(meterRegistry);
        }
        return atmEventsPublished;
    }
    
    private Counter getATMEventsFailure() {
        if (atmEventsFailure == null) {
            atmEventsFailure = Counter.builder("atm.events.failure")
                .description("Number of ATM events that failed to publish")
                .register(meterRegistry);
        }
        return atmEventsFailure;
    }
    
    private Timer getATMEventPublishLatency() {
        if (atmEventPublishLatency == null) {
            atmEventPublishLatency = Timer.builder("atm.events.publish.latency")
                .description("Latency of ATM event publishing")
                .register(meterRegistry);
        }
        return atmEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String atmId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String atmId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.atmId = atmId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}