package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.config.OpenTelemetryTracingConfig.DlqTracingHelper;
import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.RecoverableException;
import com.waqiti.common.model.alert.AlertStatus;
import com.waqiti.common.model.alert.SystemAlertRecoveryResult;
import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.model.incident.IncidentPriority;
import com.waqiti.common.service.DlqEscalationService;
import com.waqiti.common.service.DlqNotificationAdapter;
import com.waqiti.common.service.IdempotencyService;
import com.waqiti.common.service.IncidentManagementService;
import com.waqiti.common.service.SystemAlertsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class SystemAlertsDlqConsumer extends BaseDlqConsumer {

    private final SystemAlertsService systemAlertsService;
    private final IncidentManagementService incidentManagementService;
    private final DlqNotificationAdapter notificationAdapter;
    private final DlqEscalationService escalationService;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    @Autowired(required = false)
    private DlqTracingHelper tracingHelper;

    public SystemAlertsDlqConsumer(
            SystemAlertsService systemAlertsService,
            IncidentManagementService incidentManagementService,
            DlqNotificationAdapter notificationAdapter,
            DlqEscalationService escalationService,
            IdempotencyService idempotencyService,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {

        super(meterRegistry, objectMapper);
        this.systemAlertsService = systemAlertsService;
        this.incidentManagementService = incidentManagementService;
        this.notificationAdapter = notificationAdapter;
        this.escalationService = escalationService;
        this.idempotencyService = idempotencyService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;

        this.processedCounter = Counter.builder("system_alerts_dlq_processed_total")
                .description("Total system alerts DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("system_alerts_dlq_errors_total")
                .description("Total system alerts DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("system_alerts_dlq_duration")
                .description("System alerts DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "system.alerts.dlq",
        groupId = "common-service-system-alerts-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=300000",
            "spring.kafka.consumer.session-timeout-ms=30000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 12000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {RecoverableException.class},
        exclude = {ValidationException.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dlq-notifications", fallbackMethod = "handleSystemAlertsDlqFallback")
    @Retry(name = "dlq-notifications")
    public void handleSystemAlertsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Alert-Type", required = false) String alertType,
            @Header(value = "X-Severity", required = false) String severity,
            @Header(value = "X-Source-Service", required = false) String sourceService,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        // Start distributed trace span
        Span processingSpan = null;
        if (tracingHelper != null) {
            processingSpan = tracingHelper.startDlqProcessingSpan(topic, eventId, correlationId);
        }

        try {
            // Distributed idempotency check
            if (idempotencyService.isAlreadyProcessed(eventId, "SystemAlertsDlq")) {
                log.debug("System alert event already processed (idempotency): eventId={}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing system alerts DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, alertType={}, severity={}, sourceService={}",
                     topic, partition, offset, record.key(), correlationId, alertType, severity, sourceService);

            String alertData = record.value();
            validateSystemAlertData(alertData, eventId);

            // Extract metadata from alert data
            JsonNode alertNode = objectMapper.readTree(alertData);
            String extractedAlertType = alertType != null ? alertType : extractField(alertNode, "alertType", "UNKNOWN");
            String extractedSeverity = severity != null ? severity : extractField(alertNode, "severity", "MEDIUM");
            String extractedSourceService = sourceService != null ? sourceService : extractField(alertNode, "sourceService", "UNKNOWN");

            // Process system alert DLQ with full recovery logic
            SystemAlertRecoveryResult result = systemAlertsService.processSystemAlertsDlq(
                alertData,
                record.key(),
                correlationId,
                extractedAlertType,
                extractedSeverity,
                extractedSourceService,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on alert severity
            if (result.isRecovered()) {
                handleSuccessfulRecovery(result, correlationId);
            } else if (isCriticalSeverity(extractedSeverity)) {
                handleCriticalAlertFailure(result, eventId, correlationId, extractedAlertType, extractedSeverity, extractedSourceService);
            } else {
                handleStandardAlertFailure(result, eventId, correlationId, extractedAlertType, extractedSeverity, extractedSourceService);
            }

            // Mark as processed in distributed idempotency store
            idempotencyService.markAsProcessed(eventId, "SystemAlertsDlq", result);

            processedCounter.increment();
            acknowledgment.acknowledge();

            if (processingSpan != null) {
                tracingHelper.addAttribute(processingSpan, "alert.type", extractedAlertType);
                tracingHelper.addAttribute(processingSpan, "alert.severity", extractedSeverity);
                tracingHelper.addAttribute(processingSpan, "alert.recovered", result.isRecovered());
                tracingHelper.completeSpan(processingSpan);
            }

            log.info("Successfully processed system alerts DLQ: eventId={}, alertType={}, " +
                    "correlationId={}, recovered={}",
                    eventId, extractedAlertType, correlationId, result.isRecovered());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in system alerts DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }

            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();

        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in system alerts DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }
            throw e;

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in system alerts DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }

            handleCriticalFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("System alert sent to DLT - CRITICAL SYSTEM MONITORING FAILURE: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        try {
            // Extract metadata
            JsonNode alertNode = objectMapper.readTree(record.value());
            String alertType = extractField(alertNode, "alertType", "UNKNOWN");
            String severity = extractField(alertNode, "severity", "CRITICAL");
            String sourceService = extractField(alertNode, "sourceService", "UNKNOWN");

            // Create P0 incident for DLT failure
            Incident incident = incidentManagementService.createIncident(
                String.format("[DLT] Permanent System Alert Failure: %s", alertType),
                String.format("System alert permanently failed after all retries.\nTopic: %s\nAlert Type: %s\nSource: %s\nError: %s",
                             topic, alertType, sourceService, exceptionMessage),
                IncidentPriority.P0,
                sourceService,
                "DLT_PERMANENT_FAILURE",
                correlationId
            );

            // Immediate P0 escalation
            escalationService.escalateP0Incident(incident);

            // Send critical alert
            notificationAdapter.sendCriticalAlert(
                String.format("[P0 DLT] Permanent System Alert Failure: %s", alertType),
                String.format("CRITICAL: System alert permanently failed.\nTopic: %s\nAlert: %s\nService: %s\nError: %s\nIncident: %s",
                             topic, alertType, sourceService, exceptionMessage, incident.getId())
            );

        } catch (Exception e) {
            log.error("Failed to process DLT handler: correlationId={}", correlationId, e);
        }

        // Update DLT metrics
        Counter.builder("system_alerts_dlt_critical_events_total")
                .description("Critical system alert events sent to DLT")
                .tag("topic", topic)
                .tag("severity", "critical")
                .tag("system_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleSystemAlertsDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String alertType, String severity, String sourceService,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for system alerts DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        try {
            // Create incident for circuit breaker activation
            incidentManagementService.createIncident(
                "[CIRCUIT BREAKER] System Alerts DLQ",
                String.format("Circuit breaker activated for system alerts DLQ.\nAlert Type: %s\nSeverity: %s\nService: %s\nError: %s",
                             alertType, severity, sourceService, ex.getMessage()),
                IncidentPriority.P1,
                sourceService != null ? sourceService : "system-alerts-dlq",
                "CIRCUIT_BREAKER_OPEN",
                correlationId
            );

            // Send alert notification
            notificationAdapter.sendAlert(
                "[CIRCUIT BREAKER] System Alerts DLQ",
                String.format("Circuit breaker activated. Alert Type: %s, Severity: %s, Error: %s",
                             alertType, severity, ex.getMessage())
            );

        } catch (Exception e) {
            log.error("Failed to process fallback: correlationId={}", correlationId, e);
        }

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("system_alerts_dlq_circuit_breaker_activations_total")
                .tag("severity", "critical")
                .tag("system_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    private void validateSystemAlertData(String alertData, String eventId) {
        if (alertData == null || alertData.trim().isEmpty()) {
            throw new IllegalArgumentException("System alert data is null or empty for eventId: " + eventId);
        }

        if (!alertData.contains("alertType")) {
            throw new IllegalArgumentException("System alert data missing alertType for eventId: " + eventId);
        }

        if (!alertData.contains("severity")) {
            throw new IllegalArgumentException("System alert data missing severity for eventId: " + eventId);
        }

        if (!alertData.contains("sourceService")) {
            throw new IllegalArgumentException("System alert data missing sourceService for eventId: " + eventId);
        }

        // Validate alert criticality requirements
        validateAlertCriticality(alertData, eventId);
    }

    private void validateAlertCriticality(String alertData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(alertData);
            String severity = data.get("severity").asText();
            String alertType = data.get("alertType").asText();

            // Validate critical alert requirements
            if ("CRITICAL".equals(severity) || "EMERGENCY".equals(severity)) {
                if (!data.has("incidentCommand")) {
                    log.warn("Critical alert missing incident command information: eventId={}", eventId);
                }

                if (!data.has("escalationPath")) {
                    log.warn("Critical alert missing escalation path: eventId={}", eventId);
                }
            }

            // Validate system health alerts
            if (alertType.contains("SYSTEM_DOWN") || alertType.contains("SERVICE_UNAVAILABLE")) {
                if (!data.has("affectedServices")) {
                    throw new IllegalArgumentException("System health alert missing affected services for eventId: " + eventId);
                }
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to validate alert criticality: " + e.getMessage());
        }
    }

    private void handleSuccessfulRecovery(SystemAlertRecoveryResult result, String correlationId) {
        log.info("System alert successfully recovered: alertType={}, severity={}, correlationId={}",
                result.getAlertType(), result.getSeverity(), correlationId);

        // Update alert status to resolved
        systemAlertsService.updateAlertStatus(
            result.getAlertId(),
            AlertStatus.RESOLVED,
            "Successfully recovered from DLQ",
            correlationId
        );

        // Send resolution notification
        notificationAdapter.sendAlert(
            String.format("[RESOLVED] %s Alert", result.getAlertType()),
            String.format("Alert %s from %s has been successfully recovered. Severity: %s",
                         result.getAlertType(), result.getSourceService(), result.getSeverity())
        );
    }

    private void handleCriticalAlertFailure(SystemAlertRecoveryResult result, String eventId,
                                           String correlationId, String alertType, String severity,
                                           String sourceService) {
        log.error("Critical system alert recovery failed: alertType={}, severity={}, reason={}, correlationId={}",
                alertType, severity, result.getFailureReason(), correlationId);

        // Create P0 incident for critical alert failure
        Incident incident = incidentManagementService.createIncident(
            String.format("[CRITICAL] System Alert Failure: %s", alertType),
            String.format("Critical system alert failed to recover: %s from %s. Reason: %s",
                         alertType, sourceService, result.getFailureReason()),
            IncidentPriority.P0,
            sourceService,
            "SYSTEM_ALERT_FAILURE",
            correlationId
        );

        // Escalate P0 incident immediately
        escalationService.escalateP0Incident(incident);

        // Send critical notification
        notificationAdapter.sendCriticalAlert(
            String.format("[P0] Critical Alert System Failure: %s", alertType),
            String.format("CRITICAL: Alert %s from %s failed to recover.\nReason: %s\nIncident ID: %s",
                         alertType, sourceService, result.getFailureReason(), incident.getId())
        );

        // Update alert status to escalated
        systemAlertsService.updateAlertStatus(
            result.getAlertId(),
            AlertStatus.ESCALATED,
            "Failed recovery - escalated to P0 incident: " + incident.getId(),
            correlationId
        );
    }

    private void handleStandardAlertFailure(SystemAlertRecoveryResult result, String eventId,
                                           String correlationId, String alertType, String severity,
                                           String sourceService) {
        log.warn("Standard system alert recovery failed: alertType={}, severity={}, reason={}, correlationId={}",
                alertType, severity, result.getFailureReason(), correlationId);

        // Create P2 incident for standard alert failure
        Incident incident = incidentManagementService.createIncident(
            String.format("System Alert Failure: %s", alertType),
            String.format("System alert failed to recover: %s from %s. Reason: %s",
                         alertType, sourceService, result.getFailureReason()),
            IncidentPriority.P2,
            sourceService,
            "SYSTEM_ALERT_FAILURE",
            correlationId
        );

        // Send notification
        notificationAdapter.sendAlert(
            String.format("[ALERT FAILURE] %s", alertType),
            String.format("Alert %s from %s failed to recover.\nReason: %s\nIncident ID: %s",
                         alertType, sourceService, result.getFailureReason(), incident.getId())
        );

        // Update alert status
        systemAlertsService.updateAlertStatus(
            result.getAlertId(),
            AlertStatus.IN_PROGRESS,
            "Failed recovery - created incident: " + incident.getId(),
            correlationId
        );
    }

    private boolean isCriticalSeverity(String severity) {
        return "CRITICAL".equalsIgnoreCase(severity) ||
               "EMERGENCY".equalsIgnoreCase(severity) ||
               "P0".equalsIgnoreCase(severity);
    }

    private String extractField(JsonNode node, String fieldName, String defaultValue) {
        return node.has(fieldName) ? node.get(fieldName).asText() : defaultValue;
    }

    /**
     * PRODUCTION FIX: Check if system alert has critical business impact
     */
    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        try {
            JsonNode messageNode = objectMapper.readTree(originalMessage.toString());
            String severity = extractField(messageNode, "severity", "");
            String alertType = extractField(messageNode, "alertType", "");

            return isCriticalSeverity(severity) ||
                   alertType.contains("SYSTEM_DOWN") ||
                   alertType.contains("SERVICE_UNAVAILABLE") ||
                   alertType.contains("DATA_BREACH");
        } catch (Exception e) {
            log.warn("Failed to determine business impact: {}", e.getMessage());
            return false; // Default to non-critical if unable to parse
        }
    }

    /**
     * PRODUCTION FIX: Get business domain for system alerts
     */
    @Override
    protected String getBusinessDomain() {
        return "SYSTEM_ALERTS";
    }

    /**
     * PRODUCTION FIX: Generate domain-specific alerts for system alerts
     */
    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                               String errorMessage, String correlationId) {
        try {
            log.info("Generating domain-specific alerts for system alerts DLQ: topic={}, correlationId={}",
                    topic, correlationId);

            // Extract alert information from original message
            String alertType = "SYSTEM_ALERT_DLQ";
            String severity = "HIGH";

            if (originalMessage != null) {
                try {
                    JsonNode messageNode = objectMapper.readTree(originalMessage.toString());
                    alertType = extractField(messageNode, "alertType", "SYSTEM_ALERT_DLQ");
                    severity = extractField(messageNode, "severity", "HIGH");
                } catch (Exception e) {
                    log.warn("Failed to parse original message for alert generation: {}", e.getMessage());
                }
            }

            // Send notification about the DLQ event
            notificationAdapter.sendAlert(
                String.format("[DLQ] System Alert Failed: %s", alertType),
                String.format("System alert sent to DLQ.\nTopic: %s\nSeverity: %s\nError: %s\nCorrelation ID: %s",
                             topic, severity, errorMessage, correlationId)
            );

        } catch (Exception e) {
            log.error("Failed to generate domain-specific alerts for system alerts DLQ: correlationId={}",
                    correlationId, e);
        }
    }

    @Override
    protected void processDomainSpecificLogic(Object message, String topic, String errorReason, String correlationId) {
        log.info("Processing system alert DLQ message: topic={}, correlationId={}, error={}",
                topic, correlationId, errorReason);
        // Domain-specific processing for system alerts
        // This method allows subclasses to add custom processing logic
    }

    @Override
    protected String getConsumerName() {
        return "SystemAlertsDlqConsumer";
    }
}