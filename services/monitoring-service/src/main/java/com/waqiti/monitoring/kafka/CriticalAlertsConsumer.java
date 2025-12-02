package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.CriticalAlertEvent;
import com.waqiti.monitoring.service.CriticalAlertService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.InfrastructureMetricsService;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class CriticalAlertsConsumer {

    private final CriticalAlertService criticalAlertService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
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
        successCounter = Counter.builder("critical_alerts_processed_total")
            .description("Total number of successfully processed critical alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("critical_alerts_errors_total")
            .description("Total number of critical alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("critical_alerts_processing_duration")
            .description("Time taken to process critical alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"critical-alerts", "emergency-alerts", "urgent-system-alerts"},
        groupId = "critical-alerts-group",
        containerFactory = "emergencyKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "critical-alerts", fallbackMethod = "handleCriticalAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCriticalAlertEvent(
            @Payload CriticalAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("critical-alert-%s-p%d-o%d", event.getAlertId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAlertId(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL alert: alertId={}, type={}, severity={}, source={}",
                event.getAlertId(), event.getAlertType(), event.getSeverity(), event.getSource());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case SYSTEM_DOWN:
                    handleSystemDown(event, correlationId);
                    break;

                case DATA_BREACH:
                    handleDataBreach(event, correlationId);
                    break;

                case SERVICE_OUTAGE:
                    handleServiceOutage(event, correlationId);
                    break;

                case INFRASTRUCTURE_FAILURE:
                    handleInfrastructureFailure(event, correlationId);
                    break;

                case SECURITY_INCIDENT:
                    handleSecurityIncident(event, correlationId);
                    break;

                case FINANCIAL_ANOMALY:
                    handleFinancialAnomaly(event, correlationId);
                    break;

                case COMPLIANCE_VIOLATION:
                    handleComplianceViolation(event, correlationId);
                    break;

                case DISASTER_EVENT:
                    handleDisasterEvent(event, correlationId);
                    break;

                case CUSTOMER_IMPACT:
                    handleCriticalCustomerImpact(event, correlationId);
                    break;

                case ALERT_ACKNOWLEDGED:
                    handleAlertAcknowledged(event, correlationId);
                    break;

                case ALERT_RESOLVED:
                    handleAlertResolved(event, correlationId);
                    break;

                default:
                    log.warn("Unknown critical alert type: {}", event.getAlertType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCriticalEvent("CRITICAL_ALERT_PROCESSED", event.getAlertId(),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "source", event.getSource(), "affectedSystems", event.getAffectedSystems(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process critical alert event: {}", e.getMessage(), e);

            // Send fallback event - critical alerts must be handled
            kafkaTemplate.send("critical-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCriticalAlertEventFallback(
            CriticalAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("critical-alert-fallback-%s-p%d-o%d", event.getAlertId(), partition, offset);

        log.error("Circuit breaker fallback triggered for CRITICAL alert: alertId={}, error={}",
            event.getAlertId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("critical-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // IMMEDIATE notification to operations team for critical alerts
        try {
            notificationService.sendEmergencyAlert(
                "CRITICAL ALERT PROCESSING FAILURE",
                String.format("URGENT: Critical alert %s processing failed: %s", event.getAlertId(), ex.getMessage()),
                "EMERGENCY"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send emergency alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCriticalAlertEvent(
            @Payload CriticalAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-critical-alert-%s-%d", event.getAlertId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - CRITICAL alert permanently failed: alertId={}, topic={}, error={}",
            event.getAlertId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCriticalEvent("CRITICAL_ALERT_DLT_EVENT", event.getAlertId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresImmediateIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert for DLT critical events
        try {
            notificationService.sendEmergencyAlert(
                "CRITICAL ALERT DEAD LETTER EVENT",
                String.format("EMERGENCY: Critical alert %s sent to DLT: %s", event.getAlertId(), exceptionMessage),
                Map.of("alertId", event.getAlertId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void handleSystemDown(CriticalAlertEvent event, String correlationId) {
        log.error("SYSTEM DOWN ALERT: system={}, downtime={}, affectedUsers={}",
            event.getAffectedSystem(), event.getDowntimeDuration(), event.getAffectedUsers());

        criticalAlertService.handleSystemDown(event.getAffectedSystem(), event.getDowntimeDuration());

        alertingService.sendEmergencyAlert(
            "SYSTEM DOWN - IMMEDIATE ACTION REQUIRED",
            String.format("System %s is DOWN. Downtime: %s. Affected users: %d",
                event.getAffectedSystem(), event.getDowntimeDuration(), event.getAffectedUsers()),
            correlationId
        );

        // Trigger disaster recovery procedures
        criticalAlertService.triggerDisasterRecovery(event.getAffectedSystem());

        metricsService.recordSystemDown(event.getAffectedSystem(), event.getAffectedUsers());

        // Escalate to CEO/CTO for major system outages
        if (event.getAffectedUsers() > 100000) {
            notificationService.escalateToExecutive(
                "Major System Outage",
                String.format("Critical system %s is down affecting %d users", event.getAffectedSystem(), event.getAffectedUsers()),
                Map.of("system", event.getAffectedSystem(), "correlationId", correlationId)
            );
        }
    }

    private void handleDataBreach(CriticalAlertEvent event, String correlationId) {
        log.error("DATA BREACH ALERT: breachType={}, affectedRecords={}, confidentialityLevel={}",
            event.getBreachType(), event.getAffectedRecords(), event.getConfidentialityLevel());

        criticalAlertService.handleDataBreach(event.getBreachType(), event.getAffectedRecords(), event.getConfidentialityLevel());

        alertingService.sendEmergencyAlert(
            "DATA BREACH DETECTED - IMMEDIATE CONTAINMENT REQUIRED",
            String.format("Data breach: %s. Records affected: %d. Level: %s",
                event.getBreachType(), event.getAffectedRecords(), event.getConfidentialityLevel()),
            correlationId
        );

        // Trigger security protocols
        criticalAlertService.triggerSecurityProtocols(event.getBreachType());

        metricsService.recordDataBreach(event.getBreachType(), event.getAffectedRecords());

        // Mandatory executive and legal notification
        notificationService.escalateToExecutive(
            "DATA BREACH - LEGAL NOTIFICATION REQUIRED",
            String.format("Data breach detected: %s affecting %d records", event.getBreachType(), event.getAffectedRecords()),
            Map.of("breachType", event.getBreachType(), "affectedRecords", event.getAffectedRecords(), "correlationId", correlationId)
        );

        // Send to compliance team
        kafkaTemplate.send("compliance-urgent-events", Map.of(
            "eventType", "DATA_BREACH",
            "breachType", event.getBreachType(),
            "affectedRecords", event.getAffectedRecords(),
            "confidentialityLevel", event.getConfidentialityLevel(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleServiceOutage(CriticalAlertEvent event, String correlationId) {
        log.error("SERVICE OUTAGE: service={}, impactLevel={}, estimatedResolution={}",
            event.getAffectedService(), event.getImpactLevel(), event.getEstimatedResolution());

        criticalAlertService.handleServiceOutage(event.getAffectedService(), event.getImpactLevel());

        alertingService.sendCriticalAlert(
            "Service Outage Detected",
            String.format("Service %s is experiencing outage (impact: %s, ETA: %s)",
                event.getAffectedService(), event.getImpactLevel(), event.getEstimatedResolution()),
            correlationId
        );

        // Trigger failover if available
        criticalAlertService.triggerServiceFailover(event.getAffectedService());

        metricsService.recordServiceOutage(event.getAffectedService(), event.getImpactLevel());
    }

    private void handleInfrastructureFailure(CriticalAlertEvent event, String correlationId) {
        log.error("INFRASTRUCTURE FAILURE: component={}, failureType={}, redundancyStatus={}",
            event.getInfrastructureComponent(), event.getFailureType(), event.getRedundancyStatus());

        criticalAlertService.handleInfrastructureFailure(
            event.getInfrastructureComponent(), event.getFailureType(), event.getRedundancyStatus());

        alertingService.sendCriticalAlert(
            "Infrastructure Failure",
            String.format("Infrastructure failure in %s: %s (redundancy: %s)",
                event.getInfrastructureComponent(), event.getFailureType(), event.getRedundancyStatus()),
            correlationId
        );

        // Activate redundant systems if available
        if ("AVAILABLE".equals(event.getRedundancyStatus())) {
            criticalAlertService.activateRedundantSystems(event.getInfrastructureComponent());
        }

        metricsService.recordInfrastructureFailure(event.getInfrastructureComponent(), event.getFailureType());
    }

    private void handleSecurityIncident(CriticalAlertEvent event, String correlationId) {
        log.error("SECURITY INCIDENT: incidentType={}, threatLevel={}, attackVector={}",
            event.getSecurityIncidentType(), event.getThreatLevel(), event.getAttackVector());

        criticalAlertService.handleSecurityIncident(
            event.getSecurityIncidentType(), event.getThreatLevel(), event.getAttackVector());

        alertingService.sendEmergencyAlert(
            "SECURITY INCIDENT - IMMEDIATE RESPONSE REQUIRED",
            String.format("Security incident: %s (threat level: %s, vector: %s)",
                event.getSecurityIncidentType(), event.getThreatLevel(), event.getAttackVector()),
            correlationId
        );

        // Trigger security lockdown procedures
        criticalAlertService.triggerSecurityLockdown(event.getSecurityIncidentType());

        metricsService.recordSecurityIncident(event.getSecurityIncidentType(), event.getThreatLevel());

        // Send to security operations center
        kafkaTemplate.send("security-operations-events", Map.of(
            "eventType", "CRITICAL_SECURITY_INCIDENT",
            "incidentType", event.getSecurityIncidentType(),
            "threatLevel", event.getThreatLevel(),
            "attackVector", event.getAttackVector(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleFinancialAnomaly(CriticalAlertEvent event, String correlationId) {
        log.error("FINANCIAL ANOMALY: anomalyType={}, amount={}, riskLevel={}",
            event.getFinancialAnomalyType(), event.getAnomalyAmount(), event.getRiskLevel());

        criticalAlertService.handleFinancialAnomaly(
            event.getFinancialAnomalyType(), event.getAnomalyAmount(), event.getRiskLevel());

        alertingService.sendCriticalAlert(
            "Critical Financial Anomaly",
            String.format("Financial anomaly detected: %s (amount: %s, risk: %s)",
                event.getFinancialAnomalyType(), event.getAnomalyAmount(), event.getRiskLevel()),
            correlationId
        );

        // Freeze suspicious transactions if high risk
        if ("HIGH".equals(event.getRiskLevel())) {
            criticalAlertService.freezeSuspiciousTransactions(event.getFinancialAnomalyType());
        }

        metricsService.recordFinancialAnomaly(event.getFinancialAnomalyType(), event.getAnomalyAmount());

        // Send to financial crimes unit
        kafkaTemplate.send("financial-crimes-events", Map.of(
            "eventType", "CRITICAL_FINANCIAL_ANOMALY",
            "anomalyType", event.getFinancialAnomalyType(),
            "amount", event.getAnomalyAmount(),
            "riskLevel", event.getRiskLevel(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleComplianceViolation(CriticalAlertEvent event, String correlationId) {
        log.error("COMPLIANCE VIOLATION: violationType={}, regulatoryBody={}, severity={}",
            event.getComplianceViolationType(), event.getRegulatoryBody(), event.getViolationSeverity());

        criticalAlertService.handleComplianceViolation(
            event.getComplianceViolationType(), event.getRegulatoryBody(), event.getViolationSeverity());

        alertingService.sendCriticalAlert(
            "Critical Compliance Violation",
            String.format("Compliance violation: %s (%s, severity: %s)",
                event.getComplianceViolationType(), event.getRegulatoryBody(), event.getViolationSeverity()),
            correlationId
        );

        metricsService.recordComplianceViolation(event.getComplianceViolationType(), event.getRegulatoryBody());

        // Notify legal and compliance teams
        notificationService.notifyLegalTeam(
            "Critical Compliance Violation",
            String.format("Violation: %s under %s regulations", event.getComplianceViolationType(), event.getRegulatoryBody()),
            Map.of("violationType", event.getComplianceViolationType(), "correlationId", correlationId)
        );
    }

    private void handleDisasterEvent(CriticalAlertEvent event, String correlationId) {
        log.error("DISASTER EVENT: disasterType={}, affectedRegions={}, evacuationRequired={}",
            event.getDisasterType(), event.getAffectedRegions(), event.isEvacuationRequired());

        criticalAlertService.handleDisasterEvent(
            event.getDisasterType(), event.getAffectedRegions(), event.isEvacuationRequired());

        alertingService.sendEmergencyAlert(
            "DISASTER EVENT - EMERGENCY PROCEDURES ACTIVATED",
            String.format("Disaster: %s affecting regions: %s (evacuation: %s)",
                event.getDisasterType(), String.join(", ", event.getAffectedRegions()),
                event.isEvacuationRequired() ? "REQUIRED" : "NOT REQUIRED"),
            correlationId
        );

        // Trigger business continuity plans
        criticalAlertService.triggerBusinessContinuityPlan(event.getDisasterType(), event.getAffectedRegions());

        metricsService.recordDisasterEvent(event.getDisasterType(), event.getAffectedRegions().size());
    }

    private void handleCriticalCustomerImpact(CriticalAlertEvent event, String correlationId) {
        log.error("CRITICAL CUSTOMER IMPACT: impactType={}, affectedCustomers={}, businessImpact={}",
            event.getCustomerImpactType(), event.getAffectedCustomers(), event.getBusinessImpact());

        criticalAlertService.handleCriticalCustomerImpact(
            event.getCustomerImpactType(), event.getAffectedCustomers(), event.getBusinessImpact());

        alertingService.sendCriticalAlert(
            "Critical Customer Impact",
            String.format("Customer impact: %s affecting %d customers (business impact: %s)",
                event.getCustomerImpactType(), event.getAffectedCustomers(), event.getBusinessImpact()),
            correlationId
        );

        // Trigger customer communication protocols
        criticalAlertService.triggerCustomerCommunication(event.getCustomerImpactType(), event.getAffectedCustomers());

        metricsService.recordCriticalCustomerImpact(event.getCustomerImpactType(), event.getAffectedCustomers());
    }

    private void handleAlertAcknowledged(CriticalAlertEvent event, String correlationId) {
        log.info("Critical alert acknowledged: alertId={}, acknowledgedBy={}, responseTime={}",
            event.getAlertId(), event.getAcknowledgedBy(), event.getResponseTime());

        criticalAlertService.handleAlertAcknowledged(event.getAlertId(), event.getAcknowledgedBy());

        metricsService.recordAlertAcknowledged(event.getAlertId(), event.getResponseTime());
    }

    private void handleAlertResolved(CriticalAlertEvent event, String correlationId) {
        log.info("Critical alert resolved: alertId={}, resolvedBy={}, resolutionTime={}, solution={}",
            event.getAlertId(), event.getResolvedBy(), event.getResolutionTime(), event.getResolutionSummary());

        criticalAlertService.handleAlertResolved(
            event.getAlertId(), event.getResolvedBy(), event.getResolutionSummary());

        alertingService.sendInfoAlert(
            "Critical Alert Resolved",
            String.format("Alert %s resolved by %s: %s",
                event.getAlertId(), event.getResolvedBy(), event.getResolutionSummary()),
            correlationId
        );

        metricsService.recordAlertResolved(event.getAlertId(), event.getResolutionTime());
    }
}