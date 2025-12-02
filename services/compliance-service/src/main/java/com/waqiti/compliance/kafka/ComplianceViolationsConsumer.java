package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ComplianceViolationsEvent;
import com.waqiti.compliance.domain.ComplianceViolation;
import com.waqiti.compliance.repository.ComplianceViolationRepository;
import com.waqiti.compliance.service.ComplianceViolationService;
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
public class ComplianceViolationsConsumer {

    private final ComplianceViolationRepository violationRepository;
    private final ComplianceViolationService violationService;
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
        successCounter = Counter.builder("compliance_violations_processed_total")
            .description("Total number of successfully processed compliance violations events")
            .register(meterRegistry);
        errorCounter = Counter.builder("compliance_violations_errors_total")
            .description("Total number of compliance violations processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compliance_violations_processing_duration")
            .description("Time taken to process compliance violations events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance-violations"},
        groupId = "compliance-violations-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "compliance-violations", fallbackMethod = "handleComplianceViolationsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleComplianceViolationsEvent(
            @Payload ComplianceViolationsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("violation-%s-p%d-o%d", event.getViolationId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getViolationId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing compliance violation: violationId={}, type={}, severity={}",
                event.getViolationId(), event.getViolationType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case VIOLATION_DETECTED:
                    processViolationDetected(event, correlationId);
                    break;

                case VIOLATION_UPDATED:
                    processViolationUpdated(event, correlationId);
                    break;

                case VIOLATION_RESOLVED:
                    processViolationResolved(event, correlationId);
                    break;

                case VIOLATION_ESCALATED:
                    processViolationEscalated(event, correlationId);
                    break;

                case VIOLATION_ASSIGNED:
                    processViolationAssigned(event, correlationId);
                    break;

                case VIOLATION_CLOSED:
                    processViolationClosed(event, correlationId);
                    break;

                default:
                    log.warn("Unknown compliance violations event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPLIANCE_VIOLATIONS_EVENT_PROCESSED", event.getViolationId(),
                Map.of("eventType", event.getEventType(), "violationType", event.getViolationType(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "riskLevel", event.getRiskLevel(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process compliance violations event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-violations-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleComplianceViolationsEventFallback(
            ComplianceViolationsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("violation-fallback-%s-p%d-o%d", event.getViolationId(), partition, offset);

        log.error("Circuit breaker fallback triggered for compliance violations: violationId={}, error={}",
            event.getViolationId(), ex.getMessage());

        kafkaTemplate.send("compliance-violations-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Compliance Violations Circuit Breaker Triggered",
                String.format("Compliance violation %s failed: %s", event.getViolationId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltComplianceViolationsEvent(
            @Payload ComplianceViolationsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-violation-%s-%d", event.getViolationId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Compliance violations permanently failed: violationId={}, topic={}, error={}",
            event.getViolationId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPLIANCE_VIOLATIONS_DLT_EVENT", event.getViolationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Compliance Violations Dead Letter Event",
                String.format("Compliance violation %s sent to DLT: %s", event.getViolationId(), exceptionMessage),
                Map.of("violationId", event.getViolationId(), "topic", topic, "correlationId", correlationId)
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

    private void processViolationDetected(ComplianceViolationsEvent event, String correlationId) {
        ComplianceViolation violation = ComplianceViolation.builder()
            .violationId(event.getViolationId())
            .violationType(event.getViolationType())
            .severity(event.getSeverity())
            .riskLevel(event.getRiskLevel())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .description(event.getDescription())
            .detectedAt(LocalDateTime.now())
            .status("OPEN")
            .priority(determinePriority(event.getSeverity(), event.getRiskLevel()))
            .dueDate(calculateDueDate(event.getSeverity()))
            .correlationId(correlationId)
            .build();
        violationRepository.save(violation);

        violationService.processViolation(event.getViolationId(), event.getViolationType());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_VIOLATION_DETECTED",
            "violationId", event.getViolationId(),
            "violationType", event.getViolationType(),
            "severity", event.getSeverity(),
            "riskLevel", event.getRiskLevel(),
            "entityId", event.getEntityId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("compliance-review-queue", Map.of(
                "eventType", "REVIEW_REQUIRED",
                "violationId", event.getViolationId(),
                "reviewType", "VIOLATION_REVIEW",
                "priority", violation.getPriority(),
                "dueDate", violation.getDueDate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        if ("CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("compliance-critical-violations", Map.of(
                "eventType", "CRITICAL_VIOLATION_DETECTED",
                "violationId", event.getViolationId(),
                "violationType", event.getViolationType(),
                "severity", "CRITICAL",
                "riskLevel", event.getRiskLevel(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordComplianceViolation(event.getViolationType(), event.getSeverity());

        log.info("Compliance violation detected: violationId={}, type={}, severity={}",
            event.getViolationId(), event.getViolationType(), event.getSeverity());
    }

    private void processViolationUpdated(ComplianceViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        String oldSeverity = violation.getSeverity();
        String oldRiskLevel = violation.getRiskLevel();

        violation.setSeverity(event.getSeverity());
        violation.setRiskLevel(event.getRiskLevel());
        violation.setDescription(event.getDescription());
        violation.setUpdatedAt(LocalDateTime.now());
        violation.setUpdatedBy(event.getUpdatedBy());
        violationRepository.save(violation);

        violationService.updateViolation(event.getViolationId(), event.getSeverity(), event.getRiskLevel());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_VIOLATION_UPDATED",
            "violationId", event.getViolationId(),
            "oldSeverity", oldSeverity,
            "newSeverity", event.getSeverity(),
            "oldRiskLevel", oldRiskLevel,
            "newRiskLevel", event.getRiskLevel(),
            "updatedBy", event.getUpdatedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (isSeverityEscalated(oldSeverity, event.getSeverity())) {
            kafkaTemplate.send("compliance-violations", Map.of(
                "eventType", "VIOLATION_ESCALATED",
                "violationId", event.getViolationId(),
                "escalationReason", "Severity increased",
                "oldSeverity", oldSeverity,
                "newSeverity", event.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordViolationUpdated();

        log.info("Compliance violation updated: violationId={}, severity: {} -> {}",
            event.getViolationId(), oldSeverity, event.getSeverity());
    }

    private void processViolationResolved(ComplianceViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        violation.setStatus("RESOLVED");
        violation.setResolvedAt(LocalDateTime.now());
        violation.setResolvedBy(event.getResolvedBy());
        violation.setResolutionNotes(event.getResolutionNotes());
        violationRepository.save(violation);

        violationService.resolveViolation(event.getViolationId(), event.getResolutionNotes(), event.getResolvedBy());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_VIOLATION_RESOLVED",
            "violationId", event.getViolationId(),
            "violationType", event.getViolationType(),
            "resolvedBy", event.getResolvedBy(),
            "resolutionTime", calculateResolutionTime(violation.getDetectedAt()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (violation.getAssignedTo() != null) {
            notificationService.sendNotification(violation.getAssignedTo(), "Compliance Violation Resolved",
                String.format("Compliance violation %s has been resolved by %s",
                    event.getViolationId(), event.getResolvedBy()),
                correlationId);
        }

        metricsService.recordViolationResolved(event.getViolationType(),
            calculateResolutionTime(violation.getDetectedAt()));

        log.info("Compliance violation resolved: violationId={}, resolvedBy={}",
            event.getViolationId(), event.getResolvedBy());
    }

    private void processViolationEscalated(ComplianceViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        violation.setStatus("ESCALATED");
        violation.setEscalatedAt(LocalDateTime.now());
        violation.setEscalationReason(event.getEscalationReason());
        violation.setPriority("HIGH");
        violationRepository.save(violation);

        violationService.escalateViolation(event.getViolationId(), event.getEscalationReason(), event.getEscalatedTo());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COMPLIANCE_VIOLATION_ESCALATED",
            "violationId", event.getViolationId(),
            "violationType", event.getViolationType(),
            "escalationReason", event.getEscalationReason(),
            "escalatedTo", event.getEscalatedTo(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getEscalatedTo(), "Compliance Violation Escalated",
            String.format("Compliance violation %s has been escalated to you: %s",
                event.getViolationId(), event.getEscalationReason()),
            correlationId);

        metricsService.recordViolationEscalated();

        log.warn("Compliance violation escalated: violationId={}, escalatedTo={}, reason={}",
            event.getViolationId(), event.getEscalatedTo(), event.getEscalationReason());
    }

    private void processViolationAssigned(ComplianceViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        violation.setAssignedTo(event.getAssignedTo());
        violation.setAssignedAt(LocalDateTime.now());
        violation.setStatus("ASSIGNED");
        violationRepository.save(violation);

        violationService.assignViolation(event.getViolationId(), event.getAssignedTo());

        notificationService.sendNotification(event.getAssignedTo(), "Compliance Violation Assigned",
            String.format("You have been assigned compliance violation %s: %s",
                event.getViolationId(), event.getDescription()),
            correlationId);

        kafkaTemplate.send("compliance-tasks", Map.of(
            "taskType", "COMPLIANCE_VIOLATION_REVIEW",
            "violationId", event.getViolationId(),
            "assignedTo", event.getAssignedTo(),
            "priority", violation.getPriority(),
            "dueDate", violation.getDueDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordViolationAssigned();

        log.info("Compliance violation assigned: violationId={}, assignedTo={}",
            event.getViolationId(), event.getAssignedTo());
    }

    private void processViolationClosed(ComplianceViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        violation.setStatus("CLOSED");
        violation.setClosedAt(LocalDateTime.now());
        violation.setClosedBy(event.getClosedBy());
        violation.setClosureReason(event.getClosureReason());
        violationRepository.save(violation);

        violationService.closeViolation(event.getViolationId(), event.getClosureReason(), event.getClosedBy());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPLIANCE_VIOLATION_CLOSED",
            "violationId", event.getViolationId(),
            "violationType", event.getViolationType(),
            "closedBy", event.getClosedBy(),
            "closureReason", event.getClosureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordViolationClosed(event.getViolationType());

        log.info("Compliance violation closed: violationId={}, closedBy={}, reason={}",
            event.getViolationId(), event.getClosedBy(), event.getClosureReason());
    }

    private String determinePriority(String severity, String riskLevel) {
        if ("CRITICAL".equals(severity) || "CRITICAL".equals(riskLevel)) {
            return "CRITICAL";
        } else if ("HIGH".equals(severity) || "HIGH".equals(riskLevel)) {
            return "HIGH";
        } else if ("MEDIUM".equals(severity) || "MEDIUM".equals(riskLevel)) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private LocalDateTime calculateDueDate(String severity) {
        switch (severity) {
            case "CRITICAL":
                return LocalDateTime.now().plusHours(4);
            case "HIGH":
                return LocalDateTime.now().plusDays(1);
            case "MEDIUM":
                return LocalDateTime.now().plusDays(3);
            case "LOW":
                return LocalDateTime.now().plusDays(7);
            default:
                return LocalDateTime.now().plusDays(5);
        }
    }

    private long calculateResolutionTime(LocalDateTime detectedAt) {
        return java.time.Duration.between(detectedAt, LocalDateTime.now()).toMinutes();
    }

    private boolean isSeverityEscalated(String oldSeverity, String newSeverity) {
        Map<String, Integer> severityLevels = Map.of(
            "LOW", 1, "MEDIUM", 2, "HIGH", 3, "CRITICAL", 4
        );
        return severityLevels.getOrDefault(newSeverity, 0) >
               severityLevels.getOrDefault(oldSeverity, 0);
    }
}