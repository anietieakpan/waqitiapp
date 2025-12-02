package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade consumer for failed AML alert events
 * Handles critical AML compliance failures with immediate regulatory escalation
 * Provides emergency compliance protection and regulatory reporting
 *
 * Critical for: AML compliance, regulatory reporting, financial crime prevention
 * SLA: Must process failed AML alerts within 5 seconds for regulatory compliance
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AmlAlertFailedConsumer {

    private final AmlComplianceService amlComplianceService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final FinancialCrimePreventionService financialCrimeService;
    private final ComplianceIncidentService complianceIncidentService;
    private final EmergencyComplianceService emergencyComplianceService;
    private final AMLInvestigationService amlInvestigationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 72-hour TTL for compliance events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 72;

    // Metrics
    private final Counter failedAmlAlertCounter = Counter.builder("aml_alert_failed_processed_total")
            .description("Total number of failed AML alert events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalAmlFailureCounter = Counter.builder("critical_aml_failures_total")
            .description("Total number of critical AML failures processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("aml_alert_failed_processing_duration")
            .description("Time taken to process failed AML alert events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"compliance.aml.alert.failed"},
        groupId = "compliance-service-aml-alert-failed-processor",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "aml-alert-failed-processor", fallbackMethod = "handleAmlAlertFailedFailure")
    @Retry(name = "aml-alert-failed-processor")
    public void processAmlAlertFailed(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("AML ALERT FAILED: Processing failed AML alert: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("AML alert failed event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate AML alert failure data
            AmlAlertFailureData alertData = extractAlertFailureData(event.getPayload());
            validateAlertFailureData(alertData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Critical compliance and regulatory assessment
            AmlFailureComplianceAssessment assessment = assessAmlFailureCompliance(alertData, event);

            // Process failed AML alert with emergency compliance procedures
            processFailedAmlAlert(alertData, assessment, event);

            // Record successful processing metrics
            failedAmlAlertCounter.increment();

            if ("CRITICAL".equals(assessment.getComplianceLevel())) {
                criticalAmlFailureCounter.increment();
            }

            // Audit the AML failure processing
            auditAmlAlertFailureProcessing(alertData, event, assessment, "SUCCESS");

            log.error("AML ALERT FAILED: Successfully processed AML failure: {} - Compliance: {} Amount: {} Action: {}",
                    eventId, assessment.getComplianceLevel(), alertData.getTransactionAmount(),
                    assessment.getRegulatoryAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("AML ALERT FAILED: Invalid AML alert failure data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("AML ALERT FAILED: Failed to process AML alert failure event: {}", eventId, e);
            auditAmlAlertFailureProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("AML alert failure processing failed", e);

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

    private AmlAlertFailureData extractAlertFailureData(Map<String, Object> payload) {
        return AmlAlertFailureData.builder()
                .eventId(extractString(payload, "eventId"))
                .alertId(extractString(payload, "alertId"))
                .accountId(extractString(payload, "accountId"))
                .customerId(extractString(payload, "customerId"))
                .transactionId(extractString(payload, "transactionId"))
                .alertType(extractString(payload, "alertType"))
                .failureReason(extractString(payload, "failureReason"))
                .originalAlertData(extractMap(payload, "originalAlertData"))
                .transactionAmount(extractBigDecimal(payload, "transactionAmount"))
                .currency(extractString(payload, "currency"))
                .failedTimestamp(extractInstant(payload, "failedTimestamp"))
                .originalAlertTimestamp(extractInstant(payload, "originalAlertTimestamp"))
                .riskScore(extractDouble(payload, "riskScore"))
                .riskLevel(extractString(payload, "riskLevel"))
                .suspiciousActivities(extractStringList(payload, "suspiciousActivities"))
                .amlIndicators(extractStringList(payload, "amlIndicators"))
                .counterpartyInfo(extractMap(payload, "counterpartyInfo"))
                .geographicRisks(extractStringList(payload, "geographicRisks"))
                .regulatoryRequirements(extractStringList(payload, "regulatoryRequirements"))
                .complianceFlags(extractStringList(payload, "complianceFlags"))
                .investigationRequired(extractBoolean(payload, "investigationRequired"))
                .reportingDeadline(extractInstant(payload, "reportingDeadline"))
                .jurisdictions(extractStringList(payload, "jurisdictions"))
                .relatedTransactions(extractStringList(payload, "relatedTransactions"))
                .customerProfile(extractMap(payload, "customerProfile"))
                .businessImpact(extractString(payload, "businessImpact"))
                .build();
    }

    private void validateAlertFailureData(AmlAlertFailureData alertData) {
        if (alertData.getEventId() == null || alertData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (alertData.getAlertId() == null || alertData.getAlertId().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert ID is required");
        }

        if (alertData.getAlertType() == null || alertData.getAlertType().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert type is required");
        }

        List<String> validAlertTypes = List.of(
            "SUSPICIOUS_TRANSACTION", "STRUCTURING", "LAYERING", "PLACEMENT",
            "SMURFING", "CASH_INTENSIVE_BUSINESS", "HIGH_RISK_JURISDICTION",
            "PEP_TRANSACTION", "SANCTIONS_SCREENING", "UNUSUAL_PATTERN"
        );
        if (!validAlertTypes.contains(alertData.getAlertType())) {
            throw new IllegalArgumentException("Invalid alert type: " + alertData.getAlertType());
        }

        if (alertData.getFailureReason() == null || alertData.getFailureReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason is required");
        }

        if (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction amount cannot be negative");
        }

        List<String> validRiskLevels = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (alertData.getRiskLevel() != null && !validRiskLevels.contains(alertData.getRiskLevel())) {
            throw new IllegalArgumentException("Invalid risk level: " + alertData.getRiskLevel());
        }
    }

    private AmlFailureComplianceAssessment assessAmlFailureCompliance(AmlAlertFailureData alertData, GenericKafkaEvent event) {
        // Critical compliance assessment for failed AML alerts
        String complianceLevel = determineComplianceLevel(alertData);
        String regulatoryAction = determineRegulatoryAction(alertData, complianceLevel);
        boolean requiresImmediateEscalation = requiresImmediateEscalation(alertData, complianceLevel);
        List<String> complianceMeasures = determineComplianceMeasures(alertData, complianceLevel);
        String regulatoryImpact = assessRegulatoryImpact(alertData);

        return AmlFailureComplianceAssessment.builder()
                .complianceLevel(complianceLevel)
                .regulatoryAction(regulatoryAction)
                .requiresImmediateEscalation(requiresImmediateEscalation)
                .complianceMeasures(complianceMeasures)
                .regulatoryImpact(regulatoryImpact)
                .executiveNotificationRequired(determineExecutiveNotification(alertData, complianceLevel))
                .regulatoryReportingRequired(determineRegulatoryReporting(alertData))
                .emergencyInvestigationRequired(determineEmergencyInvestigation(alertData, complianceLevel))
                .customerSuspensionRequired(determineCustomerSuspension(alertData))
                .lawEnforcementNotificationRequired(determineLawEnforcementNotification(alertData))
                .internationalReportingRequired(determineInternationalReporting(alertData))
                .build();
    }

    private String determineComplianceLevel(AmlAlertFailureData alertData) {
        if (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("100000")) > 0) {
            return "CRITICAL";
        } else if ("CRITICAL".equals(alertData.getRiskLevel()) ||
                   alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
                   alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION")) {
            return "CRITICAL";
        } else if ("HIGH".equals(alertData.getRiskLevel()) ||
                   alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("50000")) > 0) {
            return "HIGH";
        } else if (alertData.getComplianceFlags().contains("REGULATORY_ATTENTION") ||
                   alertData.getGeographicRisks().contains("HIGH_RISK_JURISDICTION")) {
            return "ELEVATED";
        } else {
            return "STANDARD";
        }
    }

    private String determineRegulatoryAction(AmlAlertFailureData alertData, String complianceLevel) {
        if ("CRITICAL".equals(complianceLevel)) {
            if (alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING")) {
                return "IMMEDIATE_REGULATORY_NOTIFICATION_AND_FREEZE";
            } else if (alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION")) {
                return "EMERGENCY_SANCTIONS_COMPLIANCE";
            } else {
                return "IMMEDIATE_SAR_FILING_AND_INVESTIGATION";
            }
        } else if ("HIGH".equals(complianceLevel)) {
            return "EXPEDITED_INVESTIGATION_AND_REPORTING";
        } else {
            return "STANDARD_COMPLIANCE_RECOVERY";
        }
    }

    private boolean requiresImmediateEscalation(AmlAlertFailureData alertData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) ||
               alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
               alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION") ||
               (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("500000")) > 0);
    }

    private List<String> determineComplianceMeasures(AmlAlertFailureData alertData, String complianceLevel) {
        if ("CRITICAL".equals(complianceLevel)) {
            return List.of(
                "IMMEDIATE_ACCOUNT_FREEZE",
                "EMERGENCY_SAR_FILING",
                "REGULATORY_NOTIFICATION",
                "LAW_ENFORCEMENT_ALERT",
                "TRANSACTION_MONITORING_ENHANCEMENT",
                "CUSTOMER_DUE_DILIGENCE_REVIEW",
                "SANCTIONS_SCREENING_VERIFICATION"
            );
        } else if ("HIGH".equals(complianceLevel)) {
            return List.of(
                "ENHANCED_MONITORING",
                "EXPEDITED_INVESTIGATION",
                "REGULATORY_REPORTING",
                "CUSTOMER_REVIEW",
                "TRANSACTION_ANALYSIS"
            );
        } else {
            return List.of(
                "ALERT_REPROCESSING",
                "INVESTIGATION_INITIATION",
                "COMPLIANCE_REVIEW"
            );
        }
    }

    private String assessRegulatoryImpact(AmlAlertFailureData alertData) {
        if (alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
            alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION")) {
            return "SEVERE";
        } else if (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("100000")) > 0) {
            return "HIGH";
        } else if ("CRITICAL".equals(alertData.getRiskLevel())) {
            return "HIGH";
        } else if (alertData.getComplianceFlags().contains("REGULATORY_ATTENTION")) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private boolean determineExecutiveNotification(AmlAlertFailureData alertData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) ||
               alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
               (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("1000000")) > 0);
    }

    private boolean determineRegulatoryReporting(AmlAlertFailureData alertData) {
        return alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0 ||
               alertData.getComplianceFlags().contains("SAR_REQUIRED") ||
               alertData.getRegulatoryRequirements().contains("IMMEDIATE_REPORTING");
    }

    private boolean determineEmergencyInvestigation(AmlAlertFailureData alertData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) ||
               alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
               alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION");
    }

    private boolean determineCustomerSuspension(AmlAlertFailureData alertData) {
        return alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
               alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION") ||
               (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("500000")) > 0);
    }

    private boolean determineLawEnforcementNotification(AmlAlertFailureData alertData) {
        return alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
               alertData.getAmlIndicators().contains("CRIMINAL_ACTIVITY") ||
               (alertData.getTransactionAmount() != null && alertData.getTransactionAmount().compareTo(new BigDecimal("1000000")) > 0);
    }

    private boolean determineInternationalReporting(AmlAlertFailureData alertData) {
        return alertData.getGeographicRisks().contains("HIGH_RISK_JURISDICTION") ||
               alertData.getJurisdictions().size() > 1 ||
               alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING");
    }

    private void processFailedAmlAlert(AmlAlertFailureData alertData,
                                     AmlFailureComplianceAssessment assessment,
                                     GenericKafkaEvent event) {
        log.error("AML ALERT FAILED: Processing failed AML alert - Alert: {}, Amount: {}, Type: {}, Compliance: {}",
                alertData.getAlertId(), alertData.getTransactionAmount(),
                alertData.getAlertType(), assessment.getComplianceLevel());

        try {
            // Create critical compliance incident
            String incidentId = complianceIncidentService.createAmlFailureIncident(alertData, assessment);

            // Execute immediate compliance measures
            executeComplianceMeasures(alertData, assessment, incidentId);

            // Send immediate notifications
            sendImmediateNotifications(alertData, assessment, incidentId);

            // Emergency investigation if required
            if (assessment.isEmergencyInvestigationRequired()) {
                initiateEmergencyInvestigation(alertData, assessment, incidentId);
            }

            // Customer suspension if required
            if (assessment.isCustomerSuspensionRequired()) {
                suspendCustomer(alertData, assessment, incidentId);
            }

            // Regulatory reporting if required
            if (assessment.isRegulatoryReportingRequired()) {
                initiateRegulatoryReporting(alertData, assessment, incidentId);
            }

            // Law enforcement notification if required
            if (assessment.isLawEnforcementNotificationRequired()) {
                notifyLawEnforcement(alertData, assessment, incidentId);
            }

            // International reporting if required
            if (assessment.isInternationalReportingRequired()) {
                initiateInternationalReporting(alertData, assessment, incidentId);
            }

            // Executive notification if required
            if (assessment.isExecutiveNotificationRequired()) {
                escalateToExecutives(alertData, assessment, incidentId);
            }

            log.error("AML ALERT FAILED: AML failure processed - IncidentId: {}, ComplianceApplied: {}, RegulatoryImpact: {}",
                    incidentId, assessment.getComplianceMeasures().size(), assessment.getRegulatoryImpact());

        } catch (Exception e) {
            log.error("AML ALERT FAILED: Failed to process AML alert failure for alert: {}", alertData.getAlertId(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(alertData, e);

            throw new RuntimeException("AML alert failure processing failed", e);
        }
    }

    private void executeComplianceMeasures(AmlAlertFailureData alertData,
                                         AmlFailureComplianceAssessment assessment,
                                         String incidentId) {
        // Execute regulatory action based on assessment
        switch (assessment.getRegulatoryAction()) {
            case "IMMEDIATE_REGULATORY_NOTIFICATION_AND_FREEZE":
                emergencyComplianceService.immediateRegulatoryNotification(alertData, incidentId);
                emergencyComplianceService.emergencyAccountFreeze(alertData.getAccountId(), incidentId);
                break;

            case "EMERGENCY_SANCTIONS_COMPLIANCE":
                emergencyComplianceService.emergencySanctionsCompliance(alertData, incidentId);
                break;

            case "IMMEDIATE_SAR_FILING_AND_INVESTIGATION":
                regulatoryReportingService.emergencySarFiling(alertData, incidentId);
                amlInvestigationService.initiateEmergencyInvestigation(alertData.getAlertId(), incidentId);
                break;

            case "EXPEDITED_INVESTIGATION_AND_REPORTING":
                amlInvestigationService.initiateExpeditedInvestigation(alertData.getAlertId(), incidentId);
                regulatoryReportingService.expeditedReporting(alertData, incidentId);
                break;

            default:
                amlComplianceService.standardComplianceRecovery(alertData.getAlertId(), alertData);
        }

        // Apply additional compliance measures
        for (String measure : assessment.getComplianceMeasures()) {
            try {
                switch (measure) {
                    case "IMMEDIATE_ACCOUNT_FREEZE":
                        emergencyComplianceService.freezeAccount(alertData.getAccountId(), incidentId);
                        break;

                    case "EMERGENCY_SAR_FILING":
                        regulatoryReportingService.emergencySarFiling(alertData, incidentId);
                        break;

                    case "REGULATORY_NOTIFICATION":
                        regulatoryReportingService.notifyRegulators(alertData, assessment, incidentId);
                        break;

                    case "LAW_ENFORCEMENT_ALERT":
                        financialCrimeService.alertLawEnforcement(alertData, incidentId);
                        break;

                    case "TRANSACTION_MONITORING_ENHANCEMENT":
                        amlComplianceService.enhanceTransactionMonitoring(alertData.getAccountId(), incidentId);
                        break;

                    case "CUSTOMER_DUE_DILIGENCE_REVIEW":
                        amlComplianceService.initiateCustomerDueDiligenceReview(alertData.getCustomerId(), incidentId);
                        break;

                    case "SANCTIONS_SCREENING_VERIFICATION":
                        amlComplianceService.verifySanctionsScreening(alertData.getCustomerId(), incidentId);
                        break;

                    default:
                        amlComplianceService.applyGenericComplianceMeasure(measure, alertData, incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to apply compliance measure: {}", measure, e);
            }
        }
    }

    private void sendImmediateNotifications(AmlAlertFailureData alertData,
                                          AmlFailureComplianceAssessment assessment,
                                          String incidentId) {
        // Critical compliance levels require immediate escalation
        if ("CRITICAL".equals(assessment.getComplianceLevel()) || assessment.isRequiresImmediateEscalation()) {
            notificationService.sendCriticalAlert(
                    "CRITICAL AML ALERT FAILURE - REGULATORY EMERGENCY",
                    String.format("Critical AML alert processing failed: Alert %s, Amount: %s, Type: %s, Impact: %s",
                            alertData.getAlertId(),
                            alertData.getTransactionAmount() != null ? alertData.getTransactionAmount().toString() : "N/A",
                            alertData.getAlertType(), assessment.getRegulatoryImpact()),
                    Map.of(
                            "alertId", alertData.getAlertId(),
                            "transactionAmount", alertData.getTransactionAmount() != null ? alertData.getTransactionAmount().toString() : "0",
                            "alertType", alertData.getAlertType(),
                            "complianceLevel", assessment.getComplianceLevel(),
                            "regulatoryImpact", assessment.getRegulatoryImpact(),
                            "incidentId", incidentId,
                            "regulatoryAction", assessment.getRegulatoryAction()
                    )
            );

            // Page compliance team for critical failures
            notificationService.pageComplianceTeam(
                    "AML_ALERT_FAILURE_CRITICAL",
                    alertData.getAlertType(),
                    assessment.getComplianceLevel(),
                    incidentId
            );

            // Page legal team for regulatory violations
            if (alertData.getSuspiciousActivities().contains("TERRORISM_FINANCING") ||
                alertData.getAmlIndicators().contains("SANCTIONS_VIOLATION")) {
                notificationService.pageLegalTeam(
                    "AML_REGULATORY_VIOLATION",
                    alertData.getAlertType(),
                    assessment.getRegulatoryImpact(),
                    incidentId
                );
            }
        }
    }

    private void initiateEmergencyInvestigation(AmlAlertFailureData alertData,
                                              AmlFailureComplianceAssessment assessment,
                                              String incidentId) {
        // Initiate emergency AML investigation
        amlInvestigationService.initiateEmergencyInvestigation(
                alertData.getAlertId(),
                alertData.getCustomerId(),
                alertData.getAccountId(),
                assessment.getComplianceLevel(),
                incidentId
        );

        // Enhanced transaction analysis
        amlInvestigationService.analyzeRelatedTransactions(
                alertData.getRelatedTransactions(),
                alertData.getSuspiciousActivities(),
                incidentId
        );
    }

    private void suspendCustomer(AmlAlertFailureData alertData,
                               AmlFailureComplianceAssessment assessment,
                               String incidentId) {
        // Emergency customer suspension for critical violations
        emergencyComplianceService.suspendCustomer(
                alertData.getCustomerId(),
                alertData.getAccountId(),
                "AML_ALERT_FAILURE_SUSPENSION",
                incidentId
        );
    }

    private void initiateRegulatoryReporting(AmlAlertFailureData alertData,
                                           AmlFailureComplianceAssessment assessment,
                                           String incidentId) {
        // Generate regulatory reports based on jurisdiction and requirements
        for (String requirement : alertData.getRegulatoryRequirements()) {
            try {
                switch (requirement) {
                    case "SAR_FILING":
                        regulatoryReportingService.fileSuspiciousActivityReport(alertData, incidentId);
                        break;

                    case "CTR_FILING":
                        regulatoryReportingService.fileCurrencyTransactionReport(alertData, incidentId);
                        break;

                    case "OFAC_REPORTING":
                        regulatoryReportingService.fileOfacReport(alertData, incidentId);
                        break;

                    case "FINTEN_REPORTING":
                        regulatoryReportingService.fileFintenReport(alertData, incidentId);
                        break;

                    default:
                        regulatoryReportingService.fileGenericReport(requirement, alertData, incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to file regulatory report: {}", requirement, e);
            }
        }
    }

    private void notifyLawEnforcement(AmlAlertFailureData alertData,
                                    AmlFailureComplianceAssessment assessment,
                                    String incidentId) {
        // Notify law enforcement for criminal activities
        financialCrimeService.notifyLawEnforcement(
                alertData.getAlertId(),
                alertData.getSuspiciousActivities(),
                alertData.getTransactionAmount(),
                assessment.getRegulatoryImpact(),
                incidentId
        );
    }

    private void initiateInternationalReporting(AmlAlertFailureData alertData,
                                              AmlFailureComplianceAssessment assessment,
                                              String incidentId) {
        // International reporting for cross-border transactions
        for (String jurisdiction : alertData.getJurisdictions()) {
            try {
                regulatoryReportingService.fileInternationalReport(
                        jurisdiction,
                        alertData,
                        assessment,
                        incidentId
                );
            } catch (Exception e) {
                log.error("Failed to file international report for jurisdiction: {}", jurisdiction, e);
            }
        }
    }

    private void escalateToExecutives(AmlAlertFailureData alertData,
                                    AmlFailureComplianceAssessment assessment,
                                    String incidentId) {
        notificationService.sendExecutiveAlert(
                "Critical AML Alert Failure - Executive Action Required",
                String.format("Critical AML compliance failure requiring executive attention: Alert %s, Amount: %s, Impact: %s",
                        alertData.getAlertId(),
                        alertData.getTransactionAmount() != null ? alertData.getTransactionAmount().toString() : "N/A",
                        assessment.getRegulatoryImpact()),
                Map.of(
                        "incidentId", incidentId,
                        "alertId", alertData.getAlertId(),
                        "complianceLevel", assessment.getComplianceLevel(),
                        "regulatoryImpact", assessment.getRegulatoryImpact(),
                        "alertType", alertData.getAlertType()
                )
        );
    }

    private void executeEmergencyFallback(AmlAlertFailureData alertData, Exception error) {
        log.error("EMERGENCY: Executing emergency AML compliance fallback due to processing failure");

        try {
            // Emergency compliance protection
            emergencyComplianceService.emergencyComplianceProtection(
                    alertData.getAccountId(),
                    alertData.getCustomerId()
            );

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: AML Alert Failure Processing Failed",
                    String.format("Failed to process critical AML alert failure for alert %s: %s",
                            alertData.getAlertId(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    alertData.getEventId(),
                    "AML_ALERT_FAILURE_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency AML compliance fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("AML ALERT FAILED: Validation failed for AML alert failure event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "AML_ALERT_FAILURE_VALIDATION_ERROR",
                null,
                "AML alert failure validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditAmlAlertFailureProcessing(AmlAlertFailureData alertData,
                                              GenericKafkaEvent event,
                                              AmlFailureComplianceAssessment assessment,
                                              String status) {
        try {
            auditService.auditSecurityEvent(
                    "AML_ALERT_FAILURE_PROCESSED",
                    alertData != null ? alertData.getCustomerId() : null,
                    String.format("AML alert failure processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "alertId", alertData != null ? alertData.getAlertId() : "unknown",
                            "alertType", alertData != null ? alertData.getAlertType() : "unknown",
                            "transactionAmount", alertData != null && alertData.getTransactionAmount() != null ?
                                alertData.getTransactionAmount().toString() : "0",
                            "complianceLevel", assessment != null ? assessment.getComplianceLevel() : "unknown",
                            "regulatoryAction", assessment != null ? assessment.getRegulatoryAction() : "none",
                            "regulatoryImpact", assessment != null ? assessment.getRegulatoryImpact() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit AML alert failure processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: AML alert failure event sent to DLT - EventId: {}", event.getEventId());

        try {
            AmlAlertFailureData alertData = extractAlertFailureData(event.getPayload());

            // Emergency compliance protection for DLT events
            emergencyComplianceService.emergencyComplianceProtection(
                    alertData.getAccountId(),
                    alertData.getCustomerId()
            );

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: AML Alert Failure in DLT",
                    "Critical AML alert failure could not be processed - immediate regulatory compliance at risk"
            );

        } catch (Exception e) {
            log.error("Failed to handle AML alert failure DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleAmlAlertFailedFailure(GenericKafkaEvent event, String topic, int partition,
                                          long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for AML alert failure processing - EventId: {}",
                event.getEventId(), e);

        try {
            AmlAlertFailureData alertData = extractAlertFailureData(event.getPayload());

            // Emergency protection
            emergencyComplianceService.emergencyComplianceProtection(
                    alertData.getAccountId(),
                    alertData.getCustomerId()
            );

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "AML Alert Failure Circuit Breaker Open",
                    "AML alert failure processing is failing - regulatory compliance severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle AML alert failure circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
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
    public static class AmlAlertFailureData {
        private String eventId;
        private String alertId;
        private String accountId;
        private String customerId;
        private String transactionId;
        private String alertType;
        private String failureReason;
        private Map<String, Object> originalAlertData;
        private BigDecimal transactionAmount;
        private String currency;
        private Instant failedTimestamp;
        private Instant originalAlertTimestamp;
        private Double riskScore;
        private String riskLevel;
        private List<String> suspiciousActivities;
        private List<String> amlIndicators;
        private Map<String, Object> counterpartyInfo;
        private List<String> geographicRisks;
        private List<String> regulatoryRequirements;
        private List<String> complianceFlags;
        private Boolean investigationRequired;
        private Instant reportingDeadline;
        private List<String> jurisdictions;
        private List<String> relatedTransactions;
        private Map<String, Object> customerProfile;
        private String businessImpact;
    }

    @lombok.Data
    @lombok.Builder
    public static class AmlFailureComplianceAssessment {
        private String complianceLevel;
        private String regulatoryAction;
        private boolean requiresImmediateEscalation;
        private List<String> complianceMeasures;
        private String regulatoryImpact;
        private boolean executiveNotificationRequired;
        private boolean regulatoryReportingRequired;
        private boolean emergencyInvestigationRequired;
        private boolean customerSuspensionRequired;
        private boolean lawEnforcementNotificationRequired;
        private boolean internationalReportingRequired;
    }
}