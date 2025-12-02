package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for compliance breach alerts
 * Handles regulatory compliance breach notifications with immediate escalation,
 * regulatory reporting, and remediation workflows
 * 
 * Critical for: Regulatory compliance, breach management, legal requirements
 * SLA: Must process compliance breaches within 5 seconds for regulatory requirements
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceBreachAlertsConsumer {

    private final ComplianceBreachService complianceBreachService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceWorkflowService complianceWorkflowService;
    private final LegalNotificationService legalNotificationService;
    private final ComplianceAuditService complianceAuditService;
    private final UniversalDLQHandler universalDLQHandler;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter complianceBreachCounter = Counter.builder("compliance_breach_alerts_processed_total")
            .description("Total number of compliance breach alerts processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalBreachCounter = Counter.builder("critical_compliance_breaches_total")
            .description("Total number of critical compliance breaches processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("compliance_breach_processing_duration")
            .description("Time taken to process compliance breach alerts")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"compliance-breach-alerts"},
        groupId = "compliance-service-breach-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "compliance-breach-processor", fallbackMethod = "handleComplianceBreachFailure")
    @Retry(name = "compliance-breach-processor")
    public void processComplianceBreachAlert(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.error("COMPLIANCE BREACH: Processing breach alert: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Compliance breach event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate breach data
            ComplianceBreachData breachData = extractBreachData(event.getPayload());
            validateBreachData(breachData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process compliance breach
            processComplianceBreach(breachData, event);

            // Record successful processing metrics
            complianceBreachCounter.increment();
            
            if ("CRITICAL".equals(breachData.getSeverity())) {
                criticalBreachCounter.increment();
            }
            
            // Audit the breach processing
            auditComplianceBreachProcessing(breachData, event, "SUCCESS");

            log.error("COMPLIANCE: Successfully processed breach: {} for regulation: {} - severity: {} type: {}", 
                    eventId, breachData.getRegulation(), breachData.getSeverity(), breachData.getBreachType());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid breach alert data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process breach alert: {}", eventId, e);
            auditComplianceBreachProcessing(null, event, "FAILED: " + e.getMessage());

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, GenericKafkaEvent> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, eventId, event);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send compliance breach alert to DLQ: {}", eventId, dlqEx);
            }

            throw new RuntimeException("Compliance breach processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private ComplianceBreachData extractBreachData(Map<String, Object> payload) {
        return ComplianceBreachData.builder()
                .breachId(extractString(payload, "breachId"))
                .regulation(extractString(payload, "regulation"))
                .breachType(extractString(payload, "breachType"))
                .severity(extractString(payload, "severity"))
                .description(extractString(payload, "description"))
                .affectedEntity(extractString(payload, "affectedEntity"))
                .affectedAccounts(extractStringList(payload, "affectedAccounts"))
                .regulatoryRequirements(extractStringList(payload, "regulatoryRequirements"))
                .violatedRules(extractStringList(payload, "violatedRules"))
                .potentialPenalties(extractStringList(payload, "potentialPenalties"))
                .reportingRequirements(extractMap(payload, "reportingRequirements"))
                .remediationActions(extractStringList(payload, "remediationActions"))
                .breachContext(extractMap(payload, "breachContext"))
                .detectionMethod(extractString(payload, "detectionMethod"))
                .detectedAt(extractInstant(payload, "detectedAt"))
                .reportingDeadline(extractInstant(payload, "reportingDeadline"))
                .businessImpact(extractString(payload, "businessImpact"))
                .customerImpact(extractString(payload, "customerImpact"))
                .dataSubjects(extractInteger(payload, "dataSubjects"))
                .jurisdictions(extractStringList(payload, "jurisdictions"))
                .build();
    }

    private void validateBreachData(ComplianceBreachData breachData) {
        if (breachData.getBreachId() == null || breachData.getBreachId().trim().isEmpty()) {
            throw new IllegalArgumentException("Breach ID is required");
        }
        
        if (breachData.getRegulation() == null || breachData.getRegulation().trim().isEmpty()) {
            throw new IllegalArgumentException("Regulation is required");
        }
        
        if (breachData.getBreachType() == null || breachData.getBreachType().trim().isEmpty()) {
            throw new IllegalArgumentException("Breach type is required");
        }
        
        if (breachData.getSeverity() == null || breachData.getSeverity().trim().isEmpty()) {
            throw new IllegalArgumentException("Severity is required");
        }

        List<String> validSeverities = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (!validSeverities.contains(breachData.getSeverity())) {
            throw new IllegalArgumentException("Invalid severity level: " + breachData.getSeverity());
        }
        
        if (breachData.getDetectedAt() == null) {
            throw new IllegalArgumentException("Detection timestamp is required");
        }
    }

    private void processComplianceBreach(ComplianceBreachData breachData, GenericKafkaEvent event) {
        log.error("COMPLIANCE BREACH: Processing - Regulation: {}, Type: {}, Severity: {}, Entity: {}", 
                breachData.getRegulation(), breachData.getBreachType(), 
                breachData.getSeverity(), breachData.getAffectedEntity());

        try {
            // Create compliance breach incident
            String incidentId = complianceBreachService.createBreachIncident(breachData);

            // Immediate regulatory notifications
            sendImmediateNotifications(breachData, incidentId);

            // Assess regulatory impact
            RegulatoryImpactAssessment impact = assessRegulatoryImpact(breachData);

            // Generate regulatory reports
            generateRegulatoryReports(breachData, impact, incidentId);

            // Initiate remediation workflows
            initiateRemediationWorkflows(breachData, incidentId);

            // Update compliance monitoring
            updateComplianceMonitoring(breachData, impact);

            // Legal team notifications
            notifyLegalTeam(breachData, impact, incidentId);

            // Customer notifications if required
            processCustomerNotifications(breachData);

            log.error("COMPLIANCE: Breach processed - IncidentId: {}, RegulatoryImpact: {}, ReportsGenerated: {}", 
                    incidentId, impact.getImpactLevel(), impact.getReportsRequired().size());

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process breach for regulation: {}", breachData.getRegulation(), e);
            
            // Emergency compliance procedures
            executeEmergencyComplianceProcedures(breachData, e);
            
            throw new RuntimeException("Compliance breach processing failed", e);
        }
    }

    private void sendImmediateNotifications(ComplianceBreachData breachData, String incidentId) {
        // Critical and high severity breaches require immediate escalation
        if ("CRITICAL".equals(breachData.getSeverity()) || "HIGH".equals(breachData.getSeverity())) {
            legalNotificationService.sendEmergencyComplianceAlert(
                    "CRITICAL COMPLIANCE BREACH DETECTED",
                    breachData,
                    incidentId
            );
            
            // Page compliance team
            legalNotificationService.pageComplianceTeam(
                    "COMPLIANCE_BREACH",
                    breachData.getRegulation(),
                    breachData.getSeverity(),
                    incidentId
            );
            
            // Executive notifications for critical breaches
            if ("CRITICAL".equals(breachData.getSeverity())) {
                legalNotificationService.sendExecutiveComplianceAlert(
                        "Critical Regulatory Compliance Breach",
                        breachData,
                        incidentId
                );
            }
        }
    }

    private RegulatoryImpactAssessment assessRegulatoryImpact(ComplianceBreachData breachData) {
        return complianceBreachService.assessRegulatoryImpact(
                breachData.getRegulation(),
                breachData.getBreachType(),
                breachData.getSeverity(),
                breachData.getAffectedAccounts().size(),
                breachData.getDataSubjects(),
                breachData.getJurisdictions()
        );
    }

    private void generateRegulatoryReports(ComplianceBreachData breachData, 
                                         RegulatoryImpactAssessment impact,
                                         String incidentId) {
        // Generate reports based on regulatory requirements
        for (String reportType : impact.getReportsRequired()) {
            try {
                switch (reportType) {
                    case "GDPR_BREACH_NOTIFICATION":
                        regulatoryReportingService.generateGDPRBreachReport(breachData, incidentId);
                        break;
                        
                    case "PCI_INCIDENT_REPORT":
                        regulatoryReportingService.generatePCIIncidentReport(breachData, incidentId);
                        break;
                        
                    case "SOX_COMPLIANCE_REPORT":
                        regulatoryReportingService.generateSOXComplianceReport(breachData, incidentId);
                        break;
                        
                    case "REGULATORY_AUTHORITY_NOTIFICATION":
                        regulatoryReportingService.generateRegulatoryAuthorityNotification(breachData, incidentId);
                        break;
                        
                    default:
                        regulatoryReportingService.generateGenericComplianceReport(breachData, reportType, incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to generate regulatory report: {}", reportType, e);
            }
        }

        // Schedule automatic report submissions if required
        if (breachData.getReportingDeadline() != null) {
            regulatoryReportingService.scheduleAutomaticReportSubmission(
                    incidentId,
                    impact.getReportsRequired(),
                    breachData.getReportingDeadline()
            );
        }
    }

    private void initiateRemediationWorkflows(ComplianceBreachData breachData, String incidentId) {
        for (String action : breachData.getRemediationActions()) {
            try {
                switch (action) {
                    case "IMMEDIATE_SYSTEM_LOCKDOWN":
                        complianceWorkflowService.initiateSystemLockdown(breachData.getAffectedEntity(), incidentId);
                        break;
                        
                    case "DATA_BREACH_CONTAINMENT":
                        complianceWorkflowService.initiateDataBreachContainment(breachData.getAffectedAccounts(), incidentId);
                        break;
                        
                    case "CUSTOMER_NOTIFICATION_WORKFLOW":
                        complianceWorkflowService.initiateCustomerNotificationWorkflow(breachData, incidentId);
                        break;
                        
                    case "FORENSIC_INVESTIGATION":
                        complianceWorkflowService.initiateForensicInvestigation(breachData, incidentId);
                        break;
                        
                    case "REGULATORY_CONSULTATION":
                        complianceWorkflowService.initiateRegulatoryConsultation(breachData.getRegulation(), incidentId);
                        break;
                        
                    default:
                        complianceWorkflowService.initiateGenericRemediationAction(action, breachData, incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to initiate remediation action: {}", action, e);
            }
        }
    }

    private void updateComplianceMonitoring(ComplianceBreachData breachData, RegulatoryImpactAssessment impact) {
        // Update compliance dashboards
        complianceBreachService.updateComplianceDashboard(
                breachData.getRegulation(),
                breachData.getBreachType(),
                breachData.getSeverity(),
                impact.getImpactLevel()
        );

        // Update regulatory risk scoring
        complianceBreachService.updateRegulatoryRiskScoring(
                breachData.getRegulation(),
                breachData.getAffectedEntity(),
                impact.getRiskScore()
        );

        // Schedule compliance audits if required
        if (impact.requiresAudit()) {
            complianceAuditService.scheduleComplianceAudit(
                    breachData.getRegulation(),
                    breachData.getAffectedEntity(),
                    impact.getAuditRequirements()
            );
        }
    }

    private void notifyLegalTeam(ComplianceBreachData breachData, 
                               RegulatoryImpactAssessment impact,
                               String incidentId) {
        // Legal team notifications based on impact severity
        if (impact.requiresLegalReview()) {
            legalNotificationService.sendLegalTeamNotification(
                    "Compliance Breach Requires Legal Review",
                    breachData,
                    impact,
                    incidentId
            );
        }

        // External legal counsel notifications for critical breaches
        if ("CRITICAL".equals(breachData.getSeverity()) && impact.requiresExternalCounsel()) {
            legalNotificationService.sendExternalCounselNotification(
                    "Critical Compliance Breach - External Legal Support Required",
                    breachData,
                    incidentId
            );
        }
    }

    private void processCustomerNotifications(ComplianceBreachData breachData) {
        // Customer notifications based on data subject impact
        if (breachData.getDataSubjects() != null && breachData.getDataSubjects() > 0) {
            if (requiresCustomerNotification(breachData)) {
                complianceWorkflowService.scheduleCustomerNotifications(
                        breachData.getAffectedAccounts(),
                        breachData.getBreachType(),
                        breachData.getCustomerImpact()
                );
            }
        }
    }

    private boolean requiresCustomerNotification(ComplianceBreachData breachData) {
        // GDPR requires customer notification for high-risk breaches
        if ("GDPR".equals(breachData.getRegulation()) && 
            ("HIGH".equals(breachData.getSeverity()) || "CRITICAL".equals(breachData.getSeverity()))) {
            return true;
        }
        
        // PCI DSS requires customer notification for payment data breaches
        if ("PCI_DSS".equals(breachData.getRegulation()) && 
            breachData.getBreachType().contains("PAYMENT_DATA")) {
            return true;
        }
        
        return false;
    }

    private void executeEmergencyComplianceProcedures(ComplianceBreachData breachData, Exception error) {
        log.error("EMERGENCY: Executing emergency compliance procedures due to processing failure");
        
        try {
            // Emergency system protection
            if ("CRITICAL".equals(breachData.getSeverity())) {
                complianceBreachService.executeEmergencyProtection(breachData.getAffectedEntity());
            }
            
            // Emergency notification
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: Compliance Breach Processing Failed",
                    String.format("Failed to process compliance breach for %s: %s", 
                            breachData.getRegulation(), error.getMessage())
            );
            
            // Manual intervention alert
            legalNotificationService.escalateToManualIntervention(
                    breachData.getBreachId(),
                    "COMPLIANCE_BREACH_PROCESSING_FAILED",
                    error.getMessage()
            );
            
        } catch (Exception e) {
            log.error("EMERGENCY: Emergency compliance procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("COMPLIANCE: Breach alert validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "COMPLIANCE_BREACH_VALIDATION_ERROR",
                null,
                "Compliance breach validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditComplianceBreachProcessing(ComplianceBreachData breachData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "COMPLIANCE_BREACH_PROCESSED",
                    breachData != null ? breachData.getAffectedEntity() : null,
                    String.format("Compliance breach processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "breachId", breachData != null ? breachData.getBreachId() : "unknown",
                            "regulation", breachData != null ? breachData.getRegulation() : "unknown",
                            "breachType", breachData != null ? breachData.getBreachType() : "unknown",
                            "severity", breachData != null ? breachData.getSeverity() : "unknown",
                            "affectedEntity", breachData != null ? breachData.getAffectedEntity() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit compliance breach processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Compliance breach event sent to DLT - EventId: {}", event.getEventId());
        
        try {
            ComplianceBreachData breachData = extractBreachData(event.getPayload());
            
            // Emergency compliance procedures for DLT events
            complianceBreachService.executeEmergencyProtection(breachData.getAffectedEntity());
            
            // Critical alert
            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: Compliance Breach in DLT",
                    "Compliance breach could not be processed - immediate manual intervention required"
            );
            
        } catch (Exception e) {
            log.error("Failed to handle compliance breach DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleComplianceBreachFailure(GenericKafkaEvent event, String topic, int partition,
                                            long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for compliance breach processing - EventId: {}", 
                event.getEventId(), e);

        try {
            ComplianceBreachData breachData = extractBreachData(event.getPayload());
            
            // Emergency protection
            complianceBreachService.executeEmergencyProtection(breachData.getAffectedEntity());

            // Emergency alert
            legalNotificationService.sendEmergencySystemAlert(
                    "Compliance Breach Circuit Breaker Open",
                    "Compliance breach processing is failing - regulatory compliance severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle compliance breach circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class ComplianceBreachData {
        private String breachId;
        private String regulation;
        private String breachType;
        private String severity;
        private String description;
        private String affectedEntity;
        private List<String> affectedAccounts;
        private List<String> regulatoryRequirements;
        private List<String> violatedRules;
        private List<String> potentialPenalties;
        private Map<String, Object> reportingRequirements;
        private List<String> remediationActions;
        private Map<String, Object> breachContext;
        private String detectionMethod;
        private Instant detectedAt;
        private Instant reportingDeadline;
        private String businessImpact;
        private String customerImpact;
        private Integer dataSubjects;
        private List<String> jurisdictions;
    }

    @lombok.Data
    @lombok.Builder
    public static class RegulatoryImpactAssessment {
        private String impactLevel;
        private Double riskScore;
        private List<String> reportsRequired;
        private boolean requiresAudit;
        private boolean requiresLegalReview;
        private boolean requiresExternalCounsel;
        private Map<String, String> auditRequirements;
    }
}