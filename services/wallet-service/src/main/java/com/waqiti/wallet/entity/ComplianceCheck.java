package com.waqiti.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compliance Check Entity
 * 
 * Represents compliance checks performed on wallets and users including
 * AML, KYC, sanctions screening, PEP screening, and SAR filings.
 * 
 * COMPLIANCE: Immutable audit trail for all compliance checks
 * SECURITY: All checks are versioned and permanently stored
 * REGULATORY: Supports FinCEN, OFAC, and other regulatory requirements
 */
@Entity
@Table(name = "compliance_checks", indexes = {
    @Index(name = "idx_compliance_wallet", columnList = "wallet_id"),
    @Index(name = "idx_compliance_user", columnList = "user_id"),
    @Index(name = "idx_compliance_type", columnList = "check_type"),
    @Index(name = "idx_compliance_status", columnList = "status"),
    @Index(name = "idx_compliance_risk", columnList = "risk_level"),
    @Index(name = "idx_compliance_initiated", columnList = "initiated_at"),
    @Index(name = "idx_compliance_external", columnList = "external_check_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheck {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(name = "wallet_id", length = 100)
    private String walletId;
    
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    
    @Column(name = "check_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CheckType checkType;
    
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CheckStatus status = CheckStatus.PENDING;
    
    @Column(name = "risk_level", length = 50)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    @Column(name = "risk_score", precision = 5, scale = 4)
    private Double riskScore;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "transaction_details", columnDefinition = "jsonb")
    private String transactionDetails;
    
    @Column(name = "check_result", columnDefinition = "jsonb")
    private String checkResult;
    
    @Column(name = "external_check_id", length = 200)
    private String externalCheckId;
    
    @Column(name = "flags", columnDefinition = "TEXT")
    private String flags;
    
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private String recommendations;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;
    
    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum CheckType {
        AML,
        KYC,
        SANCTIONS,
        PEP,
        ADVERSE_MEDIA,
        TRANSACTION_MONITORING,
        VELOCITY_CHECK,
        SAR,
        CTR,
        LIMIT_CHECK,
        REGULATORY_HOLD,
        ENHANCED_DUE_DILIGENCE
    }
    
    public enum CheckStatus {
        PENDING,
        IN_PROGRESS,
        PASSED,
        FAILED,
        FLAGGED,
        BLOCKED,
        REQUIRES_REVIEW,
        REVIEWED,
        APPROVED,
        REJECTED
    }
    
    public enum RiskLevel {
        MINIMAL,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public boolean isPassed() {
        return status == CheckStatus.PASSED || status == CheckStatus.APPROVED;
    }
    
    public boolean isBlocked() {
        return status == CheckStatus.BLOCKED || status == CheckStatus.REJECTED;
    }
    
    public boolean requiresReview() {
        return status == CheckStatus.FLAGGED || status == CheckStatus.REQUIRES_REVIEW;
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean isCompleted() {
        return completedAt != null;
    }
    
    public void markCompleted(CheckStatus finalStatus) {
        this.status = finalStatus;
        this.completedAt = LocalDateTime.now();
    }
    
    public void markReviewed(String reviewedBy, String reviewNotes, CheckStatus finalStatus) {
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNotes = reviewNotes;
        this.status = finalStatus;
    }
}