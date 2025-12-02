package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.AlertNotificationService;
import com.waqiti.notification.service.NotificationRoutingService;
import com.waqiti.notification.service.EscalationService;
import com.waqiti.notification.service.AlertCorrelationService;
import com.waqiti.notification.domain.Alert;
import com.waqiti.notification.domain.AlertSeverity;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.common.audit.AuditService;
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
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka consumer for handling alert trigger events
 * Processes various alert trigger events like threshold breaches, rule violations, pattern detections
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AlertTriggersConsumer {

    private final AlertNotificationService alertNotificationService;
    private final NotificationRoutingService routingService;
    private final EscalationService escalationService;
    private final AlertCorrelationService correlationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter escalatedCounter;
    private Counter thresholdBreachCounter;
    private Counter ruleViolationCounter;
    private Counter patternDetectionCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("alert_triggers_processed_total")
            .description("Total number of successfully processed alert trigger events")
            .register(meterRegistry);

        errorCounter = Counter.builder("alert_triggers_errors_total")
            .description("Total number of alert trigger processing errors")
            .register(meterRegistry);

        escalatedCounter = Counter.builder("alert_triggers_escalated_total")
            .description("Total number of escalated alert triggers")
            .register(meterRegistry);

        thresholdBreachCounter = Counter.builder("alert_triggers_threshold_breach_total")
            .description("Total number of threshold breach alerts processed")
            .register(meterRegistry);

        ruleViolationCounter = Counter.builder("alert_triggers_rule_violation_total")
            .description("Total number of rule violation alerts processed")
            .register(meterRegistry);

        patternDetectionCounter = Counter.builder("alert_triggers_pattern_detection_total")
            .description("Total number of pattern detection alerts processed")
            .register(meterRegistry);

        processingTimer = Timer.builder("alert_triggers_processing_duration")
            .description("Time taken to process alert trigger events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"alert-triggers", "threshold-breaches", "rule-violations", "pattern-detections", "anomaly-alerts"},
        groupId = "alert-triggers-notification-processor-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "alert-triggers", fallbackMethod = "handleAlertTriggerEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAlertTriggerEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = String.format("trigger-%s-p%d-o%d-%d", topic, partition, offset, System.currentTimeMillis());
        String correlationId = String.format("alert-trigger-%d", System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventId)) {
                log.info("Alert trigger event already processed, skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing alert trigger event - eventId: {}, topic: {}, partition: {}, offset: {}",
                eventId, topic, partition, offset);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            // Extract common alert trigger fields
            String triggerId = (String) eventData.get("triggerId");
            String triggerType = (String) eventData.get("triggerType");
            String severity = (String) eventData.get("severity");
            String title = (String) eventData.get("title");
            String description = (String) eventData.get("description");
            String source = (String) eventData.get("source");
            String affectedService = (String) eventData.get("affectedService");
            String category = (String) eventData.get("category");
            Boolean requiresAcknowledgment = (Boolean) eventData.getOrDefault("requiresAcknowledgment", false);

            // Alert trigger specific fields
            String ruleId = (String) eventData.get("ruleId");
            String thresholdValue = (String) eventData.get("thresholdValue");
            String currentValue = (String) eventData.get("currentValue");
            String patternType = (String) eventData.get("patternType");
            Map<String, Object> triggerContext = (Map<String, Object>) eventData.getOrDefault("triggerContext", new HashMap<>());

            correlationId = String.format("alert-trigger-%s-%d", triggerId, System.currentTimeMillis());

            log.info("Processing alert trigger - triggerId: {}, type: {}, severity: {}, correlationId: {}",
                triggerId, triggerType, severity, correlationId);

            // Process based on trigger type
            switch (triggerType.toUpperCase()) {
                case "THRESHOLD_BREACH":
                    processThresholdBreach(triggerId, eventData, correlationId);
                    thresholdBreachCounter.increment();
                    break;

                case "RULE_VIOLATION":
                    processRuleViolation(triggerId, eventData, correlationId);
                    ruleViolationCounter.increment();
                    break;

                case "PATTERN_DETECTION":
                    processPatternDetection(triggerId, eventData, correlationId);
                    patternDetectionCounter.increment();
                    break;

                case "ANOMALY_DETECTION":
                    processAnomalyDetection(triggerId, eventData, correlationId);
                    break;

                case "COMPOSITE_TRIGGER":
                    processCompositeTrigger(triggerId, eventData, correlationId);
                    break;

                default:
                    processGenericAlertTrigger(triggerId, eventData, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventId);

            auditService.logNotificationEvent("ALERT_TRIGGER_EVENT_PROCESSED", triggerId,
                Map.of("triggerType", triggerType, "severity", severity,
                    "source", source, "affectedService", affectedService,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process alert trigger event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("alert-trigger-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Process threshold breach alerts
     */
    private void processThresholdBreach(String triggerId, Map<String, Object> eventData, String correlationId) {
        Alert alert = buildAlert(triggerId, eventData, correlationId);

        // Add threshold-specific context
        String thresholdValue = (String) eventData.get("thresholdValue");
        String currentValue = (String) eventData.get("currentValue");
        String metricName = (String) eventData.get("metricName");

        alert.setDescription(String.format("Threshold breach detected: %s exceeded threshold of %s (current: %s). %s",
            metricName, thresholdValue, currentValue, alert.getDescription()));

        processAlert(alert, correlationId);

        // Check for threshold breach patterns
        checkThresholdBreachPattern(alert, eventData, correlationId);
    }

    /**
     * Process rule violation alerts
     */
    private void processRuleViolation(String triggerId, Map<String, Object> eventData, String correlationId) {
        Alert alert = buildAlert(triggerId, eventData, correlationId);

        // Add rule-specific context
        String ruleId = (String) eventData.get("ruleId");
        String ruleName = (String) eventData.get("ruleName");
        String violationType = (String) eventData.get("violationType");

        alert.setTitle(String.format("Rule Violation: %s", ruleName != null ? ruleName : ruleId));
        alert.setDescription(String.format("Rule violation detected: %s (%s). %s",
            ruleName != null ? ruleName : ruleId, violationType, alert.getDescription()));

        processAlert(alert, correlationId);

        // Check for compliance-related escalation
        if (isComplianceRule(ruleId, ruleName)) {
            escalateComplianceViolation(alert, correlationId);
        }
    }

    /**
     * Process pattern detection alerts
     */
    private void processPatternDetection(String triggerId, Map<String, Object> eventData, String correlationId) {
        Alert alert = buildAlert(triggerId, eventData, correlationId);

        // Add pattern-specific context
        String patternType = (String) eventData.get("patternType");
        String confidence = (String) eventData.get("confidence");
        String dataPoints = (String) eventData.get("dataPoints");

        alert.setTitle(String.format("Pattern Detection: %s", patternType));
        alert.setDescription(String.format("Pattern detected: %s (confidence: %s, data points: %s). %s",
            patternType, confidence, dataPoints, alert.getDescription()));

        processAlert(alert, correlationId);

        // Check for fraud patterns
        if (isFraudPattern(patternType)) {
            escalateFraudPattern(alert, correlationId);
        }
    }

    /**
     * Process anomaly detection alerts
     */
    private void processAnomalyDetection(String triggerId, Map<String, Object> eventData, String correlationId) {
        Alert alert = buildAlert(triggerId, eventData, correlationId);

        // Add anomaly-specific context
        String anomalyScore = (String) eventData.get("anomalyScore");
        String baseline = (String) eventData.get("baseline");
        String deviation = (String) eventData.get("deviation");

        alert.setTitle("Anomaly Detection Alert");
        alert.setDescription(String.format("Anomaly detected with score %s (baseline: %s, deviation: %s). %s",
            anomalyScore, baseline, deviation, alert.getDescription()));

        processAlert(alert, correlationId);

        // High anomaly scores may require immediate escalation
        if (isHighAnomalyScore(anomalyScore)) {
            escalationService.escalateAlert(alert, correlationId);
            escalatedCounter.increment();
        }
    }

    /**
     * Process composite trigger alerts (multiple conditions met)
     */
    private void processCompositeTrigger(String triggerId, Map<String, Object> eventData, String correlationId) {
        Alert alert = buildAlert(triggerId, eventData, correlationId);

        // Add composite trigger context
        List<String> triggeredRules = (List<String>) eventData.getOrDefault("triggeredRules", new ArrayList<>());
        String compositeScore = (String) eventData.get("compositeScore");

        alert.setTitle("Composite Alert Trigger");
        alert.setDescription(String.format("Multiple conditions triggered: %s (score: %s). %s",
            String.join(", ", triggeredRules), compositeScore, alert.getDescription()));

        // Composite triggers are typically more serious
        if (alert.getSeverity() == AlertSeverity.MEDIUM) {
            alert.setSeverity(AlertSeverity.HIGH);
        }

        processAlert(alert, correlationId);

        // Composite triggers often require escalation
        escalationService.escalateAlert(alert, correlationId);
        escalatedCounter.increment();
    }

    /**
     * Process generic alert triggers
     */
    private void processGenericAlertTrigger(String triggerId, Map<String, Object> eventData, String correlationId) {
        Alert alert = buildAlert(triggerId, eventData, correlationId);
        processAlert(alert, correlationId);
    }

    /**
     * Main alert processing logic
     */
    private void processAlert(Alert alert, String correlationId) {
        // Determine notification channels based on severity and type
        List<NotificationChannel> notificationChannels = routingService.determineChannels(alert);

        // Apply time-based filtering
        notificationChannels = routingService.filterChannelsByTime(notificationChannels, alert);

        // Send notifications through appropriate channels
        for (NotificationChannel channel : notificationChannels) {
            try {
                alertNotificationService.sendAlert(alert, channel, correlationId);
            } catch (Exception e) {
                log.error("Failed to send alert through channel {}: {}", channel, e.getMessage());
            }
        }

        // Check for correlation with other alerts
        correlationService.checkAlertCorrelation(alert, correlationId);

        // Handle escalation if required
        if (shouldEscalateAlert(alert)) {
            escalationService.escalateAlert(alert, correlationId);
            escalatedCounter.increment();
        }

        // Handle acknowledgment-required alerts
        if (Boolean.TRUE.equals(alert.getRequiresAcknowledgment())) {
            handleAcknowledgmentRequired(alert, correlationId);
        }

        // Publish alert status update
        publishAlertStatusUpdate(alert, notificationChannels, correlationId);

        log.info("Alert trigger processed and notifications sent - triggerId: {}, type: {}, severity: {}, channels: {}, correlationId: {}",
            alert.getId(), alert.getType(), alert.getSeverity(), notificationChannels, correlationId);
    }

    /**
     * Build Alert object from event data
     */
    private Alert buildAlert(String triggerId, Map<String, Object> eventData, String correlationId) {
        return Alert.builder()
            .id(triggerId)
            .type((String) eventData.getOrDefault("triggerType", "UNKNOWN"))
            .severity(AlertSeverity.valueOf((String) eventData.getOrDefault("severity", "MEDIUM")))
            .title((String) eventData.getOrDefault("title", "Alert Trigger"))
            .description((String) eventData.getOrDefault("description", "Alert triggered"))
            .source((String) eventData.getOrDefault("source", "UNKNOWN"))
            .affectedService((String) eventData.get("affectedService"))
            .category((String) eventData.getOrDefault("category", "SYSTEM"))
            .requiresAcknowledgment((Boolean) eventData.getOrDefault("requiresAcknowledgment", false))
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .metadata((Map<String, Object>) eventData.getOrDefault("triggerContext", new HashMap<>()))
            .build();
    }

    /**
     * Check for threshold breach patterns
     */
    private void checkThresholdBreachPattern(Alert alert, Map<String, Object> eventData, String correlationId) {
        // Implementation for detecting repeated threshold breaches
        log.debug("Checking threshold breach patterns for alert: {}", alert.getId());
    }

    /**
     * Check if alert should be escalated
     */
    private boolean shouldEscalateAlert(Alert alert) {
        return alert.getSeverity() == AlertSeverity.CRITICAL ||
               (alert.getSeverity() == AlertSeverity.HIGH && Boolean.TRUE.equals(alert.getRequiresAcknowledgment())) ||
               isCriticalService(alert.getAffectedService());
    }

    /**
     * Handle acknowledgment-required alerts
     */
    private void handleAcknowledgmentRequired(Alert alert, String correlationId) {
        // Send to acknowledgment tracking system
        kafkaTemplate.send("alert-acknowledgment-tracking", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getType(),
            "severity", alert.getSeverity().getLevel(),
            "requiresResponse", true,
            "acknowledgmentTimeout", getAcknowledgmentTimeout(alert.getSeverity()),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));

        // Schedule escalation if not acknowledged within timeout
        if (alert.getSeverity().isCriticalOrHigh()) {
            escalationService.escalateUnacknowledgedAlert(alert, correlationId,
                getAcknowledgmentTimeout(alert.getSeverity()));
        }
    }

    /**
     * Publish alert status update
     */
    private void publishAlertStatusUpdate(Alert alert, List<NotificationChannel> channels, String correlationId) {
        kafkaTemplate.send("alert-status-updates", Map.of(
            "alertId", alert.getId(),
            "status", "TRIGGERED",
            "alertType", alert.getType(),
            "severity", alert.getSeverity().getLevel(),
            "notificationChannels", channels.toString(),
            "eventType", "ALERT_TRIGGERED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    // Helper methods
    private boolean isComplianceRule(String ruleId, String ruleName) {
        return (ruleId != null && ruleId.toLowerCase().contains("compliance")) ||
               (ruleName != null && ruleName.toLowerCase().contains("compliance"));
    }

    private boolean isFraudPattern(String patternType) {
        return patternType != null && patternType.toLowerCase().contains("fraud");
    }

    private boolean isHighAnomalyScore(String anomalyScore) {
        try {
            return Double.parseDouble(anomalyScore) > 0.8;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCriticalService(String serviceName) {
        if (serviceName == null) return false;
        String[] criticalServices = {"payment", "authentication", "fraud", "core-banking"};
        return Arrays.stream(criticalServices)
            .anyMatch(service -> serviceName.toLowerCase().contains(service));
    }

    private long getAcknowledgmentTimeout(AlertSeverity severity) {
        switch (severity) {
            case CRITICAL: return 5; // 5 minutes
            case HIGH: return 15; // 15 minutes
            case MEDIUM: return 30; // 30 minutes
            default: return 60; // 60 minutes
        }
    }

    private void escalateComplianceViolation(Alert alert, String correlationId) {
        log.warn("Escalating compliance violation: {}", alert.getId());
        escalationService.escalateAlert(alert, correlationId + "-compliance");
        escalatedCounter.increment();
    }

    private void escalateFraudPattern(Alert alert, String correlationId) {
        log.warn("Escalating fraud pattern detection: {}", alert.getId());
        escalationService.emergencyEscalation(alert, correlationId + "-fraud");
        escalatedCounter.increment();
    }

    // Idempotency and cleanup methods
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

    // Circuit breaker fallback method
    public void handleAlertTriggerEventFallback(
            String message,
            String topic,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("trigger-fallback-%s-p%d-o%d", topic, partition, offset);

        log.error("Circuit breaker fallback triggered for alert trigger: topic={}, error={}", topic, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("alert-triggers-dlq", Map.of(
            "originalMessage", message,
            "originalTopic", topic,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            alertNotificationService.sendCriticalOperationalAlert(
                "Alert Trigger Circuit Breaker Triggered",
                String.format("Alert trigger processing failed for topic %s: %s", topic, ex.getMessage()),
                Map.of("topic", topic, "error", ex.getMessage(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAlertTriggerEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-trigger-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Alert trigger permanently failed: topic={}, error={}", topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logNotificationEvent("ALERT_TRIGGER_DLT_EVENT", "UNKNOWN",
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "originalMessage", message, "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            alertNotificationService.sendCriticalOperationalAlert(
                "Alert Trigger Dead Letter Event",
                String.format("Alert trigger sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "message", message, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }
}