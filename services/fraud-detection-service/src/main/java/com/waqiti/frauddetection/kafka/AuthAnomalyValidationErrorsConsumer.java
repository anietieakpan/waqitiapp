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
 * Production-grade Kafka consumer for authentication anomaly validation error events
 * Handles validation errors during authentication anomaly processing
 *
 * Critical for: Authentication validation, error handling, security integrity
 * SLA: Must process validation errors within 10 seconds for security maintenance
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthAnomalyValidationErrorsConsumer {

    private final AuthAnomalyValidationService authAnomalyValidationService;
    private final AuthenticationSecurityService authenticationSecurityService;
    private final ErrorHandlingService errorHandlingService;
    private final CustomerCommunicationService customerCommunicationService;
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
        successCounter = Counter.builder("auth_anomaly_validation_errors_processed_total")
            .description("Total number of successfully processed auth anomaly validation error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auth_anomaly_validation_errors_errors_total")
            .description("Total number of auth anomaly validation error processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auth_anomaly_validation_errors_processing_duration")
            .description("Time taken to process auth anomaly validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auth-anomaly-validation-errors", "authentication-validation-errors"},
        groupId = "fraud-auth-anomaly-validation-errors-group",
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
    @CircuitBreaker(name = "auth-anomaly-validation-errors", fallbackMethod = "handleAuthAnomalyValidationErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuthAnomalyValidationErrorEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-anomaly-val-err-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auth anomaly validation error: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String sessionId = (String) event.getData().get("sessionId");
            String deviceId = (String) event.getData().get("deviceId");

            switch (event.getEventType()) {
                case "AUTHENTICATION_VALIDATION_FAILED":
                    handleAuthenticationValidationFailed(event, userId, sessionId, deviceId, correlationId);
                    break;

                case "ANOMALY_SCORE_VALIDATION_ERROR":
                    handleAnomalyScoreValidationError(event, userId, sessionId, correlationId);
                    break;

                case "DEVICE_FINGERPRINT_VALIDATION_ERROR":
                    handleDeviceFingerprintValidationError(event, userId, deviceId, correlationId);
                    break;

                case "BIOMETRIC_VALIDATION_FAILED":
                    handleBiometricValidationFailed(event, userId, sessionId, correlationId);
                    break;

                case "LOCATION_VALIDATION_ERROR":
                    handleLocationValidationError(event, userId, sessionId, correlationId);
                    break;

                case "BEHAVIOR_PATTERN_VALIDATION_ERROR":
                    handleBehaviorPatternValidationError(event, userId, sessionId, correlationId);
                    break;

                case "SESSION_VALIDATION_FAILED":
                    handleSessionValidationFailed(event, userId, sessionId, correlationId);
                    break;

                case "TIMESTAMP_VALIDATION_ERROR":
                    handleTimestampValidationError(event, userId, sessionId, correlationId);
                    break;

                case "DATA_INTEGRITY_VALIDATION_ERROR":
                    handleDataIntegrityValidationError(event, userId, correlationId);
                    break;

                default:
                    log.warn("Unknown auth anomaly validation error type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("AUTH_ANOMALY_VALIDATION_ERROR_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "sessionId", sessionId,
                    "deviceId", deviceId, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auth anomaly validation error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("auth-anomaly-validation-errors-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuthAnomalyValidationErrorEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("auth-anomaly-val-err-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for auth anomaly validation error: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("auth-anomaly-validation-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Auth Anomaly Validation Errors Consumer Circuit Breaker Triggered",
                String.format("Auth anomaly validation error event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuthAnomalyValidationErrorEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-auth-anomaly-val-err-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auth anomaly validation error permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("AUTH_ANOMALY_VALIDATION_ERROR_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Auth Anomaly Validation Error Event Dead Letter",
                String.format("Auth anomaly validation error event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void handleAuthenticationValidationFailed(GenericKafkaEvent event, String userId, String sessionId, String deviceId, String correlationId) {
        String validationError = (String) event.getData().get("validationError");
        String fieldName = (String) event.getData().get("fieldName");
        log.warn("Authentication validation failed: userId={}, error={}, field={}", userId, validationError, fieldName);

        // Record validation failure
        authAnomalyValidationService.recordAuthValidationFailure(userId, sessionId, validationError, fieldName);

        // Determine recovery action
        boolean canRecover = authAnomalyValidationService.canRecoverFromValidationError(validationError);

        if (canRecover) {
            // Attempt automatic recovery
            authAnomalyValidationService.attemptValidationRecovery(userId, sessionId, validationError);
        } else {
            // Escalate for manual intervention
            authenticationSecurityService.escalateValidationFailure(userId, sessionId, validationError);
        }

        log.info("Authentication validation failure processed: userId={}, recoverable={}", userId, canRecover);
    }

    private void handleAnomalyScoreValidationError(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        Double invalidScore = (Double) event.getData().get("invalidScore");
        String validationReason = (String) event.getData().get("validationReason");
        log.warn("Anomaly score validation error: userId={}, score={}, reason={}", userId, invalidScore, validationReason);

        // Recalculate anomaly score
        Double recalculatedScore = authAnomalyValidationService.recalculateAnomalyScore(userId, sessionId);

        // Update score if recalculation is valid
        if (recalculatedScore != null && recalculatedScore >= 0.0 && recalculatedScore <= 1.0) {
            authAnomalyValidationService.updateValidatedScore(userId, sessionId, recalculatedScore);
        } else {
            // Use fallback scoring
            authAnomalyValidationService.applyFallbackScoring(userId, sessionId);
        }

        log.info("Anomaly score validation error resolved: userId={}, newScore={}", userId, recalculatedScore);
    }

    private void handleDeviceFingerprintValidationError(GenericKafkaEvent event, String userId, String deviceId, String correlationId) {
        String fingerprintError = (String) event.getData().get("fingerprintError");
        log.warn("Device fingerprint validation error: userId={}, deviceId={}, error={}", userId, deviceId, fingerprintError);

        // Regenerate device fingerprint
        boolean regenerated = authAnomalyValidationService.regenerateDeviceFingerprint(userId, deviceId);

        if (!regenerated) {
            // Mark device as untrusted
            authenticationSecurityService.markDeviceAsUntrusted(userId, deviceId, fingerprintError);

            // Require device re-verification
            customerCommunicationService.requestDeviceReVerification(userId, deviceId);
        }

        log.info("Device fingerprint validation error handled: userId={}, regenerated={}", userId, regenerated);
    }

    private void handleBiometricValidationFailed(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String biometricType = (String) event.getData().get("biometricType");
        String validationError = (String) event.getData().get("validationError");
        log.warn("Biometric validation failed: userId={}, type={}, error={}", userId, biometricType, validationError);

        // Retry biometric validation
        boolean retrySuccessful = authAnomalyValidationService.retryBiometricValidation(userId, sessionId, biometricType);

        if (!retrySuccessful) {
            // Fall back to alternative authentication
            authenticationSecurityService.requireAlternativeAuthentication(userId, sessionId);

            // Notify customer
            customerCommunicationService.sendBiometricValidationFailureNotification(userId, biometricType);
        }

        log.info("Biometric validation failure handled: userId={}, retrySuccessful={}", userId, retrySuccessful);
    }

    private void handleLocationValidationError(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String locationError = (String) event.getData().get("locationError");
        String providedLocation = (String) event.getData().get("providedLocation");
        log.warn("Location validation error: userId={}, error={}, location={}", userId, locationError, providedLocation);

        // Attempt location re-validation
        boolean revalidated = authAnomalyValidationService.revalidateLocation(userId, sessionId, providedLocation);

        if (!revalidated) {
            // Use IP-based location as fallback
            String fallbackLocation = authAnomalyValidationService.getFallbackLocation(userId, sessionId);
            authAnomalyValidationService.updateLocationWithFallback(userId, sessionId, fallbackLocation);
        }

        log.info("Location validation error resolved: userId={}, revalidated={}", userId, revalidated);
    }

    private void handleBehaviorPatternValidationError(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String patternError = (String) event.getData().get("patternError");
        String behaviorType = (String) event.getData().get("behaviorType");
        log.warn("Behavior pattern validation error: userId={}, error={}, type={}", userId, patternError, behaviorType);

        // Reanalyze behavior pattern
        boolean reanalyzed = authAnomalyValidationService.reanalyzeBehaviorPattern(userId, sessionId, behaviorType);

        if (!reanalyzed) {
            // Use baseline behavior pattern
            authAnomalyValidationService.applyBaselineBehaviorPattern(userId, sessionId, behaviorType);
        }

        log.info("Behavior pattern validation error resolved: userId={}, reanalyzed={}", userId, reanalyzed);
    }

    private void handleSessionValidationFailed(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String sessionError = (String) event.getData().get("sessionError");
        log.warn("Session validation failed: userId={}, sessionId={}, error={}", userId, sessionId, sessionError);

        // Check session integrity
        boolean isValidSession = authAnomalyValidationService.validateSessionIntegrity(userId, sessionId);

        if (!isValidSession) {
            // Terminate invalid session
            authenticationSecurityService.terminateSession(userId, sessionId, sessionError);

            // Require re-authentication
            authenticationSecurityService.requireReAuthentication(userId, "SESSION_VALIDATION_FAILED");
        }

        log.info("Session validation failure handled: userId={}, validSession={}", userId, isValidSession);
    }

    private void handleTimestampValidationError(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String timestampError = (String) event.getData().get("timestampError");
        Long providedTimestamp = (Long) event.getData().get("providedTimestamp");
        log.warn("Timestamp validation error: userId={}, error={}, timestamp={}", userId, timestampError, providedTimestamp);

        // Verify system clock synchronization
        boolean clockSynced = authAnomalyValidationService.verifyClockSynchronization();

        if (!clockSynced) {
            // Report clock sync issue
            errorHandlingService.reportClockSynchronizationIssue(timestampError);
        }

        // Use server timestamp as authoritative
        authAnomalyValidationService.useServerTimestamp(userId, sessionId);

        log.info("Timestamp validation error resolved: userId={}, clockSynced={}", userId, clockSynced);
    }

    private void handleDataIntegrityValidationError(GenericKafkaEvent event, String userId, String correlationId) {
        String integrityError = (String) event.getData().get("integrityError");
        String affectedData = (String) event.getData().get("affectedData");
        log.error("Data integrity validation error: userId={}, error={}, data={}", userId, integrityError, affectedData);

        // Record integrity violation
        errorHandlingService.recordDataIntegrityViolation(userId, integrityError, affectedData);

        // Escalate immediately
        authenticationSecurityService.escalateDataIntegrityIssue(userId, integrityError, affectedData);

        // Send critical alert
        fraudNotificationService.sendDataIntegrityAlert(userId, integrityError, affectedData, correlationId);

        log.info("Data integrity validation error escalated: userId={}", userId);
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