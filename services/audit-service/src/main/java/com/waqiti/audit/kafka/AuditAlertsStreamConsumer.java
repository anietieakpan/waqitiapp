package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.domain.AuditAlert;
import com.waqiti.audit.domain.AlertSeverity;
import com.waqiti.audit.domain.AlertStatus;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.repository.AuditAlertRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.AuditAlertService;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.audit.service.ComplianceEscalationService;
import com.waqiti.audit.service.SIEMIntegrationService;
import com.waqiti.common.audit.AuditService as CommonAuditService;
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
public class AuditAlertsStreamConsumer {

    private final AuditEventRepository auditEventRepository;
    private final AuditAlertRepository auditAlertRepository;
    private final AuditService auditService;
    private final AuditAlertService auditAlertService;
    private final AuditNotificationService auditNotificationService;
    private final ComplianceEscalationService escalationService;
    private final SIEMIntegrationService siemService;
    private final CommonAuditService commonAuditService;
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
    private Counter streamAlertsCounter;
    private Counter realTimeAlertsCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_alerts_stream_processed_total")
            .description("Total number of successfully processed audit alerts stream events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_alerts_stream_errors_total")
            .description("Total number of audit alerts stream processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_alerts_stream_processing_duration")
            .description("Time taken to process audit alerts stream events")
            .register(meterRegistry);
        streamAlertsCounter = Counter.builder("audit_alerts_stream_events_total")
            .description("Total number of audit alerts stream events received")
            .register(meterRegistry);
        realTimeAlertsCounter = Counter.builder("audit_alerts_realtime_total")
            .description("Total number of real-time audit alerts processed")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit.alerts.stream"},
        groupId = "audit-alerts-stream-processor-group",
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
    @CircuitBreaker(name = "audit-alerts-stream", fallbackMethod = "handleAuditAlertsStreamEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditAlertsStreamEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-stream-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("stream-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit alerts stream event: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String alertType = (String) eventData.get("alertType");
            String severity = (String) eventData.get("severity");
            String serviceName = (String) eventData.get("serviceName");
            String description = (String) eventData.get("description");
            String userId = (String) eventData.get("userId");
            String resourceId = (String) eventData.get("resourceId");
            String resourceType = (String) eventData.get("resourceType");
            String action = (String) eventData.get("action");
            Boolean isRealTime = (Boolean) eventData.getOrDefault("isRealTime", false);
            String alertPattern = (String) eventData.get("alertPattern");

            streamAlertsCounter.increment();
            if (isRealTime) {
                realTimeAlertsCounter.increment();
            }

            // Process streaming audit alert
            processStreamingAuditAlert(eventData, alertType, severity, serviceName, description,
                userId, resourceId, resourceType, action, isRealTime, alertPattern, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_ALERTS_STREAM_PROCESSED", correlationId,
                Map.of("alertType", alertType, "severity", severity, "serviceName", serviceName,
                    "isRealTime", isRealTime, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit alerts stream event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-alerts-stream-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditAlertsStreamEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-stream-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit alerts stream: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-alerts-stream-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Alerts Stream Circuit Breaker Triggered",
                String.format("Audit alerts stream processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditAlertsStreamEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-stream-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit alerts stream permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_ALERTS_STREAM_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Alerts Stream Dead Letter Event",
                String.format("Audit alerts stream sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
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

    private void processStreamingAuditAlert(Map<String, Object> eventData, String alertType,
            String severity, String serviceName, String description, String userId,
            String resourceId, String resourceType, String action, Boolean isRealTime,
            String alertPattern, String correlationId) {

        // Create audit event for the alert stream
        AuditEvent auditEvent = AuditEvent.builder()
            .eventType("AUDIT_ALERT_STREAM")
            .serviceName(serviceName)
            .userId(userId)
            .resourceId(resourceId)
            .resourceType(resourceType)
            .action(action)
            .description(description)
            .result(AuditEvent.AuditResult.SUCCESS)
            .severity(mapSeverity(severity))
            .correlationId(correlationId)
            .metadata(convertToStringMap(eventData))
            .complianceTags("AUDIT_STREAM,REAL_TIME_MONITORING")
            .build();

        auditEventRepository.save(auditEvent);

        // Create audit alert if severity warrants it
        if (!"LOW".equals(severity)) {
            AuditAlert alert = AuditAlert.builder()
                .alertType(alertType)
                .severity(mapAlertSeverity(severity))
                .status(AlertStatus.OPEN)
                .service(serviceName)
                .description(description)
                .userId(userId)
                .resourceId(resourceId)
                .detectedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .metadata(eventData)
                .build();

            auditAlertRepository.save(alert);

            // Process the alert based on severity and real-time nature
            if (isRealTime && ("HIGH".equals(severity) || "CRITICAL".equals(severity))) {
                processRealTimeAlert(alert, correlationId);
            } else {
                auditAlertService.processAuditAlert(alert, correlationId);
            }

            // Pattern-based processing
            if (alertPattern != null) {
                processPatternBasedAlert(alert, alertPattern, correlationId);
            }

            // Send to SIEM for correlation analysis
            siemService.sendAuditAlert(alert, correlationId);

            log.info("Processed streaming audit alert: type={}, severity={}, service={}, realTime={}, correlationId={}",
                alertType, severity, serviceName, isRealTime, correlationId);
        }

        // Send downstream events for stream processing
        kafkaTemplate.send("audit-alert-stream-processed", Map.of(
            "eventId", auditEvent.getId(),
            "alertType", alertType,
            "severity", severity,
            "serviceName", serviceName,
            "userId", userId,
            "resourceId", resourceId,
            "isRealTime", isRealTime,
            "alertPattern", alertPattern != null ? alertPattern : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Real-time streaming analytics
        if (isRealTime) {
            kafkaTemplate.send("audit-realtime-analytics", Map.of(
                "alertType", alertType,
                "severity", severity,
                "serviceName", serviceName,
                "pattern", alertPattern != null ? alertPattern : "",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void processRealTimeAlert(AuditAlert alert, String correlationId) {
        log.warn("Processing real-time audit alert: type={}, severity={}, service={}, correlationId={}",
            alert.getAlertType(), alert.getSeverity(), alert.getService(), correlationId);

        // Immediate escalation for real-time critical alerts
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            escalationService.escalateCriticalAlert(alert, correlationId);
        } else if (alert.getSeverity() == AlertSeverity.HIGH) {
            escalationService.escalateHighSeverityAlert(alert, correlationId);
        }

        // Real-time notification
        auditNotificationService.sendRealTimeAlert(alert, correlationId);

        // Send to real-time processing topic
        kafkaTemplate.send("audit-alerts-realtime", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "severity", alert.getSeverity().toString(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "userId", alert.getUserId(),
            "resourceId", alert.getResourceId(),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processPatternBasedAlert(AuditAlert alert, String alertPattern, String correlationId) {
        log.info("Processing pattern-based audit alert: pattern={}, type={}, correlationId={}",
            alertPattern, alert.getAlertType(), correlationId);

        // Send to pattern analysis service
        kafkaTemplate.send("audit-pattern-analysis", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "pattern", alertPattern,
            "service", alert.getService(),
            "severity", alert.getSeverity().toString(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for pattern-based escalation rules
        if (shouldEscalateBasedOnPattern(alertPattern, alert.getSeverity())) {
            escalationService.escalatePatternBasedAlert(alert, alertPattern, correlationId);
        }
    }

    private boolean shouldEscalateBasedOnPattern(String pattern, AlertSeverity severity) {
        // Define escalation rules based on patterns
        return switch (pattern.toLowerCase()) {
            case "multiple_failed_logins", "suspicious_activity", "data_breach" -> true;
            case "unusual_transaction_pattern" -> severity == AlertSeverity.HIGH || severity == AlertSeverity.CRITICAL;
            case "compliance_violation" -> true;
            default -> severity == AlertSeverity.CRITICAL;
        };
    }

    private AuditEvent.AuditSeverity mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> AuditEvent.AuditSeverity.CRITICAL;
            case "HIGH" -> AuditEvent.AuditSeverity.HIGH;
            case "MEDIUM" -> AuditEvent.AuditSeverity.MEDIUM;
            case "LOW" -> AuditEvent.AuditSeverity.LOW;
            default -> AuditEvent.AuditSeverity.MEDIUM;
        };
    }

    private AlertSeverity mapAlertSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> AlertSeverity.CRITICAL;
            case "HIGH" -> AlertSeverity.HIGH;
            case "MEDIUM" -> AlertSeverity.MEDIUM;
            case "LOW" -> AlertSeverity.LOW;
            default -> AlertSeverity.MEDIUM;
        };
    }

    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return stringMap;
    }
}