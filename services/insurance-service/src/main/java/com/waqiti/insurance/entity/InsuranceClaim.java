package com.waqiti.insurance.entity;

import com.waqiti.insurance.model.ClaimStatus;
import com.waqiti.insurance.model.ClaimComplexity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Insurance Claim Entity
 * Represents a claim filed against an insurance policy
 */
@Entity
@Table(name = "insurance_claims", indexes = {
        @Index(name = "idx_claim_number", columnList = "claim_number", unique = true),
        @Index(name = "idx_policy_id", columnList = "policy_id"),
        @Index(name = "idx_claim_status", columnList = "status"),
        @Index(name = "idx_claim_type", columnList = "claim_type"),
        @Index(name = "idx_incident_date", columnList = "incident_date"),
        @Index(name = "idx_submission_date", columnList = "submission_date"),
        @Index(name = "idx_policyholder_id", columnList = "policyholder_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_number", unique = true, nullable = false, length = 50)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private InsurancePolicy policy;

    @Column(name = "policyholder_id", nullable = false)
    private UUID policyholderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "complexity", length = 20)
    private ClaimComplexity complexity;

    @Column(name = "claim_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal claimAmount;

    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "settled_amount", precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "deductible_applied", precision = 19, scale = 2)
    private BigDecimal deductibleApplied;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location")
    private String incidentLocation;

    @Column(name = "incident_description", columnDefinition = "TEXT")
    private String incidentDescription;

    @Column(name = "submission_date", nullable = false)
    private LocalDateTime submissionDate;

    @Column(name = "approval_date")
    private LocalDateTime approvalDate;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "adjuster_id")
    private UUID adjusterId;

    @Column(name = "adjuster_assigned_at")
    private LocalDateTime adjusterAssignedAt;

    @Column(name = "fraud_score")
    private Double fraudScore;

    @Column(name = "fraud_flags", columnDefinition = "TEXT")
    private String fraudFlags;

    @Column(name = "requires_investigation")
    private Boolean requiresInvestigation = false;

    @Column(name = "investigation_completed")
    private Boolean investigationCompleted = false;

    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;

    @Column(name = "denial_reason", columnDefinition = "TEXT")
    private String denialReason;

    @Column(name = "priority")
    private Integer priority = 5; // 1-10 scale

    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Column(name = "sla_breached")
    private Boolean slaBreached = false;

    @Column(name = "specialty_required")
    private String specialtyRequired;

    @Column(name = "supporting_documents", columnDefinition = "TEXT")
    private String supportingDocuments;

    @Column(name = "processing_notes", columnDefinition = "TEXT")
    private String processingNotes;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClaimDocument> documents = new ArrayList<>();

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum ClaimType {
        MEDICAL, ACCIDENT, PROPERTY_DAMAGE, THEFT, FIRE, FLOOD,
        DEATH_BENEFIT, DISABILITY, TRAVEL_INTERRUPTION, LIABILITY
    }

    /**
     * Check if claim is pending
     */
    public boolean isPending() {
        return status == ClaimStatus.PENDING ||
               status == ClaimStatus.UNDER_REVIEW ||
               status == ClaimStatus.PENDING_DOCUMENTS;
    }

    /**
     * Check if claim is approved
     */
    public boolean isApproved() {
        return status == ClaimStatus.APPROVED || status == ClaimStatus.SETTLED;
    }

    /**
     * Check if SLA is breached
     */
    public boolean isSLABreached() {
        return slaDeadline != null && LocalDateTime.now().isAfter(slaDeadline);
    }

    /**
     * Calculate days since submission
     */
    public long getDaysSinceSubmission() {
        return java.time.temporal.ChronoUnit.DAYS.between(
                submissionDate.toLocalDate(),
                LocalDate.now()
        );
    }
}
