package com.waqiti.audit.service;

import com.waqiti.audit.domain.CriticalAuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Regulatory Escalation Service for compliance and regulatory escalations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegulatoryEscalationService {

    private final ComplianceReportingService complianceReportingService;
    private final EmergencyNotificationService emergencyNotificationService;
    
    public void escalateRegulatoryIssue(String regulationType, String issueDescription, Object issueData) {
        log.error("Escalating regulatory issue: type={}, description={}", regulationType, issueDescription);

        complianceReportingService.recordComplianceEvent("REGULATORY_ESCALATION", regulationType, issueData);

        emergencyNotificationService.sendEmergencyNotification("REGULATORY_ESCALATION",
            String.format("Regulatory issue escalated: %s - %s", regulationType, issueDescription));
    }

    public void escalateComplianceViolation(CriticalAuditAlert alert, String correlationId) {
        log.error("REGULATORY: Escalating compliance violation - alertId={}, type={}, correlationId={}",
            alert.getId(), alert.getAlertType(), correlationId);

        complianceReportingService.recordComplianceEvent("COMPLIANCE_VIOLATION_ESCALATED",
            alert.getAlertType(),
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "service", alert.getService(),
                "incidentCategory", alert.getIncidentCategory(),
                "correlationId", correlationId,
                "escalatedAt", java.time.LocalDateTime.now()
            ));
    }
}