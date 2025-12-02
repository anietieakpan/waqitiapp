package com.waqiti.wallet.events;

import com.waqiti.wallet.events.model.WalletEvent;
import com.waqiti.wallet.events.model.DeadLetterWalletEvent;
import com.waqiti.wallet.events.store.WalletEventStore;
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
 * Enterprise-grade wallet event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WalletEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final WalletEventStore eventStore;
    
    // Topic constants
    private static final String WALLET_CREATION_EVENTS_TOPIC = "wallet-creation-events";
    private static final String WALLET_TOPUP_EVENTS_TOPIC = "wallet-topup-events";
    private static final String WALLET_TRANSFER_EVENTS_TOPIC = "wallet-transfer-events";
    private static final String WALLET_WITHDRAWAL_EVENTS_TOPIC = "wallet-withdrawal-events";
    private static final String WALLET_FREEZING_EVENTS_TOPIC = "wallet-freezing-events";
    private static final String WALLET_LIMIT_MANAGEMENT_EVENTS_TOPIC = "wallet-limit-management-events";
    private static final String WALLET_REWARDS_EVENTS_TOPIC = "wallet-rewards-events";
    private static final String WALLET_STATEMENT_EVENTS_TOPIC = "wallet-statement-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<WalletEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter walletEventsPublished;
    private Counter walletEventsFailure;
    private Timer walletEventPublishLatency;
    
    /**
     * Publishes wallet created event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletCreated(
            String walletId, String userId, String walletType, BigDecimal initialBalance, String currency) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_CREATED")
                .walletId(walletId)
                .userId(userId)
                .walletType(walletType)
                .amount(initialBalance)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_CREATION_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes wallet top-up event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletTopUp(
            String walletId, String userId, BigDecimal amount, String currency, 
            String sourceAccountId, String transactionId) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_TOP_UP")
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .sourceAccountId(sourceAccountId)
                .transactionId(transactionId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_TOPUP_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes wallet transfer event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletTransfer(
            String fromWalletId, String toWalletId, String fromUserId, String toUserId,
            BigDecimal amount, String currency, String transactionId, String transferType) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_TRANSFER")
                .walletId(fromWalletId)
                .targetWalletId(toWalletId)
                .userId(fromUserId)
                .targetUserId(toUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .transferType(transferType)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_TRANSFER_EVENTS_TOPIC, event, fromWalletId);
    }
    
    /**
     * Publishes wallet withdrawal event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletWithdrawal(
            String walletId, String userId, BigDecimal amount, String currency,
            String destinationAccountId, String transactionId, String withdrawalMethod) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_WITHDRAWAL")
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .destinationAccountId(destinationAccountId)
                .transactionId(transactionId)
                .withdrawalMethod(withdrawalMethod)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_WITHDRAWAL_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes wallet frozen event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletFrozen(
            String walletId, String userId, String reason, String freezeType, String adminId) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_FROZEN")
                .walletId(walletId)
                .userId(userId)
                .reason(reason)
                .freezeType(freezeType)
                .adminId(adminId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(WALLET_FREEZING_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes wallet limit updated event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletLimitUpdated(
            String walletId, String userId, String limitType, BigDecimal oldLimit, 
            BigDecimal newLimit, String currency, String adminId) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_LIMIT_UPDATED")
                .walletId(walletId)
                .userId(userId)
                .limitType(limitType)
                .previousAmount(oldLimit)
                .amount(newLimit)
                .currency(currency)
                .adminId(adminId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_LIMIT_MANAGEMENT_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes wallet reward issued event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletRewardIssued(
            String walletId, String userId, BigDecimal rewardAmount, String currency,
            String rewardType, String campaignId, String transactionId) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_REWARD_ISSUED")
                .walletId(walletId)
                .userId(userId)
                .amount(rewardAmount)
                .currency(currency)
                .rewardType(rewardType)
                .campaignId(campaignId)
                .transactionId(transactionId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_REWARDS_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes wallet statement event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWalletStatement(
            String walletId, String userId, Instant statementPeriodStart, 
            Instant statementPeriodEnd, BigDecimal openingBalance, BigDecimal closingBalance,
            String currency, String statementId) {
        
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WALLET_STATEMENT_GENERATED")
                .walletId(walletId)
                .userId(userId)
                .statementId(statementId)
                .statementPeriodStart(statementPeriodStart)
                .statementPeriodEnd(statementPeriodEnd)
                .previousAmount(openingBalance)
                .amount(closingBalance)
                .currency(currency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(WALLET_STATEMENT_EVENTS_TOPIC, event, walletId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<WalletEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<WalletEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<WalletEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<WalletEvent> topicEvents = entry.getValue();
            
            for (WalletEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getWalletId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getWalletEventPublishLatency());
                if (ex == null) {
                    log.info("Batch wallet events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getWalletEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch wallet events", ex);
                    getWalletEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, WalletEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing wallet event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for wallet events")
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
                sample.stop(getWalletEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getWalletEventPublishLatency());
            log.error("Failed to publish wallet event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, WalletEvent event, String key) {
        
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
            
            sample.stop(getWalletEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getWalletEventPublishLatency());
            log.error("Failed to publish high-priority wallet event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed wallet events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        WalletEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getWalletId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed wallet event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(WalletEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getWalletEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Wallet event published successfully: type={}, walletId={}, offset={}", 
            event.getEventType(), event.getWalletId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(WalletEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getWalletEventsFailure().increment();
        
        log.error("Failed to publish wallet event: type={}, walletId={}, topic={}", 
            event.getEventType(), event.getWalletId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(WalletEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed wallet event for retry: type={}, walletId={}", 
            event.getEventType(), event.getWalletId());
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
        log.error("Wallet event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Wallet event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, WalletEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterWalletEvent dlqEvent = DeadLetterWalletEvent.builder()
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
                    log.warn("Wallet event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send wallet event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish wallet event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "wallet-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(WalletEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getWalletId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<WalletEvent>> groupEventsByTopic(List<WalletEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "WALLET_CREATED":
                return WALLET_CREATION_EVENTS_TOPIC;
            case "WALLET_TOP_UP":
                return WALLET_TOPUP_EVENTS_TOPIC;
            case "WALLET_TRANSFER":
                return WALLET_TRANSFER_EVENTS_TOPIC;
            case "WALLET_WITHDRAWAL":
                return WALLET_WITHDRAWAL_EVENTS_TOPIC;
            case "WALLET_FROZEN":
            case "WALLET_UNFROZEN":
                return WALLET_FREEZING_EVENTS_TOPIC;
            case "WALLET_LIMIT_UPDATED":
                return WALLET_LIMIT_MANAGEMENT_EVENTS_TOPIC;
            case "WALLET_REWARD_ISSUED":
                return WALLET_REWARDS_EVENTS_TOPIC;
            case "WALLET_STATEMENT_GENERATED":
                return WALLET_STATEMENT_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown wallet event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "wallet-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getWalletEventsPublished() {
        if (walletEventsPublished == null) {
            walletEventsPublished = Counter.builder("wallet.events.published")
                .description("Number of wallet events published")
                .register(meterRegistry);
        }
        return walletEventsPublished;
    }
    
    private Counter getWalletEventsFailure() {
        if (walletEventsFailure == null) {
            walletEventsFailure = Counter.builder("wallet.events.failure")
                .description("Number of wallet events that failed to publish")
                .register(meterRegistry);
        }
        return walletEventsFailure;
    }
    
    private Timer getWalletEventPublishLatency() {
        if (walletEventPublishLatency == null) {
            walletEventPublishLatency = Timer.builder("wallet.events.publish.latency")
                .description("Latency of wallet event publishing")
                .register(meterRegistry);
        }
        return walletEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String walletId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String walletId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.walletId = walletId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}