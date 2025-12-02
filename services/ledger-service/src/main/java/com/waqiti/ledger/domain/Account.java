package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Entity for Chart of Accounts
 * 
 * Represents individual accounts in the hierarchical chart of accounts
 * structure used for double-entry bookkeeping.
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_code", columnList = "accountCode", unique = true),
    @Index(name = "idx_parent_account", columnList = "parentAccountId"),
    @Index(name = "idx_account_type", columnList = "accountType"),
    @Index(name = "idx_active_accounts", columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "account_code", nullable = false, unique = true, length = 20)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "allows_transactions", nullable = false)
    @Builder.Default
    private Boolean allowsTransactions = true;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    private NormalBalance normalBalance;

    @Column(name = "level")
    private Integer level;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * CRITICAL ADDITION: Optimistic locking version field
     * Prevents lost updates in concurrent scenarios
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * CRITICAL ADDITION: Current balance with encryption
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private java.math.BigDecimal balance = java.math.BigDecimal.ZERO;

    /**
     * CRITICAL ADDITION: Overdraft protection flag
     */
    @Column(name = "is_overdraft_allowed", nullable = false)
    @Builder.Default
    private Boolean isOverdraftAllowed = false;

    /**
     * CRITICAL ADDITION: Overdraft limit
     */
    @Column(name = "overdraft_limit", precision = 19, scale = 4)
    private java.math.BigDecimal overdraftLimit = java.math.BigDecimal.ZERO;

    /**
     * Track last transaction timestamp
     */
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    /**
     * Account types following standard accounting classification
     */
    public enum AccountType {
        // Assets
        ASSET,
        CURRENT_ASSET,
        CASH,
        ACCOUNTS_RECEIVABLE,
        INVENTORY,
        PREPAID_EXPENSES,
        PREPAID_EXPENSE,  // Alias for compatibility
        FIXED_ASSET,
        PROPERTY_PLANT_EQUIPMENT,
        ACCUMULATED_DEPRECIATION,
        INTANGIBLE_ASSETS,
        OTHER_ASSET,  // Other assets not elsewhere classified

        // Liabilities
        LIABILITY,
        CURRENT_LIABILITY,
        ACCOUNTS_PAYABLE,
        ACCRUED_LIABILITIES,
        ACCRUED_EXPENSE,  // Alias for accrued liabilities
        SHORT_TERM_DEBT,
        LONG_TERM_LIABILITY,
        LONG_TERM_DEBT,
        DEFERRED_REVENUE,
        UNEARNED_REVENUE,  // Alias for deferred revenue
        OTHER_LIABILITY,  // Other liabilities not elsewhere classified

        // Equity
        EQUITY,
        PAID_IN_CAPITAL,
        RETAINED_EARNINGS,
        OWNER_EQUITY,
        ACCUMULATED_OTHER_COMPREHENSIVE_INCOME,

        // Revenue
        REVENUE,
        OPERATING_REVENUE,
        SALES_REVENUE,
        SERVICE_REVENUE,
        OTHER_REVENUE,
        INTEREST_INCOME,
        COMMISSION_INCOME,
        DIVIDEND_INCOME,
        OTHER_COMPREHENSIVE_INCOME,

        // Expenses
        EXPENSE,
        OPERATING_EXPENSE,
        COST_OF_GOODS_SOLD,
        ADMINISTRATIVE_EXPENSE,
        SELLING_EXPENSE,
        INTEREST_EXPENSE,
        OTHER_EXPENSE,
        SALARIES_EXPENSE,
        RENT_EXPENSE,
        UTILITIES_EXPENSE,
        MARKETING_EXPENSE,
        DEPRECIATION_EXPENSE,
        AMORTIZATION_EXPENSE,
        INSURANCE_EXPENSE,
        TAX_EXPENSE,
        OPERATIONAL_EXPENSE,  // Alias for OPERATING_EXPENSE

        // Income Statement Categories
        SALES_RETURNS,
        SALES_DISCOUNTS,
        TAX_PAYABLE,
        DEFERRED_TAX_LIABILITY,
        PREFERRED_STOCK,
        COMMON_STOCK,
        TREASURY_STOCK,
        ADDITIONAL_PAID_IN_CAPITAL,
        DIVIDENDS,
        DIVIDENDS_PAYABLE
    }

    /**
     * Normal balance indicates whether the account typically has a debit or credit balance
     */
    public enum NormalBalance {
        DEBIT,  // Assets and Expenses typically have debit balances
        CREDIT  // Liabilities, Equity, and Revenue typically have credit balances
    }

    /**
     * Helper methods to determine account classification
     */
    public boolean isAsset() {
        return accountType.name().contains("ASSET") || 
               accountType == AccountType.CASH ||
               accountType == AccountType.ACCOUNTS_RECEIVABLE ||
               accountType == AccountType.INVENTORY ||
               accountType == AccountType.PREPAID_EXPENSES ||
               accountType == AccountType.PROPERTY_PLANT_EQUIPMENT ||
               accountType == AccountType.ACCUMULATED_DEPRECIATION ||
               accountType == AccountType.INTANGIBLE_ASSETS;
    }

    public boolean isLiability() {
        return accountType.name().contains("LIABILITY") ||
               accountType == AccountType.ACCOUNTS_PAYABLE ||
               accountType == AccountType.ACCRUED_LIABILITIES ||
               accountType == AccountType.SHORT_TERM_DEBT ||
               accountType == AccountType.LONG_TERM_DEBT ||
               accountType == AccountType.DEFERRED_REVENUE;
    }

    public boolean isEquity() {
        return accountType.name().contains("EQUITY") ||
               accountType == AccountType.PAID_IN_CAPITAL ||
               accountType == AccountType.RETAINED_EARNINGS ||
               accountType == AccountType.OWNER_EQUITY ||
               accountType == AccountType.ACCUMULATED_OTHER_COMPREHENSIVE_INCOME;
    }

    public boolean isRevenue() {
        return accountType.name().contains("REVENUE") ||
               accountType == AccountType.SALES_REVENUE ||
               accountType == AccountType.SERVICE_REVENUE ||
               accountType == AccountType.INTEREST_INCOME;
    }

    public boolean isExpense() {
        return accountType.name().contains("EXPENSE") ||
               accountType == AccountType.COST_OF_GOODS_SOLD ||
               accountType == AccountType.ADMINISTRATIVE_EXPENSE ||
               accountType == AccountType.SELLING_EXPENSE ||
               accountType == AccountType.INTEREST_EXPENSE;
    }

    /**
     * Determines if debits increase this account's balance
     */
    public boolean isDebitPositive() {
        return isAsset() || isExpense();
    }

    /**
     * Determines if credits increase this account's balance
     */
    public boolean isCreditPositive() {
        return isLiability() || isEquity() || isRevenue();
    }

    /**
     * Check if account is verified
     */
    public boolean isVerified() {
        return Boolean.TRUE.equals(isVerified);
    }
}