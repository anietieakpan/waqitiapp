package com.waqiti.purchaseprotection.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Protection claim entity representing a claim against a protection policy.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Entity
@Table(name = "protection_claims", indexes = {
    @Index(name = "idx_claims_policy", columnList = "policy_id"),
    @Index(name = "idx_claims_status", columnList = "status"),
    @Index(name = "idx_claims_type", columnList = "claim_type"),
    @Index(name = "idx_claims_filed_at", columnList = "filed_at")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtectionClaim {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private ProtectionPolicy policy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false)
    private ClaimType claimType;
    
    @Column(name = "reason", nullable = false)
    private String reason;
    
    @Column(name = "description", length = 2000)
    private String description;
    
    @Column(name = "claim_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal claimAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;
    
    @Column(name = "filed_at", nullable = false)
    private Instant filedAt;
    
    @ElementCollection
    @CollectionTable(name = "claim_evidence", joinColumns = @JoinColumn(name = "claim_id"))
    @Column(name = "evidence_url")
    private List<String> evidenceUrls;
    
    @Column(name = "fraud_score")
    private Double fraudScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_check_result")
    private FraudResult fraudCheckResult;
    
    @Column(name = "investigation_reason")
    private String investigationReason;
    
    @Column(name = "auto_approved")
    private boolean autoApproved;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
    
    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;
    
    @Column(name = "approved_by")
    private String approvedBy;
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    @Column(name = "rejected_at")
    private Instant rejectedAt;
    
    @Column(name = "rejected_by")
    private String rejectedBy;
    
    @Column(name = "paid_at")
    private Instant paidAt;
    
    @Column(name = "payment_reference")
    private String paymentReference;
    
    @Column(name = "payment_failure_reason")
    private String paymentFailureReason;
    
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ClaimDocument> documents;
    
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ClaimNote> notes;
    
    @ElementCollection
    @CollectionTable(name = "claim_metadata", joinColumns = @JoinColumn(name = "claim_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if claim is pending review.
     */
    public boolean isPending() {
        return status == ClaimStatus.SUBMITTED || 
               status == ClaimStatus.UNDER_REVIEW || 
               status == ClaimStatus.UNDER_INVESTIGATION;
    }
    
    /**
     * Check if claim has been resolved.
     */
    public boolean isResolved() {
        return status == ClaimStatus.APPROVED || 
               status == ClaimStatus.REJECTED || 
               status == ClaimStatus.PAID;
    }
}

/**
 * Claim type enumeration.
 */
enum ClaimType {
    ITEM_NOT_RECEIVED,
    ITEM_NOT_AS_DESCRIBED,
    DAMAGED_ITEM,
    COUNTERFEIT_ITEM,
    FRAUDULENT_SELLER,
    UNAUTHORIZED_TRANSACTION,
    QUALITY_ISSUE,
    SHIPPING_ISSUE,
    OTHER
}

/**
 * Claim status enumeration.
 */
enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    UNDER_INVESTIGATION,
    APPROVED,
    REJECTED,
    PAID,
    PAYMENT_FAILED,
    PROCESSING_ERROR,
    CANCELLED
}

/**
 * Fraud check result enumeration.
 */
enum FraudResult {
    LOW_RISK,
    MEDIUM_RISK,
    HIGH_RISK,
    FRAUDULENT
}

/**
 * Claim document entity.
 */
@Data
@Entity
@Table(name = "claim_documents")
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ClaimDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private ProtectionClaim claim;
    
    @Column(name = "document_type", nullable = false)
    private String documentType;
    
    @Column(name = "document_url", nullable = false)
    private String documentUrl;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
    
    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;
}

/**
 * Claim note entity for internal tracking.
 */
@Data
@Entity
@Table(name = "claim_notes")
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ClaimNote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private ProtectionClaim claim;
    
    @Column(name = "note", nullable = false, length = 1000)
    private String note;
    
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    
    @Column(name = "is_internal")
    private boolean internal;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}