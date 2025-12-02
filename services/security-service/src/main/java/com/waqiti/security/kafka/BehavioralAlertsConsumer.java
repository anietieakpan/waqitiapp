package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BehavioralAnalysisService;
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
public class BehavioralAlertsConsumer {

    private final BehavioralAnalysisService behavioralAnalysisService;
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
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("behavioral_alerts_processed_total")
            .description("Total number of successfully processed behavioral alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("behavioral_alerts_errors_total")
            .description("Total number of behavioral alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("behavioral_alerts_processing_duration")
            .description("Time taken to process behavioral alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"behavioral-alerts", "user-behavior-anomalies", "behavioral-risk-alerts"},
        groupId = "security-service-behavioral-alerts-group",
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
    @CircuitBreaker(name = "behavioral-alerts", fallbackMethod = "handleBehavioralAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBehavioralAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("behavioral-alert-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String alertId = (String) event.get("alertId");
            String userId = (String) event.get("userId");
            String alertType = (String) event.get("alertType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", alertId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing behavioral alert: alertId={}, userId={}, type={}, severity={}",
                alertId, userId, alertType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String behaviorPattern = (String) event.get("behaviorPattern");
            Double anomalyScore = ((Number) event.get("anomalyScore")).doubleValue();
            String detectionMethod = (String) event.get("detectionMethod");
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> behaviorData = (Map<String, Object>) event.get("behaviorData");
            @SuppressWarnings("unchecked")
            List<String> triggeredRules = (List<String>) event.getOrDefault("triggeredRules", List.of());
            String deviceId = (String) event.get("deviceId");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            Boolean requiresReview = (Boolean) event.getOrDefault("requiresReview", false);
            String riskLevel = (String) event.getOrDefault("riskLevel", "LOW");

            // Process behavioral alert based on type
            switch (alertType) {
                case "TRANSACTION_ANOMALY":
                    processTransactionAnomaly(alertId, userId, anomalyScore, behaviorData,
                        triggeredRules, severity, correlationId);
                    break;

                case "LOGIN_PATTERN_ANOMALY":
                    processLoginPatternAnomaly(alertId, userId, behaviorPattern, anomalyScore,
                        deviceId, ipAddress, location, severity, correlationId);
                    break;

                case "DEVICE_BEHAVIOR_ANOMALY":
                    processDeviceBehaviorAnomaly(alertId, userId, deviceId, behaviorData,
                        anomalyScore, triggeredRules, severity, correlationId);
                    break;

                case "SPENDING_PATTERN_ANOMALY":
                    processSpendingPatternAnomaly(alertId, userId, behaviorData, anomalyScore,
                        triggeredRules, severity, correlationId);
                    break;

                case "TIME_BASED_ANOMALY":
                    processTimeBasedAnomaly(alertId, userId, behaviorPattern, behaviorData,
                        anomalyScore, detectedAt, severity, correlationId);
                    break;

                case "GEOGRAPHIC_ANOMALY":
                    processGeographicAnomaly(alertId, userId, location, ipAddress, behaviorData,
                        anomalyScore, severity, correlationId);
                    break;

                default:
                    processGenericBehavioralAlert(alertId, userId, alertType, behaviorPattern,
                        anomalyScore, behaviorData, severity, correlationId);
                    break;
            }

            // Escalate based on severity and risk level
            escalateAlert(alertId, userId, alertType, severity, riskLevel, anomalyScore,
                requiresReview, correlationId);

            // Update user risk profile
            updateUserRiskProfile(userId, alertType, anomalyScore, riskLevel, behaviorData, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BEHAVIORAL_ALERT_PROCESSED", userId,
                Map.of("alertId", alertId, "alertType", alertType, "severity", severity,
                    "anomalyScore", anomalyScore, "riskLevel", riskLevel,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process behavioral alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("behavioral-alerts-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBehavioralAlertFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("behavioral-alert-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for behavioral alert: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("behavioral-alerts-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Behavioral Alert Circuit Breaker Triggered",
                String.format("Behavioral alert processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBehavioralAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-behavioral-alert-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Behavioral alert permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String userId = (String) event.get("userId");
            String alertType = (String) event.get("alertType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BEHAVIORAL_ALERT_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "alertId", alertId, "alertType", alertType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Behavioral Alert Dead Letter Event",
                String.format("Behavioral alert %s for user %s sent to DLT: %s", alertId, userId, exceptionMessage),
                Map.of("alertId", alertId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse behavioral alert DLT event: {}", eventJson, ex);
        }
    }

    private void processTransactionAnomaly(String alertId, String userId, Double anomalyScore,
                                         Map<String, Object> behaviorData, List<String> triggeredRules,
                                         String severity, String correlationId) {
        try {
            behavioralAnalysisService.analyzeTransactionAnomaly(userId, alertId, anomalyScore,
                behaviorData, triggeredRules);

            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                threatResponseService.flagSuspiciousTransaction(userId, alertId, anomalyScore,
                    behaviorData, correlationId);
            }

            log.info("Transaction anomaly processed: alertId={}, userId={}, score={}",
                alertId, userId, anomalyScore);

        } catch (Exception e) {
            log.error("Failed to process transaction anomaly: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Transaction anomaly processing failed", e);
        }
    }

    private void processLoginPatternAnomaly(String alertId, String userId, String behaviorPattern,
                                          Double anomalyScore, String deviceId, String ipAddress,
                                          String location, String severity, String correlationId) {
        try {
            behavioralAnalysisService.analyzeLoginPatternAnomaly(userId, alertId, behaviorPattern,
                anomalyScore, deviceId, ipAddress, location);

            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                threatResponseService.flagSuspiciousLogin(userId, deviceId, ipAddress, location,
                    anomalyScore, correlationId);
            }

            log.info("Login pattern anomaly processed: alertId={}, userId={}, pattern={}",
                alertId, userId, behaviorPattern);

        } catch (Exception e) {
            log.error("Failed to process login pattern anomaly: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Login pattern anomaly processing failed", e);
        }
    }

    private void processDeviceBehaviorAnomaly(String alertId, String userId, String deviceId,
                                            Map<String, Object> behaviorData, Double anomalyScore,
                                            List<String> triggeredRules, String severity, String correlationId) {
        try {
            behavioralAnalysisService.analyzeDeviceBehaviorAnomaly(userId, deviceId, alertId,
                behaviorData, anomalyScore, triggeredRules);

            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                threatResponseService.flagSuspiciousDevice(userId, deviceId, anomalyScore,
                    behaviorData, correlationId);
            }

            log.info("Device behavior anomaly processed: alertId={}, userId={}, deviceId={}",
                alertId, userId, deviceId);

        } catch (Exception e) {
            log.error("Failed to process device behavior anomaly: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Device behavior anomaly processing failed", e);
        }
    }

    private void processSpendingPatternAnomaly(String alertId, String userId, Map<String, Object> behaviorData,
                                             Double anomalyScore, List<String> triggeredRules,
                                             String severity, String correlationId) {
        try {
            behavioralAnalysisService.analyzeSpendingPatternAnomaly(userId, alertId, behaviorData,
                anomalyScore, triggeredRules);

            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                threatResponseService.flagSuspiciousSpending(userId, alertId, anomalyScore,
                    behaviorData, correlationId);
            }

            log.info("Spending pattern anomaly processed: alertId={}, userId={}, score={}",
                alertId, userId, anomalyScore);

        } catch (Exception e) {
            log.error("Failed to process spending pattern anomaly: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Spending pattern anomaly processing failed", e);
        }
    }

    private void processTimeBasedAnomaly(String alertId, String userId, String behaviorPattern,
                                       Map<String, Object> behaviorData, Double anomalyScore,
                                       LocalDateTime detectedAt, String severity, String correlationId) {
        try {
            behavioralAnalysisService.analyzeTimeBasedAnomaly(userId, alertId, behaviorPattern,
                behaviorData, anomalyScore, detectedAt);

            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                threatResponseService.flagTimeBasedAnomaly(userId, alertId, behaviorPattern,
                    anomalyScore, detectedAt, correlationId);
            }

            log.info("Time-based anomaly processed: alertId={}, userId={}, pattern={}",
                alertId, userId, behaviorPattern);

        } catch (Exception e) {
            log.error("Failed to process time-based anomaly: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Time-based anomaly processing failed", e);
        }
    }

    private void processGeographicAnomaly(String alertId, String userId, String location,
                                        String ipAddress, Map<String, Object> behaviorData,
                                        Double anomalyScore, String severity, String correlationId) {
        try {
            behavioralAnalysisService.analyzeGeographicAnomaly(userId, alertId, location,
                ipAddress, behaviorData, anomalyScore);

            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                threatResponseService.flagGeographicAnomaly(userId, alertId, location,
                    ipAddress, anomalyScore, correlationId);
            }

            log.info("Geographic anomaly processed: alertId={}, userId={}, location={}",
                alertId, userId, location);

        } catch (Exception e) {
            log.error("Failed to process geographic anomaly: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Geographic anomaly processing failed", e);
        }
    }

    private void processGenericBehavioralAlert(String alertId, String userId, String alertType,
                                             String behaviorPattern, Double anomalyScore,
                                             Map<String, Object> behaviorData, String severity,
                                             String correlationId) {
        try {
            behavioralAnalysisService.analyzeGenericBehavioralAlert(userId, alertId, alertType,
                behaviorPattern, anomalyScore, behaviorData);

            log.info("Generic behavioral alert processed: alertId={}, userId={}, type={}",
                alertId, userId, alertType);

        } catch (Exception e) {
            log.error("Failed to process generic behavioral alert: alertId={}, userId={}",
                alertId, userId, e);
            throw new RuntimeException("Generic behavioral alert processing failed", e);
        }
    }

    private void escalateAlert(String alertId, String userId, String alertType, String severity,
                             String riskLevel, Double anomalyScore, Boolean requiresReview,
                             String correlationId) {
        try {
            if ("CRITICAL".equals(severity) || anomalyScore > 0.9) {
                securityNotificationService.notifySecurityTeam(alertId, userId, alertType,
                    severity, anomalyScore, "IMMEDIATE_REVIEW_REQUIRED");
            }

            if (requiresReview || "HIGH".equals(riskLevel)) {
                threatResponseService.queueForReview(alertId, userId, alertType, severity,
                    anomalyScore, correlationId);
            }

            log.debug("Alert escalation completed: alertId={}, severity={}, riskLevel={}",
                alertId, severity, riskLevel);

        } catch (Exception e) {
            log.error("Failed to escalate alert: alertId={}, userId={}", alertId, userId, e);
            // Don't throw exception as escalation failure shouldn't block processing
        }
    }

    private void updateUserRiskProfile(String userId, String alertType, Double anomalyScore,
                                     String riskLevel, Map<String, Object> behaviorData,
                                     String correlationId) {
        try {
            behavioralAnalysisService.updateUserRiskProfile(userId, alertType, anomalyScore,
                riskLevel, behaviorData);

            log.debug("User risk profile updated: userId={}, alertType={}, score={}",
                userId, alertType, anomalyScore);

        } catch (Exception e) {
            log.error("Failed to update user risk profile: userId={}, alertType={}",
                userId, alertType, e);
            // Don't throw exception as profile update failure shouldn't block processing
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