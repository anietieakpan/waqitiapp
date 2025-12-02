package com.waqiti.security.kafka;

import com.waqiti.common.events.DeviceFingerprintEvent;
import com.waqiti.security.domain.DeviceFingerprint;
import com.waqiti.security.repository.DeviceFingerprintRepository;
import com.waqiti.security.service.DeviceFingerprintService;
import com.waqiti.security.service.SecurityIncidentService;
import com.waqiti.security.service.ValidationService;
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
public class DeviceFingerprintingValidationErrorsConsumer {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final DeviceFingerprintService deviceFingerprintService;
    private final SecurityIncidentService securityIncidentService;
    private final ValidationService validationService;
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
        successCounter = Counter.builder("device_fingerprint_validation_errors_processed_total")
            .description("Total number of successfully processed device fingerprint validation error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("device_fingerprint_validation_errors_errors_total")
            .description("Total number of device fingerprint validation error processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("device_fingerprint_validation_errors_processing_duration")
            .description("Time taken to process device fingerprint validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"device-fingerprinting-validation-errors"},
        groupId = "device-fingerprint-validation-errors-service-group",
        containerFactory = "securityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "device-fingerprint-validation-errors", fallbackMethod = "handleValidationErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleValidationErrorEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("validation-fingerprint-%s-p%d-o%d", event.getDeviceId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDeviceId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing device fingerprint validation error: deviceId={}, eventType={}, validationError={}",
                event.getDeviceId(), event.getEventType(), event.getValidationError());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case FINGERPRINT_FORMAT_INVALID:
                    processFingerprintFormatInvalid(event, correlationId);
                    break;

                case FINGERPRINT_SCHEMA_VIOLATION:
                    processFingerprintSchemaViolation(event, correlationId);
                    break;

                case FINGERPRINT_CHECKSUM_MISMATCH:
                    processFingerprintChecksumMismatch(event, correlationId);
                    break;

                case FINGERPRINT_SIZE_EXCEEDED:
                    processFingerprintSizeExceeded(event, correlationId);
                    break;

                case FINGERPRINT_REQUIRED_FIELDS_MISSING:
                    processFingerprintRequiredFieldsMissing(event, correlationId);
                    break;

                case FINGERPRINT_ENCODING_ERROR:
                    processFingerprintEncodingError(event, correlationId);
                    break;

                case FINGERPRINT_SUSPICIOUS_PATTERN:
                    processFingerprintSuspiciousPattern(event, correlationId);
                    break;

                case FINGERPRINT_DUPLICATE_DETECTED:
                    processFingerprintDuplicateDetected(event, correlationId);
                    break;

                default:
                    log.warn("Unknown device fingerprint validation error type: {}", event.getEventType());
                    processUnknownValidationError(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("DEVICE_FINGERPRINT_VALIDATION_ERROR_PROCESSED", event.getDeviceId(),
                Map.of("eventType", event.getEventType(), "validationError", event.getValidationError(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process device fingerprint validation error: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("device-fingerprint-validation-errors-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleValidationErrorEventFallback(
            DeviceFingerprintEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("validation-fingerprint-fallback-%s-p%d-o%d", event.getDeviceId(), partition, offset);

        log.error("Circuit breaker fallback triggered for device fingerprint validation error: deviceId={}, error={}",
            event.getDeviceId(), ex.getMessage());

        // Create incident for circuit breaker
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_VALIDATION_CIRCUIT_BREAKER",
            String.format("Device fingerprint validation error circuit breaker triggered for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "eventType", event.getEventType(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("device-fingerprint-validation-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendSecurityAlert(
                "Device Fingerprint Validation Circuit Breaker",
                String.format("Device %s fingerprint validation error processing failed: %s",
                    event.getDeviceId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send security alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltValidationErrorEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-validation-fingerprint-%s-%d", event.getDeviceId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Device fingerprint validation error permanently failed: deviceId={}, topic={}, error={}",
            event.getDeviceId(), topic, exceptionMessage);

        // Create incident for DLT event
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_VALIDATION_DLT_EVENT",
            String.format("Device fingerprint validation error sent to DLT for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "correlationId", correlationId,
                "requiresManualIntervention", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("DEVICE_FINGERPRINT_VALIDATION_DLT_EVENT", event.getDeviceId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendSecurityAlert(
                "Device Fingerprint Validation Dead Letter Event",
                String.format("Device %s fingerprint validation error sent to DLT: %s",
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

    private void processFingerprintFormatInvalid(DeviceFingerprintEvent event, String correlationId) {
        // Log format validation error
        validationService.logFormatError(event.getDeviceId(), event.getFingerprintData(), event.getValidationError());

        // Attempt format correction
        String correctedFingerprint = validationService.attemptFormatCorrection(event.getFingerprintData());
        if (correctedFingerprint != null) {
            deviceFingerprintService.updateFingerprintWithCorrection(event.getDeviceId(), correctedFingerprint);
        } else {
            // Flag for manual review
            deviceFingerprintService.flagForManualReview(event.getDeviceId(), "FORMAT_INVALID");
        }

        metricsService.recordValidationError("format-invalid");

        log.warn("Device fingerprint format invalid: deviceId={}, error={}",
            event.getDeviceId(), event.getValidationError());
    }

    private void processFingerprintSchemaViolation(DeviceFingerprintEvent event, String correlationId) {
        // Log schema validation error
        validationService.logSchemaViolation(event.getDeviceId(), event.getFingerprintData(), event.getValidationError());

        // Create incident for schema violation
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_SCHEMA_VIOLATION",
            String.format("Device fingerprint schema violation for device %s", event.getDeviceId()),
            "MEDIUM",
            Map.of("deviceId", event.getDeviceId(), "schemaViolation", event.getValidationError(),
                "correlationId", correlationId)
        );

        // Attempt schema repair
        boolean repaired = validationService.attemptSchemaRepair(event.getDeviceId(), event.getFingerprintData());
        if (!repaired) {
            deviceFingerprintService.quarantineFingerprint(event.getDeviceId(), "SCHEMA_VIOLATION");
        }

        metricsService.recordValidationError("schema-violation");

        log.warn("Device fingerprint schema violation: deviceId={}, violation={}",
            event.getDeviceId(), event.getValidationError());
    }

    private void processFingerprintChecksumMismatch(DeviceFingerprintEvent event, String correlationId) {
        // Create security incident for checksum mismatch
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_CHECKSUM_MISMATCH",
            String.format("Device fingerprint checksum mismatch for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "expectedChecksum", event.getExpectedChecksum(),
                "actualChecksum", event.getActualChecksum(), "correlationId", correlationId)
        );

        // Verify fingerprint integrity
        boolean integrityVerified = validationService.verifyFingerprintIntegrity(event.getDeviceId(), event.getFingerprintData());
        if (!integrityVerified) {
            // Potential tampering detected
            deviceFingerprintService.flagForSecurityReview(event.getDeviceId(), "POTENTIAL_TAMPERING");

            // Send security alert
            notificationService.sendSecurityAlert(
                "Device Fingerprint Checksum Mismatch",
                String.format("Potential tampering detected for device %s", event.getDeviceId()),
                "HIGH"
            );
        }

        // Request fresh fingerprint
        deviceFingerprintService.requestFreshFingerprint(event.getDeviceId());

        metricsService.recordValidationError("checksum-mismatch");

        log.warn("Device fingerprint checksum mismatch: deviceId={}, expected={}, actual={}",
            event.getDeviceId(), event.getExpectedChecksum(), event.getActualChecksum());
    }

    private void processFingerprintSizeExceeded(DeviceFingerprintEvent event, String correlationId) {
        // Log size violation
        validationService.logSizeViolation(event.getDeviceId(), event.getFingerprintSize(), event.getMaxAllowedSize());

        // Attempt data compression
        String compressedFingerprint = validationService.compressFingerprint(event.getFingerprintData());
        if (compressedFingerprint != null && compressedFingerprint.length() <= event.getMaxAllowedSize()) {
            deviceFingerprintService.updateFingerprintWithCompression(event.getDeviceId(), compressedFingerprint);
        } else {
            // Truncate to essential data
            String truncatedFingerprint = validationService.truncateToEssentials(event.getFingerprintData(), event.getMaxAllowedSize());
            deviceFingerprintService.updateFingerprintWithTruncation(event.getDeviceId(), truncatedFingerprint);
        }

        metricsService.recordValidationError("size-exceeded");

        log.warn("Device fingerprint size exceeded: deviceId={}, size={}, maxAllowed={}",
            event.getDeviceId(), event.getFingerprintSize(), event.getMaxAllowedSize());
    }

    private void processFingerprintRequiredFieldsMissing(DeviceFingerprintEvent event, String correlationId) {
        // Log missing fields
        validationService.logMissingFields(event.getDeviceId(), event.getMissingFields());

        // Attempt to reconstruct missing fields
        Map<String, Object> reconstructedFields = validationService.reconstructMissingFields(
            event.getDeviceId(), event.getFingerprintData(), event.getMissingFields());

        if (!reconstructedFields.isEmpty()) {
            deviceFingerprintService.updateFingerprintWithReconstructedFields(event.getDeviceId(), reconstructedFields);
        } else {
            // Request re-fingerprinting
            deviceFingerprintService.requestReFingerprinting(event.getDeviceId(), event.getMissingFields());
        }

        metricsService.recordValidationError("missing-fields");

        log.warn("Device fingerprint missing required fields: deviceId={}, missingFields={}",
            event.getDeviceId(), event.getMissingFields());
    }

    private void processFingerprintEncodingError(DeviceFingerprintEvent event, String correlationId) {
        // Log encoding error
        validationService.logEncodingError(event.getDeviceId(), event.getEncodingError());

        // Attempt re-encoding
        String reEncodedFingerprint = validationService.attemptReEncoding(event.getFingerprintData(), event.getTargetEncoding());
        if (reEncodedFingerprint != null) {
            deviceFingerprintService.updateFingerprintWithReEncoding(event.getDeviceId(), reEncodedFingerprint);
        } else {
            // Flag for manual review
            deviceFingerprintService.flagForManualReview(event.getDeviceId(), "ENCODING_ERROR");
        }

        metricsService.recordValidationError("encoding-error");

        log.warn("Device fingerprint encoding error: deviceId={}, encodingError={}",
            event.getDeviceId(), event.getEncodingError());
    }

    private void processFingerprintSuspiciousPattern(DeviceFingerprintEvent event, String correlationId) {
        // Create security incident for suspicious pattern
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_SUSPICIOUS_PATTERN",
            String.format("Suspicious pattern detected in device fingerprint for device %s", event.getDeviceId()),
            "HIGH",
            Map.of("deviceId", event.getDeviceId(), "suspiciousPattern", event.getSuspiciousPattern(),
                "patternType", event.getPatternType(), "correlationId", correlationId)
        );

        // Enhanced security review
        deviceFingerprintService.triggerEnhancedSecurityReview(event.getDeviceId(), event.getSuspiciousPattern());

        // Temporary restriction
        deviceFingerprintService.applyTemporaryRestrictions(event.getDeviceId(), "SUSPICIOUS_PATTERN");

        // Send security alert
        notificationService.sendSecurityAlert(
            "Suspicious Device Fingerprint Pattern",
            String.format("Suspicious pattern detected for device %s: %s", event.getDeviceId(), event.getSuspiciousPattern()),
            "HIGH"
        );

        metricsService.recordValidationError("suspicious-pattern");

        log.warn("Suspicious device fingerprint pattern: deviceId={}, pattern={}, type={}",
            event.getDeviceId(), event.getSuspiciousPattern(), event.getPatternType());
    }

    private void processFingerprintDuplicateDetected(DeviceFingerprintEvent event, String correlationId) {
        // Create incident for duplicate fingerprint
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_DUPLICATE_DETECTED",
            String.format("Duplicate device fingerprint detected for device %s", event.getDeviceId()),
            "MEDIUM",
            Map.of("deviceId", event.getDeviceId(), "duplicateDeviceId", event.getDuplicateDeviceId(),
                "correlationId", correlationId)
        );

        // Investigate potential device cloning
        deviceFingerprintService.investigateDeviceCloning(event.getDeviceId(), event.getDuplicateDeviceId());

        // Apply security measures
        deviceFingerprintService.applyDuplicateSecurityMeasures(event.getDeviceId(), event.getDuplicateDeviceId());

        // Send security alert
        notificationService.sendSecurityAlert(
            "Duplicate Device Fingerprint Detected",
            String.format("Duplicate fingerprint detected: device %s matches device %s",
                event.getDeviceId(), event.getDuplicateDeviceId()),
            "MEDIUM"
        );

        metricsService.recordValidationError("duplicate-detected");

        log.warn("Duplicate device fingerprint detected: deviceId={}, duplicateDeviceId={}",
            event.getDeviceId(), event.getDuplicateDeviceId());
    }

    private void processUnknownValidationError(DeviceFingerprintEvent event, String correlationId) {
        // Create incident for unknown validation error
        securityIncidentService.createIncident(
            "DEVICE_FINGERPRINT_UNKNOWN_VALIDATION_ERROR",
            String.format("Unknown device fingerprint validation error type %s for device %s",
                event.getEventType(), event.getDeviceId()),
            "MEDIUM",
            Map.of("deviceId", event.getDeviceId(), "unknownErrorType", event.getEventType(),
                "validationError", event.getValidationError(), "correlationId", correlationId)
        );

        // Flag for manual review
        deviceFingerprintService.flagForManualReview(event.getDeviceId(), "UNKNOWN_VALIDATION_ERROR");

        log.warn("Unknown device fingerprint validation error: deviceId={}, eventType={}, error={}",
            event.getDeviceId(), event.getEventType(), event.getValidationError());
    }
}