package com.waqiti.common.model.alert;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Production-grade system alert entity with full audit trail.
 * Supports alert lifecycle management, escalation, and resolution tracking.
 */
@Entity
@Table(name = "system_alerts", indexes = {
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_severity", columnList = "severity"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_source_service", columnList = "sourceService"),
    @Index(name = "idx_correlation_id", columnList = "correlationId"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_status_severity", columnList = "status, severity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SystemAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String alertType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Column(nullable = false, length = 128)
    private String sourceService;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    @Column(length = 500)
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 128)
    private String correlationId;

    @Column(length = 1000)
    private String statusDetails;

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

    @Column(length = 64)
    private String acknowledgedBy;

    @Column(length = 64)
    private String resolvedBy;

    @Column(length = 256)
    private String resolutionNotes;

    @Column
    private Integer escalationLevel;

    @Column
    private Instant escalatedAt;

    @Column(length = 64)
    private String assignedTo;

    // Metrics
    @Column
    private Long impactedUsers;

    @Column
    private Long impactedTransactions;

    @Version
    private Long version;

    public boolean isActive() {
        return status == AlertStatus.ACTIVE;
    }

    public boolean isCritical() {
        return severity == AlertSeverity.CRITICAL || severity == AlertSeverity.EMERGENCY;
    }

    public void acknowledge(String acknowledgedBy) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    public void resolve(String resolvedBy, String notes) {
        this.status = AlertStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;
        this.lastUpdated = Instant.now();
    }

    public void escalate(int level) {
        this.status = AlertStatus.ESCALATED;
        this.escalationLevel = level;
        this.escalatedAt = Instant.now();
        this.lastUpdated = Instant.now();
    }
}
