package com.waqiti.savings.events;

import com.waqiti.savings.events.model.SavingsEvent;
import com.waqiti.savings.events.model.DeadLetterSavingsEvent;
import com.waqiti.savings.events.store.SavingsEventStore;
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
 * Enterprise-grade savings event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SavingsEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final SavingsEventStore eventStore;
    
    // Topic constants
    private static final String SAVINGS_ACCOUNT_OPENING_EVENTS_TOPIC = "savings-account-opening-events";
    private static final String SAVINGS_DEPOSIT_EVENTS_TOPIC = "savings-deposit-events";
    private static final String SAVINGS_WITHDRAWAL_EVENTS_TOPIC = "savings-withdrawal-events";
    private static final String SAVINGS_INTEREST_CALCULATION_EVENTS_TOPIC = "savings-interest-calculation-events";
    private static final String SAVINGS_GOAL_TRACKING_EVENTS_TOPIC = "savings-goal-tracking-events";
    private static final String SAVINGS_AUTOMATION_EVENTS_TOPIC = "savings-automation-events";
    private static final String SAVINGS_BONUS_EVENTS_TOPIC = "savings-bonus-events";
    private static final String SAVINGS_CERTIFICATE_DEPOSIT_EVENTS_TOPIC = "savings-certificate-deposit-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<SavingsEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter savingsEventsPublished;
    private Counter savingsEventsFailure;
    private Timer savingsEventPublishLatency;
    
    /**
     * Publishes savings account opened event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishSavingsAccountOpened(
            String accountId, String userId, String accountType, BigDecimal initialDeposit,
            String currency, BigDecimal interestRate, String term, String maturityDate) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SAVINGS_ACCOUNT_OPENED")
                .accountId(accountId)
                .userId(userId)
                .accountType(accountType)
                .amount(initialDeposit)
                .currency(currency)
                .interestRate(interestRate)
                .term(term)
                .maturityDate(maturityDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("ACTIVE")
                .build();
        
        return publishEvent(SAVINGS_ACCOUNT_OPENING_EVENTS_TOPIC, event, accountId);
    }
    
    /**
     * Publishes savings deposit event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishSavingsDeposit(
            String accountId, String userId, BigDecimal depositAmount, String currency,
            String depositMethod, String transactionId, BigDecimal newBalance) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SAVINGS_DEPOSIT")
                .accountId(accountId)
                .userId(userId)
                .amount(depositAmount)
                .currency(currency)
                .depositMethod(depositMethod)
                .transactionId(transactionId)
                .newBalance(newBalance)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("COMPLETED")
                .build();
        
        return publishEvent(SAVINGS_DEPOSIT_EVENTS_TOPIC, event, accountId);
    }
    
    /**
     * Publishes savings withdrawal event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishSavingsWithdrawal(
            String accountId, String userId, BigDecimal withdrawalAmount, String currency,
            String withdrawalMethod, String transactionId, BigDecimal newBalance,
            BigDecimal penaltyAmount) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SAVINGS_WITHDRAWAL")
                .accountId(accountId)
                .userId(userId)
                .amount(withdrawalAmount)
                .currency(currency)
                .withdrawalMethod(withdrawalMethod)
                .transactionId(transactionId)
                .newBalance(newBalance)
                .penaltyAmount(penaltyAmount)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("COMPLETED")
                .build();
        
        return publishEvent(SAVINGS_WITHDRAWAL_EVENTS_TOPIC, event, accountId);
    }
    
    /**
     * Publishes interest calculated event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishInterestCalculated(
            String accountId, String userId, BigDecimal principal, BigDecimal interestRate,
            BigDecimal interestEarned, String currency, Instant calculationPeriodStart,
            Instant calculationPeriodEnd, String compoundingFrequency) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INTEREST_CALCULATED")
                .accountId(accountId)
                .userId(userId)
                .principal(principal)
                .interestRate(interestRate)
                .amount(interestEarned)
                .currency(currency)
                .calculationPeriodStart(calculationPeriodStart)
                .calculationPeriodEnd(calculationPeriodEnd)
                .compoundingFrequency(compoundingFrequency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("CALCULATED")
                .build();
        
        return publishEvent(SAVINGS_INTEREST_CALCULATION_EVENTS_TOPIC, event, accountId);
    }
    
    /**
     * Publishes goal progress event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishGoalProgress(
            String goalId, String accountId, String userId, String goalName, 
            BigDecimal targetAmount, BigDecimal currentAmount, String currency,
            BigDecimal progressPercentage, String goalStatus, Instant targetDate) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("GOAL_PROGRESS_UPDATED")
                .goalId(goalId)
                .accountId(accountId)
                .userId(userId)
                .goalName(goalName)
                .targetAmount(targetAmount)
                .amount(currentAmount)
                .currency(currency)
                .progressPercentage(progressPercentage)
                .status(goalStatus)
                .targetDate(targetDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(SAVINGS_GOAL_TRACKING_EVENTS_TOPIC, event, goalId);
    }
    
    /**
     * Publishes automation triggered event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishAutomationTriggered(
            String ruleId, String accountId, String userId, String automationType,
            BigDecimal transferAmount, String currency, String triggerCondition,
            String sourceAccount, String frequency) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("AUTOMATION_TRIGGERED")
                .ruleId(ruleId)
                .accountId(accountId)
                .userId(userId)
                .automationType(automationType)
                .amount(transferAmount)
                .currency(currency)
                .triggerCondition(triggerCondition)
                .sourceAccount(sourceAccount)
                .frequency(frequency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("EXECUTED")
                .build();
        
        return publishEvent(SAVINGS_AUTOMATION_EVENTS_TOPIC, event, accountId);
    }
    
    /**
     * Publishes bonus issued event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBonusIssued(
            String accountId, String userId, BigDecimal bonusAmount, String currency,
            String bonusType, String bonusReason, String campaignId, String transactionId) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BONUS_ISSUED")
                .accountId(accountId)
                .userId(userId)
                .amount(bonusAmount)
                .currency(currency)
                .bonusType(bonusType)
                .bonusReason(bonusReason)
                .campaignId(campaignId)
                .transactionId(transactionId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("ISSUED")
                .build();
        
        return publishEvent(SAVINGS_BONUS_EVENTS_TOPIC, event, accountId);
    }
    
    /**
     * Publishes certificate of deposit created event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCDCreated(
            String cdId, String accountId, String userId, BigDecimal principal,
            String currency, BigDecimal interestRate, String term, Instant maturityDate,
            BigDecimal maturityValue, String cdType) {
        
        SavingsEvent event = SavingsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CD_CREATED")
                .cdId(cdId)
                .accountId(accountId)
                .userId(userId)
                .principal(principal)
                .currency(currency)
                .interestRate(interestRate)
                .term(term)
                .maturityDate(maturityDate.toString())
                .maturityValue(maturityValue)
                .cdType(cdType)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("ACTIVE")
                .build();
        
        return publishEvent(SAVINGS_CERTIFICATE_DEPOSIT_EVENTS_TOPIC, event, cdId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<SavingsEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<SavingsEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<SavingsEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<SavingsEvent> topicEvents = entry.getValue();
            
            for (SavingsEvent event : topicEvents) {
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
                sample.stop(getSavingsEventPublishLatency());
                if (ex == null) {
                    log.info("Batch savings events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getSavingsEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch savings events", ex);
                    getSavingsEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, SavingsEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing savings event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for savings events")
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
                sample.stop(getSavingsEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getSavingsEventPublishLatency());
            log.error("Failed to publish savings event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed savings events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        SavingsEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, getKeyForEvent(event));
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed savings event replay"));
    }
    
    // Helper methods (similar structure to previous publishers)
    
    private void onPublishSuccess(SavingsEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getSavingsEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Savings event published successfully: type={}, accountId={}, offset={}", 
            event.getEventType(), event.getAccountId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(SavingsEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getSavingsEventsFailure().increment();
        
        log.error("Failed to publish savings event: type={}, accountId={}, topic={}", 
            event.getEventType(), event.getAccountId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(SavingsEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed savings event for retry: type={}, accountId={}", 
            event.getEventType(), event.getAccountId());
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
        log.error("Savings event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Savings event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, SavingsEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterSavingsEvent dlqEvent = DeadLetterSavingsEvent.builder()
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
                    log.warn("Savings event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send savings event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish savings event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "savings-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(SavingsEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getAccountId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<SavingsEvent>> groupEventsByTopic(List<SavingsEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "SAVINGS_ACCOUNT_OPENED":
                return SAVINGS_ACCOUNT_OPENING_EVENTS_TOPIC;
            case "SAVINGS_DEPOSIT":
                return SAVINGS_DEPOSIT_EVENTS_TOPIC;
            case "SAVINGS_WITHDRAWAL":
                return SAVINGS_WITHDRAWAL_EVENTS_TOPIC;
            case "INTEREST_CALCULATED":
                return SAVINGS_INTEREST_CALCULATION_EVENTS_TOPIC;
            case "GOAL_PROGRESS_UPDATED":
                return SAVINGS_GOAL_TRACKING_EVENTS_TOPIC;
            case "AUTOMATION_TRIGGERED":
                return SAVINGS_AUTOMATION_EVENTS_TOPIC;
            case "BONUS_ISSUED":
                return SAVINGS_BONUS_EVENTS_TOPIC;
            case "CD_CREATED":
                return SAVINGS_CERTIFICATE_DEPOSIT_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown savings event type: " + eventType);
        }
    }
    
    private String getKeyForEvent(SavingsEvent event) {
        if (event.getGoalId() != null) return event.getGoalId();
        if (event.getCdId() != null) return event.getCdId();
        return event.getAccountId();
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "savings-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getSavingsEventsPublished() {
        if (savingsEventsPublished == null) {
            savingsEventsPublished = Counter.builder("savings.events.published")
                .description("Number of savings events published")
                .register(meterRegistry);
        }
        return savingsEventsPublished;
    }
    
    private Counter getSavingsEventsFailure() {
        if (savingsEventsFailure == null) {
            savingsEventsFailure = Counter.builder("savings.events.failure")
                .description("Number of savings events that failed to publish")
                .register(meterRegistry);
        }
        return savingsEventsFailure;
    }
    
    private Timer getSavingsEventPublishLatency() {
        if (savingsEventPublishLatency == null) {
            savingsEventPublishLatency = Timer.builder("savings.events.publish.latency")
                .description("Latency of savings event publishing")
                .register(meterRegistry);
        }
        return savingsEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String accountId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String accountId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.accountId = accountId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}