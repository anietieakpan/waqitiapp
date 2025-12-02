package com.waqiti.audit.service;

import com.waqiti.audit.domain.CriticalAuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Incident Management Service for handling security and audit incidents
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentManagementService {

    private final AuditService auditService;
    private final EmergencyNotificationService emergencyNotificationService;
    
    public void createIncident(String incidentType, String severity, Object incidentData) {
        log.warn("Creating incident: type={}, severity={}", incidentType, severity);
        
        auditService.logEvent("INCIDENT_CREATED",
            java.util.Map.of(
                "incidentType", incidentType,
                "severity", severity,
                "createdAt", java.time.LocalDateTime.now(),
                "status", "OPEN"
            ));
        
        if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
            emergencyNotificationService.sendEmergencyNotification(incidentType, 
                String.format("High severity incident created: %s", incidentType));
        }
    }
    
    public void updateIncidentStatus(String incidentId, String status) {
        log.info("Updating incident status: id={}, status={}", incidentId, status);

        auditService.logEvent("INCIDENT_STATUS_UPDATED",
            java.util.Map.of(
                "incidentId", incidentId,
                "status", status,
                "updatedAt", java.time.LocalDateTime.now()
            ));
    }

    public void createCriticalIncident(CriticalAuditAlert alert, String correlationId) {
        log.error("Creating critical incident for alert: alertId={}, type={}, correlationId={}",
            alert.getId(), alert.getAlertType(), correlationId);

        String incidentId = UUID.randomUUID().toString();

        auditService.logEvent("CRITICAL_INCIDENT_CREATED",
            java.util.Map.of(
                "incidentId", incidentId,
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "severity", "CRITICAL",
                "service", alert.getService(),
                "incidentCategory", alert.getIncidentCategory(),
                "escalationLevel", alert.getEscalationLevel().toString(),
                "correlationId", correlationId,
                "createdAt", java.time.LocalDateTime.now(),
                "status", "OPEN"
            ));
    }

    public void escalateSecurityIncident(CriticalAuditAlert alert, String correlationId) {
        log.error("SECURITY: Escalating security incident - alertId={}, type={}, correlationId={}",
            alert.getId(), alert.getAlertType(), correlationId);

        auditService.logEvent("SECURITY_INCIDENT_ESCALATED",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "service", alert.getService(),
                "correlationId", correlationId,
                "escalatedAt", java.time.LocalDateTime.now()
            ));
    }

    public void initiateDataBreachProtocol(CriticalAuditAlert alert, String correlationId) {
        log.error("DATA BREACH: Initiating data breach protocol - alertId={}, correlationId={}",
            alert.getId(), correlationId);

        auditService.logEvent("DATA_BREACH_PROTOCOL_INITIATED",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "service", alert.getService(),
                "correlationId", correlationId,
                "initiatedAt", java.time.LocalDateTime.now()
            ));
    }

    public void initiateSystemLockdown(CriticalAuditAlert alert, String correlationId) {
        log.error("SYSTEM LOCKDOWN: Initiating system lockdown - alertId={}, correlationId={}",
            alert.getId(), correlationId);

        auditService.logEvent("SYSTEM_LOCKDOWN_INITIATED",
            java.util.Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "service", alert.getService(),
                "correlationId", correlationId,
                "initiatedAt", java.time.LocalDateTime.now()
            ));
    }
}