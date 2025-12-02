package com.waqiti.security.kafka;

import com.waqiti.common.events.DeviceFingerprintEvent;
import com.waqiti.security.domain.DeviceFingerprint;
import com.waqiti.security.repository.DeviceFingerprintRepository;
import com.waqiti.security.service.DeviceFingerprintService;
import com.waqiti.security.service.SecurityIncidentService;
import com.waqiti.security.service.RetryService;
import com.waqiti.security.metrics.SecurityMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceFingerprintingRetryConsumer {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final DeviceFingerprintService deviceFingerprintService;
    private final SecurityIncidentService securityIncidentService;
    private final RetryService retryService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("device_fingerprint_retry_processed_total")
            .description("Total number of successfully processed device fingerprint retry events")
            .register(meterRegistry);
        errorCounter = Counter.builder("device_fingerprint_retry_errors_total")
            .description("Total number of device fingerprint retry processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("device_fingerprint_retry_processing_duration")
            .description("Time taken to process device fingerprint retry events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"device-fingerprinting-retry"},
        groupId = "device-fingerprint-retry-service-group",
        containerFactory = "securityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "6",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "device-fingerprint-retry", fallbackMethod = "handleRetryEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 4, backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 16000))
    public void handleRetryEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("retry-fingerprint-%s-p%d-o%d", event.getDeviceId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDeviceId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing device fingerprint retry: deviceId={}, eventType={}, attempt={}/{}",
                event.getDeviceId(), event.getEventType(), event.getCurrentAttempt(), event.getMaxAttempts());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case FINGERPRINT_RETRY_REQUESTED:
                    processFingerprintRetryRequested(event, correlationId);
                    break;

                case FINGERPRINT_RETRY_PROCESSING:
                    processFingerprintRetryProcessing(event, correlationId);
                    break;

                case FINGERPRINT_RETRY_FAILED:
                    processFingerprintRetryFailed(event, correlationId);
                    break;

                case FINGERPRINT_RETRY_SUCCESS:
                    processFingerprintRetrySuccess(event, correlationId);
                    break;

                case FINGERPRINT_RETRY_MAX_ATTEMPTS:
                    processFingerprintRetryMaxAttempts(event, correlationId);
                    break;

                case FINGERPRINT_RETRY_ABANDONED:
                    processFingerprintRetryAbandoned(event, correlationId);
                    break;

                default:
                    log.warn("Unknown device fingerprint retry event type: {}", event.getEventType());
                    processUnknownRetryEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("DEVICE_FINGERPRINT_RETRY_EVENT_PROCESSED", event.getDeviceId(),
                Map.of("eventType", event.getEventType(), "currentAttempt", event.getCurrentAttempt(),
                    "maxAttempts", event.getMaxAttempts(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process device fingerprint retry event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("device-fingerprint-retry-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 4));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleRetryEventFallback(
            DeviceFingerprintEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("retry-fingerprint-fallback-%s-p%d-o%d", event.getDeviceId(), partition, offset);

        log.error("Circuit breaker fallback triggered for device fingerprint retry: deviceId={}, error={}",
            event.getDeviceId(), ex.getMessage());

        // Create incident for circuit breaker
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_RETRY_CIRCUIT_BREAKER",
            String.format("Device fingerprint retry circuit breaker triggered for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "eventType", event.getEventType(),
                "currentAttempt", event.getCurrentAttempt(), "error", ex.getMessage(),
                "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("device-fingerprint-retry-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendSecurityAlert(
                "Device Fingerprint Retry Circuit Breaker",
                String.format("Device %s fingerprint retry processing failed: %s",
                    event.getDeviceId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send security alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltRetryEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-retry-fingerprint-%s-%d", event.getDeviceId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Device fingerprint retry permanently failed: deviceId={}, topic={}, error={}",
            event.getDeviceId(), topic, exceptionMessage);

        // Create incident for DLT event
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_RETRY_DLT_EVENT",
            String.format("Device fingerprint retry sent to DLT for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "currentAttempt", event.getCurrentAttempt(),
                "correlationId", correlationId, "requiresManualIntervention", true)
        );

        // Mark retry as permanently failed
        retryService.markRetryAsPermanentlyFailed(event.getDeviceId(), event.getRetryId(), exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("DEVICE_FINGERPRINT_RETRY_DLT_EVENT", event.getDeviceId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendSecurityAlert(
                "Device Fingerprint Retry Dead Letter Event",
                String.format("Device %s fingerprint retry sent to DLT: %s",
                    event.getDeviceId(), exceptionMessage),
                Map.of("deviceId", event.getDeviceId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send DLT alert: {}", ex.getMessage());
        }
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

    private void processFingerprintRetryRequested(DeviceFingerprintEvent event, String correlationId) {
        // Create retry record
        retryService.createRetryRecord(event.getDeviceId(), event.getRetryId(), event.getRetryReason(),
            event.getMaxAttempts(), correlationId);

        // Schedule retry processing
        retryService.scheduleRetryProcessing(event.getDeviceId(), event.getRetryId(), event.getRetryDelay());

        // Update metrics
        metricsService.recordRetryRequested("device-fingerprint");

        log.info("Device fingerprint retry requested: deviceId={}, retryId={}, reason={}",
            event.getDeviceId(), event.getRetryId(), event.getRetryReason());
    }

    private void processFingerprintRetryProcessing(DeviceFingerprintEvent event, String correlationId) {
        // Update retry record
        retryService.updateRetryAttempt(event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt());

        // Execute retry logic
        boolean retrySuccess = false;
        try {
            switch (event.getRetryType()) {
                case FINGERPRINT_CAPTURE:
                    retrySuccess = deviceFingerprintService.retryFingerprintCapture(event.getDeviceId());
                    break;
                case FINGERPRINT_VALIDATION:
                    retrySuccess = deviceFingerprintService.retryFingerprintValidation(event.getDeviceId(), event.getFingerprintData());
                    break;
                case FINGERPRINT_STORAGE:
                    retrySuccess = deviceFingerprintService.retryFingerprintStorage(event.getDeviceId(), event.getFingerprintData());
                    break;
                case FINGERPRINT_ANALYSIS:
                    retrySuccess = deviceFingerprintService.retryFingerprintAnalysis(event.getDeviceId(), event.getFingerprintData());
                    break;
                default:
                    log.warn("Unknown retry type: {}", event.getRetryType());
                    break;
            }

            if (retrySuccess) {
                // Send success event
                kafkaTemplate.send("device-fingerprinting-retry", Map.of(
                    "deviceId", event.getDeviceId(),
                    "retryId", event.getRetryId(),
                    "eventType", "FINGERPRINT_RETRY_SUCCESS",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Send failure event
                kafkaTemplate.send("device-fingerprinting-retry", Map.of(
                    "deviceId", event.getDeviceId(),
                    "retryId", event.getRetryId(),
                    "eventType", "FINGERPRINT_RETRY_FAILED",
                    "currentAttempt", event.getCurrentAttempt(),
                    "maxAttempts", event.getMaxAttempts(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Retry processing failed: deviceId={}, error={}", event.getDeviceId(), e.getMessage());

            // Send failure event
            kafkaTemplate.send("device-fingerprinting-retry", Map.of(
                "deviceId", event.getDeviceId(),
                "retryId", event.getRetryId(),
                "eventType", "FINGERPRINT_RETRY_FAILED",
                "currentAttempt", event.getCurrentAttempt(),
                "maxAttempts", event.getMaxAttempts(),
                "errorMessage", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRetryProcessing("device-fingerprint");

        log.info("Device fingerprint retry processing: deviceId={}, retryId={}, attempt={}, success={}",
            event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt(), retrySuccess);
    }

    private void processFingerprintRetryFailed(DeviceFingerprintEvent event, String correlationId) {
        // Update retry record with failure
        retryService.updateRetryFailure(event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt(), event.getErrorMessage());

        // Check if we should continue retrying
        if (event.getCurrentAttempt() < event.getMaxAttempts()) {
            // Schedule next retry
            long nextRetryDelay = retryService.calculateBackoffDelay(event.getCurrentAttempt(), event.getRetryDelay());
            retryService.scheduleNextRetry(event.getDeviceId(), event.getRetryId(), nextRetryDelay);

            log.info("Scheduling next retry: deviceId={}, retryId={}, nextAttempt={}, delay={}ms",
                event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt() + 1, nextRetryDelay);
        } else {
            // Max attempts reached
            kafkaTemplate.send("device-fingerprinting-retry", Map.of(
                "deviceId", event.getDeviceId(),
                "retryId", event.getRetryId(),
                "eventType", "FINGERPRINT_RETRY_MAX_ATTEMPTS",
                "currentAttempt", event.getCurrentAttempt(),
                "maxAttempts", event.getMaxAttempts(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRetryFailed("device-fingerprint");

        log.warn("Device fingerprint retry failed: deviceId={}, retryId={}, attempt={}/{}, error={}",
            event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt(), event.getMaxAttempts(), event.getErrorMessage());
    }

    private void processFingerprintRetrySuccess(DeviceFingerprintEvent event, String correlationId) {
        // Mark retry as successful
        retryService.markRetryAsSuccessful(event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt());

        // Update device fingerprint status
        deviceFingerprintService.updateFingerprintStatus(event.getDeviceId(), "RETRY_SUCCESS");

        // Send success notification if needed
        if (event.getCurrentAttempt() > 1) {
            // Only notify on recovery after failures
            kafkaTemplate.send("device-fingerprint-status-updates", Map.of(
                "deviceId", event.getDeviceId(),
                "status", "FINGERPRINT_RETRY_RECOVERED",
                "attemptsRequired", event.getCurrentAttempt(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRetrySuccess("device-fingerprint", event.getCurrentAttempt());

        log.info("Device fingerprint retry successful: deviceId={}, retryId={}, attempts={}",
            event.getDeviceId(), event.getRetryId(), event.getCurrentAttempt());
    }

    private void processFingerprintRetryMaxAttempts(DeviceFingerprintEvent event, String correlationId) {
        // Mark retry as max attempts reached
        retryService.markRetryAsMaxAttemptsReached(event.getDeviceId(), event.getRetryId());

        // Create incident for max attempts
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_RETRY_MAX_ATTEMPTS",
            String.format("Device fingerprint retry max attempts reached for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "retryId", event.getRetryId(),
                "maxAttempts", event.getMaxAttempts(), "correlationId", correlationId)
        );

        // Apply fallback security measures
        deviceFingerprintService.applyFallbackSecurityMeasures(event.getDeviceId(), "RETRY_MAX_ATTEMPTS");

        // Send alert
        notificationService.sendSecurityAlert(
            "Device Fingerprint Retry Max Attempts",
            String.format("Device %s fingerprint retry exceeded max attempts (%d)",
                event.getDeviceId(), event.getMaxAttempts()),
            "HIGH"
        );

        metricsService.recordRetryMaxAttempts("device-fingerprint");

        log.error("Device fingerprint retry max attempts reached: deviceId={}, retryId={}, maxAttempts={}",
            event.getDeviceId(), event.getRetryId(), event.getMaxAttempts());
    }

    private void processFingerprintRetryAbandoned(DeviceFingerprintEvent event, String correlationId) {
        // Mark retry as abandoned
        retryService.markRetryAsAbandoned(event.getDeviceId(), event.getRetryId(), event.getAbandonReason());

        // Log abandonment
        auditService.logSecurityEvent("DEVICE_FINGERPRINT_RETRY_ABANDONED", event.getDeviceId(),
            Map.of("retryId", event.getRetryId(), "abandonReason", event.getAbandonReason(),
                "currentAttempt", event.getCurrentAttempt(), "correlationId", correlationId,
                "timestamp", Instant.now()));

        // Clean up retry resources
        retryService.cleanupRetryResources(event.getDeviceId(), event.getRetryId());

        metricsService.recordRetryAbandoned("device-fingerprint");

        log.info("Device fingerprint retry abandoned: deviceId={}, retryId={}, reason={}",
            event.getDeviceId(), event.getRetryId(), event.getAbandonReason());
    }

    private void processUnknownRetryEvent(DeviceFingerprintEvent event, String correlationId) {
        // Create incident for unknown retry event
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_UNKNOWN_RETRY_EVENT",
            String.format("Unknown device fingerprint retry event type %s for device %s",
                event.getEventType(), event.getDeviceId()),
            "MEDIUM",
            Map.of("deviceId", event.getDeviceId(), "unknownEventType", event.getEventType(),
                "retryId", event.getRetryId(), "correlationId", correlationId)
        );

        log.warn("Unknown device fingerprint retry event: deviceId={}, eventType={}, retryId={}",
            event.getDeviceId(), event.getEventType(), event.getRetryId());
    }
}