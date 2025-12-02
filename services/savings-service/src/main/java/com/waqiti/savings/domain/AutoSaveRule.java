package com.waqiti.savings.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "auto_save_rules", indexes = {
        @Index(name = "idx_auto_save_rules_goal", columnList = "goal_id"),
        @Index(name = "idx_auto_save_rules_user", columnList = "user_id"),
        @Index(name = "idx_auto_save_rules_type", columnList = "rule_type"),
        @Index(name = "idx_auto_save_rules_active", columnList = "is_active"),
        @Index(name = "idx_auto_save_rules_next_execution", columnList = "next_execution_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoSaveRule {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "goal_id", nullable = false)
    private UUID goalId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "rule_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;
    
    // Amount settings
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "round_up_to", precision = 19, scale = 4)
    private BigDecimal roundUpTo; // For round-up rules

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;
    
    // Frequency settings
    @Column(name = "frequency", length = 20)
    @Enumerated(EnumType.STRING)
    private Frequency frequency;
    
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
    
    @Column(name = "day_of_month")
    private Integer dayOfMonth;
    
    @Column(name = "hour_of_day")
    private Integer hourOfDay;
    
    // Trigger settings
    @Column(name = "trigger_type", length = 30)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_conditions", columnDefinition = "jsonb")
    private Map<String, Object> triggerConditions;
    
    // Payment settings
    @Column(name = "payment_method", length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod = PaymentMethod.BANK_ACCOUNT;
    
    @Column(name = "funding_source_id")
    private UUID fundingSourceId;
    
    // Status and control
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_paused")
    private Boolean isPaused = false;
    
    @Column(name = "priority")
    private Integer priority = 5; // 1-10, higher = more important
    
    @Column(name = "start_date")
    private LocalDateTime startDate;
    
    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    // Execution tracking
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;
    
    @Column(name = "next_execution_at")
    private LocalDateTime nextExecutionAt;
    
    @Column(name = "execution_count")
    private Integer executionCount = 0;
    
    @Column(name = "successful_executions")
    private Integer successfulExecutions = 0;
    
    @Column(name = "failed_executions")
    private Integer failedExecutions = 0;
    
    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures = 0;
    
    // Financial tracking
    @Column(name = "total_saved", precision = 19, scale = 4)
    private BigDecimal totalSaved = BigDecimal.ZERO;

    @Column(name = "average_save_amount", precision = 19, scale = 4)
    private BigDecimal averageSaveAmount;

    @Column(name = "last_save_amount", precision = 19, scale = 4)
    private BigDecimal lastSaveAmount;
    
    // Error handling
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    @Column(name = "last_error_at")
    private LocalDateTime lastErrorAt;
    
    @Column(name = "error_count")
    private Integer errorCount = 0;
    
    // Notifications
    @Column(name = "notify_on_execution")
    private Boolean notifyOnExecution = true;
    
    @Column(name = "notify_on_failure")
    private Boolean notifyOnFailure = true;
    
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
        
        if (totalSaved == null) totalSaved = BigDecimal.ZERO;
        if (executionCount == null) executionCount = 0;
        if (successfulExecutions == null) successfulExecutions = 0;
        if (failedExecutions == null) failedExecutions = 0;
        if (consecutiveFailures == null) consecutiveFailures = 0;
        if (errorCount == null) errorCount = 0;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean canExecute() {
        if (!isActive || isPaused) return false;
        if (endDate != null && LocalDateTime.now().isAfter(endDate)) return false;
        if (nextExecutionAt != null && LocalDateTime.now().isBefore(nextExecutionAt)) return false;
        if (consecutiveFailures != null && consecutiveFailures >= 5) return false;
        
        return true;
    }
    
    public boolean shouldNotify() {
        return notifyOnExecution && isActive;
    }
    
    public boolean isExpired() {
        return endDate != null && LocalDateTime.now().isAfter(endDate);
    }
    
    public boolean hasErrors() {
        return errorCount > 0 || consecutiveFailures > 0;
    }
    
    public BigDecimal getEffectiveAmount() {
        if (ruleType == RuleType.FIXED_AMOUNT) {
            return amount;
        }
        return null; // Calculated dynamically for other types
    }
    
    public void recordSuccess(BigDecimal savedAmount) {
        executionCount++;
        successfulExecutions++;
        consecutiveFailures = 0;
        lastExecutedAt = LocalDateTime.now();
        lastSaveAmount = savedAmount;
        totalSaved = totalSaved.add(savedAmount);
        
        // Update average
        if (successfulExecutions > 0) {
            averageSaveAmount = totalSaved.divide(
                BigDecimal.valueOf(successfulExecutions), 
                2, 
                java.math.RoundingMode.HALF_UP
            );
        }
    }
    
    public void recordFailure(String error) {
        executionCount++;
        failedExecutions++;
        consecutiveFailures++;
        errorCount++;
        lastError = error;
        lastErrorAt = LocalDateTime.now();
        lastExecutedAt = LocalDateTime.now();
    }
    
    // Enums
    public enum RuleType {
        FIXED_AMOUNT,           // Save fixed amount
        PERCENTAGE_OF_INCOME,   // Save % of income
        ROUND_UP,              // Round up transactions
        SPARE_CHANGE,          // Save spare change
        GOAL_BASED,            // Based on goal progress
        CONDITIONAL,           // Based on conditions
        MATCH_SPENDING,        // Match spending in category
        PENALTY_BASED          // Save when breaking rules
    }
    
    public enum Frequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        ANNUALLY,
        ON_TRANSACTION,
        ON_INCOME,
        CUSTOM
    }
    
    public enum TriggerType {
        TIME_BASED,            // Based on schedule
        TRANSACTION_BASED,     // On every transaction
        INCOME_BASED,          // When income received
        BALANCE_BASED,         // When balance exceeds threshold
        GOAL_BASED,            // Based on goal progress
        EVENT_BASED,           // Custom events
        MILESTONE_BASED        // On achieving milestones
    }
    
    public enum PaymentMethod {
        BANK_ACCOUNT,
        DEBIT_CARD,
        BALANCE,
        EXTERNAL_ACCOUNT
    }
}