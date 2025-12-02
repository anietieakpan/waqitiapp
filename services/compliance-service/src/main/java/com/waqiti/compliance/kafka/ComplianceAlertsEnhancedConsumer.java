package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ComplianceAlertsEvent;
import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.service.ComplianceAlertService;
import com.waqiti.compliance.service.ComplianceWorkflowService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
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
public class ComplianceAlertsEnhancedConsumer {

    private final ComplianceAlertRepository complianceAlertRepository;
    private final ComplianceAlertService alertService;
    private final ComplianceWorkflowService workflowService;
    private final ComplianceMetricsService metricsService;
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
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("compliance_alerts_enhanced_processed_total")
            .description("Total number of successfully processed compliance alerts enhanced events")
            .register(meterRegistry);
        errorCounter = Counter.builder("compliance_alerts_enhanced_errors_total")
            .description("Total number of compliance alerts enhanced processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compliance_alerts_enhanced_processing_duration")
            .description("Time taken to process compliance alerts enhanced events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance-alerts"},
        groupId = "compliance-alerts-enhanced-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "compliance-alerts-enhanced", fallbackMethod = "handleComplianceAlertsEnhancedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleComplianceAlertsEnhancedEvent(
            @Payload ComplianceAlertsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("alert-%s-p%d-o%d", event.getAlertId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAlertId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing compliance alert: alertId={}, type={}, severity={}",
                event.getAlertId(), event.getAlertType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case ALERT_RAISED:
                    processAlertRaised(event, correlationId);
                    break;

                case ALERT_ESCALATED:
                    processAlertEscalated(event, correlationId);
                    break;

                case ALERT_RESOLVED:
                    processAlertResolved(event, correlationId);
                    break;

                case ALERT_DISMISSED:
                    processAlertDismissed(event, correlationId);
                    break;

                case ALERT_ASSIGNED:
                    processAlertAssigned(event, correlationId);
                    break;

                case ALERT_UPDATED:
                    processAlertUpdated(event, correlationId);
                    break;

                case ALERT_EXPIRED:
                    processAlertExpired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown compliance alerts event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPLIANCE_ALERTS_ENHANCED_EVENT_PROCESSED", event.getAlertId(),
                Map.of("eventType", event.getEventType(), "alertType", event.getAlertType(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "priority", event.getPriority(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process compliance alerts enhanced event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleComplianceAlertsEnhancedEventFallback(
            ComplianceAlertsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("alert-fallback-%s-p%d-o%d", event.getAlertId(), partition, offset);

        log.error("Circuit breaker fallback triggered for compliance alerts enhanced: alertId={}, error={}",
            event.getAlertId(), ex.getMessage());

        kafkaTemplate.send("compliance-alerts.DLQ", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Compliance Alerts Circuit Breaker Triggered",
                String.format("Compliance alert %s failed: %s", event.getAlertId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltComplianceAlertsEnhancedEvent(
            @Payload ComplianceAlertsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-alert-%s-%d", event.getAlertId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Compliance alerts enhanced permanently failed: alertId={}, topic={}, error={}",
            event.getAlertId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPLIANCE_ALERTS_ENHANCED_DLT_EVENT", event.getAlertId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Compliance Alerts Dead Letter Event",
                String.format("Compliance alert %s sent to DLT: %s", event.getAlertId(), exceptionMessage),
                Map.of("alertId", event.getAlertId(), "topic", topic, "correlationId", correlationId)
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

    private void processAlertRaised(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = ComplianceAlert.builder()
            .alertId(event.getAlertId())
            .alertType(event.getAlertType())
            .severity(event.getSeverity())
            .priority(event.getPriority())
            .description(event.getDescription())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .status("OPEN")
            .raisedAt(LocalDateTime.now())
            .dueDate(calculateDueDate(event.getSeverity(), event.getPriority()))
            .metadata(event.getMetadata())
            .correlationId(correlationId)
            .build();
        complianceAlertRepository.save(alert);

        alertService.processNewAlert(event.getAlertId(), event.getAlertType(), event.getSeverity());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_ALERT_RAISED",
            "alertId", event.getAlertId(),
            "alertType", event.getAlertType(),
            "severity", event.getSeverity(),
            "priority", event.getPriority(),
            "entityId", event.getEntityId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("CRITICAL".equals(event.getSeverity()) || "HIGH".equals(event.getSeverity())) {
            kafkaTemplate.send("compliance-review-queue", Map.of(
                "eventType", "REVIEW_REQUIRED",
                "alertId", event.getAlertId(),
                "reviewType", "COMPLIANCE_ALERT_REVIEW",
                "priority", event.getPriority(),
                "dueDate", alert.getDueDate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            if ("CRITICAL".equals(event.getSeverity())) {
                notificationService.sendComplianceAlert("CRITICAL Compliance Alert",
                    String.format("CRITICAL compliance alert raised: %s - %s",
                        event.getAlertType(), event.getDescription()),
                    "CRITICAL", correlationId);
            }
        }

        metricsService.recordComplianceAlertRaised(event.getAlertType(), event.getSeverity());

        log.info("Compliance alert raised: alertId={}, type={}, severity={}",
            event.getAlertId(), event.getAlertType(), event.getSeverity());
    }

    private void processAlertEscalated(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = complianceAlertRepository.findByAlertId(event.getAlertId())
            .orElseThrow(() -> new RuntimeException("Compliance alert not found"));

        alert.setStatus("ESCALATED");
        alert.setEscalatedAt(LocalDateTime.now());
        alert.setEscalationReason(event.getEscalationReason());
        alert.setPriority("HIGH");
        complianceAlertRepository.save(alert);

        alertService.escalateAlert(event.getAlertId(), event.getEscalationReason(), event.getEscalatedTo());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COMPLIANCE_ALERT_ESCALATED",
            "alertId", event.getAlertId(),
            "originalAlertType", event.getAlertType(),
            "escalationReason", event.getEscalationReason(),
            "escalatedTo", event.getEscalatedTo(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getEscalatedTo(), "Compliance Alert Escalated",
            String.format("Compliance alert %s has been escalated to you: %s",
                event.getAlertId(), event.getEscalationReason()),
            correlationId);

        metricsService.recordComplianceAlertEscalated(event.getAlertType());

        log.warn("Compliance alert escalated: alertId={}, escalatedTo={}, reason={}",
            event.getAlertId(), event.getEscalatedTo(), event.getEscalationReason());
    }

    private void processAlertResolved(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = complianceAlertRepository.findByAlertId(event.getAlertId())
            .orElseThrow(() -> new RuntimeException("Compliance alert not found"));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(event.getResolvedBy());
        alert.setResolutionNotes(event.getResolutionNotes());
        complianceAlertRepository.save(alert);

        alertService.resolveAlert(event.getAlertId(), event.getResolutionNotes(), event.getResolvedBy());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_ALERT_RESOLVED",
            "alertId", event.getAlertId(),
            "alertType", event.getAlertType(),
            "resolvedBy", event.getResolvedBy(),
            "resolutionTime", calculateResolutionTime(alert.getRaisedAt()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (alert.getAssignedTo() != null) {
            notificationService.sendNotification(alert.getAssignedTo(), "Compliance Alert Resolved",
                String.format("Compliance alert %s has been resolved by %s",
                    event.getAlertId(), event.getResolvedBy()),
                correlationId);
        }

        metricsService.recordComplianceAlertResolved(event.getAlertType(),
            calculateResolutionTime(alert.getRaisedAt()));

        log.info("Compliance alert resolved: alertId={}, resolvedBy={}",
            event.getAlertId(), event.getResolvedBy());
    }

    private void processAlertDismissed(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = complianceAlertRepository.findByAlertId(event.getAlertId())
            .orElseThrow(() -> new RuntimeException("Compliance alert not found"));

        alert.setStatus("DISMISSED");
        alert.setDismissedAt(LocalDateTime.now());
        alert.setDismissedBy(event.getDismissedBy());
        alert.setDismissalReason(event.getDismissalReason());
        complianceAlertRepository.save(alert);

        alertService.dismissAlert(event.getAlertId(), event.getDismissalReason(), event.getDismissedBy());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_ALERT_DISMISSED",
            "alertId", event.getAlertId(),
            "alertType", event.getAlertType(),
            "dismissedBy", event.getDismissedBy(),
            "dismissalReason", event.getDismissalReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("compliance-audit-trail", Map.of(
                "eventType", "HIGH_SEVERITY_ALERT_DISMISSED",
                "alertId", event.getAlertId(),
                "severity", event.getSeverity(),
                "dismissedBy", event.getDismissedBy(),
                "dismissalReason", event.getDismissalReason(),
                "requiresAuditReview", true,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordComplianceAlertDismissed(event.getAlertType());

        log.info("Compliance alert dismissed: alertId={}, dismissedBy={}, reason={}",
            event.getAlertId(), event.getDismissedBy(), event.getDismissalReason());
    }

    private void processAlertAssigned(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = complianceAlertRepository.findByAlertId(event.getAlertId())
            .orElseThrow(() -> new RuntimeException("Compliance alert not found"));

        alert.setAssignedTo(event.getAssignedTo());
        alert.setAssignedAt(LocalDateTime.now());
        alert.setStatus("ASSIGNED");
        complianceAlertRepository.save(alert);

        alertService.assignAlert(event.getAlertId(), event.getAssignedTo());

        notificationService.sendNotification(event.getAssignedTo(), "Compliance Alert Assigned",
            String.format("You have been assigned compliance alert %s: %s",
                event.getAlertId(), event.getDescription()),
            correlationId);

        kafkaTemplate.send("compliance-tasks", Map.of(
            "taskType", "COMPLIANCE_ALERT_REVIEW",
            "alertId", event.getAlertId(),
            "assignedTo", event.getAssignedTo(),
            "priority", event.getPriority(),
            "dueDate", alert.getDueDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordComplianceAlertAssigned();

        log.info("Compliance alert assigned: alertId={}, assignedTo={}",
            event.getAlertId(), event.getAssignedTo());
    }

    private void processAlertUpdated(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = complianceAlertRepository.findByAlertId(event.getAlertId())
            .orElseThrow(() -> new RuntimeException("Compliance alert not found"));

        String oldSeverity = alert.getSeverity();
        String oldPriority = alert.getPriority();

        alert.setSeverity(event.getSeverity());
        alert.setPriority(event.getPriority());
        alert.setDescription(event.getDescription());
        alert.setUpdatedAt(LocalDateTime.now());
        alert.setUpdatedBy(event.getUpdatedBy());
        complianceAlertRepository.save(alert);

        alertService.updateAlert(event.getAlertId(), event.getSeverity(), event.getPriority(), event.getDescription());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_ALERT_UPDATED",
            "alertId", event.getAlertId(),
            "oldSeverity", oldSeverity,
            "newSeverity", event.getSeverity(),
            "oldPriority", oldPriority,
            "newPriority", event.getPriority(),
            "updatedBy", event.getUpdatedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (isSeverityEscalated(oldSeverity, event.getSeverity())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "eventType", "ALERT_ESCALATED",
                "alertId", event.getAlertId(),
                "escalationReason", "Severity increased",
                "oldSeverity", oldSeverity,
                "newSeverity", event.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordComplianceAlertUpdated();

        log.info("Compliance alert updated: alertId={}, oldSeverity={}, newSeverity={}",
            event.getAlertId(), oldSeverity, event.getSeverity());
    }

    private void processAlertExpired(ComplianceAlertsEvent event, String correlationId) {
        ComplianceAlert alert = complianceAlertRepository.findByAlertId(event.getAlertId())
            .orElseThrow(() -> new RuntimeException("Compliance alert not found"));

        alert.setStatus("EXPIRED");
        alert.setExpiredAt(LocalDateTime.now());
        complianceAlertRepository.save(alert);

        alertService.expireAlert(event.getAlertId());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COMPLIANCE_ALERT_EXPIRED",
            "alertId", event.getAlertId(),
            "originalAlertType", event.getAlertType(),
            "severity", event.getSeverity(),
            "priority", "HIGH",
            "escalationRequired", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (alert.getAssignedTo() != null) {
            notificationService.sendNotification(alert.getAssignedTo(), "Compliance Alert Expired",
                String.format("Compliance alert %s has expired without resolution",
                    event.getAlertId()),
                correlationId);
        }

        notificationService.sendOperationalAlert("Compliance Alert Expired",
            String.format("Alert %s expired without resolution - requires attention",
                event.getAlertId()),
            "HIGH");

        metricsService.recordComplianceAlertExpired(event.getAlertType());

        log.warn("Compliance alert expired: alertId={}, type={}",
            event.getAlertId(), event.getAlertType());
    }

    private LocalDateTime calculateDueDate(String severity, String priority) {
        if ("CRITICAL".equals(severity)) {
            return LocalDateTime.now().plusHours(4);
        } else if ("HIGH".equals(severity) || "HIGH".equals(priority)) {
            return LocalDateTime.now().plusHours(24);
        } else if ("MEDIUM".equals(severity)) {
            return LocalDateTime.now().plusDays(3);
        } else {
            return LocalDateTime.now().plusDays(7);
        }
    }

    private long calculateResolutionTime(LocalDateTime raisedAt) {
        return java.time.Duration.between(raisedAt, LocalDateTime.now()).toMinutes();
    }

    private boolean isSeverityEscalated(String oldSeverity, String newSeverity) {
        Map<String, Integer> severityLevels = Map.of(
            "LOW", 1, "MEDIUM", 2, "HIGH", 3, "CRITICAL", 4
        );

        return severityLevels.getOrDefault(newSeverity, 0) >
               severityLevels.getOrDefault(oldSeverity, 0);
    }
}