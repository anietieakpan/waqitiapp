package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ComplianceEventsEvent;
import com.waqiti.compliance.domain.ComplianceEvent;
import com.waqiti.compliance.repository.ComplianceEventRepository;
import com.waqiti.compliance.service.ComplianceEventService;
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
public class ComplianceEventsEnhancedConsumer {

    private final ComplianceEventRepository complianceEventRepository;
    private final ComplianceEventService eventService;
    private final ComplianceWorkflowService workflowService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("compliance_events_enhanced_processed_total")
            .description("Total number of successfully processed compliance events enhanced")
            .register(meterRegistry);
        errorCounter = Counter.builder("compliance_events_enhanced_errors_total")
            .description("Total number of compliance events enhanced processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compliance_events_enhanced_processing_duration")
            .description("Time taken to process compliance events enhanced")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance-events"},
        groupId = "compliance-events-enhanced-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "compliance-events-enhanced", fallbackMethod = "handleComplianceEventsEnhancedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleComplianceEventsEnhancedEvent(
            @Payload ComplianceEventsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("comp-event-%s-p%d-o%d", event.getEventId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEventId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing compliance event: eventId={}, type={}, category={}",
                event.getEventId(), event.getEventType(), event.getEventCategory());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case COMPLIANCE_EVENT_CREATED:
                    processComplianceEventCreated(event, correlationId);
                    break;

                case REGULATORY_EVENT:
                    processRegulatoryEvent(event, correlationId);
                    break;

                case POLICY_EVENT:
                    processPolicyEvent(event, correlationId);
                    break;

                case AUDIT_EVENT:
                    processAuditEvent(event, correlationId);
                    break;

                case RISK_EVENT:
                    processRiskEvent(event, correlationId);
                    break;

                case MONITORING_EVENT:
                    processMonitoringEvent(event, correlationId);
                    break;

                case WORKFLOW_EVENT:
                    processWorkflowEvent(event, correlationId);
                    break;

                case SYSTEM_EVENT:
                    processSystemEvent(event, correlationId);
                    break;

                default:
                    log.warn("Unknown compliance events event type: {}", event.getEventType());
                    processComplianceEventCreated(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPLIANCE_EVENTS_ENHANCED_EVENT_PROCESSED", event.getEventId(),
                Map.of("eventType", event.getEventType(), "eventCategory", event.getEventCategory(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "entityId", event.getEntityId(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process compliance events enhanced event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleComplianceEventsEnhancedEventFallback(
            ComplianceEventsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("comp-event-fallback-%s-p%d-o%d", event.getEventId(), partition, offset);

        log.error("Circuit breaker fallback triggered for compliance events enhanced: eventId={}, error={}",
            event.getEventId(), ex.getMessage());

        kafkaTemplate.send("compliance-events.DLQ", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Compliance Events Circuit Breaker Triggered",
                String.format("Compliance event %s failed: %s", event.getEventId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltComplianceEventsEnhancedEvent(
            @Payload ComplianceEventsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-comp-event-%s-%d", event.getEventId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Compliance events enhanced permanently failed: eventId={}, topic={}, error={}",
            event.getEventId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPLIANCE_EVENTS_ENHANCED_DLT_EVENT", event.getEventId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Compliance Events Dead Letter Event",
                String.format("Compliance event %s sent to DLT: %s", event.getEventId(), exceptionMessage),
                Map.of("eventId", event.getEventId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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

    private void processComplianceEventCreated(ComplianceEventsEvent event, String correlationId) {
        ComplianceEvent complianceEvent = ComplianceEvent.builder()
            .eventId(event.getEventId())
            .eventType(event.getEventType())
            .eventCategory(event.getEventCategory())
            .severity(event.getSeverity())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .description(event.getDescription())
            .metadata(event.getMetadata())
            .createdAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .build();
        complianceEventRepository.save(complianceEvent);

        eventService.processEvent(event.getEventId(), event.getEventCategory());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_EVENT_PROCESSED",
            "eventId", event.getEventId(),
            "eventCategory", event.getEventCategory(),
            "severity", event.getSeverity(),
            "entityId", event.getEntityId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "COMPLIANCE_EVENT_HIGH_SEVERITY",
                "eventId", event.getEventId(),
                "severity", event.getSeverity(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordComplianceEvent(event.getEventCategory(), event.getSeverity());

        log.info("Compliance event created: eventId={}, category={}, severity={}",
            event.getEventId(), event.getEventCategory(), event.getSeverity());
    }

    private void processRegulatoryEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processRegulatoryEvent(event.getEventId(), event.getRegulatoryFramework());

        kafkaTemplate.send("regulatory-reporting", Map.of(
            "eventType", "REGULATORY_EVENT_PROCESSED",
            "eventId", event.getEventId(),
            "regulatoryFramework", event.getRegulatoryFramework(),
            "severity", event.getSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "CRITICAL_REGULATORY_EVENT",
                "eventId", event.getEventId(),
                "regulatoryFramework", event.getRegulatoryFramework(),
                "priority", "CRITICAL",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendRegulatoryAlert("Critical Regulatory Event",
                String.format("Critical regulatory event detected: %s under %s framework",
                    event.getDescription(), event.getRegulatoryFramework()),
                Map.of("eventId", event.getEventId(), "framework", event.getRegulatoryFramework()));
        }

        metricsService.recordRegulatoryEvent(event.getRegulatoryFramework());

        log.info("Regulatory event processed: eventId={}, framework={}",
            event.getEventId(), event.getRegulatoryFramework());
    }

    private void processPolicyEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processPolicyEvent(event.getEventId(), event.getPolicyId());

        kafkaTemplate.send("compliance-policies", Map.of(
            "eventType", "POLICY_EVENT_PROCESSED",
            "eventId", event.getEventId(),
            "policyId", event.getPolicyId(),
            "policyAction", event.getPolicyAction(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("POLICY_VIOLATION".equals(event.getPolicyAction())) {
            kafkaTemplate.send("compliance-violations", Map.of(
                "eventType", "VIOLATION_DETECTED",
                "violationId", UUID.randomUUID().toString(),
                "violationType", "POLICY_VIOLATION",
                "policyId", event.getPolicyId(),
                "entityId", event.getEntityId(),
                "severity", event.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPolicyEvent(event.getPolicyAction());

        log.info("Policy event processed: eventId={}, policyId={}, action={}",
            event.getEventId(), event.getPolicyId(), event.getPolicyAction());
    }

    private void processAuditEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processAuditEvent(event.getEventId(), event.getAuditType());

        kafkaTemplate.send("compliance-audit-trail", Map.of(
            "eventType", "AUDIT_EVENT_LOGGED",
            "eventId", event.getEventId(),
            "auditType", event.getAuditType(),
            "entityId", event.getEntityId(),
            "auditDetails", event.getAuditDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("COMPLIANCE_BREACH".equals(event.getAuditType())) {
            kafkaTemplate.send("compliance-incidents", Map.of(
                "incidentType", "AUDIT_COMPLIANCE_BREACH",
                "eventId", event.getEventId(),
                "severity", event.getSeverity(),
                "auditDetails", event.getAuditDetails(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordAuditEvent(event.getAuditType());

        log.info("Audit event processed: eventId={}, auditType={}",
            event.getEventId(), event.getAuditType());
    }

    private void processRiskEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processRiskEvent(event.getEventId(), event.getRiskCategory(), event.getRiskLevel());

        kafkaTemplate.send("risk-management", Map.of(
            "eventType", "COMPLIANCE_RISK_EVENT",
            "eventId", event.getEventId(),
            "riskCategory", event.getRiskCategory(),
            "riskLevel", event.getRiskLevel(),
            "entityId", event.getEntityId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("HIGH".equals(event.getRiskLevel()) || "CRITICAL".equals(event.getRiskLevel())) {
            kafkaTemplate.send("compliance-review-queue", Map.of(
                "eventType", "REVIEW_REQUIRED",
                "eventId", event.getEventId(),
                "reviewType", "RISK_ASSESSMENT",
                "riskLevel", event.getRiskLevel(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskEvent(event.getRiskCategory(), event.getRiskLevel());

        log.info("Risk event processed: eventId={}, category={}, level={}",
            event.getEventId(), event.getRiskCategory(), event.getRiskLevel());
    }

    private void processMonitoringEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processMonitoringEvent(event.getEventId(), event.getMonitoringType());

        kafkaTemplate.send("compliance-monitoring", Map.of(
            "eventType", "MONITORING_EVENT_PROCESSED",
            "eventId", event.getEventId(),
            "monitoringType", event.getMonitoringType(),
            "monitoringResult", event.getMonitoringResult(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("ANOMALY_DETECTED".equals(event.getMonitoringResult())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "MONITORING_ANOMALY",
                "eventId", event.getEventId(),
                "monitoringType", event.getMonitoringType(),
                "priority", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordMonitoringEvent(event.getMonitoringType());

        log.info("Monitoring event processed: eventId={}, type={}, result={}",
            event.getEventId(), event.getMonitoringType(), event.getMonitoringResult());
    }

    private void processWorkflowEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processWorkflowEvent(event.getEventId(), event.getWorkflowStep());
        workflowService.processWorkflowEvent(event.getEventId(), event.getWorkflowStep());

        kafkaTemplate.send("compliance-workflow", Map.of(
            "eventType", "WORKFLOW_EVENT_PROCESSED",
            "eventId", event.getEventId(),
            "workflowStep", event.getWorkflowStep(),
            "workflowStatus", event.getWorkflowStatus(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("FAILED".equals(event.getWorkflowStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "WORKFLOW_FAILURE",
                "eventId", event.getEventId(),
                "workflowStep", event.getWorkflowStep(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordWorkflowEvent(event.getWorkflowStep());

        log.info("Workflow event processed: eventId={}, step={}, status={}",
            event.getEventId(), event.getWorkflowStep(), event.getWorkflowStatus());
    }

    private void processSystemEvent(ComplianceEventsEvent event, String correlationId) {
        eventService.processSystemEvent(event.getEventId(), event.getSystemComponent());

        kafkaTemplate.send("system-monitoring", Map.of(
            "eventType", "COMPLIANCE_SYSTEM_EVENT",
            "eventId", event.getEventId(),
            "systemComponent", event.getSystemComponent(),
            "systemStatus", event.getSystemStatus(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("ERROR".equals(event.getSystemStatus()) || "CRITICAL".equals(event.getSystemStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "SYSTEM_COMPLIANCE_ISSUE",
                "eventId", event.getEventId(),
                "systemComponent", event.getSystemComponent(),
                "systemStatus", event.getSystemStatus(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendSystemAlert("Compliance System Issue",
                String.format("System component %s has compliance status: %s",
                    event.getSystemComponent(), event.getSystemStatus()),
                "HIGH");
        }

        metricsService.recordSystemEvent(event.getSystemComponent());

        log.info("System event processed: eventId={}, component={}, status={}",
            event.getEventId(), event.getSystemComponent(), event.getSystemStatus());
    }
}