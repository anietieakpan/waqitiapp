package com.waqiti.account.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dormant Account Audit Log Entity
 * Tracks all dormancy-related actions for compliance and audit purposes
 */
@Entity
@Table(name = "dormant_account_audit_logs", indexes = {
    @Index(name = "idx_audit_account_id", columnList = "account_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_timestamp", columnList = "action_timestamp"),
    @Index(name = "idx_audit_dormancy_level", columnList = "dormancy_level")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DormantAccountAuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dormancy_level", nullable = false)
    private DormancyLevel dormancyLevel;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private DormancyAction action;
    
    @Column(name = "days_inactive", nullable = false)
    private int daysInactive;
    
    @Column(name = "reason", length = 500)
    private String reason;
    
    @Column(name = "action_taken", length = 1000)
    private String actionTaken;
    
    @Column(name = "performed_by")
    private String performedBy;
    
    @CreationTimestamp
    @Column(name = "action_timestamp", nullable = false)
    private LocalDateTime actionTimestamp;
    
    @Column(name = "next_review_date")
    private LocalDateTime nextReviewDate;
    
    @Column(name = "notification_sent")
    @Builder.Default
    private Boolean notificationSent = false;
    
    @Column(name = "compliance_notes", length = 2000)
    private String complianceNotes;
    
    @Column(name = "regulatory_action_required")
    @Builder.Default
    private Boolean regulatoryActionRequired = false;
}