package com.waqiti.account.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_number", columnList = "account_number", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_account_type", columnList = "account_type"),
    @Index(name = "idx_last_transaction_date", columnList = "last_transaction_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"accountId"})
@ToString(exclude = {"metadata"})
public class Account {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID accountId;
    
    @Column(name = "account_number", unique = true, nullable = false, length = 20)
    private String accountNumber;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", nullable = false, length = 20)
    private AccountCategory accountCategory;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;
    
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;
    
    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;
    
    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal reservedBalance = BigDecimal.ZERO;
    
    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;
    
    @Column(name = "daily_transaction_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;
    
    @Column(name = "monthly_transaction_limit", precision = 19, scale = 4)
    private BigDecimal monthlyTransactionLimit;
    
    @Column(name = "minimum_balance", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal minimumBalance = BigDecimal.ZERO;
    
    @Column(name = "maximum_balance", precision = 19, scale = 4)
    private BigDecimal maximumBalance;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_level", nullable = false, length = 20)
    @Builder.Default
    private ComplianceLevel complianceLevel = ComplianceLevel.STANDARD;
    
    @Column(name = "kyc_verified")
    @Builder.Default
    private Boolean kycVerified = false;
    
    @Column(name = "kyc_verified_date")
    private LocalDateTime kycVerifiedDate;
    
    @Column(name = "freeze_reason", length = 500)
    private String freezeReason;
    
    @Column(name = "closure_reason", length = 500)
    private String closureReason;
    
    @Column(name = "opened_date", nullable = false)
    private LocalDateTime openedDate;
    
    @Column(name = "closed_date")
    private LocalDateTime closedDate;
    
    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;
    
    @Column(name = "last_maintenance_date")
    private LocalDateTime lastMaintenanceDate;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "account_metadata", 
        joinColumns = @JoinColumn(name = "account_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;

    public String getState() {
        return "";
//        // TODO - fully implement
    }

    // Enums
    
    public enum AccountType {
        USER_WALLET,           // Standard user wallet account
        USER_SAVINGS,          // User savings account
        USER_CREDIT,           // User credit account
        BUSINESS_OPERATING,    // Business operating account
        BUSINESS_ESCROW,       // Business escrow account
        MERCHANT,              // Merchant account
        SYSTEM_ASSET,          // System asset account
        SYSTEM_LIABILITY,      // System liability account
        NOSTRO,                // Nostro account for external banking
        FEE_COLLECTION,        // Fee collection account
        SUSPENSE,              // Suspense account
        TRANSIT,               // Transit account for transfers
        RESERVE                // Reserve account
    }
    
    public enum AccountCategory {
        ASSET,
        LIABILITY,
        REVENUE,
        EXPENSE,
        EQUITY
    }
    
    public enum AccountStatus {
        PENDING,    // Awaiting activation
        ACTIVE,     // Operational
        SUSPENDED,  // Temporarily suspended
        FROZEN,     // Frozen for security/compliance
        DORMANT,    // No activity for extended period
        CLOSED      // Permanently closed
    }
    
    public enum ComplianceLevel {
        BASIC,      // Basic KYC
        STANDARD,   // Standard KYC
        ENHANCED,   // Enhanced due diligence
        RESTRICTED  // Restricted access
    }
    
    // Business methods
    
    public boolean isOperational() {
        return status == AccountStatus.ACTIVE;
    }
    
    public boolean canDebit() {
        return isOperational() && !isDebitRestricted();
    }
    
    public boolean canCredit() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.SUSPENDED;
    }
    
    public boolean isDebitRestricted() {
        return status == AccountStatus.FROZEN || status == AccountStatus.CLOSED;
    }
    
    public boolean hasReachedMaximumBalance() {
        return maximumBalance != null && currentBalance.compareTo(maximumBalance) >= 0;
    }
    
    public boolean hasFallenBelowMinimumBalance() {
        return minimumBalance != null && currentBalance.compareTo(minimumBalance) < 0;
    }
    
    public BigDecimal getEffectiveAvailableBalance() {
        BigDecimal effective = availableBalance;
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            effective = effective.add(creditLimit);
        }
        return effective;
    }
    
    public void updateLastTransactionDate() {
        this.lastTransactionDate = LocalDateTime.now();
    }
}