package com.waqiti.card.events;

import com.waqiti.card.events.model.CardEvent;
import com.waqiti.card.events.model.DeadLetterCardEvent;
import com.waqiti.card.events.store.CardEventStore;
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
 * Enterprise-grade card event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CardEventStore eventStore;
    
    // Topic constants
    private static final String CARD_ISSUANCE_EVENTS_TOPIC = "card-issuance-events";
    private static final String CARD_PIN_MANAGEMENT_EVENTS_TOPIC = "card-pin-management-events";
    private static final String CARD_LIMIT_ADJUSTMENT_EVENTS_TOPIC = "card-limit-adjustment-events";
    private static final String CARD_REPLACEMENT_EVENTS_TOPIC = "card-replacement-events";
    private static final String CARD_REWARDS_EVENTS_TOPIC = "card-rewards-events";
    private static final String CARD_STATEMENT_EVENTS_TOPIC = "card-statement-events";
    private static final String CARD_BALANCE_TRANSFER_EVENTS_TOPIC = "card-balance-transfer-events";
    private static final String CARD_CASH_ADVANCE_EVENTS_TOPIC = "card-cash-advance-events";
    private static final String CARD_3DS_EVENTS_TOPIC = "card-3ds-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<CardEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter cardEventsPublished;
    private Counter cardEventsFailure;
    private Timer cardEventPublishLatency;
    
    /**
     * Publishes card issuance event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardIssuance(
            String cardId, String userId, String accountId, String cardType,
            BigDecimal creditLimit, String deliveryMethod, Instant expiryDate) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_ISSUANCE")
                .cardId(cardId)
                .userId(userId)
                .accountId(accountId)
                .cardType(cardType)
                .creditLimit(creditLimit)
                .deliveryMethod(deliveryMethod)
                .expiryDate(expiryDate)
                .cardStatus("ISSUED")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_ISSUANCE_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes card PIN management event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardPINManagement(
            String cardId, String userId, String pinStatus, String activationChannel) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_PIN_MANAGEMENT")
                .cardId(cardId)
                .userId(userId)
                .pinStatus(pinStatus)
                .activationChannel(activationChannel)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_PIN_MANAGEMENT_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes card limit adjustment event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardLimitAdjustment(
            String cardId, String userId, BigDecimal previousLimit, BigDecimal newLimit,
            String currency, String userId) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_LIMIT_ADJUSTMENT")
                .cardId(cardId)
                .userId(userId)
                .previousAmount(previousLimit)
                .creditLimit(newLimit)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_LIMIT_ADJUSTMENT_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes card replacement event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardReplacement(
            String oldCardId, String newCardId, String userId, String replacementReason,
            String deliveryMethod, Instant expiryDate) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_REPLACEMENT")
                .cardId(newCardId)
                .oldCardId(oldCardId)
                .newCardId(newCardId)
                .userId(userId)
                .replacementReason(replacementReason)
                .deliveryMethod(deliveryMethod)
                .expiryDate(expiryDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(CARD_REPLACEMENT_EVENTS_TOPIC, event, newCardId);
    }
    
    /**
     * Publishes card rewards event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardRewards(
            String cardId, String userId, String rewardType, String rewardProgram,
            BigDecimal rewardPoints, BigDecimal amount, String currency) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_REWARDS")
                .cardId(cardId)
                .userId(userId)
                .rewardType(rewardType)
                .rewardProgram(rewardProgram)
                .rewardPoints(rewardPoints)
                .amount(amount)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_REWARDS_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes card statement event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardStatement(
            String cardId, String userId, String statementId, Instant statementPeriodStart,
            Instant statementPeriodEnd, BigDecimal amount, String currency) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_STATEMENT")
                .cardId(cardId)
                .userId(userId)
                .statementId(statementId)
                .statementPeriodStart(statementPeriodStart)
                .statementPeriodEnd(statementPeriodEnd)
                .amount(amount)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_STATEMENT_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes card balance transfer event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardBalanceTransfer(
            String sourceCardId, String targetCardId, String userId, BigDecimal transferAmount,
            String transferType, String currency) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_BALANCE_TRANSFER")
                .cardId(sourceCardId)
                .sourceCardId(sourceCardId)
                .targetCardId(targetCardId)
                .userId(userId)
                .transferAmount(transferAmount)
                .transferType(transferType)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_BALANCE_TRANSFER_EVENTS_TOPIC, event, sourceCardId);
    }
    
    /**
     * Publishes card cash advance event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCardCashAdvance(
            String cardId, String userId, BigDecimal cashAdvanceAmount, BigDecimal cashAdvanceFee,
            String currency, String atmId, String transactionId) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_CASH_ADVANCE")
                .cardId(cardId)
                .userId(userId)
                .cashAdvanceAmount(cashAdvanceAmount)
                .cashAdvanceFee(cashAdvanceFee)
                .currency(currency)
                .atmId(atmId)
                .transactionId(transactionId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CARD_CASH_ADVANCE_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes card 3D Secure event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCard3DSecure(
            String cardId, String userId, String threeDSVersion, String authenticationStatus,
            String transactionId, String merchantId, String merchantName, BigDecimal amount) {
        
        CardEvent event = CardEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CARD_3DS")
                .cardId(cardId)
                .userId(userId)
                .threeDSVersion(threeDSVersion)
                .authenticationStatus(authenticationStatus)
                .transactionId(transactionId)
                .merchantId(merchantId)
                .merchantName(merchantName)
                .amount(amount)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(CARD_3DS_EVENTS_TOPIC, event, cardId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<CardEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<CardEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<CardEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<CardEvent> topicEvents = entry.getValue();
            
            for (CardEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getCardId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getCardEventPublishLatency());
                if (ex == null) {
                    log.info("Batch card events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getCardEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch card events", ex);
                    getCardEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, CardEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing card event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for card events")
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
                sample.stop(getCardEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getCardEventPublishLatency());
            log.error("Failed to publish card event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, CardEvent event, String key) {
        
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
            
            sample.stop(getCardEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getCardEventPublishLatency());
            log.error("Failed to publish high-priority card event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed card events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        CardEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getCardId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed card event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(CardEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getCardEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Card event published successfully: type={}, cardId={}, offset={}", 
            event.getEventType(), event.getCardId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(CardEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getCardEventsFailure().increment();
        
        log.error("Failed to publish card event: type={}, cardId={}, topic={}", 
            event.getEventType(), event.getCardId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(CardEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed card event for retry: type={}, cardId={}", 
            event.getEventType(), event.getCardId());
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
        log.error("Card event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Card event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, CardEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterCardEvent dlqEvent = DeadLetterCardEvent.builder()
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
                    log.warn("Card event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send card event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish card event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "card-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(CardEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getCardId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<CardEvent>> groupEventsByTopic(List<CardEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "CARD_ISSUANCE":
                return CARD_ISSUANCE_EVENTS_TOPIC;
            case "CARD_PIN_MANAGEMENT":
                return CARD_PIN_MANAGEMENT_EVENTS_TOPIC;
            case "CARD_LIMIT_ADJUSTMENT":
                return CARD_LIMIT_ADJUSTMENT_EVENTS_TOPIC;
            case "CARD_REPLACEMENT":
                return CARD_REPLACEMENT_EVENTS_TOPIC;
            case "CARD_REWARDS":
                return CARD_REWARDS_EVENTS_TOPIC;
            case "CARD_STATEMENT":
                return CARD_STATEMENT_EVENTS_TOPIC;
            case "CARD_BALANCE_TRANSFER":
                return CARD_BALANCE_TRANSFER_EVENTS_TOPIC;
            case "CARD_CASH_ADVANCE":
                return CARD_CASH_ADVANCE_EVENTS_TOPIC;
            case "CARD_3DS":
                return CARD_3DS_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown card event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "card-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getCardEventsPublished() {
        if (cardEventsPublished == null) {
            cardEventsPublished = Counter.builder("card.events.published")
                .description("Number of card events published")
                .register(meterRegistry);
        }
        return cardEventsPublished;
    }
    
    private Counter getCardEventsFailure() {
        if (cardEventsFailure == null) {
            cardEventsFailure = Counter.builder("card.events.failure")
                .description("Number of card events that failed to publish")
                .register(meterRegistry);
        }
        return cardEventsFailure;
    }
    
    private Timer getCardEventPublishLatency() {
        if (cardEventPublishLatency == null) {
            cardEventPublishLatency = Timer.builder("card.events.publish.latency")
                .description("Latency of card event publishing")
                .register(meterRegistry);
        }
        return cardEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String cardId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String cardId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.cardId = cardId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}