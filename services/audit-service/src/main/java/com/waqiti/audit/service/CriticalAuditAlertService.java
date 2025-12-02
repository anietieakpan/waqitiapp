package com.waqiti.audit.service;

import com.waqiti.audit.domain.CriticalAuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Critical Audit Alert Service for handling critical alerts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CriticalAuditAlertService {

    private final AuditService auditService;
    private final AuditNotificationService notificationService;
    
    public void processCriticalAlert(String alertType, String severity, Object alertData) {
        log.error("Processing critical audit alert: type={}, severity={}", alertType, severity);

        // Log the critical event
        auditService.logEvent("CRITICAL_AUDIT_ALERT",
            java.util.Map.of(
                "alertType", alertType,
                "severity", severity,
                "timestamp", java.time.LocalDateTime.now(),
                "criticality", "HIGH"
            ));

        // Send immediate notification
        notificationService.sendCriticalAlert(alertType,
            String.format("Critical audit alert: %s (Severity: %s)", alertType, severity));
    }

    public void processCriticalAlert(CriticalAuditAlert alert, String correlationId) {
        log.error("Processing critical audit alert: alertId={}, type={}, severity={}, correlationId={}",
            alert.getId(), alert.getAlertType(), alert.getSeverity(), correlationId);

        auditService.logEvent("CRITICAL_AUDIT_ALERT_PROCESSING",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity().toString(),
                "service", alert.getService(),
                "incidentCategory", alert.getIncidentCategory(),
                "escalationLevel", alert.getEscalationLevel().toString(),
                "correlationId", correlationId,
                "processedAt", java.time.LocalDateTime.now()
            ));
    }
}