package com.waqiti.audit.service;

import com.waqiti.audit.domain.AuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Audit Alert Service for managing audit alerts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditAlertService {

    private final AuditService auditService;
    
    public void createAlert(String alertType, String severity, String message, Object alertData) {
        log.info("Creating audit alert: type={}, severity={}, message={}", alertType, severity, message);
        
        auditService.logEvent("AUDIT_ALERT_CREATED",
            java.util.Map.of(
                "alertType", alertType,
                "severity", severity,
                "message", message,
                "timestamp", java.time.LocalDateTime.now()
            ));
    }
    
    public void processAlert(String alertId) {
        log.info("Processing audit alert: {}", alertId);

        auditService.logEvent("AUDIT_ALERT_PROCESSED",
            java.util.Map.of(
                "alertId", alertId,
                "processedAt", java.time.LocalDateTime.now()
            ));
    }

    public void processAuditAlert(AuditAlert alert, String correlationId) {
        log.info("Processing audit alert: alertId={}, type={}, severity={}, correlationId={}",
            alert.getId(), alert.getAlertType(), alert.getSeverity(), correlationId);

        auditService.logEvent("AUDIT_ALERT_PROCESSING",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity().toString(),
                "service", alert.getService(),
                "correlationId", correlationId,
                "processedAt", java.time.LocalDateTime.now()
            ));
    }
}