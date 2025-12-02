package com.waqiti.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Account Suspension Entity
 * 
 * Tracks account suspensions for audit and compliance
 */
@Entity
@Table(name = "account_suspensions",
    indexes = {
        @Index(name = "idx_account_id", columnList = "account_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_suspended_at", columnList = "suspended_at"),
        @Index(name = "idx_review_date", columnList = "review_date")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSuspension {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "suspension_type", nullable = false)
    private String suspensionType;
    
    @Column(name = "suspension_reason", nullable = false, length = 500)
    private String suspensionReason;
    
    @Column(name = "severity", nullable = false)
    private String severity;
    
    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;
    
    @Column(name = "suspended_by", nullable = false)
    private String suspendedBy;
    
    @Column(name = "expected_duration")
    private Duration expectedDuration;
    
    @Column(name = "review_date")
    private LocalDateTime reviewDate;
    
    @ElementCollection
    @CollectionTable(name = "suspension_evidence", 
                     joinColumns = @JoinColumn(name = "suspension_id"))
    @Column(name = "document_id")
    private List<String> evidenceDocuments;
    
    @Column(name = "compliance_case_id")
    private String complianceCaseId;
    
    @Column(name = "automatic_unsuspension")
    private boolean automaticUnsuspension;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SuspensionStatus status;
    
    @Column(name = "unsuspended_at")
    private LocalDateTime unsuspendedAt;
    
    @Column(name = "unsuspended_by")
    private String unsuspendedBy;
    
    @Column(name = "unsuspension_reason")
    private String unsuspensionReason;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum SuspensionStatus {
        ACTIVE,
        EXPIRED,
        LIFTED,
        CONVERTED_TO_CLOSURE
    }
}