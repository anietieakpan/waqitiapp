package com.waqiti.common.model.incident;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade incident entity for critical system issues.
 * Supports P0/P1/P2 priority workflows, SLA tracking, and escalation.
 */
@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_priority", columnList = "priority"),
    @Index(name = "idx_incident_status", columnList = "status"),
    @Index(name = "idx_incident_service", columnList = "sourceService"),
    @Index(name = "idx_incident_assigned", columnList = "assignedTo"),
    @Index(name = "idx_incident_created", columnList = "createdAt"),
    @Index(name = "idx_incident_correlation", columnList = "correlationId"),
    @Index(name = "idx_incident_parent", columnList = "parentIncidentId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IncidentPriority priority;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IncidentStatus status;

    @Column(nullable = false, length = 128)
    private String sourceService;

    @Column(length = 64)
    private String incidentType;

    @Column(length = 128)
    private String correlationId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant lastUpdated;

    @Column
    private Instant acknowledgedAt;

    @Column
    private Instant resolvedAt;

    @Column
    private Instant closedAt;

    @Column(length = 64)
    private String createdBy;

    @Column(length = 64)
    private String assignedTo;

    @Column(length = 64)
    private String acknowledgedBy;

    @Column(length = 64)
    private String resolvedBy;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    // SLA tracking
    @Column
    private Instant slaDeadline;

    @Column
    private Boolean slaBreached;

    @Column
    private Duration timeToAcknowledge;

    @Column
    private Duration timeToResolve;

    // Impact metrics
    @Column
    private Long impactedUsers;

    @Column
    private Long impactedTransactions;

    @Column
    private String impactLevel; // LOW, MEDIUM, HIGH, CRITICAL

    // Escalation
    @Column
    private Integer escalationLevel;

    @Column
    private Instant escalatedAt;

    @Column(length = 256)
    private String escalationReason;

    // Related incidents
    @Column(length = 64)
    private String parentIncidentId;

    @ElementCollection
    @CollectionTable(name = "incident_related", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "related_incident_id")
    @Builder.Default
    private List<String> relatedIncidentIds = new ArrayList<>();

    // Associated alerts
    @ElementCollection
    @CollectionTable(name = "incident_alerts", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "alert_id")
    @Builder.Default
    private List<String> associatedAlertIds = new ArrayList<>();

    // Communication
    @Column(length = 256)
    private String slackChannel;

    @Column(length = 256)
    private String pagerDutyIncidentId;

    @Column(length = 512)
    private String statusPageUrl;

    @Version
    private Long version;

    // Business methods
    public boolean isP0() {
        return priority == IncidentPriority.P0;
    }

    public boolean isP1() {
        return priority == IncidentPriority.P1;
    }

    public boolean isCritical() {
        return priority == IncidentPriority.P0 || priority == IncidentPriority.P1;
    }

    public boolean isActive() {
        return status == IncidentStatus.OPEN || 
               status == IncidentStatus.ACKNOWLEDGED || 
               status == IncidentStatus.IN_PROGRESS;
    }

    public void acknowledge(String acknowledgedBy) {
        this.status = IncidentStatus.ACKNOWLEDGED;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = Instant.now();
        this.timeToAcknowledge = Duration.between(createdAt, this.acknowledgedAt);
        this.lastUpdated = Instant.now();
    }

    public void resolve(String resolvedBy, String notes) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;
        this.timeToResolve = Duration.between(createdAt, this.resolvedAt);
        this.lastUpdated = Instant.now();

        // Check SLA breach
        if (slaDeadline != null && this.resolvedAt.isAfter(slaDeadline)) {
            this.slaBreached = true;
        }
    }

    public void close() {
        this.status = IncidentStatus.CLOSED;
        this.closedAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    public void escalate(int level, String reason) {
        this.escalationLevel = level;
        this.escalatedAt = Instant.now();
        this.escalationReason = reason;
        this.lastUpdated = Instant.now();
    }

    public boolean isSlaBreached() {
        if (slaDeadline == null) {
            return false;
        }
        Instant now = Instant.now();
        return now.isAfter(slaDeadline) && !status.isResolved();
    }

    public Duration getAge() {
        return Duration.between(createdAt, Instant.now());
    }

    public void addRelatedIncident(String incidentId) {
        if (this.relatedIncidentIds == null) {
            this.relatedIncidentIds = new ArrayList<>();
        }
        if (!this.relatedIncidentIds.contains(incidentId)) {
            this.relatedIncidentIds.add(incidentId);
        }
    }

    public void addAssociatedAlert(String alertId) {
        if (this.associatedAlertIds == null) {
            this.associatedAlertIds = new ArrayList<>();
        }
        if (!this.associatedAlertIds.contains(alertId)) {
            this.associatedAlertIds.add(alertId);
        }
    }
}
