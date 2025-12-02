package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import com.waqiti.corebanking.exception.InsufficientFundsException;

/**
 * Core Banking Account Entity
 *
 * Represents a financial account in the double-entry bookkeeping system.
 * Supports various account types for comprehensive banking operations.
 *
 * GDPR COMPLIANCE: Supports soft delete for data retention requirements
 * - Deleted accounts are marked with deletedAt timestamp
 * - Queries automatically filter out deleted records via @Where clause
 * - Hard delete performed after regulatory retention period (7 years)
 *
 * EntityGraph Optimization:
 * - basic: Loads only account fields (default)
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_number", columnList = "accountNumber", unique = true),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_account_type_status", columnList = "accountType, status"),
    @Index(name = "idx_parent_account", columnList = "parentAccountId"),
    @Index(name = "idx_currency", columnList = "currency"),
    @Index(name = "idx_deleted_at", columnList = "deletedAt")
})
@EntityListeners(AuditingEntityListener.class)
@org.hibernate.annotations.SQLDelete(sql = "UPDATE accounts SET deleted_at = CURRENT_TIMESTAMP WHERE account_id = ? AND version = ?")
@org.hibernate.annotations.Where(clause = "deleted_at IS NULL")
@NamedEntityGraph(name = "Account.basic")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID accountId;

    @Column(name = "account_number", unique = true, nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", nullable = false)
    private AccountCategory accountCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "current_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "pending_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal pendingBalance;

    @Column(name = "reserved_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal reservedBalance;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "daily_transaction_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "monthly_transaction_limit", precision = 19, scale = 4)
    private BigDecimal monthlyTransactionLimit;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    @Column(name = "account_code", length = 20)
    private String accountCode; // Chart of Accounts reference

    @Column(name = "minimum_balance", precision = 19, scale = 4)
    private BigDecimal minimumBalance;

    @Column(name = "maximum_balance", precision = 19, scale = 4)
    private BigDecimal maximumBalance;

    @Column(name = "interest_rate", precision = 8, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "fee_schedule_id")
    private UUID feeScheduleId;

    @Column(name = "interest_calculation_method")
    @Enumerated(EnumType.STRING)
    private InterestCalculationMethod interestCalculationMethod;

    @Column(name = "last_interest_calculation_date")
    private LocalDate lastInterestCalculationDate;

    @Column(name = "last_interest_credited_amount", precision = 19, scale = 4)
    private BigDecimal lastInterestCreditedAmount;

    @Column(name = "minimum_balance_for_interest", precision = 19, scale = 4)
    private BigDecimal minimumBalanceForInterest;

    @Column(name = "minimum_interest_amount", precision = 19, scale = 4)
    private BigDecimal minimumInterestAmount;

    /**
     * PRODUCTION-GRADE ENHANCEMENT: Fund reservations now handled by ProductionFundReservationService
     * Replaced in-memory tracking with database-persistent reservations for:
     * - Atomic operations with balance verification
     * - Persistence across service restarts
     * - Automatic expiration handling
     * - Comprehensive audit trail
     * 
     * @deprecated Use ProductionFundReservationService instead
     */
    @Transient
    @Deprecated(forRemoval = true, since = "1.0")
    @Builder.Default
    private final Map<String, BigDecimal> reservationMap = new ConcurrentHashMap<>();

    @Column(name = "maximum_interest_amount", precision = 19, scale = 4)
    private BigDecimal maximumInterestAmount;

    @Column(name = "compliance_level")
    @Enumerated(EnumType.STRING)
    private ComplianceLevel complianceLevel;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "external_account_id", length = 100)
    private String externalAccountId;

    @Column(name = "last_kyc_update")
    private LocalDateTime lastKycUpdate;

    @Column(name = "freeze_reason", length = 500)
    private String freezeReason;

    @Column(name = "closure_reason", length = 500)
    private String closureReason;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "last_statement_date")
    private LocalDateTime lastStatementDate;

    @Column(name = "opened_date", nullable = false)
    private LocalDateTime openedDate;

    @Column(name = "closed_date")
    private LocalDateTime closedDate;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON for additional account-specific data

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Soft Delete Fields (GDPR Compliance)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    // Business methods

    /**
     * Check if account is soft-deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Mark account as deleted (soft delete)
     * Use this instead of repository.delete() to preserve audit trail
     */
    public void markDeleted(UUID deletedBy, String reason) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.deletionReason = reason;
        this.status = AccountStatus.CLOSED;
    }

    /**
     * Checks if account can perform debit operations
     */
    public boolean canDebit(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            return false;
        }
        
        BigDecimal resultingBalance = availableBalance.subtract(amount);
        BigDecimal effectiveCreditLimit = creditLimit != null ? creditLimit : BigDecimal.ZERO;
        
        return resultingBalance.add(effectiveCreditLimit).compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Checks if account can perform credit operations
     */
    public boolean canCredit(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            return false;
        }
        
        if (maximumBalance != null) {
            BigDecimal resultingBalance = currentBalance.add(amount);
            return resultingBalance.compareTo(maximumBalance) <= 0;
        }
        
        return true;
    }

    /**
     * Checks if account has sufficient available balance
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    /**
     * Updates balance after a transaction
     */
    public synchronized void updateBalance(BigDecimal debitAmount, BigDecimal creditAmount) {
        // Validate inputs
        if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Debit amount cannot be negative");
        }
        if (creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit amount cannot be negative");
        }
        
        // Calculate new balances atomically
        BigDecimal newCurrentBalance = this.currentBalance;
        BigDecimal newAvailableBalance = this.availableBalance;
        
        if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
            newCurrentBalance = newCurrentBalance.subtract(debitAmount);
            newAvailableBalance = newAvailableBalance.subtract(debitAmount);
            
            // Validate overdraft limits
            if (!canDebit(debitAmount)) {
                throw new InsufficientFundsException("Insufficient funds for debit of " + debitAmount);
            }
        }
        
        if (creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) > 0) {
            newCurrentBalance = newCurrentBalance.add(creditAmount);
            newAvailableBalance = newAvailableBalance.add(creditAmount);
        }
        
        // Apply changes atomically
        this.currentBalance = newCurrentBalance;
        this.availableBalance = newAvailableBalance;
        this.lastTransactionDate = LocalDateTime.now();
        
        // Validate final state
        if (this.availableBalance.compareTo(this.currentBalance.add(getEffectiveCreditLimit())) > 0) {
            throw new IllegalStateException("Available balance cannot exceed current balance plus credit limit");
        }
    }

    /**
     * Reserves funds for pending transactions
     */
    public boolean reserveFunds(BigDecimal amount) {
        if (!hasSufficientBalance(amount)) {
            return false;
        }
        
        availableBalance = availableBalance.subtract(amount);
        reservedBalance = reservedBalance.add(amount);
        return true;
    }

    /**
     * Releases reserved funds
     */
    public void releaseReservedFunds(BigDecimal amount) {
        if (reservedBalance.compareTo(amount) >= 0) {
            reservedBalance = reservedBalance.subtract(amount);
            availableBalance = availableBalance.add(amount);
        }
    }

    /**
     * Confirms reserved funds transaction
     */
    public void confirmReservedTransaction(BigDecimal amount) {
        if (reservedBalance.compareTo(amount) >= 0) {
            reservedBalance = reservedBalance.subtract(amount);
            currentBalance = currentBalance.subtract(amount);
            lastTransactionDate = LocalDateTime.now();
        }
    }

    /**
     * Checks if account is operational
     */
    public boolean isOperational() {
        return status == AccountStatus.ACTIVE && 
               complianceLevel != ComplianceLevel.BLOCKED;
    }

    /**
     * Gets effective credit limit
     */
    public BigDecimal getEffectiveCreditLimit() {
        return creditLimit != null ? creditLimit : BigDecimal.ZERO;
    }

    /**
     * Gets total available funds including credit
     */
    public BigDecimal getTotalAvailableFunds() {
        return availableBalance.add(getEffectiveCreditLimit());
    }

    /**
     * Checks if account is a system account
     */
    public boolean isSystemAccount() {
        return accountType == AccountType.SYSTEM_ASSET || 
               accountType == AccountType.SYSTEM_LIABILITY ||
               accountType == AccountType.FEE_COLLECTION ||
               accountType == AccountType.SUSPENSE;
    }

    /**
     * Checks if account belongs to a user
     */
    public boolean isUserAccount() {
        return userId != null && !isSystemAccount();
    }

    /**
     * Gets account display name
     */
    public String getDisplayName() {
        return accountName + " (" + accountNumber + ")";
    }

    /**
     * Checks if account is active
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Checks if account is blocked
     */
    public boolean isBlocked() {
        return complianceLevel == ComplianceLevel.BLOCKED || status == AccountStatus.FROZEN;
    }

    /**
     * Gets the account ID for compatibility
     */
    public UUID getId() {
        return accountId;
    }

    /**
     * Gets account balance information
     */
    public BigDecimal getAccountBalance(String accountId, String type) {
        return switch (type.toLowerCase()) {
            case "current" -> currentBalance;
            case "available" -> availableBalance;
            case "pending" -> pendingBalance;
            case "reserved" -> reservedBalance;
            default -> currentBalance;
        };
    }

    /**
     * Sets last activity timestamp
     */
    public void setLastActivityAt(LocalDateTime timestamp) {
        this.lastTransactionDate = timestamp;
    }

    /**
     * Gets last activity timestamp
     */
    public LocalDateTime getLastActivityAt() {
        return lastTransactionDate;
    }


    /**
     * Checks if account is frozen
     */
    public boolean getIsFrozen() {
        return status == AccountStatus.FROZEN;
    }

    /**
     * Checks if account has available balance
     */
    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    /**
     * Gets allow overdraft status
     */
    public boolean getAllowOverdraft() {
        return creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * @deprecated This method uses in-memory reservations that are lost on service restart.
     * Use ProductionFundReservationService.reserveFunds() instead for database-persistent reservations.
     * This method now throws UnsupportedOperationException to force migration.
     *
     * @throws UnsupportedOperationException always - use ProductionFundReservationService instead
     */
    @Deprecated(forRemoval = true, since = "1.0")
    public synchronized void reserveFunds(String accountId, String transactionId, BigDecimal amount, String reason) {
        throw new UnsupportedOperationException(
            "In-memory fund reservations are deprecated and disabled. " +
            "Use ProductionFundReservationService.reserveFunds() instead. " +
            "In-memory reservations are lost on service restart and create data inconsistency. " +
            "Transaction ID: " + transactionId + ", Account: " + accountId
        );
    }

    /**
     * @deprecated This method uses in-memory reservations that are lost on service restart.
     * Use ProductionFundReservationService.releaseFunds() instead for database-persistent reservations.
     * This method now throws UnsupportedOperationException to force migration.
     *
     * @throws UnsupportedOperationException always - use ProductionFundReservationService instead
     */
    @Deprecated(forRemoval = true, since = "1.0")
    public synchronized void releaseFunds(String accountId, String transactionId) {
        throw new UnsupportedOperationException(
            "In-memory fund reservations are deprecated and disabled. " +
            "Use ProductionFundReservationService.releaseFunds() instead. " +
            "In-memory reservations are lost on service restart and create data inconsistency. " +
            "Transaction ID: " + transactionId + ", Account: " + accountId
        );
    }
    
    /**
     * Helper method to find reserved amount for a specific transaction
     */
    @Deprecated(forRemoval = true, since = "1.0")
    private BigDecimal findReservedAmountForTransaction(String transactionId) {
        // DEPRECATED: Use ProductionFundReservationService.getReservedAmountForTransaction() instead
        log.warn("DEPRECATED: Using legacy in-memory reservation tracking for transaction: {}. " +
                "Please migrate to ProductionFundReservationService", transactionId);
        return reservationMap.getOrDefault(transactionId, BigDecimal.ZERO);
    }
    
    /**
     * Helper method to remove reservation record
     */
    @Deprecated(forRemoval = true, since = "1.0")
    private void removeReservationRecord(String transactionId) {
        // DEPRECATED: Use ProductionFundReservationService.releaseFunds() instead
        log.warn("DEPRECATED: Using legacy in-memory reservation removal for transaction: {}. " +
                "Please migrate to ProductionFundReservationService", transactionId);
        reservationMap.remove(transactionId);
    }

    /**
     * Gets daily transaction limit
     */
    public BigDecimal getDailyLimit() {
        return dailyTransactionLimit != null ? dailyTransactionLimit : BigDecimal.valueOf(100000);
    }

    /**
     * Gets monthly transaction limit
     */
    public BigDecimal getMonthlyLimit() {
        return monthlyTransactionLimit != null ? monthlyTransactionLimit : BigDecimal.valueOf(3000000);
    }

    /**
     * Sets monthly limit
     */
    public void setMonthlyLimit(BigDecimal limit) {
        this.monthlyTransactionLimit = limit;
    }

    /**
     * Sets daily limit
     */
    public void setDailyLimit(BigDecimal limit) {
        this.dailyTransactionLimit = limit;
    }

    /**
     * Gets description - placeholder for metadata
     */
    public String getDescription() {
        return metadata;
    }

    /**
     * Sets description
     */
    public void setDescription(String description) {
        this.metadata = description;
    }

    /**
     * Gets closed at timestamp
     */
    public java.time.Instant getClosedAt() {
        return closedDate != null ? closedDate.atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * Sets closed at timestamp
     */
    public void setClosedAt(java.time.Instant closedAt) {
        this.closedDate = closedAt != null ? LocalDateTime.ofInstant(closedAt, java.time.ZoneId.systemDefault()) : null;
    }

    /**
     * Gets created at as Instant
     */
    public java.time.Instant getCreatedAt() {
        return createdAt != null ? createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * Gets updated at as Instant
     */
    public java.time.Instant getUpdatedAt() {
        return updatedAt != null ? updatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * Sets frozen status
     */
    public void setIsFrozen(boolean frozen) {
        if (frozen) {
            this.status = AccountStatus.FROZEN;
        } else if (this.status == AccountStatus.FROZEN) {
            this.status = AccountStatus.ACTIVE;
        }
    }

    /**
     * Debit account - reduces balance
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (!canDebit(amount)) {
            throw new InsufficientFundsException("Insufficient funds for debit: " + amount);
        }
        this.currentBalance = this.currentBalance.subtract(amount);
        this.availableBalance = this.availableBalance.subtract(amount);
        this.lastTransactionDate = LocalDateTime.now();
    }

    /**
     * Credit account - increases balance
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.currentBalance = this.currentBalance.add(amount);
        this.availableBalance = this.availableBalance.add(amount);
        this.lastTransactionDate = LocalDateTime.now();
    }

    /**
     * Gets effective balance including credit limit if allowed
     */
    public BigDecimal getEffectiveBalance() {
        BigDecimal effective = availableBalance;
        if (getAllowOverdraft() && creditLimit != null) {
            effective = effective.add(creditLimit);
        }
        return effective;
    }


    // Enums

    public enum AccountType {
        // User accounts
        USER_WALLET,           // User's primary wallet
        USER_SAVINGS,          // User's savings account
        USER_CREDIT,           // User's credit account

        // Standard banking accounts
        CHECKING,              // Checking account
        SAVINGS,               // Traditional savings account
        BUSINESS,              // Business account
        INVESTMENT,            // Investment account
        LOAN,                  // Loan account
        CREDIT,                // Credit account
        MONEY_MARKET,          // Money market account
        CERTIFICATE_OF_DEPOSIT, // Certificate of deposit

        // Business accounts
        BUSINESS_OPERATING,    // Business operating account
        BUSINESS_ESCROW,       // Business escrow account

        // System accounts
        SYSTEM_ASSET,          // System asset accounts
        SYSTEM_LIABILITY,      // System liability accounts
        FEE_COLLECTION,        // Fee collection account
        SUSPENSE,              // Suspense/pending account
        NOSTRO,                // External bank accounts

        // Special accounts
        MERCHANT,              // Merchant settlement account
        TRANSIT,               // Transit/clearing account
        RESERVE,               // Reserve/float account

        // Interest-bearing accounts
        FIXED_DEPOSIT          // Fixed deposit account
    }

    public enum AccountCategory {
        // Traditional accounting categories
        ASSET,                 // Assets (user wallets, system cash)
        LIABILITY,             // Liabilities (user balances, accruals)
        EQUITY,                // Equity (capital, retained earnings)
        REVENUE,               // Revenue (fees, interest income)
        EXPENSE,               // Expenses (operational costs, losses)

        // Functional categories for account grouping
        USER,                  // User-owned accounts
        BUSINESS,              // Business accounts
        SYSTEM                 // System accounts
    }

    public enum AccountStatus {
        PENDING,               // Account created but not activated
        ACTIVE,                // Fully operational account
        SUSPENDED,             // Temporarily suspended
        FROZEN,                // Frozen due to compliance/security
        DORMANT,               // Inactive for extended period
        CLOSED                 // Permanently closed
    }

    public enum ComplianceLevel {
        BASIC,                 // Basic compliance level
        STANDARD,              // Standard compliance checks
        ENHANCED,              // Enhanced due diligence
        PREMIUM,               // Premium compliance level
        RESTRICTED,            // Restricted operations
        MONITORED,             // Under compliance monitoring
        BLOCKED                // Blocked for compliance violations
    }

    public enum InterestCalculationMethod {
        DAILY_BALANCE,         // Daily interest calculation
        MONTHLY_COMPOUND,      // Monthly compound interest
        QUARTERLY_COMPOUND     // Quarterly compound interest
    }
}