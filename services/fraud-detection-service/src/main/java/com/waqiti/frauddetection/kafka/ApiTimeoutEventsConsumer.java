package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for API timeout events
 * Handles API timeout monitoring and response for fraud detection systems
 *
 * Critical for: API performance monitoring, timeout detection, system reliability
 * SLA: Must process timeout events within 3 seconds for rapid response
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiTimeoutEventsConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final ApiMonitoringService apiMonitoringService;
    private final TimeoutManagementService timeoutManagementService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("api_timeout_events_processed_total")
            .description("Total number of successfully processed API timeout events")
            .register(meterRegistry);
        errorCounter = Counter.builder("api_timeout_events_errors_total")
            .description("Total number of API timeout event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("api_timeout_events_processing_duration")
            .description("Time taken to process API timeout events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"api-timeout-events", "fraud-api-timeouts"},
        groupId = "fraud-api-timeout-events-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "api-timeout-events", fallbackMethod = "handleApiTimeoutEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleApiTimeoutEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("api-timeout-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing API timeout event: id={}, type={}, apiName={}",
                event.getId(), event.getEventType(), event.getData().get("apiName"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String apiName = (String) event.getData().get("apiName");
            Long timeoutDuration = (Long) event.getData().get("timeoutDuration");
            String requestId = (String) event.getData().get("requestId");

            switch (event.getEventType()) {
                case "API_REQUEST_TIMEOUT":
                    handleApiRequestTimeout(event, apiName, timeoutDuration, requestId, correlationId);
                    break;

                case "API_RESPONSE_TIMEOUT":
                    handleApiResponseTimeout(event, apiName, timeoutDuration, requestId, correlationId);
                    break;

                case "CONNECTION_TIMEOUT":
                    handleConnectionTimeout(event, apiName, timeoutDuration, correlationId);
                    break;

                case "READ_TIMEOUT":
                    handleReadTimeout(event, apiName, timeoutDuration, requestId, correlationId);
                    break;

                case "TIMEOUT_PATTERN_DETECTED":
                    handleTimeoutPatternDetected(event, apiName, correlationId);
                    break;

                case "TIMEOUT_THRESHOLD_EXCEEDED":
                    handleTimeoutThresholdExceeded(event, apiName, correlationId);
                    break;

                default:
                    log.warn("Unknown API timeout event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("API_TIMEOUT_EVENT_PROCESSED",
                String.valueOf(event.getData().get("userId")),
                Map.of("eventType", event.getEventType(), "apiName", apiName,
                    "timeoutDuration", timeoutDuration, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process API timeout event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("api-timeout-events-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleApiTimeoutEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("api-timeout-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for API timeout: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("api-timeout-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "API Timeout Events Consumer Circuit Breaker Triggered",
                String.format("API timeout event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltApiTimeoutEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-api-timeout-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - API timeout event permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("API_TIMEOUT_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "API Timeout Event Dead Letter",
                String.format("API timeout event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void handleApiRequestTimeout(GenericKafkaEvent event, String apiName, Long timeoutDuration, String requestId, String correlationId) {
        log.warn("API request timeout detected: api={}, duration={}ms, requestId={}", apiName, timeoutDuration, requestId);

        // Record timeout incident
        timeoutManagementService.recordRequestTimeout(apiName, timeoutDuration, requestId);

        // Check if this affects fraud detection performance
        fraudDetectionService.assessTimeoutImpact(apiName, "REQUEST_TIMEOUT", timeoutDuration);

        // Update API monitoring thresholds if needed
        apiMonitoringService.updateTimeoutThresholds(apiName, timeoutDuration);

        // Send operational notification
        fraudNotificationService.sendOperationalAlert(
            String.format("API Request Timeout: %s", apiName),
            String.format("API %s request timeout after %dms (request: %s)", apiName, timeoutDuration, requestId),
            "MEDIUM",
            correlationId
        );

        log.info("Request timeout handling completed for API: {}", apiName);
    }

    private void handleApiResponseTimeout(GenericKafkaEvent event, String apiName, Long timeoutDuration, String requestId, String correlationId) {
        log.warn("API response timeout detected: api={}, duration={}ms, requestId={}", apiName, timeoutDuration, requestId);

        // Record response timeout
        timeoutManagementService.recordResponseTimeout(apiName, timeoutDuration, requestId);

        // Check for potential downstream issues
        fraudDetectionService.assessDownstreamImpact(apiName, timeoutDuration);

        // Enable fallback mechanisms if available
        apiMonitoringService.considerFallbackActivation(apiName, timeoutDuration);

        log.info("Response timeout handling completed for API: {}", apiName);
    }

    private void handleConnectionTimeout(GenericKafkaEvent event, String apiName, Long timeoutDuration, String correlationId) {
        log.warn("Connection timeout detected: api={}, duration={}ms", apiName, timeoutDuration);

        // Record connection timeout
        timeoutManagementService.recordConnectionTimeout(apiName, timeoutDuration);

        // This could indicate network issues
        fraudDetectionService.assessNetworkConnectivity(apiName);

        // Send higher priority alert for connection issues
        fraudNotificationService.sendOperationalAlert(
            String.format("Connection Timeout: %s", apiName),
            String.format("API %s connection timeout after %dms", apiName, timeoutDuration),
            "HIGH",
            correlationId
        );

        log.info("Connection timeout handling completed for API: {}", apiName);
    }

    private void handleReadTimeout(GenericKafkaEvent event, String apiName, Long timeoutDuration, String requestId, String correlationId) {
        log.warn("Read timeout detected: api={}, duration={}ms, requestId={}", apiName, timeoutDuration, requestId);

        // Record read timeout
        timeoutManagementService.recordReadTimeout(apiName, timeoutDuration, requestId);

        // Check for potential server overload
        fraudDetectionService.assessServerHealth(apiName);

        log.info("Read timeout handling completed for API: {}", apiName);
    }

    private void handleTimeoutPatternDetected(GenericKafkaEvent event, String apiName, String correlationId) {
        log.warn("Timeout pattern detected for API: {}", apiName);

        // Analyze timeout patterns
        timeoutManagementService.analyzeTimeoutPatterns(apiName);

        // This indicates systemic issues
        fraudDetectionService.assessSystemicTimeoutIssues(apiName);

        // Send critical alert for patterns
        fraudNotificationService.sendCriticalAlert(
            String.format("Timeout Pattern Detected: %s", apiName),
            String.format("API %s showing systematic timeout patterns", apiName),
            correlationId
        );

        log.info("Timeout pattern handling completed for API: {}", apiName);
    }

    private void handleTimeoutThresholdExceeded(GenericKafkaEvent event, String apiName, String correlationId) {
        log.error("Timeout threshold exceeded for API: {}", apiName);

        // Record threshold breach
        timeoutManagementService.recordThresholdBreach(apiName);

        // This requires immediate attention
        fraudDetectionService.handleCriticalTimeoutThreshold(apiName);

        // Send critical alert
        fraudNotificationService.sendCriticalAlert(
            String.format("Timeout Threshold Exceeded: %s", apiName),
            String.format("API %s has exceeded timeout thresholds", apiName),
            correlationId
        );

        log.info("Timeout threshold exceeded handling completed for API: {}", apiName);
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}