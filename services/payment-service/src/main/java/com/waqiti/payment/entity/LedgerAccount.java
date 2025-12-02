package com.waqiti.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ledger Account entity for double-entry bookkeeping
 */
@Entity
@Table(name = "ledger_accounts", indexes = {
    @Index(name = "idx_ledger_account_number", columnList = "accountNumber"),
    @Index(name = "idx_ledger_account_type", columnList = "accountType"),
    @Index(name = "idx_ledger_account_owner", columnList = "ownerId"),
    @Index(name = "idx_ledger_account_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String accountName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountCategory category;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pendingDebits;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pendingCredits;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column
    private Boolean isReconciled;

    @Column
    private LocalDateTime lastReconciledAt;

    @Column
    private BigDecimal creditLimit;

    @Column
    private BigDecimal minimumBalance;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum AccountType {
        ASSET,      // Increases with debit, decreases with credit
        LIABILITY,  // Decreases with debit, increases with credit
        EQUITY,     // Decreases with debit, increases with credit
        REVENUE,    // Decreases with debit, increases with credit
        EXPENSE     // Increases with debit, decreases with credit
    }

    public enum AccountCategory {
        // Asset categories
        CASH,
        BANK_ACCOUNT,
        ACCOUNTS_RECEIVABLE,
        INVENTORY,
        FIXED_ASSET,
        
        // Liability categories
        ACCOUNTS_PAYABLE,
        CREDIT_CARD,
        LOAN,
        UNEARNED_REVENUE,
        
        // Equity categories
        OWNERS_EQUITY,
        RETAINED_EARNINGS,
        
        // Revenue categories
        SALES_REVENUE,
        SERVICE_REVENUE,
        INTEREST_REVENUE,
        
        // Expense categories
        OPERATING_EXPENSE,
        COST_OF_GOODS,
        PAYROLL,
        RENT,
        UTILITIES,
        FEES
    }

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        FROZEN,
        CLOSED,
        SUSPENDED
    }

    /**
     * Check if account type increases with debit
     */
    public boolean isDebitPositive() {
        return accountType == AccountType.ASSET || accountType == AccountType.EXPENSE;
    }

    /**
     * Check if account type increases with credit
     */
    public boolean isCreditPositive() {
        return accountType == AccountType.LIABILITY || 
               accountType == AccountType.EQUITY || 
               accountType == AccountType.REVENUE;
    }
}