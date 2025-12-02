package com.waqiti.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.model.alert.SystemAlert;
import com.waqiti.common.model.incident.*;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.repository.IncidentRepository;
import com.waqiti.common.repository.SystemAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-grade incident management service with P0/P1/P2 workflows,
 * SLA tracking, escalation, and comprehensive lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentManagementService {

    private final IncidentRepository incidentRepository;
    private final SystemAlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private EscalationService escalationService;

    // Metrics
    private Counter incidentsCreated;
    private Counter incidentsResolved;
    private Counter slaBreaches;
    private Timer incidentResolutionTime;

    @javax.annotation.PostConstruct
    public void initMetrics() {
        incidentsCreated = Counter.builder("incidents_created_total")
                .description("Total incidents created")
                .register(meterRegistry);
        incidentsResolved = Counter.builder("incidents_resolved_total")
                .description("Total incidents resolved")
                .register(meterRegistry);
        slaBreaches = Counter.builder("incidents_sla_breached_total")
                .description("Total SLA breaches")
                .register(meterRegistry);
        incidentResolutionTime = Timer.builder("incident_resolution_duration")
                .description("Time to resolve incidents")
                .register(meterRegistry);
    }

    /**
     * Create incident from alert with automatic priority assignment
     */
    @Transactional
    public Incident createIncident(Object alert) {
        log.info("Creating incident from alert: {}", alert);

        try {
            String alertJson = objectMapper.writeValueAsString(alert);
            JsonNode alertNode = objectMapper.readTree(alertJson);

            String alertType = extractField(alertNode, "alertType", "UNKNOWN");
            String severity = extractField(alertNode, "severity", "MEDIUM");
            String sourceService = extractField(alertNode, "sourceService", "UNKNOWN");
            String correlationId = extractField(alertNode, "correlationId", UUID.randomUUID().toString());

            // Determine incident priority from alert severity
            IncidentPriority priority = mapSeverityToPriority(severity);

            Incident incident = Incident.builder()
                    .title(String.format("[%s] %s Alert", severity, alertType))
                    .description(alertJson)
                    .priority(priority)
                    .status(IncidentStatus.OPEN)
                    .sourceService(sourceService)
                    .incidentType(alertType)
                    .correlationId(correlationId)
                    .createdBy("system")
                    .slaDeadline(calculateSlaDeadline(priority))
                    .build();

            incident = incidentRepository.save(incident);
            incidentsCreated.increment();

            log.info("Incident created: id={}, priority={}, service={}",
                    incident.getId(), priority, sourceService);

            // Send notifications for critical incidents
            if (incident.isCritical() && notificationService != null) {
                notificationService.sendIncidentCreated(incident);
            }

            return incident;

        } catch (Exception e) {
            log.error("Failed to create incident from alert", e);
            throw new RuntimeException("Incident creation failed", e);
        }
    }

    /**
     * Create incident with full details
     */
    @Transactional
    public Incident createIncident(String title, String description, IncidentPriority priority,
                                   String sourceService, String incidentType, String correlationId) {
        log.info("Creating incident: title={}, priority={}, service={}", title, priority, sourceService);

        Incident incident = Incident.builder()
                .title(title)
                .description(description)
                .priority(priority)
                .status(IncidentStatus.OPEN)
                .sourceService(sourceService)
                .incidentType(incidentType)
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .createdBy("system")
                .slaDeadline(calculateSlaDeadline(priority))
                .build();

        incident = incidentRepository.save(incident);
        incidentsCreated.increment();

        log.info("Incident created: id={}, priority={}", incident.getId(), priority);

        // Auto-escalate P0 incidents immediately
        if (incident.isP0() && escalationService != null) {
            escalationService.escalateP0Incident(incident);
        }

        // Send notifications
        if (incident.isCritical() && notificationService != null) {
            notificationService.sendIncidentCreated(incident);
        }

        return incident;
    }

    /**
     * Acknowledge incident
     */
    @Transactional
    public void acknowledgeIncident(String incidentId, String acknowledgedBy) {
        log.info("Acknowledging incident: id={}, by={}", incidentId, acknowledgedBy);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        incident.acknowledge(acknowledgedBy);
        incidentRepository.save(incident);

        log.info("Incident acknowledged: id={}, timeToAck={}",
                incidentId, incident.getTimeToAcknowledge());

        // Check if ACK SLA was breached
        Duration ackSla = incident.getPriority().getAcknowledgeSla();
        if (incident.getTimeToAcknowledge().compareTo(ackSla) > 0) {
            log.warn("ACK SLA breached for incident: id={}, sla={}min, actual={}min",
                    incidentId, ackSla.toMinutes(), incident.getTimeToAcknowledge().toMinutes());
            slaBreaches.increment();
        }
    }

    /**
     * Assign incident to engineer
     */
    @Transactional
    public void assignIncident(String incidentId, String assignedTo) {
        log.info("Assigning incident: id={}, to={}", incidentId, assignedTo);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        incident.setAssignedTo(assignedTo);
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setLastUpdated(Instant.now());
        incidentRepository.save(incident);

        log.info("Incident assigned: id={}, assignee={}", incidentId, assignedTo);

        if (notificationService != null) {
            notificationService.sendIncidentAssigned(incident, assignedTo);
        }
    }

    /**
     * Resolve incident
     */
    @Transactional
    public void resolveIncident(String incidentId, String resolvedBy, String resolutionNotes) {
        log.info("Resolving incident: id={}, by={}", incidentId, resolvedBy);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        incident.resolve(resolvedBy, resolutionNotes);
        incidentRepository.save(incident);

        incidentsResolved.increment();
        incidentResolutionTime.record(incident.getTimeToResolve());

        log.info("Incident resolved: id={}, timeToResolve={}, slaBreached={}",
                incidentId, incident.getTimeToResolve(), incident.getSlaBreached());

        // Record SLA breach metric
        if (Boolean.TRUE.equals(incident.getSlaBreached())) {
            slaBreaches.increment();
        }

        if (notificationService != null) {
            notificationService.sendIncidentResolved(incident);
        }
    }

    /**
     * Resolve incident (simple version for backward compatibility)
     */
    @Transactional
    public void resolveIncident(String incidentId) {
        resolveIncident(incidentId, "system", "Auto-resolved");
    }

    /**
     * Update incident status
     */
    @Transactional
    public void updateIncidentStatus(String incidentId, String statusStr) {
        log.info("Updating incident status: id={}, status={}", incidentId, statusStr);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        IncidentStatus status = IncidentStatus.valueOf(statusStr.toUpperCase());
        incident.setStatus(status);
        incident.setLastUpdated(Instant.now());
        incidentRepository.save(incident);

        log.info("Incident status updated: id={}, status={}", incidentId, status);
    }

    /**
     * Close incident
     */
    @Transactional
    public void closeIncident(String incidentId) {
        log.info("Closing incident: {}", incidentId);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        incident.close();
        incidentRepository.save(incident);

        log.info("Incident closed: {}", incidentId);
    }

    /**
     * Escalate incident
     */
    @Transactional
    public void escalateIncident(String incidentId, int level, String reason) {
        log.warn("Escalating incident: id={}, level={}, reason={}", incidentId, level, reason);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        incident.escalate(level, reason);
        incidentRepository.save(incident);

        log.warn("Incident escalated: id={}, level={}", incidentId, level);

        if (notificationService != null && escalationService != null) {
            escalationService.notifyEscalation(incident, level, reason);
        }
    }

    /**
     * Link alert to incident
     */
    @Transactional
    public void linkAlertToIncident(String incidentId, String alertId) {
        log.info("Linking alert to incident: incidentId={}, alertId={}", incidentId, alertId);

        Optional<Incident> optIncident = incidentRepository.findById(incidentId);
        if (optIncident.isEmpty()) {
            log.warn("Incident not found: {}", incidentId);
            return;
        }

        Incident incident = optIncident.get();
        incident.addAssociatedAlert(alertId);
        incidentRepository.save(incident);

        log.info("Alert linked to incident: incidentId={}, alertId={}", incidentId, alertId);
    }

    /**
     * Get all active incidents
     */
    public List<Incident> getActiveIncidents() {
        return incidentRepository.findAllActive();
    }

    /**
     * Get active critical incidents (P0/P1)
     */
    public List<Incident> getActiveCriticalIncidents() {
        return incidentRepository.findActiveCritical();
    }

    /**
     * Get unacknowledged incidents
     */
    public List<Incident> getUnacknowledgedIncidents() {
        return incidentRepository.findUnacknowledged();
    }

    /**
     * Get SLA-breached incidents
     */
    public List<Incident> getSlaBreachedIncidents() {
        return incidentRepository.findSlaBreached(Instant.now());
    }

    /**
     * Check and escalate stale incidents (async)
     */
    @Async
    public void checkAndEscalateStaleIncidents() {
        log.info("Checking for stale incidents");

        List<Incident> unacknowledged = incidentRepository.findUnacknowledgedCritical();
        Instant now = Instant.now();

        for (Incident incident : unacknowledged) {
            Duration age = Duration.between(incident.getCreatedAt(), now);
            Duration ackSla = incident.getPriority().getAcknowledgeSla();

            if (age.compareTo(ackSla) > 0) {
                log.warn("Stale incident detected - auto-escalating: id={}, age={}min, sla={}min",
                        incident.getId(), age.toMinutes(), ackSla.toMinutes());
                escalateIncident(incident.getId(), 1, "Auto-escalated due to ACK SLA breach");
            }
        }
    }

    // Helper methods
    private IncidentPriority mapSeverityToPriority(String severity) {
        return switch (severity.toUpperCase()) {
            case "EMERGENCY" -> IncidentPriority.P0;
            case "CRITICAL" -> IncidentPriority.P1;
            case "HIGH" -> IncidentPriority.P2;
            case "MEDIUM" -> IncidentPriority.P3;
            default -> IncidentPriority.P4;
        };
    }

    private Instant calculateSlaDeadline(IncidentPriority priority) {
        return Instant.now().plus(priority.getResolveSla());
    }

    private String extractField(JsonNode node, String fieldName, String defaultValue) {
        return node.has(fieldName) ? node.get(fieldName).asText() : defaultValue;
    }
}