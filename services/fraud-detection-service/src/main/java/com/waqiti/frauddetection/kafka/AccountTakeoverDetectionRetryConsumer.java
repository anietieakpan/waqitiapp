package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for account takeover detection retry events
 * Handles retry processing for failed ATO detection attempts with comprehensive
 * error handling, circuit breakers, and metrics
 * 
 * Critical for: Account security, fraud prevention, retry reliability
 * SLA: Must process retry events within 10 seconds for timely ATO detection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountTakeoverDetectionRetryConsumer {

    private final ATORetryService atoRetryService;
    private final ATODetectionService atoDetectionService;
    private final AccountProtectionService accountProtectionService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter processedEventsCounter = Counter.builder("ato_retry_events_processed_total")
            .description("Total number of ATO retry events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter failedEventsCounter = Counter.builder("ato_retry_events_failed_total")
            .description("Total number of ATO retry events that failed processing")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ato_retry_processing_duration")
            .description("Time taken to process ATO retry events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"account-takeover-detection-retry"},
        groupId = "fraud-service-ato-retry-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "ato-retry-processor", fallbackMethod = "handleATORetryFailure")
    @Retry(name = "ato-retry-processor")
    public void processATORetryEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing ATO retry event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate retry event data
            ATORetryEventData retryData = extractATORetryEventData(event.getPayload());
            validateATORetryEvent(retryData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process ATO retry attempt
            processATORetryAttempt(retryData, event);

            // Record successful processing metrics
            processedEventsCounter.increment();
            
            // Audit the retry processing
            auditATORetryProcessing(retryData, event, "SUCCESS");

            log.info("Successfully processed ATO retry event: {} for account: {} (attempt {})", 
                    eventId, retryData.getAccountId(), retryData.getRetryAttempt());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid ATO retry event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ATO retry event: {}", eventId, e);
            failedEventsCounter.increment();
            auditATORetryProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("ATO retry event processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            // Check if still within TTL
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                // Remove expired entry
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private ATORetryEventData extractATORetryEventData(Map<String, Object> payload) throws JsonProcessingException {
        return ATORetryEventData.builder()
                .originalEventId(extractString(payload, "originalEventId"))
                .accountId(extractString(payload, "accountId"))
                .userId(extractString(payload, "userId"))
                .retryAttempt(extractInteger(payload, "retryAttempt", 1))
                .maxRetryAttempts(extractInteger(payload, "maxRetryAttempts", 3))
                .lastFailureReason(extractString(payload, "lastFailureReason"))
                .retryDelay(extractLong(payload, "retryDelay", 0L))
                .originalPayload(extractMap(payload, "originalPayload"))
                .retryTimestamp(extractInstant(payload, "retryTimestamp"))
                .originalTimestamp(extractInstant(payload, "originalTimestamp"))
                .priority(extractString(payload, "priority", "NORMAL"))
                .build();
    }

    private void validateATORetryEvent(ATORetryEventData retryData) {
        if (retryData.getOriginalEventId() == null || retryData.getOriginalEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Original event ID is required");
        }
        
        if (retryData.getAccountId() == null || retryData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (retryData.getUserId() == null || retryData.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (retryData.getRetryAttempt() <= 0) {
            throw new IllegalArgumentException("Retry attempt must be positive");
        }
        
        if (retryData.getRetryAttempt() > retryData.getMaxRetryAttempts()) {
            throw new IllegalArgumentException("Retry attempt exceeds maximum allowed retries");
        }
        
        if (retryData.getOriginalPayload() == null || retryData.getOriginalPayload().isEmpty()) {
            throw new IllegalArgumentException("Original payload is required for retry");
        }
    }

    private void processATORetryAttempt(ATORetryEventData retryData, GenericKafkaEvent event) {
        log.info("Processing ATO retry attempt {} of {} for account: {} (original event: {})", 
                retryData.getRetryAttempt(), retryData.getMaxRetryAttempts(), 
                retryData.getAccountId(), retryData.getOriginalEventId());

        try {
            // Record retry attempt
            atoRetryService.recordRetryAttempt(retryData);

            // Recreate original ATO detection event from retry data
            GenericKafkaEvent originalEvent = recreateOriginalEvent(retryData);

            // Attempt ATO detection processing again
            ATODetectionResult detectionResult = atoDetectionService.processATODetection(
                    retryData.getAccountId(),
                    retryData.getUserId(),
                    retryData.getOriginalPayload()
            );

            // Process successful retry result
            handleSuccessfulRetry(retryData, detectionResult);

            // Update retry status
            atoRetryService.markRetrySuccessful(retryData.getOriginalEventId(), detectionResult);

            log.info("ATO retry successful for account: {} after {} attempt(s)", 
                    retryData.getAccountId(), retryData.getRetryAttempt());

        } catch (Exception e) {
            log.error("ATO retry attempt failed for account: {} (attempt {})", 
                    retryData.getAccountId(), retryData.getRetryAttempt(), e);

            // Handle retry failure
            handleRetryFailure(retryData, e);
            
            throw new RuntimeException("ATO retry processing failed", e);
        }
    }

    private GenericKafkaEvent recreateOriginalEvent(ATORetryEventData retryData) {
        GenericKafkaEvent originalEvent = new GenericKafkaEvent();
        originalEvent.setEventId(retryData.getOriginalEventId());
        originalEvent.setPayload(retryData.getOriginalPayload());
        originalEvent.setEventType("ACCOUNT_TAKEOVER_DETECTION");
        originalEvent.setTimestamp(retryData.getOriginalTimestamp());
        originalEvent.setSource("ATO_RETRY_PROCESSOR");
        return originalEvent;
    }

    private void handleSuccessfulRetry(ATORetryEventData retryData, ATODetectionResult detectionResult) {
        // Apply any necessary account protection measures
        if (detectionResult.requiresImmediateAction()) {
            accountProtectionService.applyProtectionMeasures(
                    retryData.getAccountId(), 
                    detectionResult
            );
        }

        // Send notifications for successful retry processing
        fraudNotificationService.sendATORetrySuccessNotification(
                retryData.getAccountId(),
                retryData.getUserId(),
                retryData.getRetryAttempt(),
                detectionResult
        );

        // Update metrics for successful retry
        metricsService.recordATORetrySuccess(
                retryData.getAccountId(),
                retryData.getRetryAttempt(),
                detectionResult.getConfidenceScore()
        );
    }

    private void handleRetryFailure(ATORetryEventData retryData, Exception error) {
        // Record retry failure
        atoRetryService.recordRetryFailure(
                retryData.getOriginalEventId(),
                retryData.getRetryAttempt(),
                error.getMessage()
        );

        // Check if this was the final retry attempt
        if (retryData.getRetryAttempt() >= retryData.getMaxRetryAttempts()) {
            log.error("All retry attempts exhausted for ATO detection of account: {} (original event: {})", 
                    retryData.getAccountId(), retryData.getOriginalEventId());

            // Mark as permanently failed
            atoRetryService.markRetryExhausted(retryData.getOriginalEventId(), error.getMessage());

            // Send critical alert for failed ATO detection
            fraudNotificationService.sendCriticalATORetryFailureAlert(
                    retryData.getAccountId(),
                    retryData.getUserId(),
                    retryData.getOriginalEventId(),
                    error.getMessage()
            );

            // Apply fallback protection measures
            accountProtectionService.applyFallbackProtection(
                    retryData.getAccountId(),
                    "ATO_RETRY_EXHAUSTED"
            );
        } else {
            // Schedule next retry attempt
            scheduleNextRetry(retryData, error.getMessage());
        }

        // Update failure metrics
        metricsService.recordATORetryFailure(
                retryData.getAccountId(),
                retryData.getRetryAttempt(),
                error.getClass().getSimpleName()
        );
    }

    private void scheduleNextRetry(ATORetryEventData retryData, String failureReason) {
        int nextAttempt = retryData.getRetryAttempt() + 1;
        long nextRetryDelay = calculateNextRetryDelay(nextAttempt);

        log.info("Scheduling retry attempt {} for account: {} with delay: {}ms", 
                nextAttempt, retryData.getAccountId(), nextRetryDelay);

        atoRetryService.scheduleNextRetry(
                retryData.getOriginalEventId(),
                nextAttempt,
                nextRetryDelay,
                failureReason
        );
    }

    private long calculateNextRetryDelay(int attemptNumber) {
        // Exponential backoff: 1s, 2s, 4s, 8s, etc. (capped at 60s)
        return Math.min(TimeUnit.SECONDS.toMillis((long) Math.pow(2, attemptNumber - 1)), 60000);
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("ATO retry event validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ATO_RETRY_VALIDATION_ERROR",
                null,
                "ATO retry event validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );

        // Send to validation errors topic for analysis
        fraudNotificationService.sendValidationErrorAlert(event, e.getMessage());
    }

    private void auditATORetryProcessing(ATORetryEventData retryData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ATO_RETRY_EVENT_PROCESSED",
                    retryData != null ? retryData.getAccountId() : null,
                    String.format("ATO retry event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "originalEventId", retryData != null ? retryData.getOriginalEventId() : "unknown",
                            "accountId", retryData != null ? retryData.getAccountId() : "unknown",
                            "retryAttempt", retryData != null ? retryData.getRetryAttempt() : 0,
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit ATO retry processing", e);
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic) {
        
        log.error("ATO retry event sent to DLT - EventId: {}, OriginalTopic: {}", 
                event.getEventId(), originalTopic);

        try {
            ATORetryEventData retryData = extractATORetryEventData(event.getPayload());
            
            // Mark retry as permanently failed
            atoRetryService.markRetryFailed(retryData.getOriginalEventId(), "SENT_TO_DLT");

            // Send critical alert
            fraudNotificationService.sendCriticalATORetryDLTAlert(
                    retryData.getAccountId(),
                    retryData.getOriginalEventId(),
                    "Event sent to Dead Letter Queue"
            );

            // Apply emergency protection measures
            accountProtectionService.applyEmergencyProtection(
                    retryData.getAccountId(),
                    "ATO_RETRY_DLT"
            );

            // Audit DLT handling
            auditService.auditSecurityEvent(
                    "ATO_RETRY_DLT",
                    retryData.getAccountId(),
                    "ATO retry event sent to Dead Letter Queue - manual intervention required",
                    Map.of(
                            "eventId", event.getEventId(),
                            "originalEventId", retryData.getOriginalEventId(),
                            "accountId", retryData.getAccountId(),
                            "originalTopic", originalTopic
                    )
            );

        } catch (Exception e) {
            log.error("Failed to handle ATO retry DLT event: {}", event.getEventId(), e);
        }
    }

    // Circuit breaker fallback method
    public void handleATORetryFailure(GenericKafkaEvent event, String topic, int partition,
                                     long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for ATO retry processing - EventId: {}", 
                event.getEventId(), e);

        try {
            ATORetryEventData retryData = extractATORetryEventData(event.getPayload());
            
            // Apply emergency protection
            accountProtectionService.applyEmergencyProtection(
                    retryData.getAccountId(),
                    "ATO_RETRY_CIRCUIT_BREAKER"
            );

            // Send critical system alert
            fraudNotificationService.sendSystemCriticalAlert(
                    "ATO Retry Circuit Breaker Open",
                    "ATO retry processing is failing - account security may be compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle ATO retry circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class ATORetryEventData {
        private String originalEventId;
        private String accountId;
        private String userId;
        private Integer retryAttempt;
        private Integer maxRetryAttempts;
        private String lastFailureReason;
        private Long retryDelay;
        private Map<String, Object> originalPayload;
        private Instant retryTimestamp;
        private Instant originalTimestamp;
        private String priority;
    }

    @lombok.Data
    @lombok.Builder
    public static class ATODetectionResult {
        private String resultId;
        private String accountId;
        private double confidenceScore;
        private String threatLevel;
        private boolean requiresImmediateAction;
        private Map<String, Object> detectionDetails;
        private Instant detectionTimestamp;
    }
}