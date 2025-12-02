package com.waqiti.compliance.entity;

import com.waqiti.compliance.enums.IncidentSeverity;
import com.waqiti.compliance.enums.IncidentStatus;
import com.waqiti.compliance.enums.IncidentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compliance Incident Entity
 *
 * Comprehensive incident management with SLA tracking, escalation workflows,
 * and regulatory reporting triggers.
 *
 * Compliance: SOX 404, PCI DSS 10.x, GDPR Article 33, Reg E, GLBA
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Entity
@Table(name = "compliance_incidents", indexes = {
    @Index(name = "idx_incidents_status", columnList = "status"),
    @Index(name = "idx_incidents_assigned", columnList = "assigned_to"),
    @Index(name = "idx_incidents_user", columnList = "user_id"),
    @Index(name = "idx_incidents_type", columnList = "incident_type"),
    @Index(name = "idx_incidents_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceIncident {

    @Id
    @Column(name = "incident_id", nullable = false, length = 255)
    private String incidentId;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 100)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IncidentStatus status;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Type(type = "jsonb")
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "assigned_to", nullable = false, length = 255)
    private String assignedTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalation_count", nullable = false)
    @Builder.Default
    private Integer escalationCount = 0;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 255)
    private String closedBy;

    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

    @Column(name = "sla_breached", nullable = false)
    @Builder.Default
    private boolean slaBreached = false;

    @Column(name = "regulatory_reporting_required", nullable = false)
    @Builder.Default
    private boolean regulatoryReportingRequired = false;

    @Type(type = "jsonb")
    @Column(name = "linked_investigations", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> linkedInvestigations = new ArrayList<>();

    @Column(name = "status_notes", columnDefinition = "TEXT")
    private String statusNotes;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Add linked investigation to incident
     */
    public void addLinkedInvestigation(String investigationId) {
        if (linkedInvestigations == null) {
            linkedInvestigations = new ArrayList<>();
        }
        if (!linkedInvestigations.contains(investigationId)) {
            linkedInvestigations.add(investigationId);
        }
    }

    /**
     * Check if SLA is breached
     */
    public boolean isSLABreached() {
        return slaBreached || (slaDeadline != null && LocalDateTime.now().isAfter(slaDeadline));
    }

    /**
     * Check if incident is critical
     */
    public boolean isCritical() {
        return severity == IncidentSeverity.CRITICAL || "P0".equals(priority);
    }

    /**
     * Check if incident is open (not resolved or closed)
     */
    public boolean isOpen() {
        return status != IncidentStatus.RESOLVED && status != IncidentStatus.CLOSED;
    }
}
