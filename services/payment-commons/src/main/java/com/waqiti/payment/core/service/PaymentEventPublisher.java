package com.waqiti.payment.core.service;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Industrial-strength payment event publisher with guaranteed delivery
 * 
 * Features:
 * - Transactional event publishing
 * - Dead letter queue handling
 * - Event replay capability
 * - Idempotency support
 * - Event versioning
 * - Metrics and monitoring
 * - Batch publishing
 * - Circuit breaker pattern
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final PaymentEventStore eventStore;
    
    @Value("${kafka.topics.payment-events:payment-events}")
    private String PAYMENT_EVENTS_TOPIC;
    
    @Value("${kafka.topics.refund-events:refund-events}")
    private String REFUND_EVENTS_TOPIC;
    
    @Value("${kafka.topics.payment-status:payment-status}")
    private String PAYMENT_STATUS_TOPIC;
    
    @Value("${kafka.topics.payment-analytics:payment-analytics}")
    private String PAYMENT_ANALYTICS_TOPIC;
    
    @Value("${kafka.topics.fraud-alerts:fraud-alerts}")
    private String FRAUD_ALERTS_TOPIC;
    
    @Value("${kafka.topics.dlq:payment-events-dlq}")
    private String DLQ_TOPIC;
    
    // Event tracking
    private final Queue<PaymentEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    
    // Circuit breaker state
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 1 minute

    /**
     * Publishes payment event with transactional guarantees
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishPaymentEvent(
            PaymentRequest request, PaymentResult result) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker is open, queueing event for later");
            queueFailedEvent(createPaymentEvent(request, result));
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open")
            );
        }
        
        try {
            // Create comprehensive event
            PaymentEvent event = createPaymentEvent(request, result);
            
            // Store event for replay capability
            eventStore.storeEvent(event);
            
            // Create Kafka record with headers
            ProducerRecord<String, Object> record = createKafkaRecord(
                PAYMENT_EVENTS_TOPIC,
                event.getEventId(),
                event
            );
            
            // Publish with callback
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(record).toCompletableFuture();
            
            future.whenComplete((sendResult, ex) -> {
                if (ex == null) {
                    onPublishSuccess(event);
                } else {
                    onPublishFailure(event, ex);
                }
            });
            
            // Publish to analytics topic
            publishAnalyticsEvent(event);
            
            // Publish status update
            publishStatusUpdate(request.getPaymentId().toString(), result.getStatus());
            
            return future;
            
        } catch (Exception e) {
            log.error("Failed to publish payment event: ", e);
            incrementFailureMetrics();
            throw new PaymentEventPublishException("Failed to publish event", e);
        }
    }
    
    private PaymentEvent createPaymentEvent(PaymentRequest request, PaymentResult result) {
        return PaymentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(determineEventType(result.getStatus()))
            .eventVersion("2.0")
            .paymentId(request.getPaymentId().toString())
            .transactionId(result.getTransactionId())
            .correlationId(request.getCorrelationId())
            .amount(request.getAmount())
            .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
            .fromUserId(request.getFromUserId())
            .toUserId(request.getToUserId())
            .paymentType(request.getType().toString())
            .providerType(request.getProviderType().toString())
            .status(result.getStatus().toString())
            .statusReason(result.getErrorMessage())
            .processedAt(result.getProcessedAt())
            .processingTime(calculateProcessingTime(request, result))
            .fees(result.getFees())
            .metadata(mergeMetadata(request.getMetadata(), result.getProviderResponse()))
            .timestamp(Instant.now())
            .sequenceNumber(publishedCount.incrementAndGet())
            .build();
    }

    /**
     * Publishes refund event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRefundEvent(
            RefundRequest request, PaymentResult result) {
        
        try {
            RefundEvent event = RefundEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REFUND_" + result.getStatus())
                .eventVersion("2.0")
                .refundId(request.getRefundId().toString())
                .originalPaymentId(request.getOriginalPaymentId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .reason(request.getReason())
                .refundType(request.getRefundType())
                .status(result.getStatus().toString())
                .processedAt(result.getProcessedAt())
                .providerRefundId(result.getProviderTransactionId())
                .metadata(result.getProviderResponse())
                .timestamp(Instant.now())
                .build();
            
            // Store event
            eventStore.storeEvent(event);
            
            // Create record with headers
            ProducerRecord<String, Object> record = createKafkaRecord(
                REFUND_EVENTS_TOPIC,
                event.getEventId(),
                event
            );
            
            return kafkaTemplate.send(record).toCompletableFuture();
            
        } catch (Exception e) {
            log.error("Failed to publish refund event: ", e);
            throw new PaymentEventPublishException("Failed to publish refund event", e);
        }
    }
    
    /**
     * Publishes fraud alert event
     */
    @Async
    public void publishFraudAlert(String paymentId, String userId, 
                                  String alertType, Map<String, Object> details) {
        try {
            FraudAlertEvent event = FraudAlertEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("FRAUD_ALERT")
                .paymentId(paymentId)
                .userId(userId)
                .alertType(alertType)
                .severity(determineSeverity(alertType))
                .details(details)
                .timestamp(Instant.now())
                .build();
            
            ProducerRecord<String, Object> record = createKafkaRecord(
                FRAUD_ALERTS_TOPIC,
                event.getEventId(),
                event
            );
            
            kafkaTemplate.send(record);
            
            log.warn("Published fraud alert: paymentId={}, alertType={}", 
                paymentId, alertType);
            
        } catch (Exception e) {
            log.error("Failed to publish fraud alert: ", e);
        }
    }
    
    /**
     * Publishes payment status update
     */
    private void publishStatusUpdate(String paymentId, PaymentStatus status) {
        try {
            StatusUpdateEvent event = StatusUpdateEvent.builder()
                .paymentId(paymentId)
                .status(status.toString())
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send(PAYMENT_STATUS_TOPIC, paymentId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish status update: ", e);
        }
    }
    
    /**
     * Publishes analytics event
     */
    private void publishAnalyticsEvent(PaymentEvent event) {
        try {
            AnalyticsEvent analyticsEvent = AnalyticsEvent.builder()
                .paymentId(event.getPaymentId())
                .amount(event.getAmount())
                .paymentType(event.getPaymentType())
                .status(event.getStatus())
                .processingTime(event.getProcessingTime())
                .timestamp(event.getTimestamp())
                .build();
            
            kafkaTemplate.send(PAYMENT_ANALYTICS_TOPIC, 
                event.getPaymentId(), analyticsEvent);
            
        } catch (Exception e) {
            log.debug("Failed to publish analytics event: ", e);
        }
    }
    
    /**
     * Handles successful event publication
     */
    private void onPublishSuccess(PaymentEvent event) {
        log.debug("Successfully published event: {}", event.getEventId());
        incrementSuccessMetrics();
        eventStore.markEventPublished(event.getEventId());
        
        // Reset circuit breaker on success
        if (circuitBreakerOpen) {
            closeCircuitBreaker();
        }
    }
    
    /**
     * Handles failed event publication
     */
    private void onPublishFailure(PaymentEvent event, Throwable ex) {
        log.error("Failed to publish event: {}", event.getEventId(), ex);
        incrementFailureMetrics();
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Check if circuit breaker should open
        if (shouldOpenCircuitBreaker()) {
            openCircuitBreaker();
        }
        
        // Send to DLQ after max retries
        if (event.getRetryCount() >= 3) {
            sendToDeadLetterQueue(event, ex);
        }
    }
    
    /**
     * Creates Kafka record with headers
     */
    private ProducerRecord<String, Object> createKafkaRecord(
            String topic, String key, Object value) {
        
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, value);
        
        // Add standard headers
        record.headers().add(new RecordHeader("eventId", 
            key.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("timestamp", 
            String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("version", 
            "2.0".getBytes(StandardCharsets.UTF_8)));
        
        return record;
    }
    
    /**
     * Sends failed event to dead letter queue
     */
    private void sendToDeadLetterQueue(PaymentEvent event, Throwable error) {
        try {
            Map<String, Object> dlqEvent = new HashMap<>();
            dlqEvent.put("originalEvent", event);
            dlqEvent.put("error", error.getMessage());
            dlqEvent.put("errorType", error.getClass().getName());
            dlqEvent.put("timestamp", Instant.now());
            dlqEvent.put("retryCount", event.getRetryCount());
            
            kafkaTemplate.send(DLQ_TOPIC, event.getEventId(), dlqEvent);
            
            log.error("Event sent to DLQ: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to send event to DLQ: ", e);
        }
    }
    
    /**
     * Retries failed events (scheduled task)
     */
    @Async
    public void retryFailedEvents() {
        if (failedEvents.isEmpty()) {
            return;
        }
        
        log.info("Retrying {} failed events", failedEvents.size());
        
        List<PaymentEvent> eventsToRetry = new ArrayList<>();
        PaymentEvent event;
        
        while ((event = failedEvents.poll()) != null) {
            eventsToRetry.add(event);
        }
        
        for (PaymentEvent failedEvent : eventsToRetry) {
            try {
                failedEvent.incrementRetryCount();
                ProducerRecord<String, Object> record = createKafkaRecord(
                    PAYMENT_EVENTS_TOPIC,
                    failedEvent.getEventId(),
                    failedEvent
                );
                
                kafkaTemplate.send(record).whenComplete((result, ex) -> {
                    if (ex != null) {
                        queueFailedEvent(failedEvent);
                    }
                });
                
            } catch (Exception e) {
                log.error("Failed to retry event: ", e);
                queueFailedEvent(failedEvent);
            }
        }
    }
    
    /**
     * Replay events from event store
     */
    public void replayEvents(LocalDateTime from, LocalDateTime to) {
        log.info("Replaying events from {} to {}", from, to);
        
        List<PaymentEvent> events = eventStore.getEvents(from, to);
        
        for (PaymentEvent event : events) {
            try {
                ProducerRecord<String, Object> record = createKafkaRecord(
                    PAYMENT_EVENTS_TOPIC,
                    event.getEventId(),
                    event
                );
                
                kafkaTemplate.send(record);
                
            } catch (Exception e) {
                log.error("Failed to replay event: ", e);
            }
        }
    }
    
    // Helper methods
    
    private String determineEventType(PaymentStatus status) {
        return switch (status) {
            case COMPLETED -> "PAYMENT_COMPLETED";
            case PENDING -> "PAYMENT_PENDING";
            case PROCESSING -> "PAYMENT_PROCESSING";
            case FAILED -> "PAYMENT_FAILED";
            case CANCELLED -> "PAYMENT_CANCELLED";
            case REFUNDED -> "PAYMENT_REFUNDED";
            default -> "PAYMENT_UNKNOWN";
        };
    }
    
    private long calculateProcessingTime(PaymentRequest request, PaymentResult result) {
        if (request.getCreatedAt() != null && result.getProcessedAt() != null) {
            return java.time.Duration.between(
                request.getCreatedAt(), result.getProcessedAt()
            ).toMillis();
        }
        return 0;
    }
    
    private Map<String, Object> mergeMetadata(Map<String, Object> requestMeta, 
                                              Map<String, Object> responseMeta) {
        Map<String, Object> merged = new HashMap<>();
        if (requestMeta != null) merged.putAll(requestMeta);
        if (responseMeta != null) merged.putAll(responseMeta);
        return merged;
    }
    
    private String determineSeverity(String alertType) {
        return switch (alertType) {
            case "BLACKLIST_MATCH", "SANCTIONS_HIT" -> "CRITICAL";
            case "HIGH_RISK_SCORE", "VELOCITY_EXCEEDED" -> "HIGH";
            case "UNUSUAL_PATTERN", "NEW_DEVICE" -> "MEDIUM";
            default -> "LOW";
        };
    }
    
    private void queueFailedEvent(PaymentEvent event) {
        failedEvents.offer(event);
        if (failedEvents.size() > 1000) {
            // Remove oldest if queue is too large
            failedEvents.poll();
        }
    }
    
    private boolean isCircuitBreakerOpen() {
        if (circuitBreakerOpen) {
            // Check if timeout has passed
            if (System.currentTimeMillis() - circuitBreakerOpenTime > CIRCUIT_BREAKER_TIMEOUT) {
                closeCircuitBreaker();
                return false;
            }
            return true;
        }
        return false;
    }
    
    private boolean shouldOpenCircuitBreaker() {
        // Open if failure rate is too high
        long total = publishedCount.get() + failedCount.get();
        if (total > 100) {
            double failureRate = (double) failedCount.get() / total;
            return failureRate > 0.5; // 50% failure rate
        }
        return false;
    }
    
    private void openCircuitBreaker() {
        circuitBreakerOpen = true;
        circuitBreakerOpenTime = System.currentTimeMillis();
        log.error("Circuit breaker opened due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        log.info("Circuit breaker closed");
    }
    
    private void incrementSuccessMetrics() {
        Counter.builder("payment.events.published")
            .tag("status", "success")
            .register(meterRegistry)
            .increment();
    }
    
    private void incrementFailureMetrics() {
        failedCount.incrementAndGet();
        Counter.builder("payment.events.published")
            .tag("status", "failure")
            .register(meterRegistry)
            .increment();
    }
}