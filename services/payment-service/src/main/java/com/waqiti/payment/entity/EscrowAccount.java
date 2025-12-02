package com.waqiti.payment.entity;

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
 * Escrow Account Entity
 * Represents a secure holding account for multi-party transactions
 * 
 * COMPLIANCE: Required for PCI DSS and consumer protection regulations
 * AUDIT: All state changes must be tracked and immutable
 */
@Entity
@Table(name = "escrow_accounts", indexes = {
    @Index(name = "idx_escrow_account_id", columnList = "account_id", unique = true),
    @Index(name = "idx_escrow_buyer", columnList = "buyer_id"),
    @Index(name = "idx_escrow_seller", columnList = "seller_id"),
    @Index(name = "idx_escrow_agent", columnList = "escrow_agent_id"),
    @Index(name = "idx_escrow_status", columnList = "status"),
    @Index(name = "idx_escrow_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(name = "account_id", nullable = false, unique = true, length = 100)
    private String accountId;
    
    @Column(name = "buyer_id", nullable = false)
    private String buyerId;
    
    @Column(name = "seller_id", nullable = false)
    private String sellerId;
    
    @Column(name = "escrow_agent_id", nullable = false)
    private String escrowAgentId;
    
    @Column(name = "contract_id", nullable = false, length = 100)
    private String contractId;
    
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.PENDING;
    
    @Column(name = "escrow_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EscrowType escrowType;
    
    @Column(name = "required_approvals", nullable = false)
    @Builder.Default
    private Integer requiredApprovals = 2;
    
    @Column(name = "received_approvals", nullable = false)
    @Builder.Default
    private Integer receivedApprovals = 0;
    
    @Column(name = "release_date")
    private LocalDateTime releaseDate;
    
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;
    
    @Column(name = "is_disputed", nullable = false)
    @Builder.Default
    private Boolean isDisputed = false;
    
    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;
    
    @Column(name = "dispute_opened_at")
    private LocalDateTime disputeOpenedAt;
    
    @Column(name = "frozen", nullable = false)
    @Builder.Default
    private Boolean frozen = false;
    
    @Column(name = "frozen_reason", columnDefinition = "TEXT")
    private String frozenReason;
    
    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;
    
    public enum EscrowStatus {
        PENDING,              // Account created, awaiting deposit
        FUNDED,               // Funds deposited, awaiting release conditions
        HELD,                 // Funds held pending approval
        APPROVED_FOR_RELEASE, // Approved but not yet released
        RELEASED,             // Funds released to seller
        REFUNDED,             // Funds refunded to buyer
        DISPUTED,             // Under dispute resolution
        EXPIRED,              // Expired without completion
        CANCELLED,            // Cancelled by parties
        COMPLETED             // Transaction completed successfully
    }
    
    public enum EscrowType {
        STANDARD,             // Standard escrow for goods/services
        MILESTONE,            // Milestone-based release
        REAL_ESTATE,          // Real estate transactions
        INTELLECTUAL_PROPERTY, // IP transfers
        DOMAIN_NAME,          // Domain name transfers
        BUSINESS_SALE,        // Business acquisition
        FREELANCE,            // Freelance work escrow
        MARKETPLACE           // Marketplace transactions
    }
    
    /**
     * Check if account can accept deposits
     */
    public boolean canAcceptDeposit() {
        return (status == EscrowStatus.PENDING || status == EscrowStatus.HELD) 
               && !frozen && !isDisputed;
    }
    
    /**
     * Check if funds can be released
     */
    public boolean canReleaseFunds() {
        return (status == EscrowStatus.FUNDED || status == EscrowStatus.APPROVED_FOR_RELEASE)
               && balance.compareTo(BigDecimal.ZERO) > 0
               && !frozen && !isDisputed
               && receivedApprovals >= requiredApprovals;
    }
    
    /**
     * Check if funds can be refunded
     */
    public boolean canRefundFunds() {
        return (status == EscrowStatus.FUNDED || status == EscrowStatus.HELD || status == EscrowStatus.DISPUTED)
               && balance.compareTo(BigDecimal.ZERO) > 0
               && !frozen;
    }
    
    /**
     * Add approval
     */
    public void addApproval() {
        this.receivedApprovals++;
        if (this.receivedApprovals >= this.requiredApprovals) {
            this.status = EscrowStatus.APPROVED_FOR_RELEASE;
        }
    }
    
    /**
     * Open dispute
     */
    public void openDispute(String reason) {
        this.isDisputed = true;
        this.disputeReason = reason;
        this.disputeOpenedAt = LocalDateTime.now();
        this.status = EscrowStatus.DISPUTED;
    }
    
    /**
     * Freeze account
     */
    public void freeze(String reason) {
        this.frozen = true;
        this.frozenReason = reason;
        this.frozenAt = LocalDateTime.now();
    }
    
    /**
     * Unfreeze account
     */
    public void unfreeze() {
        this.frozen = false;
        this.frozenReason = null;
        this.frozenAt = null;
    }
}