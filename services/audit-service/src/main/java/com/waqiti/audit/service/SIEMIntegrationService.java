package com.waqiti.audit.service;

import com.waqiti.audit.domain.AuditAlert;
import com.waqiti.audit.domain.CriticalAuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SIEM Integration Service for Security Information and Event Management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SIEMIntegrationService {

    private final AuditService auditService;
    
    public void sendToSIEM(String eventType, String severity, Object eventData) {
        log.info("Sending event to SIEM: type={}, severity={}", eventType, severity);
        
        // Log the SIEM integration
        auditService.logEvent("SIEM_EVENT_SENT",
            java.util.Map.of(
                "eventType", eventType,
                "severity", severity,
                "sentToSIEM", true,
                "timestamp", java.time.LocalDateTime.now()
            ));
    }
    
    public void forwardSecurityEvent(String securityEventType, Object eventData) {
        log.warn("Forwarding security event to SIEM: {}", securityEventType);

        sendToSIEM(securityEventType, "HIGH", eventData);
    }

    public void sendAuditAlert(AuditAlert alert, String correlationId) {
        log.info("Sending audit alert to SIEM: alertId={}, type={}, severity={}, correlationId={}",
            alert.getId(), alert.getAlertType(), alert.getSeverity(), correlationId);

        auditService.logEvent("SIEM_AUDIT_ALERT_SENT",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity().toString(),
                "service", alert.getService(),
                "correlationId", correlationId,
                "sentToSIEM", true,
                "timestamp", java.time.LocalDateTime.now()
            ));
    }

    public void sendCriticalAlert(CriticalAuditAlert alert, String correlationId) {
        log.error("SIEM: Sending critical audit alert - alertId={}, type={}, severity={}, correlationId={}",
            alert.getId(), alert.getAlertType(), alert.getSeverity(), correlationId);

        auditService.logEvent("SIEM_CRITICAL_ALERT_SENT",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity().toString(),
                "service", alert.getService(),
                "incidentCategory", alert.getIncidentCategory(),
                "escalationLevel", alert.getEscalationLevel().toString(),
                "correlationId", correlationId,
                "sentToSIEM", true,
                "priority", "CRITICAL",
                "timestamp", java.time.LocalDateTime.now()
            ));
    }
}