package com.waqiti.expense.domain;

import com.waqiti.expense.domain.enums.BudgetPeriod;
import com.waqiti.expense.domain.enums.BudgetStatus;
import com.waqiti.expense.domain.enums.BudgetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Budget Entity
 * Represents budget planning and tracking with flexible configurations
 */
@Entity
@Table(name = "budgets", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_period_start", columnList = "period_start"),
    @Index(name = "idx_period_end", columnList = "period_end"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_budget_type", columnList = "budget_type"),
    @Index(name = "idx_user_period", columnList = "user_id, period_start, period_end")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class Budget {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_type", nullable = false)
    private BudgetType budgetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_period", nullable = false)
    private BudgetPeriod budgetPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BudgetStatus status;

    // Budget Amounts
    @Column(name = "planned_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedAmount;

    @Column(name = "spent_amount", precision = 19, scale = 2)
    private BigDecimal spentAmount;

    @Column(name = "remaining_amount", precision = 19, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // Period Information
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    @Column(name = "auto_renew")
    private Boolean autoRenew = false;

    // Category and Scope
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "budget_categories",
        joinColumns = @JoinColumn(name = "budget_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<ExpenseCategory> categories = new ArrayList<>();

    @OneToMany(mappedBy = "budget", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<BudgetAlert> alerts = new ArrayList<>();

    // Alert Thresholds
    @Column(name = "warning_threshold", precision = 5, scale = 2)
    private BigDecimal warningThreshold; // Percentage (e.g., 80.00 for 80%)

    @Column(name = "critical_threshold", precision = 5, scale = 2)
    private BigDecimal criticalThreshold; // Percentage (e.g., 95.00 for 95%)

    @Column(name = "overspend_allowed")
    private Boolean overspendAllowed = false;

    @Column(name = "max_overspend_amount", precision = 19, scale = 2)
    private BigDecimal maxOverspendAmount;

    // Analytics and Tracking
    @Column(name = "average_daily_spend", precision = 19, scale = 2)
    private BigDecimal averageDailySpend;

    @Column(name = "projected_spend", precision = 19, scale = 2)
    private BigDecimal projectedSpend;

    @Column(name = "variance_amount", precision = 19, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "variance_percentage", precision = 5, scale = 2)
    private BigDecimal variancePercentage;

    @Column(name = "days_remaining")
    private Integer daysRemaining;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    // Notifications and Settings
    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = true;

    @Column(name = "email_alerts")
    private Boolean emailAlerts = true;

    @Column(name = "push_notifications")
    private Boolean pushNotifications = true;

    @Column(name = "weekly_digest")
    private Boolean weeklyDigest = true;

    // Goals and Targets
    @Column(name = "savings_goal", precision = 19, scale = 2)
    private BigDecimal savingsGoal;

    @Column(name = "savings_achieved", precision = 19, scale = 2)
    private BigDecimal savingsAchieved;

    @Column(name = "improvement_target", precision = 5, scale = 2)
    private BigDecimal improvementTarget; // Percentage reduction target

    // Metadata
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "budget_metadata", joinColumns = @JoinColumn(name = "budget_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    @Version
    private Long version;

    // Business Methods

    /**
     * Add expense to budget and recalculate
     */
    public void addExpense(BigDecimal expenseAmount) {
        if (spentAmount == null) {
            spentAmount = BigDecimal.ZERO;
        }
        spentAmount = spentAmount.add(expenseAmount);
        recalculateAmounts();
    }

    /**
     * Remove expense from budget and recalculate
     */
    public void removeExpense(BigDecimal expenseAmount) {
        if (spentAmount == null) {
            spentAmount = BigDecimal.ZERO;
        }
        spentAmount = spentAmount.subtract(expenseAmount);
        if (spentAmount.compareTo(BigDecimal.ZERO) < 0) {
            spentAmount = BigDecimal.ZERO;
        }
        recalculateAmounts();
    }

    /**
     * Recalculate all derived amounts
     */
    public void recalculateAmounts() {
        if (spentAmount == null) spentAmount = BigDecimal.ZERO;
        
        // Calculate remaining amount
        remainingAmount = plannedAmount.subtract(spentAmount);
        
        // Calculate variance
        varianceAmount = spentAmount.subtract(plannedAmount);
        if (plannedAmount.compareTo(BigDecimal.ZERO) > 0) {
            variancePercentage = varianceAmount.divide(plannedAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        
        // Calculate days remaining
        LocalDate today = LocalDate.now();
        if (today.isBefore(periodEnd)) {
            daysRemaining = (int) java.time.temporal.ChronoUnit.DAYS.between(today, periodEnd);
        } else {
            daysRemaining = 0;
        }
        
        // Calculate average daily spend
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(periodStart, today) + 1;
        if (totalDays > 0) {
            averageDailySpend = spentAmount.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        }
        
        // Calculate projected spend
        if (averageDailySpend != null && daysRemaining > 0) {
            BigDecimal projectedAdditional = averageDailySpend.multiply(BigDecimal.valueOf(daysRemaining));
            projectedSpend = spentAmount.add(projectedAdditional);
        } else {
            projectedSpend = spentAmount;
        }
        
        // Calculate performance score (0-100)
        performanceScore = calculatePerformanceScore();
        
        lastCalculatedAt = LocalDateTime.now();
    }

    /**
     * Check if budget is over limit
     */
    public boolean isOverBudget() {
        return spentAmount != null && spentAmount.compareTo(plannedAmount) > 0;
    }

    /**
     * Check if budget is over specific amount
     */
    public boolean isOverBudget(BigDecimal additionalAmount) {
        BigDecimal totalSpend = (spentAmount != null ? spentAmount : BigDecimal.ZERO).add(additionalAmount);
        return totalSpend.compareTo(plannedAmount) > 0;
    }

    /**
     * Get spending percentage
     */
    public BigDecimal getSpendingPercentage() {
        if (plannedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return spentAmount.divide(plannedAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if warning threshold is reached
     */
    public boolean isWarningThresholdReached() {
        if (warningThreshold == null) return false;
        return getSpendingPercentage().compareTo(warningThreshold) >= 0;
    }

    /**
     * Check if critical threshold is reached
     */
    public boolean isCriticalThresholdReached() {
        if (criticalThreshold == null) return false;
        return getSpendingPercentage().compareTo(criticalThreshold) >= 0;
    }

    /**
     * Check if budget is active for the current period
     */
    public boolean isActiveForPeriod() {
        LocalDate today = LocalDate.now();
        return status == BudgetStatus.ACTIVE &&
               !today.isBefore(periodStart) &&
               !today.isAfter(periodEnd);
    }

    /**
     * Check if budget period has ended
     */
    public boolean isPeriodEnded() {
        return LocalDate.now().isAfter(periodEnd);
    }

    /**
     * Get budget health status
     */
    public BudgetHealth getBudgetHealth() {
        if (isCriticalThresholdReached() || isOverBudget()) {
            return BudgetHealth.CRITICAL;
        } else if (isWarningThresholdReached()) {
            return BudgetHealth.WARNING;
        } else if (getSpendingPercentage().compareTo(BigDecimal.valueOf(50)) <= 0) {
            return BudgetHealth.EXCELLENT;
        } else {
            return BudgetHealth.GOOD;
        }
    }

    /**
     * Get recommended daily spend to stay on budget
     */
    public BigDecimal getRecommendedDailySpend() {
        if (daysRemaining == null || daysRemaining <= 0) {
            return BigDecimal.ZERO;
        }
        return remainingAmount.divide(BigDecimal.valueOf(daysRemaining), 2, RoundingMode.HALF_UP);
    }

    /**
     * Add category to budget
     */
    public void addCategory(ExpenseCategory category) {
        if (categories == null) {
            categories = new ArrayList<>();
        }
        if (!categories.contains(category)) {
            categories.add(category);
        }
    }

    /**
     * Remove category from budget
     */
    public void removeCategory(ExpenseCategory category) {
        if (categories != null) {
            categories.remove(category);
        }
    }

    /**
     * Check if budget covers specific category
     */
    public boolean coversCategory(ExpenseCategory category) {
        if (budgetType == BudgetType.OVERALL) {
            return true;
        }
        return categories != null && categories.contains(category);
    }

    /**
     * Calculate performance score based on multiple factors
     */
    private BigDecimal calculatePerformanceScore() {
        double score = 100.0;
        
        // Deduct points for overspending
        if (isOverBudget()) {
            double overspendPercentage = variancePercentage.doubleValue();
            score -= Math.min(50, overspendPercentage); // Max 50 point deduction
        }
        
        // Deduct points for being over warning threshold
        if (isWarningThresholdReached()) {
            score -= 10;
        }
        
        // Deduct points for being over critical threshold
        if (isCriticalThresholdReached()) {
            score -= 20;
        }
        
        // Bonus points for staying well under budget
        double spendingPercentage = getSpendingPercentage().doubleValue();
        if (spendingPercentage < 70) {
            score += 10;
        }
        if (spendingPercentage < 50) {
            score += 10;
        }
        
        return BigDecimal.valueOf(Math.max(0, Math.min(100, score)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Create next period budget if recurring
     */
    public Budget createNextPeriodBudget() {
        if (!isRecurring || !autoRenew) {
            return null;
        }
        
        LocalDate nextStart = calculateNextPeriodStart();
        LocalDate nextEnd = calculateNextPeriodEnd(nextStart);
        
        return Budget.builder()
                .userId(this.userId)
                .name(this.name)
                .description(this.description)
                .budgetType(this.budgetType)
                .budgetPeriod(this.budgetPeriod)
                .status(BudgetStatus.ACTIVE)
                .plannedAmount(this.plannedAmount)
                .spentAmount(BigDecimal.ZERO)
                .remainingAmount(this.plannedAmount)
                .currency(this.currency)
                .periodStart(nextStart)
                .periodEnd(nextEnd)
                .isRecurring(this.isRecurring)
                .autoRenew(this.autoRenew)
                .categories(new ArrayList<>(this.categories))
                .warningThreshold(this.warningThreshold)
                .criticalThreshold(this.criticalThreshold)
                .overspendAllowed(this.overspendAllowed)
                .maxOverspendAmount(this.maxOverspendAmount)
                .notificationsEnabled(this.notificationsEnabled)
                .emailAlerts(this.emailAlerts)
                .pushNotifications(this.pushNotifications)
                .weeklyDigest(this.weeklyDigest)
                .savingsGoal(this.savingsGoal)
                .improvementTarget(this.improvementTarget)
                .build();
    }

    private LocalDate calculateNextPeriodStart() {
        return switch (budgetPeriod) {
            case WEEKLY -> periodStart.plusWeeks(1);
            case MONTHLY -> periodStart.plusMonths(1);
            case QUARTERLY -> periodStart.plusMonths(3);
            case YEARLY -> periodStart.plusYears(1);
            default -> periodEnd.plusDays(1);
        };
    }

    private LocalDate calculateNextPeriodEnd(LocalDate start) {
        return switch (budgetPeriod) {
            case WEEKLY -> start.plusWeeks(1).minusDays(1);
            case MONTHLY -> start.plusMonths(1).minusDays(1);
            case QUARTERLY -> start.plusMonths(3).minusDays(1);
            case YEARLY -> start.plusYears(1).minusDays(1);
            default -> start.plusMonths(1).minusDays(1);
        };
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = BudgetStatus.ACTIVE;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (spentAmount == null) {
            spentAmount = BigDecimal.ZERO;
        }
        if (remainingAmount == null) {
            remainingAmount = plannedAmount;
        }
        if (warningThreshold == null) {
            warningThreshold = BigDecimal.valueOf(80);
        }
        if (criticalThreshold == null) {
            criticalThreshold = BigDecimal.valueOf(95);
        }
        recalculateAmounts();
    }

    @PreUpdate
    protected void onUpdate() {
        recalculateAmounts();
    }

    /**
     * Budget health enumeration
     */
    public enum BudgetHealth {
        EXCELLENT,
        GOOD,
        WARNING,
        CRITICAL
    }
}