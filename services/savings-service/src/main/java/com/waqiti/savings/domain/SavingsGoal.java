package com.waqiti.savings.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "savings_goals", indexes = {
        @Index(name = "idx_savings_goals_user", columnList = "user_id"),
        @Index(name = "idx_savings_goals_status", columnList = "status"),
        @Index(name = "idx_savings_goals_category", columnList = "category"),
        @Index(name = "idx_savings_goals_priority", columnList = "priority"),
        @Index(name = "idx_savings_goals_target_date", columnList = "target_date"),
        @Index(name = "idx_savings_goals_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoal {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    // Goal details
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "category", length = 50)
    @Enumerated(EnumType.STRING)
    private Category category;
    
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentAmount = BigDecimal.ZERO;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "target_date")
    private LocalDateTime targetDate;
    
    @Column(name = "priority", nullable = false)
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;
    
    @Column(name = "visibility", nullable = false)
    @Enumerated(EnumType.STRING)
    private Visibility visibility = Visibility.PRIVATE;
    
    // Visual customization
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    
    @Column(name = "icon", length = 50)
    private String icon;
    
    @Column(name = "color", length = 7)
    private String color;
    
    // Progress tracking
    @Column(name = "progress_percentage", precision = 5, scale = 2)
    private BigDecimal progressPercentage = BigDecimal.ZERO;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;
    
    @Column(name = "abandoned_at")
    private LocalDateTime abandonedAt;
    
    // Savings behavior
    @Column(name = "auto_save_enabled")
    private Boolean autoSaveEnabled = false;
    
    @Column(name = "flexible_target")
    private Boolean flexibleTarget = false;
    
    @Column(name = "allow_withdrawals")
    private Boolean allowWithdrawals = true;
    
    @Column(name = "minimum_contribution", precision = 19, scale = 4)
    private BigDecimal minimumContribution;

    @Column(name = "maximum_contribution", precision = 19, scale = 4)
    private BigDecimal maximumContribution;
    
    // Interest and returns
    @Column(name = "interest_rate", precision = 5, scale = 4)
    private BigDecimal interestRate;
    
    @Column(name = "interest_earned", precision = 19, scale = 4)
    private BigDecimal interestEarned = BigDecimal.ZERO;
    
    @Column(name = "interest_calculation_type", length = 20)
    @Enumerated(EnumType.STRING)
    private InterestCalculationType interestCalculationType;
    
    // Analytics
    @Column(name = "total_contributions")
    private Integer totalContributions = 0;
    
    @Column(name = "total_withdrawals")
    private Integer totalWithdrawals = 0;
    
    @Column(name = "average_monthly_contribution", precision = 19, scale = 4)
    private BigDecimal averageMonthlyContribution;

    @Column(name = "required_monthly_saving", precision = 19, scale = 4)
    private BigDecimal requiredMonthlySaving;
    
    @Column(name = "projected_completion_date")
    private LocalDateTime projectedCompletionDate;
    
    @Column(name = "current_streak")
    private Integer currentStreak = 0;
    
    @Column(name = "longest_streak")
    private Integer longestStreak = 0;
    
    @Column(name = "last_contribution_at")
    private LocalDateTime lastContributionAt;
    
    @Column(name = "last_withdrawal_at")
    private LocalDateTime lastWithdrawalAt;
    
    // Notifications
    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = true;
    
    @Column(name = "reminder_frequency", length = 20)
    @Enumerated(EnumType.STRING)
    private ReminderFrequency reminderFrequency;
    
    @Column(name = "next_reminder_at")
    private LocalDateTime nextReminderAt;
    
    // Sharing and collaboration
    @Column(name = "is_shared")
    private Boolean isShared = false;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shared_with", columnDefinition = "jsonb")
    private Map<String, Object> sharedWith;
    
    @Column(name = "share_code", length = 20)
    private String shareCode;
    
    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @org.springframework.data.annotation.CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @org.springframework.data.annotation.LastModifiedBy
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (currentAmount == null) currentAmount = BigDecimal.ZERO;
        if (progressPercentage == null) progressPercentage = BigDecimal.ZERO;
        if (totalContributions == null) totalContributions = 0;
        if (totalWithdrawals == null) totalWithdrawals = 0;
        if (interestEarned == null) interestEarned = BigDecimal.ZERO;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
    
    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }
    
    public boolean isPaused() {
        return status == Status.PAUSED;
    }
    
    public boolean canContribute() {
        return status == Status.ACTIVE || (status == Status.COMPLETED && flexibleTarget);
    }
    
    public boolean canWithdraw() {
        return allowWithdrawals && currentAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isOnTrack() {
        if (targetDate == null || requiredMonthlySaving == null) return true;
        
        return averageMonthlyContribution != null && 
               averageMonthlyContribution.compareTo(requiredMonthlySaving) >= 0;
    }
    
    public BigDecimal getRemainingAmount() {
        return targetAmount.subtract(currentAmount).max(BigDecimal.ZERO);
    }
    
    public boolean hasReachedTarget() {
        return currentAmount.compareTo(targetAmount) >= 0;
    }
    
    public boolean isOverdue() {
        return targetDate != null && 
               LocalDateTime.now().isAfter(targetDate) && 
               !isCompleted();
    }
    
    public boolean isHighPriority() {
        return priority == Priority.HIGH;
    }
    
    public boolean needsReminder() {
        return notificationsEnabled && 
               reminderFrequency != null &&
               (nextReminderAt == null || LocalDateTime.now().isAfter(nextReminderAt));
    }
    
    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
    
    public boolean isSharedWith(UUID userId) {
        if (!isShared || sharedWith == null) return false;
        return sharedWith.containsKey(userId.toString());
    }
    
    public BigDecimal getCompletionPercentage() {
        if (targetAmount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        
        return currentAmount.divide(targetAmount, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .min(BigDecimal.valueOf(100));
    }
    
    public Integer getDaysRemaining() {
        if (targetDate == null) return null;
        
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), targetDate);
        return days > 0 ? (int) days : 0;
    }
    
    public Integer getMonthsRemaining() {
        if (targetDate == null) return null;
        
        long months = java.time.temporal.ChronoUnit.MONTHS.between(LocalDateTime.now(), targetDate);
        return months > 0 ? (int) months : 0;
    }
    
    // Enums
    public enum Status {
        ACTIVE,
        COMPLETED,
        PAUSED,
        ABANDONED,
        ARCHIVED
    }
    
    public enum Category {
        EMERGENCY_FUND,
        VACATION,
        HOME,
        CAR,
        EDUCATION,
        RETIREMENT,
        WEDDING,
        BUSINESS,
        GADGET,
        HEALTH,
        INVESTMENT,
        DEBT_PAYMENT,
        GIFT,
        OTHER
    }
    
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum Visibility {
        PRIVATE,
        FRIENDS,
        PUBLIC
    }
    
    public enum InterestCalculationType {
        NONE,
        SIMPLE,
        COMPOUND_DAILY,
        COMPOUND_MONTHLY,
        COMPOUND_ANNUALLY
    }
    
    public enum ReminderFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        CUSTOM
    }
}