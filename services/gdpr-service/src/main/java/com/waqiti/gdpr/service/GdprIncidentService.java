package com.waqiti.gdpr.service;

import com.waqiti.common.service.IncidentManagementService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * GDPR Incident Service
 * Wrapper around IncidentManagementService for GDPR compliance incidents
 * Handles P0/P1/P2 incident creation for GDPR violations, data breaches, and compliance failures
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprIncidentService {

    private final IncidentManagementService incidentManagementService;
    private final MeterRegistry meterRegistry;

    /**
     * Create GDPR violation incident (P0 - Critical)
     */
    @Async
    public void createGdprViolationIncident(String exportId, String subjectId,
                                            String violationType, String eventId,
                                            String correlationId) {
        log.error("Creating GDPR violation incident: exportId={} subjectId={} violation={} correlationId={}",
                exportId, subjectId, violationType, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "GDPR_VIOLATION");
        incidentData.put("exportId", exportId);
        incidentData.put("subjectId", subjectId);
        incidentData.put("violationType", violationType);
        incidentData.put("eventId", eventId);
        incidentData.put("correlationId", correlationId);
        incidentData.put("regulatoryImpact", "HIGH");
        incidentData.put("potentialFine", "Up to 4% of global revenue");

        try {
            incidentManagementService.createIncident(
                    "P0",
                    "GDPR_VIOLATION",
                    String.format("GDPR Violation in data export - Export: %s - Violation: %s",
                            exportId, violationType),
                    incidentData,
                    correlationId
            );

            recordMetric("gdpr_violation_incidents_total", "violation_type", violationType);

            log.error("GDPR violation incident created: exportId={} correlationId={}",
                    exportId, correlationId);

        } catch (Exception e) {
            log.error("Failed to create GDPR violation incident: exportId={} correlationId={}",
                    exportId, correlationId, e);
        }
    }

    /**
     * Create GDPR compliance failure incident (P1 - High)
     */
    @Async
    public void createComplianceFailureIncident(String exportId, String subjectId,
                                               String failureReason, String correlationId) {
        log.error("Creating GDPR compliance failure incident: exportId={} subjectId={} reason={} correlationId={}",
                exportId, subjectId, failureReason, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "GDPR_COMPLIANCE_FAILURE");
        incidentData.put("exportId", exportId);
        incidentData.put("subjectId", subjectId);
        incidentData.put("failureReason", failureReason);
        incidentData.put("correlationId", correlationId);
        incidentData.put("regulatoryImpact", "MEDIUM");

        try {
            incidentManagementService.createIncident(
                    "P1",
                    "GDPR_COMPLIANCE_FAILURE",
                    String.format("GDPR compliance failure - Export: %s - Reason: %s",
                            exportId, failureReason),
                    incidentData,
                    correlationId
            );

            recordMetric("gdpr_compliance_failure_incidents_total");

            log.error("GDPR compliance failure incident created: exportId={} correlationId={}",
                    exportId, correlationId);

        } catch (Exception e) {
            log.error("Failed to create GDPR compliance failure incident: exportId={} correlationId={}",
                    exportId, correlationId, e);
        }
    }

    /**
     * Create data breach notification incident (P0 - Critical)
     */
    @Async
    public void createDataBreachIncident(String exportId, String subjectId,
                                        String breachType, String correlationId) {
        log.error("CRITICAL: Creating data breach incident: exportId={} subjectId={} breach={} correlationId={}",
                exportId, subjectId, breachType, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "DATA_BREACH");
        incidentData.put("exportId", exportId);
        incidentData.put("subjectId", subjectId);
        incidentData.put("breachType", breachType);
        incidentData.put("correlationId", correlationId);
        incidentData.put("regulatoryImpact", "CRITICAL");
        incidentData.put("requiresAuthorityNotification", true);
        incidentData.put("notificationDeadlineHours", 72); // GDPR Article 33

        try {
            incidentManagementService.createIncident(
                    "P0",
                    "DATA_BREACH",
                    String.format("CRITICAL: Data breach detected - Export: %s - Type: %s - " +
                                    "Requires supervisory authority notification within 72 hours",
                            exportId, breachType),
                    incidentData,
                    correlationId
            );

            recordMetric("data_breach_incidents_total", "breach_type", breachType);

            log.error("Data breach incident created: exportId={} correlationId={}",
                    exportId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create data breach incident: exportId={} correlationId={}",
                    exportId, correlationId, e);
        }
    }

    /**
     * Create legal review case (P1)
     */
    @Async
    public void createLegalReviewCase(String exportId, String caseType,
                                     String caseDetails, String correlationId) {
        log.warn("Creating legal review case: exportId={} type={} correlationId={}",
                exportId, caseType, correlationId);

        Map<String, Object> caseData = new HashMap<>();
        caseData.put("incidentType", "LEGAL_REVIEW_REQUIRED");
        caseData.put("exportId", exportId);
        caseData.put("caseType", caseType);
        caseData.put("caseDetails", caseDetails);
        caseData.put("correlationId", correlationId);
        caseData.put("requiresLegalTeam", true);

        try {
            incidentManagementService.createIncident(
                    "P1",
                    "LEGAL_REVIEW_REQUIRED",
                    String.format("Legal review required - Export: %s - Type: %s",
                            exportId, caseType),
                    caseData,
                    correlationId
            );

            recordMetric("legal_review_cases_total", "case_type", caseType);

            log.warn("Legal review case created: exportId={} correlationId={}",
                    exportId, correlationId);

        } catch (Exception e) {
            log.error("Failed to create legal review case: exportId={} correlationId={}",
                    exportId, correlationId, e);
        }
    }

    /**
     * Create GDPR compliance incident (general)
     */
    @Async
    public void createComplianceIncident(String severity, String incidentType,
                                        String description, Map<String, String> details,
                                        String correlationId) {
        log.warn("Creating GDPR compliance incident: severity={} type={} correlationId={}",
                severity, incidentType, correlationId);

        Map<String, Object> incidentData = new HashMap<>(details);
        incidentData.put("incidentType", incidentType);
        incidentData.put("correlationId", correlationId);
        incidentData.put("category", "GDPR_COMPLIANCE");

        try {
            incidentManagementService.createIncident(
                    severity,
                    incidentType,
                    description,
                    incidentData,
                    correlationId
            );

            recordMetric("gdpr_compliance_incidents_total",
                    "incident_type", incidentType,
                    "severity", severity);

            log.warn("GDPR compliance incident created: type={} severity={} correlationId={}",
                    incidentType, severity, correlationId);

        } catch (Exception e) {
            log.error("Failed to create GDPR compliance incident: type={} correlationId={}",
                    incidentType, correlationId, e);
        }
    }

    private void recordMetric(String metricName, String... tags) {
        Counter.Builder builder = Counter.builder(metricName);

        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                builder.tag(tags[i], tags[i + 1]);
            }
        }

        builder.register(meterRegistry).increment();
    }
}
