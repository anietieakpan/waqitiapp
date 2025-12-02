package com.waqiti.insurance.entity;

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
 * Insurance Policy Entity
 * Represents an insurance policy contract between the insurer and policyholder
 */
@Entity
@Table(name = "insurance_policies", indexes = {
        @Index(name = "idx_policy_number", columnList = "policy_number", unique = true),
        @Index(name = "idx_policyholder_id", columnList = "policyholder_id"),
        @Index(name = "idx_policy_status", columnList = "status"),
        @Index(name = "idx_policy_type", columnList = "policy_type"),
        @Index(name = "idx_effective_date", columnList = "effective_date"),
        @Index(name = "idx_expiry_date", columnList = "expiry_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_number", unique = true, nullable = false, length = 50)
    private String policyNumber;

    @Column(name = "policyholder_id", nullable = false)
    private UUID policyholderId;

    @Column(name = "policyholder_name", nullable = false)
    private String policyholderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 30)
    private PolicyType policyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PolicyStatus status;

    @Column(name = "coverage_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal coverageAmount;

    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "deductible", precision = 19, scale = 2)
    private BigDecimal deductible;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", nullable = false, length = 20)
    private PaymentFrequency paymentFrequency;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "next_renewal_date")
    private LocalDate nextRenewalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", length = 20)
    private RiskCategory riskCategory;

    @Column(name = "underwriter_id")
    private UUID underwriterId;

    @Column(name = "underwriting_date")
    private LocalDateTime underwritingDate;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approval_date")
    private LocalDateTime approvalDate;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "total_claims_count")
    private Integer totalClaimsCount = 0;

    @Column(name = "total_claims_amount", precision = 19, scale = 2)
    private BigDecimal totalClaimsAmount = BigDecimal.ZERO;

    @Column(name = "last_claim_date")
    private LocalDateTime lastClaimDate;

    @Column(name = "policy_document_url")
    private String policyDocumentUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InsuranceClaim> claims = new ArrayList<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Premium> premiums = new ArrayList<>();

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

    public enum PolicyType {
        LIFE, HEALTH, AUTO, HOME, BUSINESS, TRAVEL, DISABILITY, CRITICAL_ILLNESS
    }

    public enum PolicyStatus {
        PENDING, ACTIVE, SUSPENDED, CANCELLED, EXPIRED, LAPSED
    }

    public enum PaymentFrequency {
        MONTHLY, QUARTERLY, SEMI_ANNUALLY, ANNUALLY
    }

    public enum RiskCategory {
        LOW, MEDIUM, HIGH, VERY_HIGH
    }

    /**
     * Check if policy is active
     */
    public boolean isActive() {
        return status == PolicyStatus.ACTIVE &&
               LocalDate.now().isAfter(effectiveDate) &&
               LocalDate.now().isBefore(expiryDate);
    }

    /**
     * Check if policy has expired
     */
    public boolean isExpired() {
        return LocalDate.now().isAfter(expiryDate) ||
               status == PolicyStatus.EXPIRED;
    }

    /**
     * Get remaining coverage amount
     */
    public BigDecimal getRemainingCoverage() {
        return coverageAmount.subtract(totalClaimsAmount != null ? totalClaimsAmount : BigDecimal.ZERO);
    }
}
