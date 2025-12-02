package com.waqiti.wallet.entity;

import com.waqiti.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet entity with comprehensive optimistic locking and audit support.
 * Critical for financial operations - prevents concurrent modification issues.
 */
@Entity
@Table(name = "wallets", 
    indexes = {
        @Index(name = "idx_wallet_user_id", columnList = "userId"),
        @Index(name = "idx_wallet_status", columnList = "status"),
        @Index(name = "idx_wallet_type", columnList = "walletType"),
        @Index(name = "idx_wallet_currency", columnList = "currency"),
        @Index(name = "idx_wallet_created", columnList = "createdAt")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_currency_type", 
                         columnNames = {"userId", "currency", "walletType"})
    })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pendingBalance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenBalance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletType walletType;

    @Column(precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(precision = 19, scale = 4)
    private BigDecimal monthlyLimit;

    @Column(precision = 19, scale = 4)
    private BigDecimal dailySpent;

    @Column(precision = 19, scale = 4)
    private BigDecimal monthlySpent;

    private LocalDateTime lastTransactionAt;

    private LocalDateTime limitResetDate;

    @Column(length = 500)
    private String description;

    // Optimistic locking - CRITICAL for financial operations
    @Version
    @Column(nullable = false)
    private Long version;

    // Audit fields for compliance
    @Column(length = 100)
    private String lastModifiedBy;

    private LocalDateTime lastModifiedAt;

    @Column(length = 100)
    private String freezeReason;

    private LocalDateTime frozenAt;

    private String frozenBy;

    // Additional metadata for extensibility
    @ElementCollection
    @CollectionTable(name = "wallet_metadata", 
                    joinColumns = @JoinColumn(name = "wallet_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    // Risk management fields
    @Column(precision = 5, scale = 4)
    private BigDecimal riskScore;

    private LocalDateTime riskAssessmentAt;

    @Column(length = 50)
    private String riskLevel;

    // KYC compliance
    private boolean kycRequired;
    
    private boolean kycCompleted;
    
    private LocalDateTime kycCompletedAt;

    // Business logic methods with optimistic locking awareness
    
    /**
     * Update balance with optimistic locking validation.
     * This method should be used for all balance updates.
     */
    public void updateBalance(BigDecimal newBalance, String modifiedBy) {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }
        
        this.balance = newBalance;
        this.lastModifiedBy = modifiedBy;
        this.lastModifiedAt = LocalDateTime.now();
        this.lastTransactionAt = LocalDateTime.now();
        
        // Recalculate available balance
        recalculateAvailableBalance();
    }
    
    /**
     * Add amount to balance with validation.
     */
    public void credit(BigDecimal amount, String reason, String modifiedBy) {
        validateAmount(amount);
        
        BigDecimal newBalance = this.balance.add(amount);
        updateBalance(newBalance, modifiedBy);
        
        // Log the credit operation
        addMetadata("last_credit_amount", amount.toString());
        addMetadata("last_credit_reason", reason);
        addMetadata("last_credit_at", LocalDateTime.now().toString());
    }
    
    /**
     * Subtract amount from balance with validation.
     */
    public void debit(BigDecimal amount, String reason, String modifiedBy) {
        validateAmount(amount);
        
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient available balance for debit");
        }
        
        BigDecimal newBalance = this.balance.subtract(amount);
        updateBalance(newBalance, modifiedBy);
        
        // Update spending tracking
        updateSpendingLimits(amount);
        
        // Log the debit operation
        addMetadata("last_debit_amount", amount.toString());
        addMetadata("last_debit_reason", reason);
        addMetadata("last_debit_at", LocalDateTime.now().toString());
    }
    
    /**
     * Freeze a specific amount.
     */
    public void freezeAmount(BigDecimal amount, String reason, String freezeBy) {
        validateAmount(amount);
        
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient available balance to freeze");
        }
        
        this.frozenBalance = this.frozenBalance.add(amount);
        this.freezeReason = reason;
        this.frozenAt = LocalDateTime.now();
        this.frozenBy = freezeBy;
        
        recalculateAvailableBalance();
    }
    
    /**
     * Unfreeze a specific amount.
     */
    public void unfreezeAmount(BigDecimal amount, String modifiedBy) {
        validateAmount(amount);
        
        if (this.frozenBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Cannot unfreeze more than frozen amount");
        }
        
        this.frozenBalance = this.frozenBalance.subtract(amount);
        this.lastModifiedBy = modifiedBy;
        this.lastModifiedAt = LocalDateTime.now();
        
        if (this.frozenBalance.compareTo(BigDecimal.ZERO) == 0) {
            this.freezeReason = null;
            this.frozenAt = null;
            this.frozenBy = null;
        }
        
        recalculateAvailableBalance();
    }
    
    /**
     * Check if wallet can perform a transaction.
     */
    public boolean canTransact(BigDecimal amount) {
        if (this.status != WalletStatus.ACTIVE) {
            return false;
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (this.availableBalance.compareTo(amount) < 0) {
            return false;
        }
        
        // Check daily limit
        if (this.dailyLimit != null) {
            BigDecimal potentialDailySpent = this.dailySpent.add(amount);
            if (potentialDailySpent.compareTo(this.dailyLimit) > 0) {
                return false;
            }
        }
        
        // Check monthly limit
        if (this.monthlyLimit != null) {
            BigDecimal potentialMonthlySpent = this.monthlySpent.add(amount);
            if (potentialMonthlySpent.compareTo(this.monthlyLimit) > 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if KYC is required for the transaction amount.
     */
    public boolean requiresKycForAmount(BigDecimal amount) {
        if (this.kycCompleted) {
            return false;
        }
        
        // Require KYC for amounts over $1000
        BigDecimal kycThreshold = new BigDecimal("1000.00");
        return amount.compareTo(kycThreshold) > 0;
    }
    
    // Private helper methods
    
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
    
    /**
     * P0 CRITICAL FIX: Recalculate available balance with proper error handling.
     *
     * BEFORE: Silently set negative available balance to ZERO (data corruption bug)
     * AFTER: Throw exception if wallet state becomes invalid
     *
     * Invariant: availableBalance + pendingBalance + frozenBalance + reservedBalance = balance
     *
     * If this invariant is violated, it indicates:
     * - Race condition in concurrent updates
     * - Business logic error in pending/frozen/reserved management
     * - Data corruption requiring immediate investigation
     */
    private void recalculateAvailableBalance() {
        BigDecimal calculated = this.balance
            .subtract(this.pendingBalance)
            .subtract(this.frozenBalance)
            .subtract(this.reservedBalance);

        // P0 FIX: Never silently zero out negative balances - this hides critical bugs
        if (calculated.compareTo(BigDecimal.ZERO) < 0) {
            String errorMsg = String.format(
                "WALLET_STATE_VIOLATION: Wallet %s has invalid state - " +
                "balance=%s, pending=%s, frozen=%s, reserved=%s, calculated_available=%s. " +
                "This indicates data corruption or race condition.",
                this.id, this.balance, this.pendingBalance, this.frozenBalance, this.reservedBalance, calculated
            );

            throw new WalletStateException(errorMsg);
        }

        this.availableBalance = calculated;
    }
    
    private void updateSpendingLimits(BigDecimal amount) {
        // Update daily spending
        resetLimitsIfNeeded();
        this.dailySpent = this.dailySpent.add(amount);
        this.monthlySpent = this.monthlySpent.add(amount);
    }
    
    private void resetLimitsIfNeeded() {
        LocalDateTime now = LocalDateTime.now();
        
        // Reset daily limits if new day
        if (this.limitResetDate == null || 
            this.limitResetDate.toLocalDate().isBefore(now.toLocalDate())) {
            this.dailySpent = BigDecimal.ZERO;
        }
        
        // Reset monthly limits if new month
        if (this.limitResetDate == null || 
            this.limitResetDate.getMonth() != now.getMonth() ||
            this.limitResetDate.getYear() != now.getYear()) {
            this.monthlySpent = BigDecimal.ZERO;
        }
        
        this.limitResetDate = now;
    }
    
    private void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }
    
    // Validation methods for business rules
    
    /**
     * P0 CRITICAL FIX: Validate wallet state before persisting to database.
     *
     * This ensures data integrity at the entity level, preventing corrupt data
     * from ever reaching the database.
     */
    @PreUpdate
    @PrePersist
    public void validateBeforeSave() {
        validateState();
    }

    /**
     * Validate wallet state for consistency.
     *
     * Enforces fundamental wallet invariants:
     * 1. All balances must be non-negative
     * 2. availableBalance + frozenBalance + pendingBalance + reservedBalance = balance
     */
    public void validateState() {
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
        if (this.frozenBalance == null) {
            this.frozenBalance = BigDecimal.ZERO;
        }
        if (this.pendingBalance == null) {
            this.pendingBalance = BigDecimal.ZERO;
        }
        if (this.reservedBalance == null) {
            this.reservedBalance = BigDecimal.ZERO;
        }
        if (this.availableBalance == null) {
            this.availableBalance = BigDecimal.ZERO;
        }

        if (this.balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new WalletStateException("Wallet balance cannot be negative: " + this.balance);
        }

        if (this.frozenBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new WalletStateException("Frozen balance cannot be negative: " + this.frozenBalance);
        }

        if (this.pendingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new WalletStateException("Pending balance cannot be negative: " + this.pendingBalance);
        }

        if (this.reservedBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new WalletStateException("Reserved balance cannot be negative: " + this.reservedBalance);
        }

        if (this.availableBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new WalletStateException("Available balance cannot be negative: " + this.availableBalance);
        }

        // CRITICAL: Verify balance allocation invariant
        BigDecimal totalAllocated = this.availableBalance
            .add(this.frozenBalance)
            .add(this.pendingBalance)
            .add(this.reservedBalance);

        BigDecimal difference = totalAllocated.subtract(this.balance).abs();
        BigDecimal tolerance = new BigDecimal("0.0001");

        if (difference.compareTo(tolerance) > 0) {
            String errorMsg = String.format(
                "BALANCE_ALLOCATION_MISMATCH: Wallet %s - " +
                "balance=%s, available=%s, frozen=%s, pending=%s, reserved=%s, " +
                "total_allocated=%s, difference=%s",
                this.id, this.balance, this.availableBalance,
                this.frozenBalance, this.pendingBalance, this.reservedBalance, totalAllocated, difference
            );
            throw new WalletStateException(errorMsg);
        }
    }

    /**
     * Custom exception for wallet state violations.
     */
    public static class WalletStateException extends RuntimeException {
        public WalletStateException(String message) {
            super(message);
        }

        public WalletStateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}