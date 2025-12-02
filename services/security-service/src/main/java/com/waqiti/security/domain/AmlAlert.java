package com.waqiti.security.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AML Alert entity for tracking suspicious activities and compliance monitoring
 */
@Entity
@Table(name = "aml_alerts", indexes = {
    @Index(name = "idx_aml_alerts_user_id", columnList = "user_id"),
    @Index(name = "idx_aml_alerts_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_aml_alerts_status", columnList = "status"),
    @Index(name = "idx_aml_alerts_severity", columnList = "severity"),
    @Index(name = "idx_aml_alerts_created_at", columnList = "created_at"),
    @Index(name = "idx_aml_alerts_alert_type", columnList = "alert_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AmlAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AmlAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AmlSeverity severity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertStatus status;

    @Column(name = "requires_investigation", nullable = false)
    private boolean requiresInvestigation;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "suspicious_activity")
    private Boolean suspiciousActivity;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_to")
    private UUID escalatedTo;

    @Column(name = "risk_score")
    private Integer riskScore; // 1-100 scale

    @Column(name = "automated_action")
    private String automatedAction; // Actions taken by system

    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;

    @Column(name = "external_reference")
    private String externalReference; // Reference to external systems

    @Column(name = "jurisdiction_code", length = 2)
    private String jurisdictionCode;

    @Column(name = "regulatory_reported")
    private Boolean regulatoryReported;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    public enum AlertStatus {
        OPEN,
        UNDER_INVESTIGATION,
        RESOLVED,
        ESCALATED,
        CLOSED,
        REGULATORY_REPORTED
    }
}