package com.waqiti.audit.kafka;

import com.waqiti.common.events.AuditAlertEvent;
import com.waqiti.audit.domain.AuditAlert;
import com.waqiti.audit.repository.AuditAlertRepository;
import com.waqiti.audit.service.AuditAlertService;
import com.waqiti.audit.service.AuditEscalationService;
import com.waqiti.audit.metrics.AuditMetricsService;
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
public class AuditAlertsStreamDlqConsumer {

    private final AuditAlertRepository auditAlertRepository;
    private final AuditAlertService auditAlertService;
    private final AuditEscalationService escalationService;
    private final AuditMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter criticalAlertCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_alerts_dlq_processed_total")
            .description("Total number of successfully processed audit alerts DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_alerts_dlq_errors_total")
            .description("Total number of audit alerts DLQ processing errors")
            .register(meterRegistry);
        criticalAlertCounter = Counter.builder("audit_alerts_critical_total")
            .description("Total number of critical audit alerts requiring executive escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_alerts_dlq_processing_duration")
            .description("Time taken to process audit alerts DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit-alerts-stream-dlq", "audit-critical-violations-dlq", "audit-compliance-failures-dlq"},
        groupId = "audit-alerts-dlq-service-group",
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
    @CircuitBreaker(name = "audit-alerts-dlq", fallbackMethod = "handleAuditAlertDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditAlertDlqEvent(
            @Payload AuditAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-dlq-%s-p%d-o%d", event.getAlertId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAlertId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("DLQ Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL audit alert from DLQ: alertId={}, severity={}, type={}, topic={}",
                event.getAlertId(), event.getSeverity(), event.getAlertType(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ events are critical by nature - require immediate executive escalation
            if (isCriticalAuditAlert(event)) {
                criticalAlertCounter.increment();
                escalateToExecutives(event, correlationId, topic);
            }

            switch (event.getEventType()) {
                case AUDIT_FAILURE:
                    processAuditFailureDlq(event, correlationId, topic);
                    break;

                case COMPLIANCE_VIOLATION:
                    processComplianceViolationDlq(event, correlationId, topic);
                    break;

                case REGULATORY_BREACH:
                    processRegulatoryBreachDlq(event, correlationId, topic);
                    break;

                case SECURITY_INCIDENT:
                    processSecurityIncidentDlq(event, correlationId, topic);
                    break;

                case DATA_INTEGRITY_FAILURE:
                    processDataIntegrityFailureDlq(event, correlationId, topic);
                    break;

                case AUDIT_LOG_CORRUPTION:
                    processAuditLogCorruptionDlq(event, correlationId, topic);
                    break;

                case UNAUTHORIZED_ACCESS:
                    processUnauthorizedAccessDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericAuditAlertDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAuditEvent("AUDIT_ALERT_DLQ_PROCESSED", event.getAlertId(),
                Map.of("eventType", event.getEventType(), "severity", event.getSeverity(),
                    "alertType", event.getAlertType(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit alert DLQ event: {}", e.getMessage(), e);

            // Send to executive escalation for DLQ failures
            sendExecutiveEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditAlertDlqEventFallback(
            AuditAlertEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-dlq-fallback-%s-p%d-o%d", event.getAlertId(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit alert DLQ: alertId={}, topic={}, error={}",
            event.getAlertId(), topic, ex.getMessage());

        // Critical: DLQ circuit breaker means system failure
        sendExecutiveEscalation(event, correlationId, topic, ex);

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditAlertEvent(
            @Payload AuditAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-alert-%s-%d", event.getAlertId(), System.currentTimeMillis());

        log.error("CRITICAL: Audit alert DLQ permanently failed - alertId={}, topic={}, error={}",
            event.getAlertId(), topic, exceptionMessage);

        // Save to audit trail for regulatory compliance
        auditService.logAuditEvent("AUDIT_ALERT_DLQ_DLT_EVENT", event.getAlertId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresImmediateAction", true, "timestamp", Instant.now()));

        // Immediate executive escalation for DLT audit failures
        sendExecutiveEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalAuditAlert(AuditAlertEvent event) {
        return "CRITICAL".equals(event.getSeverity()) ||
               "HIGH".equals(event.getSeverity()) ||
               Arrays.asList("REGULATORY_BREACH", "SECURITY_INCIDENT", "DATA_INTEGRITY_FAILURE",
                           "AUDIT_LOG_CORRUPTION", "UNAUTHORIZED_ACCESS").contains(event.getEventType().toString());
    }

    private void processAuditFailureDlq(AuditAlertEvent event, String correlationId, String topic) {
        AuditAlert alert = AuditAlert.builder()
            .alertId(event.getAlertId())
            .alertType("AUDIT_FAILURE_DLQ")
            .severity("CRITICAL")
            .description(String.format("Audit failure event from DLQ: %s", event.getDescription()))
            .source(topic)
            .correlationId(correlationId)
            .status("REQUIRES_IMMEDIATE_ACTION")
            .createdAt(LocalDateTime.now())
            .build();
        auditAlertRepository.save(alert);

        auditAlertService.initiateImmediateInvestigation(event.getAlertId(), "DLQ_AUDIT_FAILURE");

        log.error("Audit failure DLQ processed: alertId={}", event.getAlertId());
    }

    private void processComplianceViolationDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordComplianceViolation(event.getAlertId(), event.getDescription(), "DLQ_SOURCE");
        escalationService.escalateComplianceViolation(event, correlationId);

        // Notify compliance team immediately
        notificationService.sendComplianceAlert(
            "Critical Compliance Violation from DLQ",
            String.format("Alert %s requires immediate compliance review", event.getAlertId()),
            "CRITICAL"
        );

        log.error("Compliance violation DLQ processed: alertId={}", event.getAlertId());
    }

    private void processRegulatoryBreachDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordRegulatoryBreach(event.getAlertId(), event.getDescription());
        escalationService.escalateRegulatoryBreach(event, correlationId);

        // Immediate regulatory notification
        notificationService.sendRegulatoryAlert(
            "Critical Regulatory Breach from DLQ",
            String.format("Alert %s indicates potential regulatory breach", event.getAlertId()),
            Map.of("alertId", event.getAlertId(), "correlationId", correlationId)
        );

        log.error("Regulatory breach DLQ processed: alertId={}", event.getAlertId());
    }

    private void processSecurityIncidentDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordSecurityIncident(event.getAlertId(), event.getDescription());
        escalationService.escalateSecurityIncident(event, correlationId);

        // Immediate security team notification
        notificationService.sendSecurityAlert(
            "Critical Security Incident from DLQ",
            String.format("Alert %s indicates security incident requiring immediate attention", event.getAlertId()),
            "CRITICAL"
        );

        log.error("Security incident DLQ processed: alertId={}", event.getAlertId());
    }

    private void processDataIntegrityFailureDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordDataIntegrityFailure(event.getAlertId(), event.getDescription());
        escalationService.escalateDataIntegrityIssue(event, correlationId);

        log.error("Data integrity failure DLQ processed: alertId={}", event.getAlertId());
    }

    private void processAuditLogCorruptionDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordAuditLogCorruption(event.getAlertId(), event.getDescription());
        escalationService.escalateAuditLogCorruption(event, correlationId);

        // Critical: audit log corruption affects regulatory compliance
        notificationService.sendCriticalAlert(
            "CRITICAL: Audit Log Corruption Detected",
            String.format("Audit log corruption detected for alert %s - regulatory implications", event.getAlertId()),
            Map.of("alertId", event.getAlertId(), "correlationId", correlationId)
        );

        log.error("Audit log corruption DLQ processed: alertId={}", event.getAlertId());
    }

    private void processUnauthorizedAccessDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordUnauthorizedAccess(event.getAlertId(), event.getDescription());
        escalationService.escalateUnauthorizedAccess(event, correlationId);

        log.error("Unauthorized access DLQ processed: alertId={}", event.getAlertId());
    }

    private void processGenericAuditAlertDlq(AuditAlertEvent event, String correlationId, String topic) {
        auditAlertService.recordGenericAlert(event.getAlertId(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericAuditAlert(event, correlationId);

        log.warn("Generic audit alert DLQ processed: alertId={}, type={}",
            event.getAlertId(), event.getEventType());
    }

    private void escalateToExecutives(AuditAlertEvent event, String correlationId, String topic) {
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL: Audit Alert from DLQ Requires Executive Attention",
                String.format("Critical audit alert %s from DLQ topic %s requires immediate executive review. " +
                    "Type: %s, Severity: %s, Description: %s",
                    event.getAlertId(), topic, event.getEventType(), event.getSeverity(), event.getDescription()),
                Map.of(
                    "alertId", event.getAlertId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "severity", event.getSeverity(),
                    "alertType", event.getAlertType(),
                    "priority", "IMMEDIATE_ACTION_REQUIRED"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send executive escalation: {}", ex.getMessage());
        }
    }

    private void sendExecutiveEscalation(AuditAlertEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendExecutiveAlert(
                "SYSTEM CRITICAL: Audit DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process audit alert %s from DLQ. " +
                    "This indicates a serious system issue requiring immediate executive intervention. " +
                    "Topic: %s, Error: %s", event.getAlertId(), topic, ex.getMessage()),
                Map.of(
                    "alertId", event.getAlertId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send executive escalation for audit DLQ failure: {}", notificationEx.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}