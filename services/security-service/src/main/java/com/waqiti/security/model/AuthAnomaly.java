package com.waqiti.security.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Authentication Anomaly Entity
 * Persisted record of detected authentication anomalies
 */
@Entity
@Table(name = "auth_anomalies",
    indexes = {
        @Index(name = "idx_anomaly_event_id", columnList = "event_id"),
        @Index(name = "idx_anomaly_user_id", columnList = "user_id"),
        @Index(name = "idx_anomaly_type", columnList = "anomaly_type"),
        @Index(name = "idx_anomaly_status", columnList = "status"),
        @Index(name = "idx_anomaly_detected_at", columnList = "detected_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAnomaly {

    @Id
    @Column(name = "anomaly_id")
    private String anomalyId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "description", length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "anomaly_evidence", joinColumns = @JoinColumn(name = "anomaly_id"))
    @MapKeyColumn(name = "evidence_key")
    @Column(name = "evidence_value", length = 2000)
    private Map<String, Object> evidence;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnomalyStatus status;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
