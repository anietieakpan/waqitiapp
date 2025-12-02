package com.waqiti.merchant.events;

import com.waqiti.merchant.events.model.MerchantEvent;
import com.waqiti.merchant.events.model.DeadLetterMerchantEvent;
import com.waqiti.merchant.events.store.MerchantEventStore;
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
 * Enterprise-grade merchant event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final MerchantEventStore eventStore;
    
    // Topic constants
    private static final String MERCHANT_ACCOUNT_CREATION_EVENTS_TOPIC = "merchant-account-creation-events";
    private static final String MERCHANT_PAYMENT_PROCESSING_EVENTS_TOPIC = "merchant-payment-processing-events";
    private static final String MERCHANT_SETTLEMENT_EVENTS_TOPIC = "merchant-settlement-events";
    private static final String MERCHANT_CHARGEBACK_EVENTS_TOPIC = "merchant-chargeback-events";
    private static final String MERCHANT_REFUND_EVENTS_TOPIC = "merchant-refund-events";
    private static final String MERCHANT_FEE_CALCULATION_EVENTS_TOPIC = "merchant-fee-calculation-events";
    private static final String MERCHANT_REPORTING_EVENTS_TOPIC = "merchant-reporting-events";
    private static final String MERCHANT_COMPLIANCE_EVENTS_TOPIC = "merchant-compliance-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<MerchantEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter merchantEventsPublished;
    private Counter merchantEventsFailure;
    private Timer merchantEventPublishLatency;
    
    /**
     * Publishes merchant account created event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantAccountCreated(
            String merchantId, String businessName, String businessType, String ownerId,
            String tier, String category, String registrationNumber) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_ACCOUNT_CREATED")
                .merchantId(merchantId)
                .businessName(businessName)
                .businessType(businessType)
                .ownerId(ownerId)
                .tier(tier)
                .category(category)
                .registrationNumber(registrationNumber)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("ACTIVE")
                .build();
        
        return publishEvent(MERCHANT_ACCOUNT_CREATION_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant payment processed event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantPaymentProcessed(
            String merchantId, String paymentId, String customerId, BigDecimal amount,
            String currency, String paymentMethod, String paymentStatus, BigDecimal feeAmount) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_PAYMENT_PROCESSED")
                .merchantId(merchantId)
                .paymentId(paymentId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .status(paymentStatus)
                .feeAmount(feeAmount)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(MERCHANT_PAYMENT_PROCESSING_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant settlement event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantSettlement(
            String merchantId, String settlementId, BigDecimal settlementAmount, String currency,
            Instant settlementPeriodStart, Instant settlementPeriodEnd, 
            BigDecimal totalFees, int transactionCount) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_SETTLEMENT")
                .merchantId(merchantId)
                .settlementId(settlementId)
                .amount(settlementAmount)
                .currency(currency)
                .settlementPeriodStart(settlementPeriodStart)
                .settlementPeriodEnd(settlementPeriodEnd)
                .feeAmount(totalFees)
                .transactionCount(transactionCount)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("PENDING")
                .build();
        
        return publishEvent(MERCHANT_SETTLEMENT_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant chargeback event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantChargeback(
            String merchantId, String paymentId, String chargebackId, BigDecimal amount,
            String currency, String reasonCode, String chargebackReason, String disputeStatus) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_CHARGEBACK")
                .merchantId(merchantId)
                .paymentId(paymentId)
                .chargebackId(chargebackId)
                .amount(amount)
                .currency(currency)
                .reasonCode(reasonCode)
                .reason(chargebackReason)
                .status(disputeStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(MERCHANT_CHARGEBACK_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant refund event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantRefund(
            String merchantId, String paymentId, String refundId, BigDecimal refundAmount,
            String currency, String refundReason, String refundMethod, String refundStatus) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_REFUND")
                .merchantId(merchantId)
                .paymentId(paymentId)
                .refundId(refundId)
                .amount(refundAmount)
                .currency(currency)
                .reason(refundReason)
                .refundMethod(refundMethod)
                .status(refundStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(MERCHANT_REFUND_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant fee calculated event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantFeeCalculated(
            String merchantId, String paymentId, BigDecimal transactionAmount, String currency,
            BigDecimal feeAmount, String feeType, BigDecimal feeRate, String pricingTier) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_FEE_CALCULATED")
                .merchantId(merchantId)
                .paymentId(paymentId)
                .amount(transactionAmount)
                .currency(currency)
                .feeAmount(feeAmount)
                .feeType(feeType)
                .feeRate(feeRate)
                .tier(pricingTier)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(MERCHANT_FEE_CALCULATION_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant report event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantReport(
            String merchantId, String reportId, String reportType, String reportFormat,
            Instant reportPeriodStart, Instant reportPeriodEnd, String reportStatus,
            BigDecimal totalRevenue, int totalTransactions) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_REPORT_GENERATED")
                .merchantId(merchantId)
                .reportId(reportId)
                .reportType(reportType)
                .reportFormat(reportFormat)
                .reportPeriodStart(reportPeriodStart)
                .reportPeriodEnd(reportPeriodEnd)
                .status(reportStatus)
                .amount(totalRevenue)
                .transactionCount(totalTransactions)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(MERCHANT_REPORTING_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes merchant compliance check event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMerchantComplianceCheck(
            String merchantId, String checkId, String checkType, String checkResult,
            String complianceFramework, String riskLevel, String recommendedAction) {
        
        MerchantEvent event = MerchantEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MERCHANT_COMPLIANCE_CHECK")
                .merchantId(merchantId)
                .checkId(checkId)
                .checkType(checkType)
                .checkResult(checkResult)
                .complianceFramework(complianceFramework)
                .riskLevel(riskLevel)
                .recommendedAction(recommendedAction)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("COMPLETED")
                .build();
        
        return publishHighPriorityEvent(MERCHANT_COMPLIANCE_EVENTS_TOPIC, event, merchantId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<MerchantEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<MerchantEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<MerchantEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<MerchantEvent> topicEvents = entry.getValue();
            
            for (MerchantEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getMerchantId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getMerchantEventPublishLatency());
                if (ex == null) {
                    log.info("Batch merchant events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getMerchantEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch merchant events", ex);
                    getMerchantEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, MerchantEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing merchant event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for merchant events")
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
                sample.stop(getMerchantEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getMerchantEventPublishLatency());
            log.error("Failed to publish merchant event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, MerchantEvent event, String key) {
        
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
            
            sample.stop(getMerchantEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getMerchantEventPublishLatency());
            log.error("Failed to publish high-priority merchant event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed merchant events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        MerchantEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getMerchantId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed merchant event replay"));
    }
    
    // Helper methods (similar structure to WalletEventPublisher)
    
    private void onPublishSuccess(MerchantEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getMerchantEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Merchant event published successfully: type={}, merchantId={}, offset={}", 
            event.getEventType(), event.getMerchantId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(MerchantEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getMerchantEventsFailure().increment();
        
        log.error("Failed to publish merchant event: type={}, merchantId={}, topic={}", 
            event.getEventType(), event.getMerchantId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(MerchantEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed merchant event for retry: type={}, merchantId={}", 
            event.getEventType(), event.getMerchantId());
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
        log.error("Merchant event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Merchant event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, MerchantEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterMerchantEvent dlqEvent = DeadLetterMerchantEvent.builder()
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
                    log.warn("Merchant event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send merchant event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish merchant event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "merchant-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(MerchantEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getMerchantId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<MerchantEvent>> groupEventsByTopic(List<MerchantEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "MERCHANT_ACCOUNT_CREATED":
                return MERCHANT_ACCOUNT_CREATION_EVENTS_TOPIC;
            case "MERCHANT_PAYMENT_PROCESSED":
                return MERCHANT_PAYMENT_PROCESSING_EVENTS_TOPIC;
            case "MERCHANT_SETTLEMENT":
                return MERCHANT_SETTLEMENT_EVENTS_TOPIC;
            case "MERCHANT_CHARGEBACK":
                return MERCHANT_CHARGEBACK_EVENTS_TOPIC;
            case "MERCHANT_REFUND":
                return MERCHANT_REFUND_EVENTS_TOPIC;
            case "MERCHANT_FEE_CALCULATED":
                return MERCHANT_FEE_CALCULATION_EVENTS_TOPIC;
            case "MERCHANT_REPORT_GENERATED":
                return MERCHANT_REPORTING_EVENTS_TOPIC;
            case "MERCHANT_COMPLIANCE_CHECK":
                return MERCHANT_COMPLIANCE_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown merchant event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "merchant-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getMerchantEventsPublished() {
        if (merchantEventsPublished == null) {
            merchantEventsPublished = Counter.builder("merchant.events.published")
                .description("Number of merchant events published")
                .register(meterRegistry);
        }
        return merchantEventsPublished;
    }
    
    private Counter getMerchantEventsFailure() {
        if (merchantEventsFailure == null) {
            merchantEventsFailure = Counter.builder("merchant.events.failure")
                .description("Number of merchant events that failed to publish")
                .register(meterRegistry);
        }
        return merchantEventsFailure;
    }
    
    private Timer getMerchantEventPublishLatency() {
        if (merchantEventPublishLatency == null) {
            merchantEventPublishLatency = Timer.builder("merchant.events.publish.latency")
                .description("Latency of merchant event publishing")
                .register(meterRegistry);
        }
        return merchantEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String merchantId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String merchantId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.merchantId = merchantId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}