package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BehavioralAuditService;
import com.waqiti.security.service.SecurityNotificationService;
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
public class BehaviorAnalysisAuditConsumer {

    private final BehavioralAuditService behavioralAuditService;
    private final SecurityNotificationService securityNotificationService;
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
        successCounter = Counter.builder("behavior_analysis_audit_processed_total")
            .description("Total number of successfully processed behavior analysis audit events")
            .register(meterRegistry);
        errorCounter = Counter.builder("behavior_analysis_audit_errors_total")
            .description("Total number of behavior analysis audit processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("behavior_analysis_audit_processing_duration")
            .description("Time taken to process behavior analysis audit events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"behavior-analysis-audit", "behavioral-audit-events", "security-behavior-audit"},
        groupId = "security-service-behavior-analysis-audit-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "behavior-analysis-audit", fallbackMethod = "handleBehaviorAnalysisAuditFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBehaviorAnalysisAudit(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("behavior-audit-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String auditId = (String) event.get("auditId");
            String userId = (String) event.get("userId");
            String auditType = (String) event.get("auditType");
            String action = (String) event.get("action");
            String eventKey = String.format("%s-%s-%s", auditId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing behavior analysis audit: auditId={}, userId={}, type={}, action={}",
                auditId, userId, auditType, action);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String performedBy = (String) event.get("performedBy");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String entityType = (String) event.get("entityType");
            String entityId = (String) event.get("entityId");
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeState = (Map<String, Object>) event.getOrDefault("beforeState", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> afterState = (Map<String, Object>) event.getOrDefault("afterState", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> changeDetails = (Map<String, Object>) event.getOrDefault("changeDetails", Map.of());
            String reason = (String) event.get("reason");
            String ipAddress = (String) event.get("ipAddress");
            String userAgent = (String) event.get("userAgent");
            String sessionId = (String) event.get("sessionId");
            @SuppressWarnings("unchecked")
            Map<String, Object> auditMetadata = (Map<String, Object>) event.getOrDefault("auditMetadata", Map.of());
            String severity = (String) event.getOrDefault("severity", "INFO");
            Boolean requiresReview = (Boolean) event.getOrDefault("requiresReview", false);

            // Process audit event based on type
            switch (auditType) {
                case "BEHAVIORAL_MODEL_UPDATE":
                    processBehavioralModelUpdateAudit(auditId, userId, action, performedBy, timestamp,
                        beforeState, afterState, changeDetails, reason, correlationId);
                    break;

                case "RISK_SCORE_CHANGE":
                    processRiskScoreChangeAudit(auditId, userId, action, performedBy, timestamp,
                        beforeState, afterState, reason, auditMetadata, correlationId);
                    break;

                case "ANALYSIS_CONFIGURATION_CHANGE":
                    processAnalysisConfigurationChangeAudit(auditId, userId, action, performedBy,
                        timestamp, beforeState, afterState, reason, correlationId);
                    break;

                case "BEHAVIORAL_RULE_MODIFICATION":
                    processBehavioralRuleModificationAudit(auditId, userId, action, performedBy,
                        timestamp, beforeState, afterState, changeDetails, reason, correlationId);
                    break;

                case "PATTERN_ANALYSIS_AUDIT":
                    processPatternAnalysisAudit(auditId, userId, action, performedBy, timestamp,
                        auditMetadata, entityType, entityId, correlationId);
                    break;

                case "BEHAVIORAL_THRESHOLD_CHANGE":
                    processBehavioralThresholdChangeAudit(auditId, userId, action, performedBy,
                        timestamp, beforeState, afterState, reason, correlationId);
                    break;

                case "ANOMALY_DETECTION_AUDIT":
                    processAnomalyDetectionAudit(auditId, userId, action, performedBy, timestamp,
                        auditMetadata, severity, correlationId);
                    break;

                case "BEHAVIORAL_PROFILE_ACCESS":
                    processBehavioralProfileAccessAudit(auditId, userId, action, performedBy,
                        timestamp, ipAddress, userAgent, sessionId, reason, correlationId);
                    break;

                default:
                    processGenericBehaviorAnalysisAudit(auditId, userId, auditType, action,
                        performedBy, timestamp, auditMetadata, correlationId);
                    break;
            }

            // Handle high-severity audit events
            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                handleHighSeverityAuditEvent(auditId, userId, auditType, action, performedBy,
                    severity, reason, correlationId);
            }

            // Queue for review if required
            if (requiresReview) {
                queueAuditEventForReview(auditId, userId, auditType, action, performedBy,
                    severity, auditMetadata, correlationId);
            }

            // Store audit trail
            storeAuditTrail(auditId, userId, auditType, action, performedBy, timestamp,
                beforeState, afterState, changeDetails, auditMetadata, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BEHAVIOR_ANALYSIS_AUDIT_PROCESSED", userId,
                Map.of("auditId", auditId, "auditType", auditType, "action", action,
                    "performedBy", performedBy, "severity", severity, "requiresReview", requiresReview,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process behavior analysis audit event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("behavior-analysis-audit-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBehaviorAnalysisAuditFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("behavior-audit-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for behavior analysis audit: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("behavior-analysis-audit-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Behavior Analysis Audit Circuit Breaker Triggered",
                String.format("Behavior analysis audit processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBehaviorAnalysisAudit(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-behavior-audit-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Behavior analysis audit permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String auditId = (String) event.get("auditId");
            String userId = (String) event.get("userId");
            String auditType = (String) event.get("auditType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BEHAVIOR_ANALYSIS_AUDIT_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "auditId", auditId, "auditType", auditType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Behavior Analysis Audit Dead Letter Event",
                String.format("Behavior analysis audit %s for user %s sent to DLT: %s", auditId, userId, exceptionMessage),
                Map.of("auditId", auditId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse behavior analysis audit DLT event: {}", eventJson, ex);
        }
    }

    private void processBehavioralModelUpdateAudit(String auditId, String userId, String action,
                                                 String performedBy, LocalDateTime timestamp,
                                                 Map<String, Object> beforeState, Map<String, Object> afterState,
                                                 Map<String, Object> changeDetails, String reason,
                                                 String correlationId) {
        try {
            behavioralAuditService.processBehavioralModelUpdateAudit(auditId, userId, action,
                performedBy, timestamp, beforeState, afterState, changeDetails, reason);

            log.info("Behavioral model update audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process behavioral model update audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Behavioral model update audit processing failed", e);
        }
    }

    private void processRiskScoreChangeAudit(String auditId, String userId, String action,
                                           String performedBy, LocalDateTime timestamp,
                                           Map<String, Object> beforeState, Map<String, Object> afterState,
                                           String reason, Map<String, Object> auditMetadata,
                                           String correlationId) {
        try {
            behavioralAuditService.processRiskScoreChangeAudit(auditId, userId, action,
                performedBy, timestamp, beforeState, afterState, reason, auditMetadata);

            log.info("Risk score change audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process risk score change audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Risk score change audit processing failed", e);
        }
    }

    private void processAnalysisConfigurationChangeAudit(String auditId, String userId, String action,
                                                       String performedBy, LocalDateTime timestamp,
                                                       Map<String, Object> beforeState,
                                                       Map<String, Object> afterState, String reason,
                                                       String correlationId) {
        try {
            behavioralAuditService.processAnalysisConfigurationChangeAudit(auditId, userId, action,
                performedBy, timestamp, beforeState, afterState, reason);

            log.info("Analysis configuration change audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process analysis configuration change audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Analysis configuration change audit processing failed", e);
        }
    }

    private void processBehavioralRuleModificationAudit(String auditId, String userId, String action,
                                                      String performedBy, LocalDateTime timestamp,
                                                      Map<String, Object> beforeState,
                                                      Map<String, Object> afterState,
                                                      Map<String, Object> changeDetails, String reason,
                                                      String correlationId) {
        try {
            behavioralAuditService.processBehavioralRuleModificationAudit(auditId, userId, action,
                performedBy, timestamp, beforeState, afterState, changeDetails, reason);

            log.info("Behavioral rule modification audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process behavioral rule modification audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Behavioral rule modification audit processing failed", e);
        }
    }

    private void processPatternAnalysisAudit(String auditId, String userId, String action,
                                           String performedBy, LocalDateTime timestamp,
                                           Map<String, Object> auditMetadata, String entityType,
                                           String entityId, String correlationId) {
        try {
            behavioralAuditService.processPatternAnalysisAudit(auditId, userId, action,
                performedBy, timestamp, auditMetadata, entityType, entityId);

            log.info("Pattern analysis audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process pattern analysis audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Pattern analysis audit processing failed", e);
        }
    }

    private void processBehavioralThresholdChangeAudit(String auditId, String userId, String action,
                                                     String performedBy, LocalDateTime timestamp,
                                                     Map<String, Object> beforeState,
                                                     Map<String, Object> afterState, String reason,
                                                     String correlationId) {
        try {
            behavioralAuditService.processBehavioralThresholdChangeAudit(auditId, userId, action,
                performedBy, timestamp, beforeState, afterState, reason);

            log.info("Behavioral threshold change audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process behavioral threshold change audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Behavioral threshold change audit processing failed", e);
        }
    }

    private void processAnomalyDetectionAudit(String auditId, String userId, String action,
                                            String performedBy, LocalDateTime timestamp,
                                            Map<String, Object> auditMetadata, String severity,
                                            String correlationId) {
        try {
            behavioralAuditService.processAnomalyDetectionAudit(auditId, userId, action,
                performedBy, timestamp, auditMetadata, severity);

            log.info("Anomaly detection audit processed: auditId={}, userId={}, severity={}",
                auditId, userId, severity);

        } catch (Exception e) {
            log.error("Failed to process anomaly detection audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Anomaly detection audit processing failed", e);
        }
    }

    private void processBehavioralProfileAccessAudit(String auditId, String userId, String action,
                                                   String performedBy, LocalDateTime timestamp,
                                                   String ipAddress, String userAgent, String sessionId,
                                                   String reason, String correlationId) {
        try {
            behavioralAuditService.processBehavioralProfileAccessAudit(auditId, userId, action,
                performedBy, timestamp, ipAddress, userAgent, sessionId, reason);

            log.info("Behavioral profile access audit processed: auditId={}, userId={}, action={}",
                auditId, userId, action);

        } catch (Exception e) {
            log.error("Failed to process behavioral profile access audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Behavioral profile access audit processing failed", e);
        }
    }

    private void processGenericBehaviorAnalysisAudit(String auditId, String userId, String auditType,
                                                   String action, String performedBy, LocalDateTime timestamp,
                                                   Map<String, Object> auditMetadata, String correlationId) {
        try {
            behavioralAuditService.processGenericBehaviorAnalysisAudit(auditId, userId, auditType,
                action, performedBy, timestamp, auditMetadata);

            log.info("Generic behavior analysis audit processed: auditId={}, userId={}, type={}",
                auditId, userId, auditType);

        } catch (Exception e) {
            log.error("Failed to process generic behavior analysis audit: auditId={}, userId={}",
                auditId, userId, e);
            throw new RuntimeException("Generic behavior analysis audit processing failed", e);
        }
    }

    private void handleHighSeverityAuditEvent(String auditId, String userId, String auditType,
                                            String action, String performedBy, String severity,
                                            String reason, String correlationId) {
        try {
            behavioralAuditService.handleHighSeverityAuditEvent(auditId, userId, auditType,
                action, performedBy, severity, reason);

            // Send high severity notification
            securityNotificationService.sendCriticalAlert(
                "High Severity Behavioral Audit Event",
                String.format("High severity audit event: %s performed by %s on user %s",
                    action, performedBy, userId),
                Map.of("auditId", auditId, "severity", severity, "correlationId", correlationId)
            );

            log.warn("High severity audit event handled: auditId={}, severity={}, action={}",
                auditId, severity, action);

        } catch (Exception e) {
            log.error("Failed to handle high severity audit event: auditId={}, userId={}",
                auditId, userId, e);
            // Don't throw exception as severity handling failure shouldn't block processing
        }
    }

    private void queueAuditEventForReview(String auditId, String userId, String auditType,
                                        String action, String performedBy, String severity,
                                        Map<String, Object> auditMetadata, String correlationId) {
        try {
            behavioralAuditService.queueAuditEventForReview(auditId, userId, auditType,
                action, performedBy, severity, auditMetadata);

            log.info("Audit event queued for review: auditId={}, userId={}, type={}",
                auditId, userId, auditType);

        } catch (Exception e) {
            log.error("Failed to queue audit event for review: auditId={}, userId={}",
                auditId, userId, e);
            // Don't throw exception as review queueing failure shouldn't block processing
        }
    }

    private void storeAuditTrail(String auditId, String userId, String auditType, String action,
                               String performedBy, LocalDateTime timestamp, Map<String, Object> beforeState,
                               Map<String, Object> afterState, Map<String, Object> changeDetails,
                               Map<String, Object> auditMetadata, String correlationId) {
        try {
            behavioralAuditService.storeAuditTrail(auditId, userId, auditType, action,
                performedBy, timestamp, beforeState, afterState, changeDetails, auditMetadata);

            log.debug("Audit trail stored: auditId={}, userId={}, type={}",
                auditId, userId, auditType);

        } catch (Exception e) {
            log.error("Failed to store audit trail: auditId={}, userId={}",
                auditId, userId, e);
            // Don't throw exception as trail storage failure shouldn't block processing
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