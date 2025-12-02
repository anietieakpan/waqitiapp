package com.waqiti.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Restriction Entity
 * 
 * Represents restrictions applied to wallet transactions for compliance, 
 * risk management, or regulatory purposes.
 * 
 * COMPLIANCE: Immutable audit trail for all restriction changes
 * SECURITY: All restrictions are versioned and logged
 */
@Entity
@Table(name = "transaction_restrictions", indexes = {
    @Index(name = "idx_restriction_wallet", columnList = "wallet_id"),
    @Index(name = "idx_restriction_type", columnList = "restriction_type"),
    @Index(name = "idx_restriction_status", columnList = "status"),
    @Index(name = "idx_restriction_expires", columnList = "expires_at"),
    @Index(name = "idx_restriction_wallet_type", columnList = "wallet_id, restriction_type"),
    @Index(name = "idx_restriction_active", columnList = "wallet_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRestriction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(name = "wallet_id", nullable = false, length = 100)
    private String walletId;
    
    @Column(name = "restriction_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RestrictionType restrictionType;
    
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RestrictionStatus status = RestrictionStatus.ACTIVE;
    
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;
    
    @Column(name = "applied_by", nullable = false, length = 100)
    private String appliedBy;
    
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "removed_by", length = 100)
    private String removedBy;
    
    @Column(name = "removed_at")
    private LocalDateTime removedAt;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    public enum RestrictionType {
        WALLET_FROZEN,
        DEBIT_BLOCKED,
        CREDIT_BLOCKED,
        TRANSFER_BLOCKED,
        WITHDRAWAL_BLOCKED,
        DEPOSIT_BLOCKED,
        BALANCE_LIMIT,
        VELOCITY_LIMIT,
        AMOUNT_LIMIT,
        TRANSACTION_TYPE_BLOCKED,
        REGULATORY_HOLD,
        FRAUD_HOLD,
        COMPLIANCE_HOLD,
        AML_HOLD,
        KYC_REQUIRED
    }
    
    public enum RestrictionStatus {
        ACTIVE,
        EXPIRED,
        REMOVED,
        SUPERSEDED
    }
    
    public boolean isActive() {
        if (status != RestrictionStatus.ACTIVE) {
            return false;
        }
        
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        
        return true;
    }
    
    public void expire() {
        this.status = RestrictionStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void remove(String removedBy) {
        this.status = RestrictionStatus.REMOVED;
        this.removedBy = removedBy;
        this.removedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void supersede(String updatedBy) {
        this.status = RestrictionStatus.SUPERSEDED;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
}