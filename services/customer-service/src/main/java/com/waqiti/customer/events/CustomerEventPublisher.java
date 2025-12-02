package com.waqiti.customer.events;

import com.waqiti.customer.events.model.CustomerEvent;
import com.waqiti.customer.events.model.DeadLetterCustomerEvent;
import com.waqiti.customer.events.store.CustomerEventStore;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise-grade customer event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CustomerEventStore eventStore;
    
    // Topic constants
    private static final String CUSTOMER_ONBOARDING_EVENTS_TOPIC = "customer-onboarding-events";
    private static final String CUSTOMER_PROFILE_UPDATE_EVENTS_TOPIC = "customer-profile-update-events";
    private static final String CUSTOMER_DEATH_NOTIFICATION_EVENTS_TOPIC = "customer-death-notification-events";
    private static final String CUSTOMER_BENEFICIARY_EVENTS_TOPIC = "customer-beneficiary-events";
    private static final String CUSTOMER_CONSENT_MANAGEMENT_EVENTS_TOPIC = "customer-consent-management-events";
    private static final String CUSTOMER_COMMUNICATION_PREFERENCE_EVENTS_TOPIC = "customer-communication-preference-events";
    private static final String CUSTOMER_SERVICE_REQUEST_EVENTS_TOPIC = "customer-service-request-events";
    private static final String CUSTOMER_FEEDBACK_EVENTS_TOPIC = "customer-feedback-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<CustomerEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter customerEventsPublished;
    private Counter customerEventsFailure;
    private Timer customerEventPublishLatency;
    
    /**
     * Publishes customer onboarded event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCustomerOnboarded(
            String customerId, String userId, String firstName, String lastName, String email,
            String phoneNumber, LocalDate dateOfBirth, String kycStatus) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_ONBOARDED")
                .customerId(customerId)
                .userId(userId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber(phoneNumber)
                .dateOfBirth(dateOfBirth)
                .kycStatus(kycStatus)
                .onboardingStatus("COMPLETED")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_ONBOARDING_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes profile updated event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishProfileUpdated(
            String customerId, String fieldName, String previousValue, String newValue, String adminId) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_PROFILE_UPDATED")
                .customerId(customerId)
                .previousValue(previousValue)
                .newValue(newValue)
                .adminId(adminId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_PROFILE_UPDATE_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes death notification event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishDeathNotification(
            String customerId, String beneficiaryId, String reason, String adminId) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_DEATH_NOTIFICATION")
                .customerId(customerId)
                .beneficiaryId(beneficiaryId)
                .reason(reason)
                .adminId(adminId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(CUSTOMER_DEATH_NOTIFICATION_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes beneficiary designation event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBeneficiaryDesignation(
            String customerId, String beneficiaryId, String beneficiaryName, String beneficiaryRelationship) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_BENEFICIARY_DESIGNATION")
                .customerId(customerId)
                .beneficiaryId(beneficiaryId)
                .beneficiaryName(beneficiaryName)
                .beneficiaryRelationship(beneficiaryRelationship)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_BENEFICIARY_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes consent updated event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishConsentUpdated(
            String customerId, String consentType, String consentStatus) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_CONSENT_UPDATED")
                .customerId(customerId)
                .consentType(consentType)
                .consentStatus(consentStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_CONSENT_MANAGEMENT_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes communication preference event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCommunicationPreference(
            String customerId, String communicationChannel, String communicationFrequency) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_COMMUNICATION_PREFERENCE")
                .customerId(customerId)
                .communicationChannel(communicationChannel)
                .communicationFrequency(communicationFrequency)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_COMMUNICATION_PREFERENCE_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes service request event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishServiceRequest(
            String customerId, String serviceRequestId, String serviceRequestType, String serviceRequestStatus) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_SERVICE_REQUEST")
                .customerId(customerId)
                .serviceRequestId(serviceRequestId)
                .serviceRequestType(serviceRequestType)
                .serviceRequestStatus(serviceRequestStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_SERVICE_REQUEST_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes customer feedback event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCustomerFeedback(
            String customerId, String feedbackType, String feedbackRating, String feedbackComment) {
        
        CustomerEvent event = CustomerEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CUSTOMER_FEEDBACK")
                .customerId(customerId)
                .feedbackType(feedbackType)
                .feedbackRating(feedbackRating)
                .feedbackComment(feedbackComment)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CUSTOMER_FEEDBACK_EVENTS_TOPIC, event, customerId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<CustomerEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<CustomerEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<CustomerEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<CustomerEvent> topicEvents = entry.getValue();
            
            for (CustomerEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getCustomerId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getCustomerEventPublishLatency());
                if (ex == null) {
                    log.info("Batch customer events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getCustomerEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch customer events", ex);
                    getCustomerEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, CustomerEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing customer event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for customer events")
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
                sample.stop(getCustomerEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getCustomerEventPublishLatency());
            log.error("Failed to publish customer event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, CustomerEvent event, String key) {
        
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
            
            sample.stop(getCustomerEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getCustomerEventPublishLatency());
            log.error("Failed to publish high-priority customer event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed customer events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        CustomerEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getCustomerId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed customer event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(CustomerEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getCustomerEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Customer event published successfully: type={}, customerId={}, offset={}", 
            event.getEventType(), event.getCustomerId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(CustomerEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getCustomerEventsFailure().increment();
        
        log.error("Failed to publish customer event: type={}, customerId={}, topic={}", 
            event.getEventType(), event.getCustomerId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(CustomerEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed customer event for retry: type={}, customerId={}", 
            event.getEventType(), event.getCustomerId());
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
        log.error("Customer event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Customer event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, CustomerEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterCustomerEvent dlqEvent = DeadLetterCustomerEvent.builder()
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
                    log.warn("Customer event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send customer event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish customer event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "customer-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(CustomerEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getCustomerId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<CustomerEvent>> groupEventsByTopic(List<CustomerEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "CUSTOMER_ONBOARDED":
                return CUSTOMER_ONBOARDING_EVENTS_TOPIC;
            case "CUSTOMER_PROFILE_UPDATED":
                return CUSTOMER_PROFILE_UPDATE_EVENTS_TOPIC;
            case "CUSTOMER_DEATH_NOTIFICATION":
                return CUSTOMER_DEATH_NOTIFICATION_EVENTS_TOPIC;
            case "CUSTOMER_BENEFICIARY_DESIGNATION":
                return CUSTOMER_BENEFICIARY_EVENTS_TOPIC;
            case "CUSTOMER_CONSENT_UPDATED":
                return CUSTOMER_CONSENT_MANAGEMENT_EVENTS_TOPIC;
            case "CUSTOMER_COMMUNICATION_PREFERENCE":
                return CUSTOMER_COMMUNICATION_PREFERENCE_EVENTS_TOPIC;
            case "CUSTOMER_SERVICE_REQUEST":
                return CUSTOMER_SERVICE_REQUEST_EVENTS_TOPIC;
            case "CUSTOMER_FEEDBACK":
                return CUSTOMER_FEEDBACK_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown customer event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "customer-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getCustomerEventsPublished() {
        if (customerEventsPublished == null) {
            customerEventsPublished = Counter.builder("customer.events.published")
                .description("Number of customer events published")
                .register(meterRegistry);
        }
        return customerEventsPublished;
    }
    
    private Counter getCustomerEventsFailure() {
        if (customerEventsFailure == null) {
            customerEventsFailure = Counter.builder("customer.events.failure")
                .description("Number of customer events that failed to publish")
                .register(meterRegistry);
        }
        return customerEventsFailure;
    }
    
    private Timer getCustomerEventPublishLatency() {
        if (customerEventPublishLatency == null) {
            customerEventPublishLatency = Timer.builder("customer.events.publish.latency")
                .description("Latency of customer event publishing")
                .register(meterRegistry);
        }
        return customerEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String customerId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String customerId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.customerId = customerId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}