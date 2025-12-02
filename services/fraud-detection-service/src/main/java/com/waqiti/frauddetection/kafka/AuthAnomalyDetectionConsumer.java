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
 * Production-grade Kafka consumer for authentication anomaly detection events
 * Handles real-time authentication anomaly detection and response
 * Features enhanced DLQ and retry capabilities for critical security operations
 *
 * Critical for: Authentication security, anomaly detection, fraud prevention
 * SLA: Must process anomaly detection events within 2 seconds for real-time protection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthAnomalyDetectionConsumer {

    private final AuthAnomalyDetectionService authAnomalyDetectionService;
    private final AuthenticationSecurityService authenticationSecurityService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final RiskScoringService riskScoringService;
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
    private Counter retryCounter;
    private Counter dlqCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("auth_anomaly_detection_processed_total")
            .description("Total number of successfully processed auth anomaly detection events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auth_anomaly_detection_errors_total")
            .description("Total number of auth anomaly detection processing errors")
            .register(meterRegistry);
        retryCounter = Counter.builder("auth_anomaly_detection_retries_total")
            .description("Total number of auth anomaly detection retry attempts")
            .register(meterRegistry);
        dlqCounter = Counter.builder("auth_anomaly_detection_dlq_total")
            .description("Total number of auth anomaly detection events sent to DLQ")
            .register(meterRegistry);
        processingTimer = Timer.builder("auth_anomaly_detection_processing_duration")
            .description("Time taken to process auth anomaly detection events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auth-anomaly-detection", "authentication-anomalies", "auth-behavioral-analysis"},
        groupId = "fraud-auth-anomaly-detection-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        retryTopicSuffix = "-retry",
        dltTopicSuffix = ".DLQ"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "auth-anomaly-detection", fallbackMethod = "handleAuthAnomalyDetectionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000))
    public void handleAuthAnomalyDetectionEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-anomaly-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        // Check if this is a retry attempt
        boolean isRetry = topic != null && topic.contains("-retry");
        if (isRetry) {
            retryCounter.increment();
            log.info("Processing retry attempt for auth anomaly detection: topic={}, eventId={}", topic, event.getId());
        }

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auth anomaly detection: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String sessionId = (String) event.getData().get("sessionId");
            String deviceId = (String) event.getData().get("deviceId");

            switch (event.getEventType()) {
                case "AUTHENTICATION_ANOMALY_DETECTED":
                    handleAuthenticationAnomalyDetected(event, userId, sessionId, deviceId, correlationId);
                    break;

                case "BEHAVIORAL_ANOMALY_DETECTED":
                    handleBehavioralAnomalyDetected(event, userId, sessionId, deviceId, correlationId);
                    break;

                case "DEVICE_ANOMALY_DETECTED":
                    handleDeviceAnomalyDetected(event, userId, sessionId, deviceId, correlationId);
                    break;

                case "LOCATION_ANOMALY_DETECTED":
                    handleLocationAnomalyDetected(event, userId, sessionId, correlationId);
                    break;

                case "TIME_PATTERN_ANOMALY":
                    handleTimePatternAnomaly(event, userId, sessionId, correlationId);
                    break;

                case "VELOCITY_ANOMALY_DETECTED":
                    handleVelocityAnomalyDetected(event, userId, sessionId, correlationId);
                    break;

                case "CREDENTIAL_STUFFING_DETECTED":
                    handleCredentialStuffingDetected(event, userId, deviceId, correlationId);
                    break;

                case "ACCOUNT_TAKEOVER_SUSPECTED":
                    handleAccountTakeoverSuspected(event, userId, sessionId, deviceId, correlationId);
                    break;

                case "BIOMETRIC_ANOMALY_DETECTED":
                    handleBiometricAnomalyDetected(event, userId, sessionId, correlationId);
                    break;

                default:
                    log.warn("Unknown auth anomaly detection event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("AUTH_ANOMALY_DETECTION_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "sessionId", sessionId,
                    "deviceId", deviceId, "correlationId", correlationId,
                    "isRetry", isRetry, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auth anomaly detection event: {}", e.getMessage(), e);

            // Send fallback event with enhanced context
            kafkaTemplate.send("auth-anomaly-detection-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "isRetry", isRetry, "topic", topic,
                "retryCount", isRetry ? extractRetryNumber(topic) : 0, "maxRetries", 5));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuthAnomalyDetectionEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("auth-anomaly-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for auth anomaly detection: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to security fallback queue
        kafkaTemplate.send("auth-anomaly-detection-security-fallback", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "topic", topic,
            "securityPriority", "HIGH",
            "timestamp", Instant.now()));

        // Send high priority security alert
        try {
            notificationService.sendSecurityAlert(
                "Auth Anomaly Detection Consumer Circuit Breaker Triggered",
                String.format("Security alert: Auth anomaly detection event %s failed with circuit breaker: %s",
                    event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send security alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuthAnomalyDetectionEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace) {

        dlqCounter.increment();
        String correlationId = String.format("dlt-auth-anomaly-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auth anomaly detection permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Extract security information for escalation
        String userId = (String) event.getData().get("userId");
        String sessionId = (String) event.getData().get("sessionId");
        String deviceId = (String) event.getData().get("deviceId");

        // Save to security DLT store with enhanced details
        auditService.logSecurityEvent("AUTH_ANOMALY_DETECTION_DLT_EVENT", userId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "sessionId", sessionId,
                "deviceId", deviceId, "correlationId", correlationId,
                "stackTrace", stackTrace != null ? stackTrace : "N/A",
                "requiresSecurityEscalation", true, "timestamp", Instant.now()));

        // Mark for security review
        try {
            authAnomalyDetectionService.markForSecurityReview(userId, sessionId, deviceId,
                "DLT_PROCESSING_FAILURE", exceptionMessage);
        } catch (Exception e) {
            log.error("Failed to mark for security review: {}", e.getMessage());
        }

        // Send critical security alert
        try {
            notificationService.sendCriticalAlert(
                "CRITICAL: Auth Anomaly Detection Dead Letter Event",
                String.format("Security escalation: Auth anomaly detection event %s (user: %s, session: %s) permanently failed: %s",
                    event.getId(), userId, sessionId, exceptionMessage),
                Map.of("eventId", event.getId(), "userId", userId, "sessionId", sessionId,
                    "deviceId", deviceId, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical security alert: {}", ex.getMessage());
        }

        // Create security intervention task
        try {
            kafkaTemplate.send("security-intervention-tasks", Map.of(
                "taskType", "AUTH_ANOMALY_DETECTION_DLT",
                "userId", userId,
                "sessionId", sessionId,
                "deviceId", deviceId,
                "priority", "CRITICAL",
                "description", "Auth anomaly detection event failed and sent to DLT",
                "errorDetails", exceptionMessage,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            log.error("Failed to create security intervention task: {}", e.getMessage());
        }
    }

    private void handleAuthenticationAnomalyDetected(GenericKafkaEvent event, String userId, String sessionId, String deviceId, String correlationId) {
        Double anomalyScore = (Double) event.getData().get("anomalyScore");
        String anomalyType = (String) event.getData().get("anomalyType");
        log.warn("Authentication anomaly detected: userId={}, score={}, type={}", userId, anomalyScore, anomalyType);

        // Analyze anomaly severity
        String severity = authAnomalyDetectionService.analyzeAnomalySeverity(anomalyScore, anomalyType);

        // Take appropriate action based on severity
        authAnomalyDetectionService.processAuthenticationAnomaly(userId, sessionId, deviceId, anomalyScore, anomalyType, severity);

        // Update user risk profile
        riskScoringService.updateUserRiskProfile(userId, anomalyScore, anomalyType);

        // Send anomaly alert
        fraudNotificationService.sendAuthAnomalyAlert(userId, sessionId, anomalyScore, anomalyType, correlationId);

        log.info("Authentication anomaly processed: userId={}, severity={}", userId, severity);
    }

    private void handleBehavioralAnomalyDetected(GenericKafkaEvent event, String userId, String sessionId, String deviceId, String correlationId) {
        String behaviorType = (String) event.getData().get("behaviorType");
        Double deviationScore = (Double) event.getData().get("deviationScore");
        log.warn("Behavioral anomaly detected: userId={}, behavior={}, deviation={}", userId, behaviorType, deviationScore);

        // Analyze behavioral pattern
        behavioralAnalysisService.analyzeBehavioralAnomaly(userId, sessionId, behaviorType, deviationScore);

        // Update behavioral model
        behavioralAnalysisService.updateBehavioralModel(userId, behaviorType, deviationScore);

        // Check for escalation
        if (deviationScore > 0.8) {
            authenticationSecurityService.escalateBehavioralAnomaly(userId, sessionId, behaviorType, deviationScore);
        }

        log.info("Behavioral anomaly processed: userId={}, behavior={}", userId, behaviorType);
    }

    private void handleDeviceAnomalyDetected(GenericKafkaEvent event, String userId, String sessionId, String deviceId, String correlationId) {
        String deviceAnomalyType = (String) event.getData().get("deviceAnomalyType");
        log.warn("Device anomaly detected: userId={}, deviceId={}, anomaly={}", userId, deviceId, deviceAnomalyType);

        // Analyze device fingerprint
        authAnomalyDetectionService.analyzeDeviceFingerprint(userId, deviceId, deviceAnomalyType);

        // Check device trustworthiness
        boolean isTrustedDevice = authenticationSecurityService.checkDeviceTrust(userId, deviceId);

        if (!isTrustedDevice) {
            // Require additional authentication
            authenticationSecurityService.requireAdditionalAuthentication(userId, sessionId, deviceId);

            // Send device verification notification
            customerCommunicationService.sendDeviceVerificationRequest(userId, deviceId);
        }

        log.info("Device anomaly processed: userId={}, trusted={}", userId, isTrustedDevice);
    }

    private void handleLocationAnomalyDetected(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String currentLocation = (String) event.getData().get("currentLocation");
        String previousLocation = (String) event.getData().get("previousLocation");
        Double distance = (Double) event.getData().get("distance");
        log.warn("Location anomaly detected: userId={}, current={}, previous={}, distance={}km",
            userId, currentLocation, previousLocation, distance);

        // Analyze location pattern
        authAnomalyDetectionService.analyzeLocationPattern(userId, currentLocation, previousLocation, distance);

        // Check travel feasibility
        boolean isFeasibleTravel = authAnomalyDetectionService.checkTravelFeasibility(distance, event.getData());

        if (!isFeasibleTravel) {
            // Trigger location-based security measures
            authenticationSecurityService.triggerLocationSecurityMeasures(userId, sessionId, currentLocation);

            // Send location verification notification
            customerCommunicationService.sendLocationVerificationRequest(userId, currentLocation);
        }

        log.info("Location anomaly processed: userId={}, feasible={}", userId, isFeasibleTravel);
    }

    private void handleTimePatternAnomaly(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String timePattern = (String) event.getData().get("timePattern");
        String usualPattern = (String) event.getData().get("usualPattern");
        log.warn("Time pattern anomaly detected: userId={}, current={}, usual={}", userId, timePattern, usualPattern);

        // Analyze time-based behavior
        behavioralAnalysisService.analyzeTimePattern(userId, timePattern, usualPattern);

        // Check for off-hours activity
        boolean isOffHours = authAnomalyDetectionService.checkOffHoursActivity(userId, timePattern);

        if (isOffHours) {
            // Apply enhanced monitoring
            authenticationSecurityService.enableEnhancedMonitoring(userId, sessionId);
        }

        log.info("Time pattern anomaly processed: userId={}, offHours={}", userId, isOffHours);
    }

    private void handleVelocityAnomalyDetected(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        Integer requestCount = (Integer) event.getData().get("requestCount");
        Long timeWindow = (Long) event.getData().get("timeWindow");
        log.warn("Velocity anomaly detected: userId={}, requests={}, window={}ms", userId, requestCount, timeWindow);

        // Analyze request velocity
        authAnomalyDetectionService.analyzeRequestVelocity(userId, requestCount, timeWindow);

        // Apply rate limiting if necessary
        if (requestCount > 50) {
            authenticationSecurityService.applyRateLimiting(userId, sessionId);
        }

        log.info("Velocity anomaly processed: userId={}, requests={}", userId, requestCount);
    }

    private void handleCredentialStuffingDetected(GenericKafkaEvent event, String userId, String deviceId, String correlationId) {
        Integer attemptCount = (Integer) event.getData().get("attemptCount");
        log.warn("Credential stuffing detected: userId={}, deviceId={}, attempts={}", userId, deviceId, attemptCount);

        // Block credential stuffing attack
        authenticationSecurityService.blockCredentialStuffing(userId, deviceId, attemptCount);

        // Add to threat intelligence
        authAnomalyDetectionService.addToThreatIntelligence(deviceId, "CREDENTIAL_STUFFING", attemptCount);

        // Send security alert
        fraudNotificationService.sendCredentialStuffingAlert(userId, deviceId, attemptCount, correlationId);

        log.info("Credential stuffing blocked: userId={}, attempts={}", userId, attemptCount);
    }

    private void handleAccountTakeoverSuspected(GenericKafkaEvent event, String userId, String sessionId, String deviceId, String correlationId) {
        Double suspicionScore = (Double) event.getData().get("suspicionScore");
        String indicators = (String) event.getData().get("indicators");
        log.error("Account takeover suspected: userId={}, score={}, indicators={}", userId, suspicionScore, indicators);

        // Trigger account takeover response
        authenticationSecurityService.triggerAccountTakeoverResponse(userId, sessionId, deviceId, suspicionScore);

        // Freeze account temporarily
        authenticationSecurityService.freezeAccountTemporarily(userId, "SUSPECTED_TAKEOVER");

        // Send immediate notification
        customerCommunicationService.sendAccountTakeoverAlert(userId, sessionId);

        // Escalate to security team
        fraudNotificationService.sendAccountTakeoverAlert(userId, sessionId, suspicionScore, correlationId);

        log.info("Account takeover response triggered: userId={}", userId);
    }

    private void handleBiometricAnomalyDetected(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String biometricType = (String) event.getData().get("biometricType");
        Double confidenceScore = (Double) event.getData().get("confidenceScore");
        log.warn("Biometric anomaly detected: userId={}, type={}, confidence={}", userId, biometricType, confidenceScore);

        // Analyze biometric pattern
        authAnomalyDetectionService.analyzeBiometricPattern(userId, biometricType, confidenceScore);

        // Require biometric re-verification if confidence is low
        if (confidenceScore < 0.7) {
            authenticationSecurityService.requireBiometricReVerification(userId, sessionId, biometricType);
        }

        log.info("Biometric anomaly processed: userId={}, type={}", userId, biometricType);
    }

    private int extractRetryNumber(String topic) {
        try {
            if (topic != null && topic.contains("-retry-")) {
                String[] parts = topic.split("-retry-");
                if (parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Could not extract retry number from topic: {}", topic);
        }
        return 0;
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