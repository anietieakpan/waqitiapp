package com.waqiti.audit.service;

import com.waqiti.audit.domain.AuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Audit Notification Service for sending audit-related notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditNotificationService {

    private final AuditService auditService;
    
    public void sendNotification(String notificationType, String recipient, String message) {
        log.info("Sending audit notification: type={}, recipient={}", notificationType, recipient);
        
        auditService.logEvent("AUDIT_NOTIFICATION_SENT",
            java.util.Map.of(
                "notificationType", notificationType,
                "recipient", recipient,
                "message", message,
                "sentAt", java.time.LocalDateTime.now()
            ));
    }
    
    public void sendCriticalAlert(String alertType, String message) {
        log.warn("Sending critical audit alert: type={}, message={}", alertType, message);

        auditService.logEvent("CRITICAL_AUDIT_ALERT",
            java.util.Map.of(
                "alertType", alertType,
                "message", message,
                "severity", "CRITICAL",
                "sentAt", java.time.LocalDateTime.now()
            ));
    }

    public void sendAuditAlertNotification(AuditAlert alert, String correlationId) {
        log.info("Sending audit alert notification: alertId={}, type={}, severity={}, correlationId={}",
            alert.getId(), alert.getAlertType(), alert.getSeverity(), correlationId);

        auditService.logEvent("AUDIT_ALERT_NOTIFICATION_SENT",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity().toString(),
                "service", alert.getService(),
                "correlationId", correlationId,
                "sentAt", java.time.LocalDateTime.now()
            ));
    }

    public void sendCriticalAuditAlert(String title, String message, Map<String, String> metadata) {
        log.error("CRITICAL: Sending critical audit alert: title={}, message={}", title, message);

        auditService.logEvent("CRITICAL_AUDIT_ALERT_SENT",
            java.util.Map.of(
                "title", title,
                "message", message,
                "metadata", metadata,
                "severity", "CRITICAL",
                "sentAt", java.time.LocalDateTime.now()
            ));
    }

    /**
     * Send report request notification
     */
    public void sendReportRequestNotification(String reportType, String requestedBy,
                                             String reportFormat, boolean scheduled,
                                             String correlationId) {
        log.info("Sending report request notification: type={}, requestedBy={}, correlationId={}",
                reportType, requestedBy, correlationId);

        auditService.logEvent("AUDIT_REPORT_REQUEST_NOTIFICATION",
            Map.of(
                "reportType", reportType,
                "requestedBy", requestedBy,
                "reportFormat", reportFormat,
                "scheduled", scheduled,
                "correlationId", correlationId,
                "sentAt", java.time.LocalDateTime.now()
            ));
    }

    /**
     * Send report failure notification
     */
    public void sendReportFailureNotification(String reportType, String requestedBy,
                                             String errorMessage, String correlationId) {
        log.error("Sending report failure notification: type={}, requestedBy={}, error={}, correlationId={}",
                reportType, requestedBy, errorMessage, correlationId);

        auditService.logEvent("AUDIT_REPORT_FAILURE_NOTIFICATION",
            Map.of(
                "reportType", reportType,
                "requestedBy", requestedBy,
                "errorMessage", errorMessage,
                "correlationId", correlationId,
                "severity", "HIGH",
                "sentAt", java.time.LocalDateTime.now()
            ));
    }
}