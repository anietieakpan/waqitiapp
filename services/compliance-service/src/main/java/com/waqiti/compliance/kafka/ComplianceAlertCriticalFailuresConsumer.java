package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ComplianceAlertCriticalFailuresEvent;
import com.waqiti.compliance.domain.ComplianceCriticalFailure;
import com.waqiti.compliance.repository.ComplianceCriticalFailureRepository;
import com.waqiti.compliance.service.ComplianceIncidentService;
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
public class ComplianceAlertCriticalFailuresConsumer {

    private final ComplianceCriticalFailureRepository criticalFailureRepository;
    private final ComplianceIncidentService incidentService;
    private final ComplianceEscalationService escalationService;
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
        successCounter = Counter.builder("compliance_alert_critical_failures_processed_total")
            .description("Total number of successfully processed compliance alert critical failures events")
            .register(meterRegistry);
        errorCounter = Counter.builder("compliance_alert_critical_failures_errors_total")
            .description("Total number of compliance alert critical failures processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compliance_alert_critical_failures_processing_duration")
            .description("Time taken to process compliance alert critical failures events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance-alert-critical-failures"},
        groupId = "compliance-alert-critical-failures-group",
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
    @CircuitBreaker(name = "compliance-alert-critical-failures", fallbackMethod = "handleComplianceAlertCriticalFailuresEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500, multiplier = 1.5, maxDelay = 5000))
    public void handleComplianceAlertCriticalFailuresEvent(
            @Payload ComplianceAlertCriticalFailuresEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("critical-alert-%s-p%d-o%d", event.getAlertId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAlertId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL compliance alert failure: alertId={}, failureType={}, severity={}",
                event.getAlertId(), event.getFailureType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CRITICAL_ALERT_FAILURE:
                    processCriticalAlertFailure(event, correlationId);
                    break;

                case SYSTEM_COMPLIANCE_FAILURE:
                    processSystemComplianceFailure(event, correlationId);
                    break;

                case REGULATORY_BREACH_CRITICAL:
                    processRegulatoryBreachCritical(event, correlationId);
                    break;

                case ALERT_PROCESSING_FAILURE:
                    processAlertProcessingFailure(event, correlationId);
                    break;

                case COMPLIANCE_MONITORING_DOWN:
                    processComplianceMonitoringDown(event, correlationId);
                    break;

                case CRITICAL_THRESHOLD_BREACH:
                    processCriticalThresholdBreach(event, correlationId);
                    break;

                default:
                    log.error("Unknown compliance alert critical failures event type: {}", event.getEventType());
                    processCriticalAlertFailure(event, correlationId); // Default to critical processing
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPLIANCE_ALERT_CRITICAL_FAILURES_EVENT_PROCESSED", event.getAlertId(),
                Map.of("eventType", event.getEventType(), "failureType", event.getFailureType(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "impactLevel", event.getImpactLevel(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("CRITICAL FAILURE: Failed to process compliance alert critical failures event: {}", e.getMessage(), e);

            // For critical failures, send immediate alert even on processing failure
            try {
                notificationService.sendEmergencyAlert(
                    "CRITICAL: Compliance Alert Processing Failure",
                    String.format("URGENT: Failed to process critical compliance alert %s: %s", event.getAlertId(), e.getMessage()),
                    Map.of("alertId", event.getAlertId(), "error", e.getMessage(), "requiresImmediateAction", true)
                );
            } catch (Exception notificationEx) {
                log.error("EMERGENCY: Failed to send critical failure notification: {}", notificationEx.getMessage());
            }

            kafkaTemplate.send("compliance-critical-failures-emergency", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "emergencyLevel", "CRITICAL", "retryCount", 0, "maxRetries", 1));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleComplianceAlertCriticalFailuresEventFallback(
            ComplianceAlertCriticalFailuresEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("critical-alert-fallback-%s-p%d-o%d", event.getAlertId(), partition, offset);

        log.error("CRITICAL: Circuit breaker fallback triggered for compliance alert critical failures: alertId={}, error={}",
            event.getAlertId(), ex.getMessage());

        kafkaTemplate.send("compliance-alert-critical-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "emergencyLevel", "CRITICAL",
            "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "CRITICAL: Compliance Alert Circuit Breaker Triggered",
                String.format("EMERGENCY: Critical compliance alert %s circuit breaker activated: %s",
                    event.getAlertId(), ex.getMessage()),
                Map.of("alertId", event.getAlertId(), "failureType", event.getFailureType(),
                    "requiresImmediateEscalation", true)
            );
        } catch (Exception notificationEx) {
            log.error("EMERGENCY: Failed to send critical circuit breaker alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltComplianceAlertCriticalFailuresEvent(
            @Payload ComplianceAlertCriticalFailuresEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-critical-alert-%s-%d", event.getAlertId(), System.currentTimeMillis());

        log.error("EMERGENCY: Dead letter topic handler - Compliance alert critical failures permanently failed: alertId={}, topic={}, error={}",
            event.getAlertId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPLIANCE_ALERT_CRITICAL_FAILURES_DLT_EVENT", event.getAlertId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "emergencyLevel", "CRITICAL", "requiresImmediateIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "EMERGENCY: Critical Compliance Alert Dead Letter Event",
                String.format("CRITICAL FAILURE: Compliance alert %s sent to DLT and requires immediate manual intervention: %s",
                    event.getAlertId(), exceptionMessage),
                Map.of("alertId", event.getAlertId(), "topic", topic, "correlationId", correlationId,
                    "failureType", event.getFailureType(), "requiresExecutiveEscalation", true)
            );
        } catch (Exception ex) {
            log.error("CATASTROPHIC: Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void processCriticalAlertFailure(ComplianceAlertCriticalFailuresEvent event, String correlationId) {
        ComplianceCriticalFailure failure = ComplianceCriticalFailure.builder()
            .alertId(event.getAlertId())
            .failureType(event.getFailureType())
            .severity("CRITICAL")
            .impactLevel(event.getImpactLevel())
            .failureReason(event.getFailureReason())
            .systemsAffected(event.getSystemsAffected())
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .build();
        criticalFailureRepository.save(failure);

        incidentService.createCriticalIncident(event.getAlertId(), "COMPLIANCE_ALERT_FAILURE",
            event.getFailureReason(), "CRITICAL");

        escalationService.escalateImmediate(event.getAlertId(), "CRITICAL_ALERT_FAILURE",
            event.getFailureReason(), "EXECUTIVE");

        kafkaTemplate.send("compliance-incidents", Map.of(
            "incidentType", "CRITICAL_COMPLIANCE_FAILURE",
            "alertId", event.getAlertId(),
            "severity", "CRITICAL",
            "impactLevel", event.getImpactLevel(),
            "systemsAffected", event.getSystemsAffected(),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert("CRITICAL Compliance Alert Failure",
            String.format("CRITICAL: Compliance alert %s has failed: %s. Impact: %s",
                event.getAlertId(), event.getFailureReason(), event.getImpactLevel()),
            Map.of("alertId", event.getAlertId(), "impactLevel", event.getImpactLevel()));

        metricsService.recordCriticalComplianceFailure(event.getFailureType());

        log.error("CRITICAL compliance alert failure processed: alertId={}, impact={}",
            event.getAlertId(), event.getImpactLevel());
    }

    private void processSystemComplianceFailure(ComplianceAlertCriticalFailuresEvent event, String correlationId) {
        incidentService.createSystemIncident(event.getAlertId(), "SYSTEM_COMPLIANCE_FAILURE",
            event.getFailureReason(), "CRITICAL");

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "SYSTEM_COMPLIANCE_FAILURE",
            "alertId", event.getAlertId(),
            "severity", "CRITICAL",
            "systemsAffected", event.getSystemsAffected(),
            "priority", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("system-monitoring", Map.of(
            "eventType", "COMPLIANCE_SYSTEM_FAILURE",
            "alertId", event.getAlertId(),
            "failureType", event.getFailureType(),
            "systemsAffected", event.getSystemsAffected(),
            "requiresImmediateAttention", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendSystemAlert("System Compliance Failure",
            String.format("CRITICAL: System compliance failure detected: %s. Systems affected: %s",
                event.getFailureReason(), String.join(", ", event.getSystemsAffected())),
            "CRITICAL");

        metricsService.recordSystemComplianceFailure();

        log.error("System compliance failure processed: alertId={}, systems={}",
            event.getAlertId(), event.getSystemsAffected());
    }

    private void processRegulatoryBreachCritical(ComplianceAlertCriticalFailuresEvent event, String correlationId) {
        incidentService.createRegulatoryIncident(event.getAlertId(), "REGULATORY_BREACH_CRITICAL",
            event.getFailureReason(), "CRITICAL");

        escalationService.escalateRegulatory(event.getAlertId(), event.getFailureReason(),
            event.getRegulatoryRequirements());

        kafkaTemplate.send("regulatory-reporting", Map.of(
            "eventType", "CRITICAL_REGULATORY_BREACH",
            "alertId", event.getAlertId(),
            "breachType", event.getFailureType(),
            "regulatoryRequirements", event.getRegulatoryRequirements(),
            "severity", "CRITICAL",
            "requiresImmediateReporting", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendRegulatoryAlert("CRITICAL Regulatory Breach",
            String.format("CRITICAL: Regulatory breach detected: %s. Requirements: %s",
                event.getFailureReason(), String.join(", ", event.getRegulatoryRequirements())),
            Map.of("alertId", event.getAlertId(), "breachLevel", "CRITICAL"));

        metricsService.recordCriticalRegulatoryBreach();

        log.error("Critical regulatory breach processed: alertId={}, requirements={}",
            event.getAlertId(), event.getRegulatoryRequirements());
    }

    private void processAlertProcessingFailure(ComplianceAlertCriticalFailuresEvent event, String correlationId) {
        incidentService.createProcessingIncident(event.getAlertId(), "ALERT_PROCESSING_FAILURE",
            event.getFailureReason(), "HIGH");

        kafkaTemplate.send("system-monitoring", Map.of(
            "eventType", "ALERT_PROCESSING_FAILURE",
            "alertId", event.getAlertId(),
            "processingErrors", event.getProcessingErrors(),
            "affectedAlerts", event.getAffectedAlerts(),
            "requiresSystemCheck", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (event.getAffectedAlerts() != null && event.getAffectedAlerts().size() > 10) {
            escalationService.escalateImmediate(event.getAlertId(), "MASS_ALERT_PROCESSING_FAILURE",
                "Multiple alerts affected", "SENIOR_MANAGEMENT");
        }

        metricsService.recordAlertProcessingFailure(event.getAffectedAlerts().size());

        log.error("Alert processing failure: alertId={}, affectedAlerts={}",
            event.getAlertId(), event.getAffectedAlerts().size());
    }

    private void processComplianceMonitoringDown(ComplianceAlertCriticalFailuresEvent event, String correlationId) {
        incidentService.createMonitoringIncident(event.getAlertId(), "COMPLIANCE_MONITORING_DOWN",
            event.getFailureReason(), "CRITICAL");

        escalationService.escalateImmediate(event.getAlertId(), "MONITORING_SYSTEM_DOWN",
            "Compliance monitoring unavailable", "EXECUTIVE");

        kafkaTemplate.send("system-monitoring", Map.of(
            "eventType", "COMPLIANCE_MONITORING_DOWN",
            "alertId", event.getAlertId(),
            "monitoringSystems", event.getSystemsAffected(),
            "downtime", event.getDowntimeDuration(),
            "requiresImmediateRestoration", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert("Compliance Monitoring System Down",
            String.format("CRITICAL: Compliance monitoring systems are down: %s. Duration: %s",
                String.join(", ", event.getSystemsAffected()), event.getDowntimeDuration()),
            Map.of("alertId", event.getAlertId(), "downtime", event.getDowntimeDuration()));

        metricsService.recordComplianceMonitoringDown();

        log.error("Compliance monitoring down: alertId={}, systems={}, duration={}",
            event.getAlertId(), event.getSystemsAffected(), event.getDowntimeDuration());
    }

    private void processCriticalThresholdBreach(ComplianceAlertCriticalFailuresEvent event, String correlationId) {
        incidentService.createThresholdIncident(event.getAlertId(), "CRITICAL_THRESHOLD_BREACH",
            event.getFailureReason(), "HIGH");

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "CRITICAL_THRESHOLD_BREACH",
            "alertId", event.getAlertId(),
            "thresholds", event.getThresholds(),
            "actualValues", event.getActualValues(),
            "severity", "HIGH",
            "requiresReview", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (exceedsCriticalLevel(event.getThresholds(), event.getActualValues())) {
            escalationService.escalateThreshold(event.getAlertId(), event.getThresholds(),
                event.getActualValues(), "SENIOR_MANAGEMENT");
        }

        metricsService.recordCriticalThresholdBreach();

        log.error("Critical threshold breach: alertId={}, thresholds={}",
            event.getAlertId(), event.getThresholds());
    }

    private boolean exceedsCriticalLevel(Map<String, Object> thresholds, Map<String, Object> actualValues) {
        // Check if any actual value exceeds 150% of threshold
        return thresholds.entrySet().stream().anyMatch(entry -> {
            Object actual = actualValues.get(entry.getKey());
            if (actual instanceof Number && entry.getValue() instanceof Number) {
                double actualVal = ((Number) actual).doubleValue();
                double thresholdVal = ((Number) entry.getValue()).doubleValue();
                return actualVal > (thresholdVal * 1.5);
            }
            return false;
        });
    }
}