package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ComplianceCriticalViolationsEvent;
import com.waqiti.compliance.domain.ComplianceViolation;
import com.waqiti.compliance.repository.ComplianceViolationRepository;
import com.waqiti.compliance.service.ComplianceViolationService;
import com.waqiti.compliance.service.ComplianceEscalationService;
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
public class ComplianceCriticalViolationsConsumer {

    private final ComplianceViolationRepository violationRepository;
    private final ComplianceViolationService violationService;
    private final ComplianceEscalationService escalationService;
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
        successCounter = Counter.builder("compliance_critical_violations_processed_total")
            .description("Total number of successfully processed compliance critical violations events")
            .register(meterRegistry);
        errorCounter = Counter.builder("compliance_critical_violations_errors_total")
            .description("Total number of compliance critical violations processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compliance_critical_violations_processing_duration")
            .description("Time taken to process compliance critical violations events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance-critical-violations"},
        groupId = "compliance-critical-violations-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 1.5, maxDelay = 5000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "compliance-critical-violations", fallbackMethod = "handleComplianceCriticalViolationsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500, multiplier = 1.5, maxDelay = 5000))
    public void handleComplianceCriticalViolationsEvent(
            @Payload ComplianceCriticalViolationsEvent event,
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

            log.error("Processing CRITICAL compliance violation: violationId={}, type={}, severity={}",
                event.getViolationId(), event.getViolationType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CRITICAL_VIOLATION_DETECTED:
                    processCriticalViolationDetected(event, correlationId);
                    break;

                case REGULATORY_BREACH:
                    processRegulatoryBreach(event, correlationId);
                    break;

                case POLICY_VIOLATION_CRITICAL:
                    processPolicyViolationCritical(event, correlationId);
                    break;

                case COMPLIANCE_THRESHOLD_EXCEEDED:
                    processComplianceThresholdExceeded(event, correlationId);
                    break;

                case VIOLATION_ESCALATED:
                    processViolationEscalated(event, correlationId);
                    break;

                case VIOLATION_RESOLVED:
                    processViolationResolved(event, correlationId);
                    break;

                default:
                    log.error("Unknown compliance critical violations event type: {}", event.getEventType());
                    processCriticalViolationDetected(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPLIANCE_CRITICAL_VIOLATIONS_EVENT_PROCESSED", event.getViolationId(),
                Map.of("eventType", event.getEventType(), "violationType", event.getViolationType(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "riskLevel", event.getRiskLevel(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("CRITICAL FAILURE: Failed to process compliance critical violations event: {}", e.getMessage(), e);

            try {
                notificationService.sendEmergencyAlert(
                    "CRITICAL: Compliance Violation Processing Failure",
                    String.format("URGENT: Failed to process critical violation %s: %s", event.getViolationId(), e.getMessage()),
                    Map.of("violationId", event.getViolationId(), "error", e.getMessage())
                );
            } catch (Exception notificationEx) {
                log.error("EMERGENCY: Failed to send critical violation failure notification: {}", notificationEx.getMessage());
            }

            kafkaTemplate.send("compliance-critical-violations-emergency", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "emergencyLevel", "CRITICAL", "retryCount", 0, "maxRetries", 1));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleComplianceCriticalViolationsEventFallback(
            ComplianceCriticalViolationsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("violation-fallback-%s-p%d-o%d", event.getViolationId(), partition, offset);

        log.error("CRITICAL: Circuit breaker fallback triggered for compliance critical violations: violationId={}, error={}",
            event.getViolationId(), ex.getMessage());

        kafkaTemplate.send("compliance-critical-violations-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "emergencyLevel", "CRITICAL",
            "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "CRITICAL: Compliance Violation Circuit Breaker Triggered",
                String.format("EMERGENCY: Critical violation %s circuit breaker activated: %s",
                    event.getViolationId(), ex.getMessage()),
                Map.of("violationId", event.getViolationId(), "violationType", event.getViolationType())
            );
        } catch (Exception notificationEx) {
            log.error("EMERGENCY: Failed to send critical violation circuit breaker alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltComplianceCriticalViolationsEvent(
            @Payload ComplianceCriticalViolationsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-violation-%s-%d", event.getViolationId(), System.currentTimeMillis());

        log.error("EMERGENCY: Dead letter topic handler - Compliance critical violations permanently failed: violationId={}, topic={}, error={}",
            event.getViolationId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPLIANCE_CRITICAL_VIOLATIONS_DLT_EVENT", event.getViolationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "emergencyLevel", "CRITICAL", "requiresExecutiveIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "EMERGENCY: Critical Compliance Violation Dead Letter Event",
                String.format("CRITICAL FAILURE: Violation %s sent to DLT and requires immediate executive intervention: %s",
                    event.getViolationId(), exceptionMessage),
                Map.of("violationId", event.getViolationId(), "topic", topic, "correlationId", correlationId,
                    "violationType", event.getViolationType(), "requiresExecutiveEscalation", true)
            );
        } catch (Exception ex) {
            log.error("CATASTROPHIC: Failed to send emergency violation DLT alert: {}", ex.getMessage());
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

    private void processCriticalViolationDetected(ComplianceCriticalViolationsEvent event, String correlationId) {
        ComplianceViolation violation = ComplianceViolation.builder()
            .violationId(event.getViolationId())
            .violationType(event.getViolationType())
            .severity("CRITICAL")
            .riskLevel(event.getRiskLevel())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .description(event.getDescription())
            .detectedAt(LocalDateTime.now())
            .status("OPEN")
            .requiresImmediateAction(true)
            .correlationId(correlationId)
            .build();
        violationRepository.save(violation);

        violationService.processCriticalViolation(event.getViolationId());
        escalationService.escalateImmediate(event.getViolationId(), "CRITICAL_VIOLATION",
            event.getDescription(), "EXECUTIVE");

        kafkaTemplate.send("compliance-incidents", Map.of(
            "incidentType", "CRITICAL_COMPLIANCE_VIOLATION",
            "violationId", event.getViolationId(),
            "severity", "CRITICAL",
            "riskLevel", event.getRiskLevel(),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert("CRITICAL Compliance Violation Detected",
            String.format("CRITICAL: Compliance violation detected: %s - %s",
                event.getViolationType(), event.getDescription()),
            Map.of("violationId", event.getViolationId(), "riskLevel", event.getRiskLevel()));

        metricsService.recordCriticalComplianceViolation(event.getViolationType());

        log.error("CRITICAL compliance violation detected: violationId={}, type={}, risk={}",
            event.getViolationId(), event.getViolationType(), event.getRiskLevel());
    }

    private void processRegulatoryBreach(ComplianceCriticalViolationsEvent event, String correlationId) {
        violationService.processRegulatoryBreach(event.getViolationId(), event.getRegulatoryRequirements());
        escalationService.escalateRegulatory(event.getViolationId(), event.getDescription(),
            event.getRegulatoryRequirements());

        kafkaTemplate.send("regulatory-reporting", Map.of(
            "eventType", "REGULATORY_BREACH_DETECTED",
            "violationId", event.getViolationId(),
            "breachType", event.getViolationType(),
            "regulatoryRequirements", event.getRegulatoryRequirements(),
            "severity", "CRITICAL",
            "requiresImmediateReporting", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendRegulatoryAlert("CRITICAL Regulatory Breach",
            String.format("CRITICAL: Regulatory breach detected: %s. Requirements: %s",
                event.getDescription(), String.join(", ", event.getRegulatoryRequirements())),
            Map.of("violationId", event.getViolationId(), "breachLevel", "CRITICAL"));

        metricsService.recordRegulatoryBreach("CRITICAL");

        log.error("CRITICAL regulatory breach: violationId={}, requirements={}",
            event.getViolationId(), event.getRegulatoryRequirements());
    }

    private void processPolicyViolationCritical(ComplianceCriticalViolationsEvent event, String correlationId) {
        violationService.processPolicyViolation(event.getViolationId(), event.getPolicyViolated(), "CRITICAL");

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "CRITICAL_POLICY_VIOLATION",
            "violationId", event.getViolationId(),
            "policyViolated", event.getPolicyViolated(),
            "severity", "CRITICAL",
            "priority", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (isHighImpactPolicy(event.getPolicyViolated())) {
            escalationService.escalateImmediate(event.getViolationId(), "HIGH_IMPACT_POLICY_VIOLATION",
                event.getDescription(), "SENIOR_MANAGEMENT");
        }

        metricsService.recordPolicyViolation(event.getPolicyViolated(), "CRITICAL");

        log.error("Critical policy violation: violationId={}, policy={}",
            event.getViolationId(), event.getPolicyViolated());
    }

    private void processComplianceThresholdExceeded(ComplianceCriticalViolationsEvent event, String correlationId) {
        violationService.processThresholdViolation(event.getViolationId(), event.getThresholds(),
            event.getActualValues());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COMPLIANCE_THRESHOLD_EXCEEDED",
            "violationId", event.getViolationId(),
            "thresholds", event.getThresholds(),
            "actualValues", event.getActualValues(),
            "severity", "HIGH",
            "requiresReview", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (isSignificantThresholdBreach(event.getThresholds(), event.getActualValues())) {
            escalationService.escalateThreshold(event.getViolationId(), event.getThresholds(),
                event.getActualValues(), "SENIOR_MANAGEMENT");
        }

        metricsService.recordThresholdViolation();

        log.error("Compliance threshold exceeded: violationId={}, thresholds={}",
            event.getViolationId(), event.getThresholds());
    }

    private void processViolationEscalated(ComplianceCriticalViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        violation.setStatus("ESCALATED");
        violation.setEscalatedAt(LocalDateTime.now());
        violation.setEscalationReason(event.getEscalationReason());
        violationRepository.save(violation);

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "VIOLATION_ESCALATED",
            "violationId", event.getViolationId(),
            "escalationReason", event.getEscalationReason(),
            "escalatedTo", event.getEscalatedTo(),
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert("Critical Violation Escalated",
            String.format("Critical violation %s escalated: %s",
                event.getViolationId(), event.getEscalationReason()),
            Map.of("violationId", event.getViolationId(), "escalatedTo", event.getEscalatedTo()));

        metricsService.recordViolationEscalated();

        log.error("Violation escalated: violationId={}, reason={}",
            event.getViolationId(), event.getEscalationReason());
    }

    private void processViolationResolved(ComplianceCriticalViolationsEvent event, String correlationId) {
        ComplianceViolation violation = violationRepository.findByViolationId(event.getViolationId())
            .orElseThrow(() -> new RuntimeException("Compliance violation not found"));

        violation.setStatus("RESOLVED");
        violation.setResolvedAt(LocalDateTime.now());
        violation.setResolvedBy(event.getResolvedBy());
        violation.setResolutionNotes(event.getResolutionNotes());
        violationRepository.save(violation);

        violationService.resolveViolation(event.getViolationId(), event.getResolutionNotes(), event.getResolvedBy());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "CRITICAL_VIOLATION_RESOLVED",
            "violationId", event.getViolationId(),
            "violationType", event.getViolationType(),
            "resolvedBy", event.getResolvedBy(),
            "resolutionTime", calculateResolutionTime(violation.getDetectedAt()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordViolationResolved(event.getViolationType(),
            calculateResolutionTime(violation.getDetectedAt()));

        log.info("Critical violation resolved: violationId={}, resolvedBy={}",
            event.getViolationId(), event.getResolvedBy());
    }

    private boolean isHighImpactPolicy(String policy) {
        return Arrays.asList("AML_POLICY", "SANCTIONS_POLICY", "KYC_POLICY", "FRAUD_POLICY")
            .contains(policy);
    }

    private boolean isSignificantThresholdBreach(Map<String, Object> thresholds, Map<String, Object> actualValues) {
        return thresholds.entrySet().stream().anyMatch(entry -> {
            Object actual = actualValues.get(entry.getKey());
            if (actual instanceof Number && entry.getValue() instanceof Number) {
                double actualVal = ((Number) actual).doubleValue();
                double thresholdVal = ((Number) entry.getValue()).doubleValue();
                return actualVal > (thresholdVal * 2.0); // 200% of threshold
            }
            return false;
        });
    }

    private long calculateResolutionTime(LocalDateTime detectedAt) {
        return java.time.Duration.between(detectedAt, LocalDateTime.now()).toMinutes();
    }
}