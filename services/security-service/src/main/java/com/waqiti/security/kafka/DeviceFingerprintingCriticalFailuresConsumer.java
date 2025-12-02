package com.waqiti.security.kafka;

import com.waqiti.common.events.DeviceFingerprintEvent;
import com.waqiti.security.domain.DeviceFingerprint;
import com.waqiti.security.repository.DeviceFingerprintRepository;
import com.waqiti.security.service.DeviceFingerprintService;
import com.waqiti.security.service.SecurityIncidentService;
import com.waqiti.security.service.EmergencyResponseService;
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
public class DeviceFingerprintingCriticalFailuresConsumer {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final DeviceFingerprintService deviceFingerprintService;
    private final SecurityIncidentService securityIncidentService;
    private final EmergencyResponseService emergencyResponseService;
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
        successCounter = Counter.builder("device_fingerprint_critical_failures_processed_total")
            .description("Total number of successfully processed device fingerprint critical failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("device_fingerprint_critical_failures_errors_total")
            .description("Total number of device fingerprint critical failure processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("device_fingerprint_critical_failures_processing_duration")
            .description("Time taken to process device fingerprint critical failure events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"device-fingerprinting-critical-failures"},
        groupId = "device-fingerprint-critical-failures-service-group",
        containerFactory = "emergencySecurityKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "device-fingerprint-critical-failures", fallbackMethod = "handleCriticalFailureEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 2000))
    public void handleCriticalFailureEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("critical-fingerprint-%s-p%d-o%d", event.getDeviceId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDeviceId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL device fingerprint failure: deviceId={}, eventType={}, severity={}",
                event.getDeviceId(), event.getEventType(), event.getSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CRITICAL_FINGERPRINT_FAILURE:
                    processCriticalFingerprintFailure(event, correlationId);
                    break;

                case SYSTEM_COMPROMISE_DETECTED:
                    processSystemCompromiseDetected(event, correlationId);
                    break;

                case MASS_FINGERPRINT_CORRUPTION:
                    processMassFingerprintCorruption(event, correlationId);
                    break;

                case FINGERPRINT_SERVICE_UNAVAILABLE:
                    processFingerprintServiceUnavailable(event, correlationId);
                    break;

                case CRITICAL_SECURITY_BREACH:
                    processCriticalSecurityBreach(event, correlationId);
                    break;

                case EMERGENCY_SHUTDOWN_REQUIRED:
                    processEmergencyShutdownRequired(event, correlationId);
                    break;

                default:
                    log.error("Unknown critical device fingerprint failure type: {}", event.getEventType());
                    processUnknownCriticalFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCriticalSecurityEvent("DEVICE_FINGERPRINT_CRITICAL_FAILURE_PROCESSED", event.getDeviceId(),
                Map.of("eventType", event.getEventType(), "severity", event.getSeverity(),
                    "failureReason", event.getFailureReason(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process critical device fingerprint failure: {}", e.getMessage(), e);

            // Send emergency fallback event
            kafkaTemplate.send("device-fingerprint-emergency-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "priority", "EMERGENCY", "retryCount", 0, "maxRetries", 2));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCriticalFailureEventFallback(
            DeviceFingerprintEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("critical-fingerprint-fallback-%s-p%d-o%d", event.getDeviceId(), partition, offset);

        log.error("Circuit breaker fallback triggered for CRITICAL device fingerprint failure: deviceId={}, error={}",
            event.getDeviceId(), ex.getMessage());

        // Trigger emergency response
        emergencyResponseService.triggerEmergencyResponse(
            "DEVICE_FINGERPRINT_CRITICAL_FAILURE_CIRCUIT_BREAKER",
            String.format("Critical device fingerprint failure circuit breaker triggered for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "eventType", event.getEventType(),
                "severity", "CRITICAL", "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to emergency queue
        kafkaTemplate.send("device-fingerprint-emergency-response", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CRITICAL_FAILURE_CIRCUIT_BREAKER",
            "priority", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "CRITICAL Device Fingerprint Failure Circuit Breaker",
                String.format("EMERGENCY: Device %s critical fingerprint failure processing failed: %s",
                    event.getDeviceId(), ex.getMessage()),
                "EMERGENCY"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send emergency alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCriticalFailureEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-critical-fingerprint-%s-%d", event.getDeviceId(), System.currentTimeMillis());

        log.error("EMERGENCY: Critical device fingerprint failure sent to DLT: deviceId={}, topic={}, error={}",
            event.getDeviceId(), topic, exceptionMessage);

        // Trigger emergency response
        emergencyResponseService.triggerEmergencyResponse(
            "DEVICE_FINGERPRINT_CRITICAL_FAILURE_DLT",
            String.format("EMERGENCY: Critical device fingerprint failure sent to DLT for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "correlationId", correlationId,
                "requiresImmediateEmergencyAction", true)
        );

        // Save to emergency audit log
        auditService.logEmergencySecurityEvent("DEVICE_FINGERPRINT_CRITICAL_FAILURE_DLT", event.getDeviceId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresEmergencyIntervention", true, "timestamp", Instant.now()));

        // Send emergency escalation
        try {
            notificationService.sendEmergencyEscalation(
                "EMERGENCY: Critical Device Fingerprint Failure DLT",
                String.format("EMERGENCY: Device %s critical fingerprint failure sent to DLT: %s",
                    event.getDeviceId(), exceptionMessage),
                Map.of("deviceId", event.getDeviceId(), "topic", topic, "correlationId", correlationId,
                    "priority", "EMERGENCY", "requiresImmediateAction", true)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency escalation: {}", ex.getMessage());
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

    private void processCriticalFingerprintFailure(DeviceFingerprintEvent event, String correlationId) {
        // Create critical security incident
        securityIncidentService.createCriticalIncident(
            "CRITICAL_DEVICE_FINGERPRINT_FAILURE",
            String.format("CRITICAL: Device fingerprint failure for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "failureReason", event.getFailureReason(),
                "severity", "CRITICAL", "correlationId", correlationId)
        );

        // Immediately block device
        deviceFingerprintService.emergencyBlockDevice(event.getDeviceId(), "CRITICAL_FINGERPRINT_FAILURE");

        // Trigger security review
        deviceFingerprintService.triggerEmergencySecurityReview(event.getDeviceId());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "CRITICAL Device Fingerprint Failure",
            String.format("CRITICAL: Device %s fingerprint failure detected", event.getDeviceId()),
            "CRITICAL"
        );

        metricsService.recordCriticalFailure("device-fingerprint");

        log.error("CRITICAL device fingerprint failure: deviceId={}, reason={}",
            event.getDeviceId(), event.getFailureReason());
    }

    private void processSystemCompromiseDetected(DeviceFingerprintEvent event, String correlationId) {
        // Trigger emergency response
        emergencyResponseService.triggerEmergencyResponse(
            "DEVICE_FINGERPRINT_SYSTEM_COMPROMISE",
            String.format("EMERGENCY: System compromise detected via device fingerprint for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "compromiseDetails", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Emergency block all related devices
        deviceFingerprintService.emergencyBlockRelatedDevices(event.getDeviceId());

        // Initiate forensic investigation
        securityIncidentService.initiateForensicInvestigation(
            "DEVICE_FINGERPRINT_SYSTEM_COMPROMISE",
            event.getDeviceId(),
            Map.of("correlationId", correlationId, "compromiseType", "FINGERPRINT_BASED")
        );

        // Send emergency escalation
        notificationService.sendEmergencyEscalation(
            "EMERGENCY: System Compromise Detected",
            String.format("EMERGENCY: System compromise detected for device %s via fingerprint analysis", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "priority", "EMERGENCY")
        );

        metricsService.recordSystemCompromise("device-fingerprint");

        log.error("EMERGENCY: System compromise detected via device fingerprint: deviceId={}", event.getDeviceId());
    }

    private void processMassFingerprintCorruption(DeviceFingerprintEvent event, String correlationId) {
        // Trigger emergency response for mass corruption
        emergencyResponseService.triggerEmergencyResponse(
            "MASS_DEVICE_FINGERPRINT_CORRUPTION",
            "EMERGENCY: Mass device fingerprint corruption detected",
            Map.of("affectedDeviceCount", event.getAffectedDevices(),
                "corruptionPattern", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Emergency fingerprint service lockdown
        deviceFingerprintService.emergencyServiceLockdown("MASS_CORRUPTION_DETECTED");

        // Initiate data recovery
        deviceFingerprintService.initiateMassDataRecovery(event.getAffectedDevices());

        // Send emergency escalation
        notificationService.sendEmergencyEscalation(
            "EMERGENCY: Mass Fingerprint Corruption",
            String.format("EMERGENCY: Mass device fingerprint corruption affecting %d devices",
                event.getAffectedDevices().size()),
            Map.of("affectedDeviceCount", event.getAffectedDevices().size(), "priority", "EMERGENCY")
        );

        metricsService.recordMassCorruption("device-fingerprint", event.getAffectedDevices().size());

        log.error("EMERGENCY: Mass device fingerprint corruption detected: affectedDevices={}",
            event.getAffectedDevices().size());
    }

    private void processFingerprintServiceUnavailable(DeviceFingerprintEvent event, String correlationId) {
        // Create critical incident for service unavailability
        securityIncidentService.createCriticalIncident(
            "DEVICE_FINGERPRINT_SERVICE_UNAVAILABLE",
            "CRITICAL: Device fingerprint service unavailable",
            Map.of("serviceStatus", event.getServiceStatus(),
                "unavailableReason", event.getFailureReason(),
                "correlationId", correlationId)
        );

        // Activate backup fingerprint service
        deviceFingerprintService.activateBackupService();

        // Implement fallback security measures
        deviceFingerprintService.activateFallbackSecurityMeasures();

        // Send critical alert
        notificationService.sendCriticalAlert(
            "CRITICAL: Device Fingerprint Service Unavailable",
            String.format("CRITICAL: Device fingerprint service unavailable: %s", event.getFailureReason()),
            "CRITICAL"
        );

        metricsService.recordServiceUnavailable("device-fingerprint");

        log.error("CRITICAL: Device fingerprint service unavailable: reason={}", event.getFailureReason());
    }

    private void processCriticalSecurityBreach(DeviceFingerprintEvent event, String correlationId) {
        // Trigger emergency response for security breach
        emergencyResponseService.triggerEmergencyResponse(
            "DEVICE_FINGERPRINT_SECURITY_BREACH",
            String.format("EMERGENCY: Critical security breach detected for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "breachType", event.getBreachType(),
                "breachSeverity", "CRITICAL", "correlationId", correlationId)
        );

        // Emergency block device and related accounts
        deviceFingerprintService.emergencySecurityLockdown(event.getDeviceId());

        // Initiate breach response protocol
        securityIncidentService.initiateBreachResponseProtocol(
            "DEVICE_FINGERPRINT_BREACH",
            event.getDeviceId(),
            Map.of("breachType", event.getBreachType(), "correlationId", correlationId)
        );

        // Send emergency escalation
        notificationService.sendEmergencyEscalation(
            "EMERGENCY: Critical Security Breach",
            String.format("EMERGENCY: Critical security breach detected for device %s", event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "breachType", event.getBreachType(), "priority", "EMERGENCY")
        );

        metricsService.recordSecurityBreach("device-fingerprint", "CRITICAL");

        log.error("EMERGENCY: Critical security breach via device fingerprint: deviceId={}, breachType={}",
            event.getDeviceId(), event.getBreachType());
    }

    private void processEmergencyShutdownRequired(DeviceFingerprintEvent event, String correlationId) {
        // Trigger emergency shutdown
        emergencyResponseService.triggerEmergencyShutdown(
            "DEVICE_FINGERPRINT_EMERGENCY_SHUTDOWN",
            String.format("EMERGENCY SHUTDOWN: Device fingerprint service emergency shutdown required: %s",
                event.getFailureReason()),
            Map.of("shutdownReason", event.getFailureReason(),
                "shutdownScope", event.getShutdownScope(),
                "correlationId", correlationId)
        );

        // Execute emergency shutdown
        deviceFingerprintService.executeEmergencyShutdown(event.getShutdownScope());

        // Send emergency escalation
        notificationService.sendEmergencyEscalation(
            "EMERGENCY SHUTDOWN: Device Fingerprint Service",
            String.format("EMERGENCY SHUTDOWN: Device fingerprint service shutdown: %s", event.getFailureReason()),
            Map.of("shutdownReason", event.getFailureReason(), "priority", "EMERGENCY")
        );

        metricsService.recordEmergencyShutdown("device-fingerprint");

        log.error("EMERGENCY SHUTDOWN: Device fingerprint service: reason={}", event.getFailureReason());
    }

    private void processUnknownCriticalFailure(DeviceFingerprintEvent event, String correlationId) {
        // Create critical incident for unknown failure type
        securityIncidentService.createCriticalIncident(
            "UNKNOWN_CRITICAL_DEVICE_FINGERPRINT_FAILURE",
            String.format("CRITICAL: Unknown device fingerprint failure type %s for device %s",
                event.getEventType(), event.getDeviceId()),
            Map.of("deviceId", event.getDeviceId(), "unknownEventType", event.getEventType(),
                "correlationId", correlationId)
        );

        // Default emergency response
        deviceFingerprintService.emergencyBlockDevice(event.getDeviceId(), "UNKNOWN_CRITICAL_FAILURE");

        // Send critical alert
        notificationService.sendCriticalAlert(
            "CRITICAL: Unknown Device Fingerprint Failure",
            String.format("CRITICAL: Unknown failure type %s for device %s",
                event.getEventType(), event.getDeviceId()),
            "CRITICAL"
        );

        log.error("CRITICAL: Unknown device fingerprint failure type: deviceId={}, eventType={}",
            event.getDeviceId(), event.getEventType());
    }
}