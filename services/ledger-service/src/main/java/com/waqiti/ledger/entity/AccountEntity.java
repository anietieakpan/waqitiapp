package com.waqiti.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Account Entity
 * 
 * Represents a ledger account in the chart of accounts.
 * Supports hierarchical account structure and multiple account types.
 */
@Entity
@Table(name = "accounts",
    indexes = {
        @Index(name = "idx_account_code", columnList = "account_code", unique = true),
        @Index(name = "idx_account_type", columnList = "account_type"),
        @Index(name = "idx_parent_account", columnList = "parent_account_id"),
        @Index(name = "idx_account_active", columnList = "is_active"),
        @Index(name = "idx_account_company", columnList = "company_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"parentAccount", "childAccounts", "ledgerEntries"})
@ToString(exclude = {"parentAccount", "childAccounts", "ledgerEntries"})
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "account_code", nullable = false, unique = true, length = 50)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 200)
    private String accountName;

    @Column(name = "account_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private AccountEntity parentAccount;

    @OneToMany(mappedBy = "parentAccount", cascade = CascadeType.ALL)
    private Set<AccountEntity> childAccounts = new HashSet<>();

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "allows_transactions", nullable = false)
    private Boolean allowsTransactions = true;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "normal_balance", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private NormalBalance normalBalance;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", precision = 19, scale = 4)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "cost_center_id")
    private UUID costCenterId;

    // Audit fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    // Relationships
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<LedgerEntryEntity> ledgerEntries = new HashSet<>();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<AccountBalanceEntity> accountBalances = new HashSet<>();

    // Business methods
    public boolean isAssetAccount() {
        return accountType == AccountType.ASSET || 
               accountType == AccountType.CURRENT_ASSET || 
               accountType == AccountType.FIXED_ASSET;
    }

    public boolean isLiabilityAccount() {
        return accountType == AccountType.LIABILITY || 
               accountType == AccountType.CURRENT_LIABILITY || 
               accountType == AccountType.LONG_TERM_LIABILITY;
    }

    public boolean isEquityAccount() {
        return accountType == AccountType.EQUITY || 
               accountType == AccountType.RETAINED_EARNINGS;
    }

    public boolean isRevenueAccount() {
        return accountType == AccountType.REVENUE || 
               accountType == AccountType.OPERATING_REVENUE;
    }

    public boolean isExpenseAccount() {
        return accountType == AccountType.EXPENSE || 
               accountType == AccountType.OPERATING_EXPENSE;
    }

    public enum AccountType {
        // Assets
        ASSET,
        CURRENT_ASSET,
        FIXED_ASSET,
        OTHER_ASSET,
        
        // Liabilities
        LIABILITY,
        CURRENT_LIABILITY,
        LONG_TERM_LIABILITY,
        
        // Equity
        EQUITY,
        RETAINED_EARNINGS,
        CAPITAL,
        
        // Revenue
        REVENUE,
        OPERATING_REVENUE,
        OTHER_REVENUE,
        
        // Expenses
        EXPENSE,
        OPERATING_EXPENSE,
        COST_OF_GOODS_SOLD,
        OTHER_EXPENSE,
        
        // Special
        BANK,
        CONTRA_ASSET,
        CONTRA_LIABILITY
    }

    public enum NormalBalance {
        DEBIT,
        CREDIT
    }
}