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
 * Production-grade Kafka consumer for API circuit breaker events
 * Handles API circuit breaker triggers and monitoring for fraud detection systems
 *
 * Critical for: API reliability monitoring, system health, failover handling
 * SLA: Must process circuit breaker events within 5 seconds for rapid response
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiCircuitBreakerConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final ApiMonitoringService apiMonitoringService;
    private final CircuitBreakerManagementService circuitBreakerManagementService;
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
        successCounter = Counter.builder("api_circuit_breaker_processed_total")
            .description("Total number of successfully processed API circuit breaker events")
            .register(meterRegistry);
        errorCounter = Counter.builder("api_circuit_breaker_errors_total")
            .description("Total number of API circuit breaker processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("api_circuit_breaker_processing_duration")
            .description("Time taken to process API circuit breaker events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"api-circuit-breaker", "fraud-api-circuit-breaker"},
        groupId = "fraud-api-circuit-breaker-group",
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
    @CircuitBreaker(name = "api-circuit-breaker", fallbackMethod = "handleApiCircuitBreakerEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleApiCircuitBreakerEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("api-cb-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing API circuit breaker event: id={}, type={}, apiName={}",
                event.getId(), event.getEventType(), event.getData().get("apiName"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String apiName = (String) event.getData().get("apiName");
            String status = (String) event.getData().get("status");
            String reason = (String) event.getData().get("reason");

            switch (event.getEventType()) {
                case "CIRCUIT_BREAKER_OPENED":
                    handleCircuitBreakerOpened(event, apiName, reason, correlationId);
                    break;

                case "CIRCUIT_BREAKER_HALF_OPEN":
                    handleCircuitBreakerHalfOpen(event, apiName, correlationId);
                    break;

                case "CIRCUIT_BREAKER_CLOSED":
                    handleCircuitBreakerClosed(event, apiName, correlationId);
                    break;

                case "CIRCUIT_BREAKER_FORCED_OPEN":
                    handleCircuitBreakerForcedOpen(event, apiName, reason, correlationId);
                    break;

                case "API_DEGRADED":
                    handleApiDegraded(event, apiName, correlationId);
                    break;

                default:
                    log.warn("Unknown API circuit breaker event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("API_CIRCUIT_BREAKER_EVENT_PROCESSED",
                String.valueOf(event.getData().get("userId")),
                Map.of("eventType", event.getEventType(), "apiName", apiName,
                    "status", status, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process API circuit breaker event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("api-circuit-breaker-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleApiCircuitBreakerEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("api-cb-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for API circuit breaker: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("api-circuit-breaker-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "API Circuit Breaker Consumer Circuit Breaker Triggered",
                String.format("API circuit breaker event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltApiCircuitBreakerEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-api-cb-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - API circuit breaker event permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("API_CIRCUIT_BREAKER_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "API Circuit Breaker Event Dead Letter",
                String.format("API circuit breaker event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void handleCircuitBreakerOpened(GenericKafkaEvent event, String apiName, String reason, String correlationId) {
        log.warn("Circuit breaker OPENED for API: {}, reason: {}", apiName, reason);

        // Record circuit breaker opening
        circuitBreakerManagementService.recordCircuitBreakerOpened(apiName, reason);

        // Check if this affects fraud detection capabilities
        fraudDetectionService.assessApiCircuitBreakerImpact(apiName, "OPENED");

        // Enable fallback mechanisms if available
        apiMonitoringService.enableFallbackMechanisms(apiName);

        // Send operational alert
        fraudNotificationService.sendOperationalAlert(
            String.format("Circuit Breaker Opened: %s", apiName),
            String.format("API %s circuit breaker opened due to: %s", apiName, reason),
            "HIGH",
            correlationId
        );

        log.info("Circuit breaker opened handling completed for API: {}", apiName);
    }

    private void handleCircuitBreakerHalfOpen(GenericKafkaEvent event, String apiName, String correlationId) {
        log.info("Circuit breaker HALF-OPEN for API: {}", apiName);

        // Record circuit breaker transition
        circuitBreakerManagementService.recordCircuitBreakerHalfOpen(apiName);

        // Monitor next requests closely
        apiMonitoringService.enableEnhancedMonitoring(apiName);

        // Prepare for potential closure or reopening
        fraudDetectionService.prepareForApiRecovery(apiName);

        log.info("Circuit breaker half-open handling completed for API: {}", apiName);
    }

    private void handleCircuitBreakerClosed(GenericKafkaEvent event, String apiName, String correlationId) {
        log.info("Circuit breaker CLOSED (recovered) for API: {}", apiName);

        // Record circuit breaker recovery
        circuitBreakerManagementService.recordCircuitBreakerClosed(apiName);

        // Restore normal fraud detection capabilities
        fraudDetectionService.restoreFullApiCapabilities(apiName);

        // Disable fallback mechanisms
        apiMonitoringService.disableFallbackMechanisms(apiName);

        // Send recovery notification
        fraudNotificationService.sendOperationalAlert(
            String.format("Circuit Breaker Recovered: %s", apiName),
            String.format("API %s circuit breaker has recovered and is now closed", apiName),
            "MEDIUM",
            correlationId
        );

        log.info("Circuit breaker closed handling completed for API: {}", apiName);
    }

    private void handleCircuitBreakerForcedOpen(GenericKafkaEvent event, String apiName, String reason, String correlationId) {
        log.warn("Circuit breaker FORCED OPEN for API: {}, reason: {}", apiName, reason);

        // Record forced opening
        circuitBreakerManagementService.recordCircuitBreakerForcedOpen(apiName, reason);

        // This is a manual intervention - send critical alert
        fraudNotificationService.sendCriticalAlert(
            String.format("Circuit Breaker Forced Open: %s", apiName),
            String.format("API %s circuit breaker was manually forced open: %s", apiName, reason),
            correlationId
        );

        // Assess impact on fraud detection
        fraudDetectionService.assessCriticalApiFailure(apiName);

        log.info("Forced circuit breaker open handling completed for API: {}", apiName);
    }

    private void handleApiDegraded(GenericKafkaEvent event, String apiName, String correlationId) {
        log.warn("API degraded performance detected: {}", apiName);

        // Record API degradation
        apiMonitoringService.recordApiDegradation(apiName);

        // Adjust fraud detection thresholds if necessary
        fraudDetectionService.adjustForDegradedApi(apiName);

        // Send warning notification
        fraudNotificationService.sendOperationalAlert(
            String.format("API Performance Degraded: %s", apiName),
            String.format("API %s showing degraded performance", apiName),
            "MEDIUM",
            correlationId
        );

        log.info("API degradation handling completed for API: {}", apiName);
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