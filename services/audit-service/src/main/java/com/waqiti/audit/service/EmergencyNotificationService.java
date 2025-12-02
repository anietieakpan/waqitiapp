package com.waqiti.audit.service;

import com.waqiti.audit.domain.CriticalAuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Emergency Notification Service for urgent audit notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmergencyNotificationService {

    private final AuditNotificationService notificationService;
    private final AuditService auditService;
    
    public void sendEmergencyNotification(String emergencyType, String message) {
        log.error("Sending emergency notification: type={}, message={}", emergencyType, message);

        // Log the emergency
        auditService.logEvent("EMERGENCY_NOTIFICATION",
            java.util.Map.of(
                "emergencyType", emergencyType,
                "message", message,
                "severity", "EMERGENCY",
                "timestamp", java.time.LocalDateTime.now()
            ));

        // Send critical alert
        notificationService.sendCriticalAlert(emergencyType, message);
    }

    public void sendCriticalAlertNotification(CriticalAuditAlert alert, String correlationId) {
        log.error("EMERGENCY: Sending critical alert notification - alertId={}, type={}, correlationId={}",
            alert.getId(), alert.getAlertType(), correlationId);

        auditService.logEvent("EMERGENCY_CRITICAL_ALERT_NOTIFICATION",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", "CRITICAL",
                "service", alert.getService(),
                "incidentCategory", alert.getIncidentCategory(),
                "escalationLevel", alert.getEscalationLevel().toString(),
                "correlationId", correlationId,
                "sentAt", java.time.LocalDateTime.now()
            ));
    }

    public void sendEmergencyEscalation(CriticalAuditAlert alert, String correlationId) {
        log.error("EMERGENCY ESCALATION: Sending emergency escalation - alertId={}, type={}, correlationId={}",
            alert.getId(), alert.getAlertType(), correlationId);

        auditService.logEvent("EMERGENCY_ESCALATION_SENT",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", "CRITICAL",
                "service", alert.getService(),
                "escalationLevel", "EMERGENCY",
                "correlationId", correlationId,
                "sentAt", java.time.LocalDateTime.now()
            ));
    }
}