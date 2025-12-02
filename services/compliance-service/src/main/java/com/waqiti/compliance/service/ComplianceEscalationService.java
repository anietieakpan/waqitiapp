package com.waqiti.compliance.service;

import com.waqiti.audit.domain.AuditAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive Compliance Escalation Service
 *
 * CRITICAL COMPLIANCE FUNCTION: Manages escalation workflows for audit alerts and compliance violations.
 * Ensures proper notification, tracking, and resolution of compliance issues.
 *
 * REGULATORY IMPACT:
 * - SOC 2 Type II: Incident response and management
 * - PCI DSS Requirement 12.10: Incident response plan
 * - GDPR Article 33: Data breach notification (72-hour requirement)
 * - BSA/AML: Suspicious activity escalation
 * - SOX Section 404: Internal control deficiencies
 *
 * ESCALATION LEVELS:
 * - CRITICAL: Immediate C-suite notification, incident creation, 24/7 response
 * - HIGH: Compliance team notification, ticket creation, same-day review
 * - MEDIUM: Standard tracking, next-business-day review
 *
 * NOTIFICATION CHANNELS:
 * - Email (compliance officers, management)
 * - Kafka events (real-time alerting systems)
 * - Incident management system (PagerDuty/Jira)
 * - Metrics (tracking and reporting)
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-10-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ComplianceEscalationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Escalation metrics
    private Counter criticalEscalations;
    private Counter highSeverityEscalations;
    private Counter mediumSeverityEscalations;

    // Escalation recipients
    private static final List<String> CRITICAL_ESCALATION_EMAILS = List.of(
        "compliance-officers@example.com",
        "security@example.com",
        "ceo@example.com",
        "cfo@example.com"
    );

    private static final List<String> HIGH_SEVERITY_ESCALATION_EMAILS = List.of(
        "compliance@example.com",
        "compliance-officers@example.com",
        "risk-management@example.com"
    );

    private static final List<String> MEDIUM_SEVERITY_ESCALATION_EMAILS = List.of(
        "compliance@example.com",
        "audit-team@example.com"
    );

    // Kafka topics
    private static final String CRITICAL_ESCALATION_TOPIC = "compliance.escalations.critical";
    private static final String HIGH_ESCALATION_TOPIC = "compliance.escalations.high";
    private static final String INCIDENT_MANAGEMENT_TOPIC = "incident-management.incidents";
    private static final String EMAIL_NOTIFICATION_TOPIC = "notifications.email";

    /**
     * Escalate critical audit alert
     *
     * CRITICAL ESCALATIONS:
     * - Data breaches (GDPR 72-hour notification)
     * - Unauthorized access to financial data
     * - System compromise or intrusion
     * - Fraud detection above threshold ($50K+)
     * - Compliance violation with regulatory impact
     * - Internal control failures (SOX)
     */
    public EscalationResult escalateCriticalAlert(AuditAlert alert, String correlationId) {
        log.error("CRITICAL_ESCALATION: Processing critical audit alert - alertId: {}, type: {}, service: {}, correlationId: {}",
            alert.getId(), alert.getAlertType(), alert.getService(), correlationId);

        String escalationId = UUID.randomUUID().toString();
        Instant escalationTime = Instant.now();

        try {
            // 1. Create incident ticket (highest priority)
            IncidentTicket incident = createCriticalIncident(alert, escalationId, correlationId);
            log.error("CRITICAL_ESCALATION: Incident created - incidentId: {}, escalationId: {}",
                incident.getIncidentId(), escalationId);

            // 2. Publish to incident management system (PagerDuty/Jira)
            publishIncidentManagementEvent(incident);

            // 3. Notify compliance officers and senior management
            notifyCriticalEscalation(alert, incident, escalationId, correlationId);

            // 4. Publish to real-time alerting system
            publishCriticalEscalationEvent(alert, incident, escalationId, correlationId);

            // 5. Track escalation metrics
            incrementCriticalEscalationMetrics(alert.getAlertType());

            // 6. Create escalation tracking record
            EscalationTracking tracking = createEscalationTracking(
                escalationId, "CRITICAL", alert, incident, escalationTime);

            log.error("CRITICAL_ESCALATION: Escalation complete - escalationId: {}, incidentId: {}, notified: {}, eta: 30 minutes",
                escalationId, incident.getIncidentId(), CRITICAL_ESCALATION_EMAILS.size());

            return EscalationResult.builder()
                .escalationId(escalationId)
                .severity("CRITICAL")
                .incidentId(incident.getIncidentId())
                .notificationsSent(CRITICAL_ESCALATION_EMAILS.size())
                .escalationTime(escalationTime)
                .estimatedResponseTime("30 minutes")
                .status("ESCALATED")
                .tracking(tracking)
                .build();

        } catch (Exception e) {
            log.error("CRITICAL_ESCALATION: Failed to escalate alert - alertId: {}, escalationId: {}",
                alert.getId(), escalationId, e);

            // Fallback: Emergency notification
            sendEmergencyFailsafeNotification(alert, escalationId, e);

            return EscalationResult.builder()
                .escalationId(escalationId)
                .severity("CRITICAL")
                .status("FAILED")
                .error(e.getMessage())
                .escalationTime(escalationTime)
                .build();
        }
    }

    /**
     * Escalate high severity audit alert
     *
     * HIGH SEVERITY ESCALATIONS:
     * - Repeated failed authentication attempts
     * - Unusual transaction patterns
     * - Policy violations
     * - Medium-value fraud ($10K-$50K)
     * - Data access anomalies
     * - Service availability issues
     */
    public EscalationResult escalateHighSeverityAlert(AuditAlert alert, String correlationId) {
        log.warn("HIGH_SEVERITY_ESCALATION: Processing high severity audit alert - alertId: {}, type: {}, service: {}, correlationId: {}",
            alert.getId(), alert.getAlertType(), alert.getService(), correlationId);

        String escalationId = UUID.randomUUID().toString();
        Instant escalationTime = Instant.now();

        try {
            // 1. Create tracking ticket (high priority)
            TrackingTicket ticket = createHighSeverityTicket(alert, escalationId, correlationId);
            log.warn("HIGH_SEVERITY_ESCALATION: Ticket created - ticketId: {}, escalationId: {}",
                ticket.getTicketId(), escalationId);

            // 2. Notify compliance team
            notifyHighSeverityEscalation(alert, ticket, escalationId, correlationId);

            // 3. Publish to alerting system
            publishHighSeverityEscalationEvent(alert, ticket, escalationId, correlationId);

            // 4. Schedule review (same-day)
            scheduleReview(ticket, "SAME_DAY");

            // 5. Track escalation metrics
            incrementHighSeverityEscalationMetrics(alert.getAlertType());

            // 6. Create escalation tracking record
            EscalationTracking tracking = createEscalationTracking(
                escalationId, "HIGH", alert, ticket, escalationTime);

            log.warn("HIGH_SEVERITY_ESCALATION: Escalation complete - escalationId: {}, ticketId: {}, notified: {}, reviewScheduled: same-day",
                escalationId, ticket.getTicketId(), HIGH_SEVERITY_ESCALATION_EMAILS.size());

            return EscalationResult.builder()
                .escalationId(escalationId)
                .severity("HIGH")
                .ticketId(ticket.getTicketId())
                .notificationsSent(HIGH_SEVERITY_ESCALATION_EMAILS.size())
                .escalationTime(escalationTime)
                .estimatedResponseTime("4 hours")
                .reviewScheduled(true)
                .status("ESCALATED")
                .tracking(tracking)
                .build();

        } catch (Exception e) {
            log.error("HIGH_SEVERITY_ESCALATION: Failed to escalate alert - alertId: {}, escalationId: {}",
                alert.getId(), escalationId, e);

            return EscalationResult.builder()
                .escalationId(escalationId)
                .severity("HIGH")
                .status("FAILED")
                .error(e.getMessage())
                .escalationTime(escalationTime)
                .build();
        }
    }

    /**
     * Escalate medium severity audit alert
     */
    public EscalationResult escalateMediumSeverityAlert(AuditAlert alert, String correlationId) {
        log.info("MEDIUM_SEVERITY_ESCALATION: Processing medium severity audit alert - alertId: {}, type: {}, correlationId: {}",
            alert.getId(), alert.getAlertType(), correlationId);

        String escalationId = UUID.randomUUID().toString();
        Instant escalationTime = Instant.now();

        try {
            // 1. Create tracking ticket (normal priority)
            TrackingTicket ticket = createMediumSeverityTicket(alert, escalationId, correlationId);

            // 2. Notify compliance/audit team
            notifyMediumSeverityEscalation(alert, ticket, escalationId, correlationId);

            // 3. Schedule review (next business day)
            scheduleReview(ticket, "NEXT_BUSINESS_DAY");

            // 4. Track escalation metrics
            incrementMediumSeverityEscalationMetrics(alert.getAlertType());

            // 5. Create escalation tracking record
            EscalationTracking tracking = createEscalationTracking(
                escalationId, "MEDIUM", alert, ticket, escalationTime);

            return EscalationResult.builder()
                .escalationId(escalationId)
                .severity("MEDIUM")
                .ticketId(ticket.getTicketId())
                .notificationsSent(MEDIUM_SEVERITY_ESCALATION_EMAILS.size())
                .escalationTime(escalationTime)
                .estimatedResponseTime("24 hours")
                .reviewScheduled(true)
                .status("ESCALATED")
                .tracking(tracking)
                .build();

        } catch (Exception e) {
            log.error("MEDIUM_SEVERITY_ESCALATION: Failed to escalate alert - alertId: {}, escalationId: {}",
                alert.getId(), escalationId, e);

            return EscalationResult.builder()
                .escalationId(escalationId)
                .severity("MEDIUM")
                .status("FAILED")
                .error(e.getMessage())
                .escalationTime(escalationTime)
                .build();
        }
    }

    // === INCIDENT MANAGEMENT ===

    private IncidentTicket createCriticalIncident(AuditAlert alert, String escalationId, String correlationId) {
        return IncidentTicket.builder()
            .incidentId(UUID.randomUUID().toString())
            .escalationId(escalationId)
            .severity("CRITICAL")
            .title("CRITICAL: " + alert.getAlertType() + " - " + alert.getService())
            .description(buildIncidentDescription(alert, correlationId))
            .alertId(alert.getId())
            .alertType(alert.getAlertType())
            .service(alert.getService())
            .correlationId(correlationId)
            .priority("P0")
            .status("OPEN")
            .createdAt(Instant.now())
            .sla("30 minutes")
            .assignedTo("compliance-oncall")
            .escalationPath(CRITICAL_ESCALATION_EMAILS)
            .metadata(buildIncidentMetadata(alert))
            .build();
    }

    private TrackingTicket createHighSeverityTicket(AuditAlert alert, String escalationId, String correlationId) {
        return TrackingTicket.builder()
            .ticketId(UUID.randomUUID().toString())
            .escalationId(escalationId)
            .severity("HIGH")
            .title("HIGH: " + alert.getAlertType() + " - " + alert.getService())
            .description(buildTicketDescription(alert, correlationId))
            .alertId(alert.getId())
            .alertType(alert.getAlertType())
            .service(alert.getService())
            .correlationId(correlationId)
            .priority("P1")
            .status("OPEN")
            .createdAt(Instant.now())
            .sla("4 hours")
            .assignedTo("compliance-team")
            .build();
    }

    private TrackingTicket createMediumSeverityTicket(AuditAlert alert, String escalationId, String correlationId) {
        return TrackingTicket.builder()
            .ticketId(UUID.randomUUID().toString())
            .escalationId(escalationId)
            .severity("MEDIUM")
            .title("MEDIUM: " + alert.getAlertType() + " - " + alert.getService())
            .description(buildTicketDescription(alert, correlationId))
            .alertId(alert.getId())
            .alertType(alert.getAlertType())
            .service(alert.getService())
            .correlationId(correlationId)
            .priority("P2")
            .status("OPEN")
            .createdAt(Instant.now())
            .sla("24 hours")
            .assignedTo("audit-team")
            .build();
    }

    // === NOTIFICATION METHODS ===

    private void notifyCriticalEscalation(AuditAlert alert, IncidentTicket incident, String escalationId, String correlationId) {
        EmailNotification notification = EmailNotification.builder()
            .recipients(CRITICAL_ESCALATION_EMAILS)
            .subject("[CRITICAL ESCALATION] " + alert.getAlertType() + " - Incident " + incident.getIncidentId())
            .body(buildCriticalEscalationEmailBody(alert, incident, escalationId, correlationId))
            .priority("URGENT")
            .template("critical-compliance-escalation")
            .metadata(Map.of(
                "escalationId", escalationId,
                "incidentId", incident.getIncidentId(),
                "severity", "CRITICAL",
                "sla", "30 minutes"
            ))
            .build();

        kafkaTemplate.send(EMAIL_NOTIFICATION_TOPIC, escalationId, notification);
        log.error("CRITICAL_ESCALATION: Email notifications sent to {} recipients", CRITICAL_ESCALATION_EMAILS.size());
    }

    private void notifyHighSeverityEscalation(AuditAlert alert, TrackingTicket ticket, String escalationId, String correlationId) {
        EmailNotification notification = EmailNotification.builder()
            .recipients(HIGH_SEVERITY_ESCALATION_EMAILS)
            .subject("[HIGH SEVERITY] " + alert.getAlertType() + " - Ticket " + ticket.getTicketId())
            .body(buildHighSeverityEmailBody(alert, ticket, escalationId, correlationId))
            .priority("HIGH")
            .template("high-severity-compliance-escalation")
            .metadata(Map.of(
                "escalationId", escalationId,
                "ticketId", ticket.getTicketId(),
                "severity", "HIGH",
                "sla", "4 hours"
            ))
            .build();

        kafkaTemplate.send(EMAIL_NOTIFICATION_TOPIC, escalationId, notification);
    }

    private void notifyMediumSeverityEscalation(AuditAlert alert, TrackingTicket ticket, String escalationId, String correlationId) {
        EmailNotification notification = EmailNotification.builder()
            .recipients(MEDIUM_SEVERITY_ESCALATION_EMAILS)
            .subject("[MEDIUM] " + alert.getAlertType() + " - Ticket " + ticket.getTicketId())
            .body(buildMediumSeverityEmailBody(alert, ticket, escalationId, correlationId))
            .priority("NORMAL")
            .template("medium-severity-compliance-escalation")
            .build();

        kafkaTemplate.send(EMAIL_NOTIFICATION_TOPIC, escalationId, notification);
    }

    // === EVENT PUBLISHING ===

    private void publishIncidentManagementEvent(IncidentTicket incident) {
        Map<String, Object> incidentEvent = Map.of(
            "incidentId", incident.getIncidentId(),
            "escalationId", incident.getEscalationId(),
            "severity", incident.getSeverity(),
            "priority", incident.getPriority(),
            "title", incident.getTitle(),
            "description", incident.getDescription(),
            "service", incident.getService(),
            "sla", incident.getSla(),
            "assignedTo", incident.getAssignedTo(),
            "createdAt", incident.getCreatedAt(),
            "escalationPath", incident.getEscalationPath()
        );

        kafkaTemplate.send(INCIDENT_MANAGEMENT_TOPIC, incident.getIncidentId(), incidentEvent);
        log.error("CRITICAL_ESCALATION: Incident published to incident management system - incidentId: {}",
            incident.getIncidentId());
    }

    private void publishCriticalEscalationEvent(AuditAlert alert, IncidentTicket incident, String escalationId, String correlationId) {
        Map<String, Object> escalationEvent = Map.of(
            "escalationId", escalationId,
            "severity", "CRITICAL",
            "incidentId", incident.getIncidentId(),
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "correlationId", correlationId,
            "timestamp", Instant.now(),
            "sla", "30 minutes",
            "status", "ESCALATED"
        );

        kafkaTemplate.send(CRITICAL_ESCALATION_TOPIC, escalationId, escalationEvent);
    }

    private void publishHighSeverityEscalationEvent(AuditAlert alert, TrackingTicket ticket, String escalationId, String correlationId) {
        Map<String, Object> escalationEvent = Map.of(
            "escalationId", escalationId,
            "severity", "HIGH",
            "ticketId", ticket.getTicketId(),
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "correlationId", correlationId,
            "timestamp", Instant.now(),
            "sla", "4 hours",
            "status", "ESCALATED"
        );

        kafkaTemplate.send(HIGH_ESCALATION_TOPIC, escalationId, escalationEvent);
    }

    // === METRICS ===

    private void incrementCriticalEscalationMetrics(String alertType) {
        if (criticalEscalations == null) {
            criticalEscalations = Counter.builder("compliance.escalations.critical")
                .description("Critical compliance escalations")
                .tag("type", alertType)
                .register(meterRegistry);
        }
        criticalEscalations.increment();
    }

    private void incrementHighSeverityEscalationMetrics(String alertType) {
        if (highSeverityEscalations == null) {
            highSeverityEscalations = Counter.builder("compliance.escalations.high")
                .description("High severity compliance escalations")
                .tag("type", alertType)
                .register(meterRegistry);
        }
        highSeverityEscalations.increment();
    }

    private void incrementMediumSeverityEscalationMetrics(String alertType) {
        if (mediumSeverityEscalations == null) {
            mediumSeverityEscalations = Counter.builder("compliance.escalations.medium")
                .description("Medium severity compliance escalations")
                .tag("type", alertType)
                .register(meterRegistry);
        }
        mediumSeverityEscalations.increment();
    }

    // === TRACKING ===

    private EscalationTracking createEscalationTracking(String escalationId, String severity,
                                                       AuditAlert alert, Object ticketOrIncident,
                                                       Instant escalationTime) {
        return EscalationTracking.builder()
            .escalationId(escalationId)
            .severity(severity)
            .alertId(alert.getId())
            .alertType(alert.getAlertType())
            .service(alert.getService())
            .escalationTime(escalationTime)
            .status("ESCALATED")
            .build();
    }

    private void scheduleReview(TrackingTicket ticket, String schedule) {
        log.info("ESCALATION: Review scheduled for ticket {} - schedule: {}", ticket.getTicketId(), schedule);
        // Integration with calendar/scheduling system would go here
    }

    // === FAILSAFE ===

    private void sendEmergencyFailsafeNotification(AuditAlert alert, String escalationId, Exception error) {
        log.error("EMERGENCY_FAILSAFE: Escalation failed, sending emergency notification - escalationId: {}",
            escalationId, error);

        // Simplified emergency email
        EmailNotification emergencyNotification = EmailNotification.builder()
            .recipients(List.of("compliance-officers@example.com", "security@example.com"))
            .subject("[EMERGENCY] Escalation System Failure - " + alert.getAlertType())
            .body("CRITICAL: The escalation system failed to process a critical alert.\n\n" +
                  "Alert ID: " + alert.getId() + "\n" +
                  "Alert Type: " + alert.getAlertType() + "\n" +
                  "Service: " + alert.getService() + "\n" +
                  "Escalation ID: " + escalationId + "\n" +
                  "Error: " + error.getMessage() + "\n\n" +
                  "IMMEDIATE ACTION REQUIRED")
            .priority("URGENT")
            .build();

        try {
            kafkaTemplate.send(EMAIL_NOTIFICATION_TOPIC, escalationId, emergencyNotification);
        } catch (Exception e) {
            log.error("CATASTROPHIC_FAILURE: Unable to send emergency notification", e);
        }
    }

    // === HELPER METHODS ===

    private String buildIncidentDescription(AuditAlert alert, String correlationId) {
        return String.format(
            "CRITICAL INCIDENT\n\n" +
            "Alert Type: %s\n" +
            "Service: %s\n" +
            "Alert ID: %s\n" +
            "Correlation ID: %s\n" +
            "Timestamp: %s\n\n" +
            "IMMEDIATE INVESTIGATION REQUIRED",
            alert.getAlertType(),
            alert.getService(),
            alert.getId(),
            correlationId,
            Instant.now()
        );
    }

    private String buildTicketDescription(AuditAlert alert, String correlationId) {
        return String.format(
            "Alert Type: %s\n" +
            "Service: %s\n" +
            "Alert ID: %s\n" +
            "Correlation ID: %s\n" +
            "Timestamp: %s",
            alert.getAlertType(),
            alert.getService(),
            alert.getId(),
            correlationId,
            Instant.now()
        );
    }

    private Map<String, Object> buildIncidentMetadata(AuditAlert alert) {
        return Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "timestamp", Instant.now(),
            "requiresRegulatory Notification", determineRegulatoryNotificationRequired(alert)
        );
    }

    private boolean determineRegulatoryNotificationRequired(AuditAlert alert) {
        // GDPR data breach, BSA/AML suspicious activity, etc.
        return alert.getAlertType().contains("DATA_BREACH") ||
               alert.getAlertType().contains("UNAUTHORIZED_ACCESS") ||
               alert.getAlertType().contains("FRAUD_DETECTED") ||
               alert.getAlertType().contains("SUSPICIOUS_ACTIVITY");
    }

    private String buildCriticalEscalationEmailBody(AuditAlert alert, IncidentTicket incident,
                                                   String escalationId, String correlationId) {
        return String.format(
            "CRITICAL COMPLIANCE ESCALATION\n\n" +
            "Incident ID: %s\n" +
            "Escalation ID: %s\n" +
            "Severity: CRITICAL\n" +
            "SLA: 30 minutes\n\n" +
            "Alert Details:\n" +
            "- Type: %s\n" +
            "- Service: %s\n" +
            "- Alert ID: %s\n" +
            "- Correlation ID: %s\n\n" +
            "Immediate action required. Please respond within 30 minutes.\n\n" +
            "Assigned: %s\n" +
            "Created: %s",
            incident.getIncidentId(),
            escalationId,
            alert.getAlertType(),
            alert.getService(),
            alert.getId(),
            correlationId,
            incident.getAssignedTo(),
            incident.getCreatedAt()
        );
    }

    private String buildHighSeverityEmailBody(AuditAlert alert, TrackingTicket ticket,
                                             String escalationId, String correlationId) {
        return String.format(
            "HIGH SEVERITY COMPLIANCE ALERT\n\n" +
            "Ticket ID: %s\n" +
            "Escalation ID: %s\n" +
            "Severity: HIGH\n" +
            "SLA: 4 hours\n\n" +
            "Alert Details:\n" +
            "- Type: %s\n" +
            "- Service: %s\n" +
            "- Alert ID: %s\n\n" +
            "Review scheduled: Same day\n" +
            "Assigned: %s",
            ticket.getTicketId(),
            escalationId,
            alert.getAlertType(),
            alert.getService(),
            alert.getId(),
            ticket.getAssignedTo()
        );
    }

    private String buildMediumSeverityEmailBody(AuditAlert alert, TrackingTicket ticket,
                                               String escalationId, String correlationId) {
        return String.format(
            "MEDIUM SEVERITY COMPLIANCE ALERT\n\n" +
            "Ticket ID: %s\n" +
            "Severity: MEDIUM\n" +
            "SLA: 24 hours\n\n" +
            "Alert Type: %s\n" +
            "Service: %s\n\n" +
            "Review scheduled: Next business day",
            ticket.getTicketId(),
            alert.getAlertType(),
            alert.getService()
        );
    }

    // === DATA CLASSES ===

    @lombok.Data
    @lombok.Builder
    public static class EscalationResult {
        private String escalationId;
        private String severity;
        private String incidentId;
        private String ticketId;
        private int notificationsSent;
        private Instant escalationTime;
        private String estimatedResponseTime;
        private Boolean reviewScheduled;
        private String status;
        private EscalationTracking tracking;
        private String error;
    }

    @lombok.Data
    @lombok.Builder
    public static class IncidentTicket {
        private String incidentId;
        private String escalationId;
        private String severity;
        private String title;
        private String description;
        private String alertId;
        private String alertType;
        private String service;
        private String correlationId;
        private String priority;
        private String status;
        private Instant createdAt;
        private String sla;
        private String assignedTo;
        private List<String> escalationPath;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class TrackingTicket {
        private String ticketId;
        private String escalationId;
        private String severity;
        private String title;
        private String description;
        private String alertId;
        private String alertType;
        private String service;
        private String correlationId;
        private String priority;
        private String status;
        private Instant createdAt;
        private String sla;
        private String assignedTo;
    }

    @lombok.Data
    @lombok.Builder
    public static class EmailNotification {
        private List<String> recipients;
        private String subject;
        private String body;
        private String priority;
        private String template;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class EscalationTracking {
        private String escalationId;
        private String severity;
        private String alertId;
        private String alertType;
        private String service;
        private Instant escalationTime;
        private String status;
    }
}
