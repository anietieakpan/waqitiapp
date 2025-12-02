package com.waqiti.familyaccount.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.HashSet;

/**
 * Family Member Entity
 * 
 * Represents individual members within a family account with
 * specific permissions, limits, and controls.
 */
@Entity
@Table(name = "family_members", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"family_account_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_account_id", nullable = false)
    private FamilyAccount familyAccount;

    @Column(name = "user_id", nullable = false)
    @NotBlank(message = "User ID is required")
    @Size(max = 50)
    private String userId;

    @Column(name = "nickname")
    @Size(max = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    private MemberRole memberRole;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "individual_wallet_id")
    @Size(max = 50)
    private String individualWalletId;

    @Column(name = "current_balance", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "daily_spend_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Daily limit cannot be negative")
    private BigDecimal dailySpendingLimit;

    @Column(name = "weekly_spend_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Weekly limit cannot be negative")
    private BigDecimal weeklySpendingLimit;

    @Column(name = "monthly_spend_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Monthly limit cannot be negative")
    private BigDecimal monthlySpendingLimit;

    @Column(name = "current_daily_spent", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal currentDailySpent = BigDecimal.ZERO;

    @Column(name = "current_weekly_spent", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal currentWeeklySpent = BigDecimal.ZERO;

    @Column(name = "current_monthly_spent", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal currentMonthlySpent = BigDecimal.ZERO;

    @Column(name = "allowance_amount", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal allowanceAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "allowance_frequency")
    private AllowanceFrequency allowanceFrequency;

    @Column(name = "last_allowance_date")
    private LocalDate lastAllowanceDate;

    @Column(name = "chore_earnings", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal choreEarnings = BigDecimal.ZERO;

    @Column(name = "savings_goal_amount", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal savingsGoalAmount;

    @Column(name = "current_savings", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal currentSavings = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false)
    private MemberStatus memberStatus = MemberStatus.ACTIVE;

    // Parental Controls
    @Column(name = "transaction_approval_required", nullable = false)
    private Boolean transactionApprovalRequired = false;

    @Column(name = "spending_time_restrictions_enabled", nullable = false)
    private Boolean spendingTimeRestrictionsEnabled = false;

    @Column(name = "spending_allowed_start_time")
    private LocalTime spendingAllowedStartTime;

    @Column(name = "spending_allowed_end_time")
    private LocalTime spendingAllowedEndTime;

    @Column(name = "weekends_spending_allowed", nullable = false)
    private Boolean weekendsSpendingAllowed = true;

    @Column(name = "online_purchases_allowed", nullable = false)
    private Boolean onlinePurchasesAllowed = true;

    @Column(name = "atm_withdrawals_allowed", nullable = false)
    private Boolean atmWithdrawalsAllowed = false;

    @Column(name = "international_transactions_allowed", nullable = false)
    private Boolean internationalTransactionsAllowed = false;

    @Column(name = "peer_payments_allowed", nullable = false)
    private Boolean peerPaymentsAllowed = true;

    @Column(name = "investment_allowed", nullable = false)
    private Boolean investmentAllowed = false;

    @Column(name = "crypto_transactions_allowed", nullable = false)
    private Boolean cryptoTransactionsAllowed = false;

    // Educational Features
    @Column(name = "financial_literacy_score")
    @Min(value = 0)
    @Max(value = 100)
    private Integer financialLiteracyScore = 0;

    @Column(name = "completed_courses")
    private Integer completedCourses = 0;

    @Column(name = "badges_earned")
    private Integer badgesEarned = 0;

    @Column(name = "last_quiz_date")
    private LocalDate lastQuizDate;

    @Column(name = "can_view_family_account", nullable = false)
    private Boolean canViewFamilyAccount = false;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @OneToMany(mappedBy = "familyMember", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<MemberTransaction> transactions = new HashSet<>();

    @OneToMany(mappedBy = "familyMember", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ChoreTask> choreTasks = new HashSet<>();

    @OneToMany(mappedBy = "familyMember", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<SavingsGoal> savingsGoals = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "added_by", nullable = false)
    @Size(max = 50)
    private String addedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MemberRole {
        PRIMARY_PARENT,
        SECONDARY_PARENT,
        TEEN,
        CHILD,
        YOUNG_ADULT
    }

    public enum MemberStatus {
        ACTIVE,
        SUSPENDED,
        INACTIVE,
        PENDING_APPROVAL
    }

    public enum AllowanceFrequency {
        DAILY,
        WEEKLY,
        MONTHLY
    }

    // Business methods
    public boolean isParent() {
        return memberRole == MemberRole.PRIMARY_PARENT || memberRole == MemberRole.SECONDARY_PARENT;
    }

    public boolean isChild() {
        return memberRole == MemberRole.CHILD || memberRole == MemberRole.TEEN;
    }

    public boolean canSpendAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Check daily limit
        if (dailySpendingLimit != null &&
            currentDailySpent.add(amount).compareTo(dailySpendingLimit) > 0) {
            return false;
        }

        // Check weekly limit
        if (weeklySpendingLimit != null &&
            currentWeeklySpent.add(amount).compareTo(weeklySpendingLimit) > 0) {
            return false;
        }

        // Check monthly limit
        if (monthlySpendingLimit != null &&
            currentMonthlySpent.add(amount).compareTo(monthlySpendingLimit) > 0) {
            return false;
        }

        return currentBalance.compareTo(amount) >= 0;
    }

    public boolean isSpendingTimeAllowed() {
        if (!spendingTimeRestrictionsEnabled) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        
        // Check weekend restriction
        if (!weekendsSpendingAllowed && 
            (now.getDayOfWeek().getValue() == 6 || now.getDayOfWeek().getValue() == 7)) {
            return false;
        }

        // Check time window
        if (spendingAllowedStartTime != null && spendingAllowedEndTime != null) {
            return !currentTime.isBefore(spendingAllowedStartTime) && 
                   !currentTime.isAfter(spendingAllowedEndTime);
        }

        return true;
    }

    public void addToSpent(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.currentDailySpent = this.currentDailySpent.add(amount);
            this.currentWeeklySpent = this.currentWeeklySpent.add(amount);
            this.currentMonthlySpent = this.currentMonthlySpent.add(amount);
        }
    }

    public void resetDailySpent() {
        this.currentDailySpent = BigDecimal.ZERO;
    }

    public void resetWeeklySpent() {
        this.currentWeeklySpent = BigDecimal.ZERO;
    }

    public void resetMonthlySpent() {
        this.currentMonthlySpent = BigDecimal.ZERO;
    }

    public void addAllowance() {
        if (allowanceAmount != null && allowanceAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.currentBalance = this.currentBalance.add(allowanceAmount);
            this.lastAllowanceDate = LocalDate.now();
        }
    }

    public BigDecimal calculateAge() {
        if (dateOfBirth == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(LocalDate.now().getYear() - dateOfBirth.getYear());
    }

    // Alias method for compatibility
    public String getWalletId() {
        return this.individualWalletId;
    }

    public void setWalletId(String walletId) {
        this.individualWalletId = walletId;
    }

    public boolean hasReachedDailyLimit() {
        return dailySpendingLimit != null &&
               currentDailySpent.compareTo(dailySpendingLimit) >= 0;
    }

    public boolean hasReachedWeeklyLimit() {
        return weeklySpendingLimit != null &&
               currentWeeklySpent.compareTo(weeklySpendingLimit) >= 0;
    }

    public boolean hasReachedMonthlyLimit() {
        return monthlySpendingLimit != null &&
               currentMonthlySpent.compareTo(monthlySpendingLimit) >= 0;
    }

    public BigDecimal getRemainingDailyLimit() {
        if (dailySpendingLimit == null) {
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }
        return dailySpendingLimit.subtract(currentDailySpent).max(BigDecimal.ZERO);
    }

    public BigDecimal getRemainingWeeklyLimit() {
        if (weeklySpendingLimit == null) {
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }
        return weeklySpendingLimit.subtract(currentWeeklySpent).max(BigDecimal.ZERO);
    }

    public BigDecimal getRemainingMonthlyLimit() {
        if (monthlySpendingLimit == null) {
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }
        return monthlySpendingLimit.subtract(currentMonthlySpent).max(BigDecimal.ZERO);
    }
}