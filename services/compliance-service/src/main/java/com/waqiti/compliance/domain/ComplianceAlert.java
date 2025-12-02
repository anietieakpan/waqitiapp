package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ComplianceAlert {

    @Id
    private UUID id;

    private UUID alertId;

    @Column(nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String customerId;

    private String caseId;

    @Column(columnDefinition = "TEXT")
    private String resolutionReason;

    private String assignedInvestigator;

    private LocalDateTime assignedAt;

    private String transactionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private Boolean requiresAction;

    private String assignedTo;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime acknowledgedAt;

    private String acknowledgedBy;

    private LocalDateTime resolvedAt;

    private String resolvedBy;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(columnDefinition = "TEXT")
    private String actionTaken;

    private String regulatoryReference;

    @Column(nullable = false)
    private Integer priority;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    // Risk scoring
    private Double riskScore;

    private String riskLevel;

    // Escalation tracking
    private Boolean escalated;

    private LocalDateTime escalatedAt;

    private String escalatedTo;

    // Notification tracking
    private Boolean notificationSent;

    private LocalDateTime notificationSentAt;

    private String notificationMethod;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (alertId == null) {
            alertId = UUID.randomUUID();
        }
        if (status == null) {
            status = AlertStatus.OPEN;
        }
        if (requiresAction == null) {
            requiresAction = true;
        }
        if (priority == null) {
            priority = 3; // Default to medium priority
        }
        if (escalated == null) {
            escalated = false;
        }
        if (notificationSent == null) {
            notificationSent = false;
        }
    }

    public enum AlertStatus {
        OPEN,
        ACKNOWLEDGED,
        INVESTIGATING,
        RESOLVED,
        CLOSED,
        FALSE_POSITIVE,
        ESCALATED
    }

    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum AlertType {
        OFAC_MATCH,
        PEP_DETECTED,
        SUSPICIOUS_PATTERN,
        HIGH_RISK_CUSTOMER,
        VELOCITY_EXCEEDED,
        CTR_REQUIRED,
        SUSPICIOUS_AMOUNT,
        STRUCTURING_DETECTED,
        UNUSUAL_ACTIVITY,
        REGULATORY_BREACH,
        FRAUD_INDICATOR,
        COMPLIANCE_RULE_VIOLATION
    }
}