package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Card Limit Entity
 * Manages spending limits for virtual cards
 */
@Entity
@Table(name = "card_limits", indexes = {
    @Index(name = "idx_card_id", columnList = "card_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CardLimit {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false, unique = true)
    private String cardId;
    
    @Column(name = "daily_limit", precision = 19, scale = 2)
    private BigDecimal dailyLimit;
    
    @Column(name = "weekly_limit", precision = 19, scale = 2)
    private BigDecimal weeklyLimit;
    
    @Column(name = "monthly_limit", precision = 19, scale = 2)
    private BigDecimal monthlyLimit;
    
    @Column(name = "yearly_limit", precision = 19, scale = 2)
    private BigDecimal yearlyLimit;
    
    @Column(name = "transaction_limit", precision = 19, scale = 2)
    private BigDecimal transactionLimit;
    
    @Column(name = "atm_withdrawal_limit", precision = 19, scale = 2)
    private BigDecimal atmWithdrawalLimit;
    
    @Column(name = "online_limit", precision = 19, scale = 2)
    private BigDecimal onlineLimit;
    
    @Column(name = "international_limit", precision = 19, scale = 2)
    private BigDecimal internationalLimit;
    
    @Column(name = "contactless_limit", precision = 19, scale = 2)
    private BigDecimal contactlessLimit;
    
    @Column(name = "daily_spent", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailySpent;
    
    @Column(name = "weekly_spent", nullable = false, precision = 19, scale = 2)
    private BigDecimal weeklySpent;
    
    @Column(name = "monthly_spent", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlySpent;
    
    @Column(name = "yearly_spent", nullable = false, precision = 19, scale = 2)
    private BigDecimal yearlySpent;
    
    @Column(name = "daily_transaction_count", nullable = false)
    private Integer dailyTransactionCount;
    
    @Column(name = "weekly_transaction_count", nullable = false)
    private Integer weeklyTransactionCount;
    
    @Column(name = "monthly_transaction_count", nullable = false)
    private Integer monthlyTransactionCount;
    
    @Column(name = "max_daily_transactions")
    private Integer maxDailyTransactions;
    
    @Column(name = "max_weekly_transactions")
    private Integer maxWeeklyTransactions;
    
    @Column(name = "max_monthly_transactions")
    private Integer maxMonthlyTransactions;
    
    @Column(name = "reset_daily_at", nullable = false)
    private LocalDateTime resetDailyAt;
    
    @Column(name = "reset_weekly_at", nullable = false)
    private LocalDateTime resetWeeklyAt;
    
    @Column(name = "reset_monthly_at", nullable = false)
    private LocalDateTime resetMonthlyAt;
    
    @Column(name = "reset_yearly_at", nullable = false)
    private LocalDateTime resetYearlyAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (dailySpent == null) {
            dailySpent = BigDecimal.ZERO;
        }
        if (weeklySpent == null) {
            weeklySpent = BigDecimal.ZERO;
        }
        if (monthlySpent == null) {
            monthlySpent = BigDecimal.ZERO;
        }
        if (yearlySpent == null) {
            yearlySpent = BigDecimal.ZERO;
        }
        if (dailyTransactionCount == null) {
            dailyTransactionCount = 0;
        }
        if (weeklyTransactionCount == null) {
            weeklyTransactionCount = 0;
        }
        if (monthlyTransactionCount == null) {
            monthlyTransactionCount = 0;
        }
        if (resetDailyAt == null) {
            resetDailyAt = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        }
        if (resetWeeklyAt == null) {
            resetWeeklyAt = LocalDateTime.now().plusWeeks(1).withHour(0).withMinute(0).withSecond(0);
        }
        if (resetMonthlyAt == null) {
            resetMonthlyAt = LocalDateTime.now().plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        }
        if (resetYearlyAt == null) {
            resetYearlyAt = LocalDateTime.now().plusYears(1).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if daily limit allows transaction
     */
    public boolean isDailyLimitAvailable(BigDecimal amount) {
        if (dailyLimit == null) return true;
        return dailySpent.add(amount).compareTo(dailyLimit) <= 0;
    }
    
    /**
     * Check if weekly limit allows transaction
     */
    public boolean isWeeklyLimitAvailable(BigDecimal amount) {
        if (weeklyLimit == null) return true;
        return weeklySpent.add(amount).compareTo(weeklyLimit) <= 0;
    }
    
    /**
     * Check if monthly limit allows transaction
     */
    public boolean isMonthlyLimitAvailable(BigDecimal amount) {
        if (monthlyLimit == null) return true;
        return monthlySpent.add(amount).compareTo(monthlyLimit) <= 0;
    }
    
    /**
     * Check if transaction limit allows amount
     */
    public boolean isTransactionLimitAvailable(BigDecimal amount) {
        if (transactionLimit == null) return true;
        return amount.compareTo(transactionLimit) <= 0;
    }
}