package com.waqiti.savings.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "savings_accounts", indexes = {
        @Index(name = "idx_savings_accounts_user", columnList = "user_id"),
        @Index(name = "idx_savings_accounts_type", columnList = "account_type"),
        @Index(name = "idx_savings_accounts_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsAccount {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents concurrent balance updates
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    /**
     * Audit fields for financial compliance
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;
    
    @Column(name = "account_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;
    
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "pending_deposits", precision = 19, scale = 4)
    private BigDecimal pendingDeposits = BigDecimal.ZERO;

    @Column(name = "pending_withdrawals", precision = 19, scale = 4)
    private BigDecimal pendingWithdrawals = BigDecimal.ZERO;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    
    // Interest settings
    @Column(name = "interest_rate", precision = 5, scale = 4)
    private BigDecimal interestRate;
    
    @Column(name = "interest_calculation_type", length = 20)
    @Enumerated(EnumType.STRING)
    private InterestCalculationType interestCalculationType;
    
    @Column(name = "total_interest_earned", precision = 19, scale = 4)
    private BigDecimal totalInterestEarned = BigDecimal.ZERO;
    
    @Column(name = "last_interest_calculated_at")
    private LocalDateTime lastInterestCalculatedAt;
    
    @Column(name = "next_interest_calculation_at")
    private LocalDateTime nextInterestCalculationAt;
    
    // Account limits
    @Column(name = "minimum_balance", precision = 19, scale = 4)
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    @Column(name = "maximum_balance", precision = 19, scale = 4)
    private BigDecimal maximumBalance;

    @Column(name = "daily_deposit_limit", precision = 19, scale = 4)
    private BigDecimal dailyDepositLimit;

    @Column(name = "daily_withdrawal_limit", precision = 19, scale = 4)
    private BigDecimal dailyWithdrawalLimit;
    
    @Column(name = "monthly_withdrawal_count_limit")
    private Integer monthlyWithdrawalCountLimit;
    
    // Transaction tracking
    @Column(name = "total_deposits", precision = 19, scale = 4)
    private BigDecimal totalDeposits = BigDecimal.ZERO;

    @Column(name = "total_withdrawals", precision = 19, scale = 4)
    private BigDecimal totalWithdrawals = BigDecimal.ZERO;
    
    @Column(name = "deposit_count")
    private Integer depositCount = 0;
    
    @Column(name = "withdrawal_count")
    private Integer withdrawalCount = 0;
    
    @Column(name = "current_month_withdrawals")
    private Integer currentMonthWithdrawals = 0;
    
    @Column(name = "last_deposit_at")
    private LocalDateTime lastDepositAt;
    
    @Column(name = "last_withdrawal_at")
    private LocalDateTime lastWithdrawalAt;
    
    // Features
    @Column(name = "overdraft_enabled")
    private Boolean overdraftEnabled = false;
    
    @Column(name = "overdraft_limit", precision = 19, scale = 4)
    private BigDecimal overdraftLimit;

    @Column(name = "auto_sweep_enabled")
    private Boolean autoSweepEnabled = false;

    @Column(name = "auto_sweep_threshold", precision = 19, scale = 4)
    private BigDecimal autoSweepThreshold;
    
    @Column(name = "auto_sweep_target_account_id")
    private UUID autoSweepTargetAccountId;
    
    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        
        if (accountNumber == null) {
            accountNumber = generateAccountNumber();
        }
        
        if (balance == null) balance = BigDecimal.ZERO;
        if (availableBalance == null) availableBalance = BigDecimal.ZERO;
        if (totalDeposits == null) totalDeposits = BigDecimal.ZERO;
        if (totalWithdrawals == null) totalWithdrawals = BigDecimal.ZERO;
        if (totalInterestEarned == null) totalInterestEarned = BigDecimal.ZERO;
        if (pendingDeposits == null) pendingDeposits = BigDecimal.ZERO;
        if (pendingWithdrawals == null) pendingWithdrawals = BigDecimal.ZERO;
    }
    
    @PreUpdate
    protected void onUpdate() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
    
    public boolean canDeposit(BigDecimal amount) {
        if (!isActive()) return false;
        
        if (maximumBalance != null) {
            BigDecimal projectedBalance = balance.add(amount);
            if (projectedBalance.compareTo(maximumBalance) > 0) return false;
        }
        
        return true;
    }
    
    public boolean canWithdraw(BigDecimal amount) {
        if (!isActive()) return false;
        
        BigDecimal effectiveBalance = overdraftEnabled && overdraftLimit != null
                ? availableBalance.add(overdraftLimit)
                : availableBalance;
        
        if (amount.compareTo(effectiveBalance) > 0) return false;
        
        if (monthlyWithdrawalCountLimit != null && 
            currentMonthWithdrawals >= monthlyWithdrawalCountLimit) {
            return false;
        }
        
        return true;
    }
    
    public BigDecimal getEffectiveBalance() {
        return overdraftEnabled && overdraftLimit != null
                ? balance.add(overdraftLimit)
                : balance;
    }
    
    public boolean hasMinimumBalance() {
        return balance.compareTo(minimumBalance) >= 0;
    }
    
    public boolean shouldAutoSweep() {
        return autoSweepEnabled && 
               autoSweepThreshold != null &&
               balance.compareTo(autoSweepThreshold) > 0;
    }
    
    public BigDecimal getAutoSweepAmount() {
        if (!shouldAutoSweep()) return BigDecimal.ZERO;
        return balance.subtract(autoSweepThreshold);
    }
    
    private String generateAccountNumber() {
        return "SAV" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
    
    // Enums
    public enum AccountType {
        SAVINGS,
        HIGH_YIELD_SAVINGS,
        GOAL_BASED_SAVINGS,
        EMERGENCY_FUND,
        INVESTMENT_SAVINGS
    }
    
    public enum Status {
        ACTIVE,
        SUSPENDED,
        FROZEN,
        CLOSED,
        PENDING_CLOSURE
    }
    
    public enum InterestCalculationType {
        NONE,
        SIMPLE,
        COMPOUND_DAILY,
        COMPOUND_MONTHLY,
        COMPOUND_QUARTERLY,
        COMPOUND_ANNUALLY
    }
}