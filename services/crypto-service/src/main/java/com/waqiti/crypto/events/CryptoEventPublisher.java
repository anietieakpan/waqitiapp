package com.waqiti.crypto.events;

import com.waqiti.crypto.events.model.CryptoEvent;
import com.waqiti.crypto.events.model.DeadLetterCryptoEvent;
import com.waqiti.crypto.events.store.CryptoEventStore;
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
 * Enterprise-grade crypto event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CryptoEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CryptoEventStore eventStore;
    
    // Topic constants
    private static final String CRYPTO_WALLET_CREATION_EVENTS_TOPIC = "crypto-wallet-creation-events";
    private static final String CRYPTO_BUY_ORDER_EVENTS_TOPIC = "crypto-buy-order-events";
    private static final String CRYPTO_SELL_ORDER_EVENTS_TOPIC = "crypto-sell-order-events";
    private static final String CRYPTO_SWAP_EVENTS_TOPIC = "crypto-swap-events";
    private static final String CRYPTO_STAKING_EVENTS_TOPIC = "crypto-staking-events";
    private static final String CRYPTO_WITHDRAWAL_EVENTS_TOPIC = "crypto-withdrawal-events";
    private static final String CRYPTO_DEPOSIT_EVENTS_TOPIC = "crypto-deposit-events";
    private static final String CRYPTO_PRICE_ALERT_EVENTS_TOPIC = "crypto-price-alert-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<CryptoEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter cryptoEventsPublished;
    private Counter cryptoEventsFailure;
    private Timer cryptoEventPublishLatency;
    
    /**
     * Publishes crypto wallet created event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoWalletCreated(
            String cryptoWalletId, String userId, String cryptoSymbol, String blockchainNetwork) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_WALLET_CREATED")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .blockchainNetwork(blockchainNetwork)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(CRYPTO_WALLET_CREATION_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto buy order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoBuyOrder(
            String cryptoWalletId, String userId, String cryptoSymbol, BigDecimal amount, 
            BigDecimal price, String currency, String orderId) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_BUY_ORDER")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .amount(amount)
                .price(price)
                .totalValue(amount.multiply(price))
                .currency(currency)
                .orderId(orderId)
                .orderType("BUY")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CRYPTO_BUY_ORDER_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto sell order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoSellOrder(
            String cryptoWalletId, String userId, String cryptoSymbol, BigDecimal amount, 
            BigDecimal price, String currency, String orderId) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_SELL_ORDER")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .amount(amount)
                .price(price)
                .totalValue(amount.multiply(price))
                .currency(currency)
                .orderId(orderId)
                .orderType("SELL")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CRYPTO_SELL_ORDER_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto swap event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoSwap(
            String cryptoWalletId, String userId, String swapFromSymbol, String swapToSymbol,
            BigDecimal fromAmount, BigDecimal toAmount, BigDecimal swapRate, String transactionId) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_SWAP")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .swapFromSymbol(swapFromSymbol)
                .swapToSymbol(swapToSymbol)
                .amount(fromAmount)
                .totalValue(toAmount)
                .swapRate(swapRate)
                .transactionId(transactionId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CRYPTO_SWAP_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto staking event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoStaking(
            String cryptoWalletId, String userId, String cryptoSymbol, BigDecimal amount,
            String stakingPoolId, String stakingDuration, BigDecimal stakingReward) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_STAKING")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .amount(amount)
                .stakingPoolId(stakingPoolId)
                .stakingDuration(stakingDuration)
                .stakingReward(stakingReward)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CRYPTO_STAKING_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto withdrawal event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoWithdrawal(
            String cryptoWalletId, String userId, String cryptoSymbol, BigDecimal amount,
            String withdrawalAddress, String transactionHash, String blockchainNetwork) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_WITHDRAWAL")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .amount(amount)
                .withdrawalAddress(withdrawalAddress)
                .transactionHash(transactionHash)
                .blockchainNetwork(blockchainNetwork)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CRYPTO_WITHDRAWAL_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto deposit event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoDeposit(
            String cryptoWalletId, String userId, String cryptoSymbol, BigDecimal amount,
            String depositAddress, String transactionHash, String blockchainNetwork) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_DEPOSIT")
                .cryptoWalletId(cryptoWalletId)
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .amount(amount)
                .depositAddress(depositAddress)
                .transactionHash(transactionHash)
                .blockchainNetwork(blockchainNetwork)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CRYPTO_DEPOSIT_EVENTS_TOPIC, event, cryptoWalletId);
    }
    
    /**
     * Publishes crypto price alert event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCryptoPriceAlert(
            String userId, String cryptoSymbol, BigDecimal currentPrice, BigDecimal alertThreshold,
            String alertType) {
        
        CryptoEvent event = CryptoEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CRYPTO_PRICE_ALERT")
                .userId(userId)
                .cryptoSymbol(cryptoSymbol)
                .price(currentPrice)
                .alertThreshold(alertThreshold)
                .alertType(alertType)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(CRYPTO_PRICE_ALERT_EVENTS_TOPIC, event, userId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<CryptoEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<CryptoEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<CryptoEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<CryptoEvent> topicEvents = entry.getValue();
            
            for (CryptoEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getCryptoWalletId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getCryptoEventPublishLatency());
                if (ex == null) {
                    log.info("Batch crypto events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getCryptoEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch crypto events", ex);
                    getCryptoEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, CryptoEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing crypto event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for crypto events")
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
                sample.stop(getCryptoEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getCryptoEventPublishLatency());
            log.error("Failed to publish crypto event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, CryptoEvent event, String key) {
        
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
            
            sample.stop(getCryptoEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getCryptoEventPublishLatency());
            log.error("Failed to publish high-priority crypto event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed crypto events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        CryptoEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getCryptoWalletId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed crypto event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(CryptoEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getCryptoEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Crypto event published successfully: type={}, cryptoWalletId={}, offset={}", 
            event.getEventType(), event.getCryptoWalletId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(CryptoEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getCryptoEventsFailure().increment();
        
        log.error("Failed to publish crypto event: type={}, cryptoWalletId={}, topic={}", 
            event.getEventType(), event.getCryptoWalletId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(CryptoEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed crypto event for retry: type={}, cryptoWalletId={}", 
            event.getEventType(), event.getCryptoWalletId());
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
        log.error("Crypto event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Crypto event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, CryptoEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterCryptoEvent dlqEvent = DeadLetterCryptoEvent.builder()
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
                    log.warn("Crypto event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send crypto event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish crypto event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "crypto-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(CryptoEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getCryptoWalletId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<CryptoEvent>> groupEventsByTopic(List<CryptoEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "CRYPTO_WALLET_CREATED":
                return CRYPTO_WALLET_CREATION_EVENTS_TOPIC;
            case "CRYPTO_BUY_ORDER":
                return CRYPTO_BUY_ORDER_EVENTS_TOPIC;
            case "CRYPTO_SELL_ORDER":
                return CRYPTO_SELL_ORDER_EVENTS_TOPIC;
            case "CRYPTO_SWAP":
                return CRYPTO_SWAP_EVENTS_TOPIC;
            case "CRYPTO_STAKING":
                return CRYPTO_STAKING_EVENTS_TOPIC;
            case "CRYPTO_WITHDRAWAL":
                return CRYPTO_WITHDRAWAL_EVENTS_TOPIC;
            case "CRYPTO_DEPOSIT":
                return CRYPTO_DEPOSIT_EVENTS_TOPIC;
            case "CRYPTO_PRICE_ALERT":
                return CRYPTO_PRICE_ALERT_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown crypto event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "crypto-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getCryptoEventsPublished() {
        if (cryptoEventsPublished == null) {
            cryptoEventsPublished = Counter.builder("crypto.events.published")
                .description("Number of crypto events published")
                .register(meterRegistry);
        }
        return cryptoEventsPublished;
    }
    
    private Counter getCryptoEventsFailure() {
        if (cryptoEventsFailure == null) {
            cryptoEventsFailure = Counter.builder("crypto.events.failure")
                .description("Number of crypto events that failed to publish")
                .register(meterRegistry);
        }
        return cryptoEventsFailure;
    }
    
    private Timer getCryptoEventPublishLatency() {
        if (cryptoEventPublishLatency == null) {
            cryptoEventPublishLatency = Timer.builder("crypto.events.publish.latency")
                .description("Latency of crypto event publishing")
                .register(meterRegistry);
        }
        return cryptoEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String cryptoWalletId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String cryptoWalletId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.cryptoWalletId = cryptoWalletId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}