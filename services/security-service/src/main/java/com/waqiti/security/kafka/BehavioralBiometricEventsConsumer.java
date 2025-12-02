package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BiometricAnalysisService;
import com.waqiti.security.service.BiometricSecurityService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BehavioralBiometricEventsConsumer {

    private final BiometricAnalysisService biometricAnalysisService;
    private final BiometricSecurityService biometricSecurityService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("behavioral_biometric_events_processed_total")
            .description("Total number of successfully processed behavioral biometric events")
            .register(meterRegistry);
        errorCounter = Counter.builder("behavioral_biometric_events_errors_total")
            .description("Total number of behavioral biometric event processing errors")
            .register(meterRegistry);
        dlqCounter = Counter.builder("behavioral_biometric_events_dlq_total")
            .description("Total number of behavioral biometric events sent to DLQ")
            .register(meterRegistry);
        processingTimer = Timer.builder("behavioral_biometric_events_processing_duration")
            .description("Time taken to process behavioral biometric events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"behavioral-biometric-events", "biometric-behavior-analysis", "behavioral-biometrics"},
        groupId = "security-service-behavioral-biometric-events-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "behavioral-biometric-events", fallbackMethod = "handleBehavioralBiometricEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBehavioralBiometricEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("behavioral-biometric-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String eventId = (String) event.get("eventId");
            String userId = (String) event.get("userId");
            String eventType = (String) event.get("eventType");
            String biometricType = (String) event.get("biometricType");
            String eventKey = String.format("%s-%s-%s", eventId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing behavioral biometric event: eventId={}, userId={}, type={}, biometricType={}",
                eventId, userId, eventType, biometricType);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String deviceId = (String) event.get("deviceId");
            LocalDateTime capturedAt = LocalDateTime.parse((String) event.get("capturedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> biometricData = (Map<String, Object>) event.get("biometricData");
            @SuppressWarnings("unchecked")
            Map<String, Object> behavioralMetrics = (Map<String, Object>) event.getOrDefault("behavioralMetrics", Map.of());
            Double confidenceScore = ((Number) event.getOrDefault("confidenceScore", 0.0)).doubleValue();
            String quality = (String) event.getOrDefault("quality", "UNKNOWN");
            Boolean isAnomaly = (Boolean) event.getOrDefault("isAnomaly", false);
            Double anomalyScore = ((Number) event.getOrDefault("anomalyScore", 0.0)).doubleValue();
            @SuppressWarnings("unchecked")
            List<String> anomalyIndicators = (List<String>) event.getOrDefault("anomalyIndicators", List.of());
            String sessionId = (String) event.get("sessionId");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            @SuppressWarnings("unchecked")
            Map<String, Object> environmentalFactors = (Map<String, Object>) event.getOrDefault("environmentalFactors", Map.of());

            // Process biometric event based on type
            switch (eventType) {
                case "BIOMETRIC_CAPTURE":
                    processBiometricCapture(eventId, userId, biometricType, deviceId, biometricData,
                        behavioralMetrics, confidenceScore, quality, sessionId, correlationId);
                    break;

                case "BIOMETRIC_VERIFICATION":
                    processBiometricVerification(eventId, userId, biometricType, deviceId, biometricData,
                        confidenceScore, quality, sessionId, correlationId);
                    break;

                case "BEHAVIORAL_PATTERN_DETECTED":
                    processBehavioralPatternDetected(eventId, userId, biometricType, deviceId,
                        behavioralMetrics, confidenceScore, capturedAt, correlationId);
                    break;

                case "BIOMETRIC_ANOMALY_DETECTED":
                    processBiometricAnomalyDetected(eventId, userId, biometricType, deviceId,
                        biometricData, anomalyScore, anomalyIndicators, sessionId, correlationId);
                    break;

                case "CONTINUOUS_AUTH_UPDATE":
                    processContinuousAuthUpdate(eventId, userId, biometricType, deviceId,
                        behavioralMetrics, confidenceScore, sessionId, correlationId);
                    break;

                case "ENVIRONMENTAL_ADAPTATION":
                    processEnvironmentalAdaptation(eventId, userId, biometricType, deviceId,
                        environmentalFactors, behavioralMetrics, correlationId);
                    break;

                default:
                    processGenericBiometricEvent(eventId, userId, eventType, biometricType,
                        deviceId, biometricData, behavioralMetrics, correlationId);
                    break;
            }

            // Handle anomalies if detected
            if (isAnomaly && anomalyScore > 0.7) {
                handleBiometricAnomaly(eventId, userId, biometricType, deviceId, anomalyScore,
                    anomalyIndicators, sessionId, ipAddress, location, correlationId);
            }

            // Update biometric profile
            updateBiometricProfile(userId, biometricType, deviceId, biometricData, behavioralMetrics,
                confidenceScore, quality, correlationId);

            // Enhance continuous authentication
            enhanceContinuousAuthentication(userId, deviceId, sessionId, biometricType,
                behavioralMetrics, confidenceScore, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BEHAVIORAL_BIOMETRIC_EVENT_PROCESSED", userId,
                Map.of("eventId", eventId, "eventType", eventType, "biometricType", biometricType,
                    "confidenceScore", confidenceScore, "quality", quality, "isAnomaly", isAnomaly,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process behavioral biometric event: {}", e.getMessage(), e);

            // Send to specialized DLQ for biometric events due to their sensitive nature
            kafkaTemplate.send("behavioral-biometric-events-priority-dlq", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "processingAttempt", "INITIAL_PROCESSING_FAILED",
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3, "requiresManualReview", true));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBehavioralBiometricEventFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("behavioral-biometric-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for behavioral biometric event: error={}", ex.getMessage());

        // Send to high-priority dead letter queue for biometric events
        kafkaTemplate.send("behavioral-biometric-events-critical-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "priority", "CRITICAL",
            "requiresImmediateAttention", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send immediate notification to security team for biometric failures
        try {
            securityNotificationService.sendEmergencyNotification(
                "Behavioral Biometric Circuit Breaker Triggered",
                String.format("CRITICAL: Behavioral biometric event processing failed: %s", ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send emergency biometric alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBehavioralBiometricEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic) {

        dlqCounter.increment();
        String correlationId = String.format("dlt-behavioral-biometric-%d", System.currentTimeMillis());

        log.error("CRITICAL DLQ: Behavioral biometric event permanently failed - OriginalTopic: {}, Topic: {}, Error: {}",
            originalTopic, topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String eventId = (String) event.get("eventId");
            String userId = (String) event.get("userId");
            String biometricType = (String) event.get("biometricType");
            String deviceId = (String) event.get("deviceId");

            // Store in specialized biometric DLQ table for critical review
            auditService.logSecurityEvent("BEHAVIORAL_BIOMETRIC_EVENT_DLT_CRITICAL", userId,
                Map.of("originalTopic", originalTopic != null ? originalTopic : topic,
                    "errorMessage", exceptionMessage, "eventId", eventId,
                    "biometricType", biometricType, "deviceId", deviceId,
                    "correlationId", correlationId, "requiresManualIntervention", true,
                    "priority", "CRITICAL", "securityImplication", "HIGH",
                    "timestamp", Instant.now()));

            // Send emergency alert for biometric DLQ events
            securityNotificationService.sendEmergencyNotification(
                "CRITICAL: Behavioral Biometric Event in DLQ",
                String.format("URGENT: Biometric event %s for user %s permanently failed: %s. " +
                    "This may indicate biometric security compromise or system malfunction. " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED.", eventId, userId, exceptionMessage),
                Map.of("eventId", eventId, "userId", userId, "biometricType", biometricType,
                    "deviceId", deviceId, "topic", topic, "correlationId", correlationId,
                    "securityImpact", "POTENTIAL_BIOMETRIC_COMPROMISE")
            );

            // Notify biometric security team specifically
            securityNotificationService.notifyBiometricSecurityTeam(eventId, userId, biometricType,
                deviceId, "DLQ_EVENT", exceptionMessage, correlationId);

            // Queue for emergency security review
            threatResponseService.queueForEmergencySecurityReview(eventId, userId, "BIOMETRIC_DLQ_EVENT",
                biometricType, deviceId, exceptionMessage, correlationId);

            // Store the event for forensic analysis
            biometricSecurityService.storeDlqEventForForensicAnalysis(eventJson, eventId, userId,
                biometricType, deviceId, exceptionMessage, correlationId);

        } catch (Exception ex) {
            log.error("CRITICAL: Failed to parse behavioral biometric DLQ event - this is a severe system issue: {}",
                eventJson, ex);

            // Even if we can't parse, send a generic critical alert
            try {
                securityNotificationService.sendEmergencyNotification(
                    "SYSTEM CRITICAL: Biometric DLQ Processing Failed",
                    String.format("EMERGENCY: Cannot parse biometric DLQ event. System integrity may be compromised. " +
                        "Event: %s, Error: %s", eventJson, ex.getMessage()),
                    Map.of("rawEvent", eventJson, "parseError", ex.getMessage(),
                        "correlationId", correlationId, "systemStatus", "CRITICAL_FAILURE")
                );
            } catch (Exception notificationEx) {
                log.error("CATASTROPHIC: Cannot send emergency notification for biometric DLQ parsing failure",
                    notificationEx);
            }
        }
    }

    private void processBiometricCapture(String eventId, String userId, String biometricType,
                                       String deviceId, Map<String, Object> biometricData,
                                       Map<String, Object> behavioralMetrics, Double confidenceScore,
                                       String quality, String sessionId, String correlationId) {
        try {
            biometricAnalysisService.processBiometricCapture(userId, eventId, biometricType,
                deviceId, biometricData, behavioralMetrics, confidenceScore, quality, sessionId);

            log.info("Biometric capture processed: eventId={}, userId={}, type={}, quality={}",
                eventId, userId, biometricType, quality);

        } catch (Exception e) {
            log.error("Failed to process biometric capture: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Biometric capture processing failed", e);
        }
    }

    private void processBiometricVerification(String eventId, String userId, String biometricType,
                                            String deviceId, Map<String, Object> biometricData,
                                            Double confidenceScore, String quality,
                                            String sessionId, String correlationId) {
        try {
            biometricSecurityService.processBiometricVerification(userId, eventId, biometricType,
                deviceId, biometricData, confidenceScore, quality, sessionId);

            log.info("Biometric verification processed: eventId={}, userId={}, type={}, score={}",
                eventId, userId, biometricType, confidenceScore);

        } catch (Exception e) {
            log.error("Failed to process biometric verification: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Biometric verification processing failed", e);
        }
    }

    private void processBehavioralPatternDetected(String eventId, String userId, String biometricType,
                                                String deviceId, Map<String, Object> behavioralMetrics,
                                                Double confidenceScore, LocalDateTime capturedAt,
                                                String correlationId) {
        try {
            biometricAnalysisService.processBehavioralPatternDetected(userId, eventId, biometricType,
                deviceId, behavioralMetrics, confidenceScore, capturedAt);

            log.info("Behavioral pattern detected processed: eventId={}, userId={}, type={}",
                eventId, userId, biometricType);

        } catch (Exception e) {
            log.error("Failed to process behavioral pattern detected: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Behavioral pattern detection processing failed", e);
        }
    }

    private void processBiometricAnomalyDetected(String eventId, String userId, String biometricType,
                                               String deviceId, Map<String, Object> biometricData,
                                               Double anomalyScore, List<String> anomalyIndicators,
                                               String sessionId, String correlationId) {
        try {
            biometricSecurityService.processBiometricAnomalyDetected(userId, eventId, biometricType,
                deviceId, biometricData, anomalyScore, anomalyIndicators, sessionId);

            // Immediate security response for high-score anomalies
            if (anomalyScore > 0.8) {
                threatResponseService.respondToBiometricAnomaly(userId, eventId, biometricType,
                    deviceId, anomalyScore, anomalyIndicators, sessionId, correlationId);
            }

            log.info("Biometric anomaly detected processed: eventId={}, userId={}, score={}",
                eventId, userId, anomalyScore);

        } catch (Exception e) {
            log.error("Failed to process biometric anomaly detected: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Biometric anomaly detection processing failed", e);
        }
    }

    private void processContinuousAuthUpdate(String eventId, String userId, String biometricType,
                                           String deviceId, Map<String, Object> behavioralMetrics,
                                           Double confidenceScore, String sessionId, String correlationId) {
        try {
            biometricSecurityService.processContinuousAuthUpdate(userId, eventId, biometricType,
                deviceId, behavioralMetrics, confidenceScore, sessionId);

            log.info("Continuous auth update processed: eventId={}, userId={}, score={}",
                eventId, userId, confidenceScore);

        } catch (Exception e) {
            log.error("Failed to process continuous auth update: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Continuous auth update processing failed", e);
        }
    }

    private void processEnvironmentalAdaptation(String eventId, String userId, String biometricType,
                                               String deviceId, Map<String, Object> environmentalFactors,
                                               Map<String, Object> behavioralMetrics, String correlationId) {
        try {
            biometricAnalysisService.processEnvironmentalAdaptation(userId, eventId, biometricType,
                deviceId, environmentalFactors, behavioralMetrics);

            log.info("Environmental adaptation processed: eventId={}, userId={}, type={}",
                eventId, userId, biometricType);

        } catch (Exception e) {
            log.error("Failed to process environmental adaptation: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Environmental adaptation processing failed", e);
        }
    }

    private void processGenericBiometricEvent(String eventId, String userId, String eventType,
                                            String biometricType, String deviceId,
                                            Map<String, Object> biometricData,
                                            Map<String, Object> behavioralMetrics, String correlationId) {
        try {
            biometricAnalysisService.processGenericBiometricEvent(userId, eventId, eventType,
                biometricType, deviceId, biometricData, behavioralMetrics);

            log.info("Generic biometric event processed: eventId={}, userId={}, eventType={}",
                eventId, userId, eventType);

        } catch (Exception e) {
            log.error("Failed to process generic biometric event: eventId={}, userId={}",
                eventId, userId, e);
            throw new RuntimeException("Generic biometric event processing failed", e);
        }
    }

    private void handleBiometricAnomaly(String eventId, String userId, String biometricType,
                                      String deviceId, Double anomalyScore, List<String> anomalyIndicators,
                                      String sessionId, String ipAddress, String location,
                                      String correlationId) {
        try {
            threatResponseService.handleBiometricAnomaly(userId, eventId, biometricType, deviceId,
                anomalyScore, anomalyIndicators, sessionId, ipAddress, location, correlationId);

            // Send immediate notification for high-risk anomalies
            if (anomalyScore > 0.9) {
                securityNotificationService.sendCriticalAlert(
                    "High-Risk Biometric Anomaly Detected",
                    String.format("Critical biometric anomaly for user %s: score=%.2f, indicators=%s",
                        userId, anomalyScore, anomalyIndicators),
                    Map.of("userId", userId, "eventId", eventId, "anomalyScore", anomalyScore,
                        "correlationId", correlationId)
                );
            }

            log.warn("Biometric anomaly handled: eventId={}, userId={}, score={}",
                eventId, userId, anomalyScore);

        } catch (Exception e) {
            log.error("Failed to handle biometric anomaly: eventId={}, userId={}",
                eventId, userId, e);
            // Don't throw exception as anomaly handling failure shouldn't block processing
        }
    }

    private void updateBiometricProfile(String userId, String biometricType, String deviceId,
                                      Map<String, Object> biometricData, Map<String, Object> behavioralMetrics,
                                      Double confidenceScore, String quality, String correlationId) {
        try {
            biometricAnalysisService.updateBiometricProfile(userId, biometricType, deviceId,
                biometricData, behavioralMetrics, confidenceScore, quality);

            log.debug("Biometric profile updated: userId={}, type={}, score={}",
                userId, biometricType, confidenceScore);

        } catch (Exception e) {
            log.error("Failed to update biometric profile: userId={}, type={}",
                userId, biometricType, e);
            // Don't throw exception as profile update failure shouldn't block processing
        }
    }

    private void enhanceContinuousAuthentication(String userId, String deviceId, String sessionId,
                                               String biometricType, Map<String, Object> behavioralMetrics,
                                               Double confidenceScore, String correlationId) {
        try {
            biometricSecurityService.enhanceContinuousAuthentication(userId, deviceId, sessionId,
                biometricType, behavioralMetrics, confidenceScore);

            log.debug("Continuous authentication enhanced: userId={}, sessionId={}, score={}",
                userId, sessionId, confidenceScore);

        } catch (Exception e) {
            log.error("Failed to enhance continuous authentication: userId={}, sessionId={}",
                userId, sessionId, e);
            // Don't throw exception as enhancement failure shouldn't block processing
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
}