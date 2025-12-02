package com.waqiti.security.kafka;

import com.waqiti.common.events.DeviceFingerprintEvent;
import com.waqiti.security.domain.DeviceFingerprint;
import com.waqiti.security.repository.DeviceFingerprintRepository;
import com.waqiti.security.service.DeviceFingerprintService;
import com.waqiti.security.service.SecurityIncidentService;
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
public class DeviceFingerprintEventsDlqConsumer {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final DeviceFingerprintService deviceFingerprintService;
    private final SecurityIncidentService securityIncidentService;
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
        successCounter = Counter.builder("device_fingerprint_dlq_processed_total")
            .description("Total number of successfully processed device fingerprint DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("device_fingerprint_dlq_errors_total")
            .description("Total number of device fingerprint DLQ processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("device_fingerprint_dlq_processing_duration")
            .description("Time taken to process device fingerprint DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"device-fingerprint-events-dlq"},
        groupId = "device-fingerprint-dlq-service-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "device-fingerprint-dlq", fallbackMethod = "handleDeviceFingerprintDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleDeviceFingerprintDlqEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("dlq-fingerprint-%s-p%d-o%d", event.getDeviceId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDeviceId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing device fingerprint DLQ event: deviceId={}, eventType={}, reason={}",
                event.getDeviceId(), event.getEventType(), event.getFailureReason());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case DLQ_PROCESSING_FAILED:
                    processDlqProcessingFailed(event, correlationId);
                    break;

                case DLQ_MAX_RETRIES_EXCEEDED:
                    processDlqMaxRetriesExceeded(event, correlationId);
                    break;

                case DLQ_PERMANENT_FAILURE:
                    processDlqPermanentFailure(event, correlationId);
                    break;

                case DLQ_MANUAL_REVIEW_REQUIRED:
                    processDlqManualReviewRequired(event, correlationId);
                    break;

                case DLQ_DATA_CORRUPTION:
                    processDlqDataCorruption(event, correlationId);
                    break;

                default:
                    log.warn("Unknown device fingerprint DLQ event type: {}", event.getEventType());
                    createIncidentForUnknownEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("DEVICE_FINGERPRINT_DLQ_EVENT_PROCESSED", event.getDeviceId(),
                Map.of("eventType", event.getEventType(), "failureReason", event.getFailureReason(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process device fingerprint DLQ event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("device-fingerprint-dlq-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDeviceFingerprintDlqEventFallback(
            DeviceFingerprintEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("dlq-fingerprint-fallback-%s-p%d-o%d", event.getDeviceId(), partition, offset);

        log.error("Circuit breaker fallback triggered for device fingerprint DLQ: deviceId={}, error={}",
            event.getDeviceId(), ex.getMessage());

        // Create critical security incident
        securityIncidentService.createCriticalIncident(
            "DEVICE_FINGERPRINT_DLQ_CIRCUIT_BREAKER",
            String.format("Device fingerprint DLQ processing circuit breaker triggered for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "eventType", event.getEventType(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("device-fingerprint-dlq-critical", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Device Fingerprint DLQ Circuit Breaker Triggered",
                String.format("Device %s fingerprint DLQ processing failed: %s", event.getDeviceId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDeviceFingerprintDlqEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-dlq-fingerprint-%s-%d", event.getDeviceId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Device fingerprint DLQ permanently failed: deviceId={}, topic={}, error={}",
            event.getDeviceId(), topic, exceptionMessage);

        // Create critical security incident
        securityIncidentService.createCriticalIncident(
            "DEVICE_FINGERPRINT_DLQ_DLT_EVENT",
            String.format("Device fingerprint DLQ event sent to DLT for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "correlationId", correlationId,
                "requiresImmediateAction", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("DEVICE_FINGERPRINT_DLQ_DLT_EVENT", event.getDeviceId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Device Fingerprint DLQ Dead Letter Event",
                String.format("Device %s fingerprint DLQ sent to DLT: %s", event.getDeviceId(), exceptionMessage),
                Map.of("deviceId", event.getDeviceId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void processDlqProcessingFailed(DeviceFingerprintEvent event, String correlationId) {
        // Log DLQ processing failure
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_DLQ_PROCESSING_FAILED",
            String.format("Device fingerprint DLQ processing failed for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "failureReason", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Attempt alternative processing
        deviceFingerprintService.processFailedFingerprint(event.getDeviceId(), event.getFingerprintData());

        // Update metrics
        metricsService.recordDlqProcessingFailed("device-fingerprint");

        log.warn("Device fingerprint DLQ processing failed: deviceId={}, reason={}",
            event.getDeviceId(), event.getFailureReason());
    }

    private void processDlqMaxRetriesExceeded(DeviceFingerprintEvent event, String correlationId) {
        // Create incident for max retries exceeded
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_MAX_RETRIES_EXCEEDED",
            String.format("Device fingerprint processing max retries exceeded for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "retryCount", event.getRetryCount(),
                "correlationId", correlationId)
        );

        // Mark device for manual review
        deviceFingerprintService.flagForManualReview(event.getDeviceId(), "MAX_RETRIES_EXCEEDED");

        // Send alert to security team
        notificationService.sendSecurityAlert(
            "Device Fingerprint Max Retries Exceeded",
            String.format("Device %s fingerprint processing exceeded max retries", event.getDeviceId()),
            "HIGH"
        );

        metricsService.recordMaxRetriesExceeded("device-fingerprint");

        log.error("Device fingerprint max retries exceeded: deviceId={}", event.getDeviceId());
    }

    private void processDlqPermanentFailure(DeviceFingerprintEvent event, String correlationId) {
        // Create critical incident for permanent failure
        securityIncidentService.createCriticalIncident(
            "DEVICE_FINGERPRINT_PERMANENT_FAILURE",
            String.format("Device fingerprint permanent failure for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "failureReason", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Mark device as permanently failed
        deviceFingerprintService.markAsPermanentFailure(event.getDeviceId(), event.getFailureReason());

        // Block device for security
        deviceFingerprintService.blockDevice(event.getDeviceId(), "PERMANENT_FINGERPRINT_FAILURE");

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Device Fingerprint Permanent Failure",
            String.format("Device %s fingerprint processing permanently failed", event.getDeviceId()),
            "CRITICAL"
        );

        metricsService.recordPermanentFailure("device-fingerprint");

        log.error("Device fingerprint permanent failure: deviceId={}, reason={}",
            event.getDeviceId(), event.getFailureReason());
    }

    private void processDlqManualReviewRequired(DeviceFingerprintEvent event, String correlationId) {
        // Create incident requiring manual review
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_MANUAL_REVIEW_REQUIRED",
            String.format("Device fingerprint manual review required for device %s", event.getDeviceId()),
            "MEDIUM",
            Map.of("deviceId", event.getDeviceId(), "reviewReason", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Queue for manual review
        deviceFingerprintService.queueForManualReview(event.getDeviceId(), event.getFingerprintData(),
            event.getFailureReason());

        // Send notification to security analysts
        notificationService.sendSecurityAlert(
            "Device Fingerprint Manual Review Required",
            String.format("Device %s fingerprint requires manual review", event.getDeviceId()),
            "MEDIUM"
        );

        metricsService.recordManualReviewRequired("device-fingerprint");

        log.info("Device fingerprint manual review required: deviceId={}, reason={}",
            event.getDeviceId(), event.getFailureReason());
    }

    private void processDlqDataCorruption(DeviceFingerprintEvent event, String correlationId) {
        // Create critical incident for data corruption
        securityIncidentService.createCriticalIncident(
            "DEVICE_FINGERPRINT_DATA_CORRUPTION",
            String.format("Device fingerprint data corruption detected for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "corruptionDetails", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Attempt data recovery
        deviceFingerprintService.attemptDataRecovery(event.getDeviceId(), event.getFingerprintData());

        // Block device temporarily
        deviceFingerprintService.temporaryBlock(event.getDeviceId(), "DATA_CORRUPTION_DETECTED");

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Device Fingerprint Data Corruption",
            String.format("Data corruption detected for device %s fingerprint", event.getDeviceId()),
            "CRITICAL"
        );

        metricsService.recordDataCorruption("device-fingerprint");

        log.error("Device fingerprint data corruption: deviceId={}, details={}",
            event.getDeviceId(), event.getFailureReason());
    }

    private void createIncidentForUnknownEvent(DeviceFingerprintEvent event, String correlationId) {
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_UNKNOWN_DLQ_EVENT",
            String.format("Unknown device fingerprint DLQ event type %s for device %s",
                event.getEventType(), event.getDeviceId()),
            "MEDIUM",
            Map.of("deviceId", event.getDeviceId(), "eventType", event.getEventType(),
                "correlationId", correlationId)
        );

        log.warn("Unknown device fingerprint DLQ event type: deviceId={}, eventType={}",
            event.getDeviceId(), event.getEventType());
    }
}