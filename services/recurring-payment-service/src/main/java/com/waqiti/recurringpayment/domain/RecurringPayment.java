package com.waqiti.recurringpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Entity
@Table(name = "recurring_payments")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "recipient_id", nullable = false)
    private String recipientId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "description", nullable = false)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private RecurringFrequency frequency;
    
    @Column(name = "start_date", nullable = false)
    private Instant startDate;
    
    @Column(name = "end_date")
    private Instant endDate;
    
    @Column(name = "next_execution_date")
    private Instant nextExecutionDate;
    
    @Column(name = "max_occurrences")
    private Integer maxOccurrences;
    
    @Column(name = "day_of_month")
    private Integer dayOfMonth;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "monthly_pattern")
    private MonthlyPattern monthlyPattern;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecurringStatus status;
    
    @Column(name = "total_executions", nullable = false)
    @Builder.Default
    private Integer totalExecutions = 0;
    
    @Column(name = "successful_executions", nullable = false)
    @Builder.Default
    private Integer successfulExecutions = 0;
    
    @Column(name = "failed_executions", nullable = false)
    @Builder.Default
    private Integer failedExecutions = 0;
    
    @Column(name = "total_amount_paid", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalAmountPaid = BigDecimal.ZERO;
    
    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;
    
    @Column(name = "reminder_enabled", nullable = false)
    @Builder.Default
    private boolean reminderEnabled = false;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_payment_reminder_days", 
                    joinColumns = @JoinColumn(name = "recurring_payment_id"))
    @Column(name = "days_before")
    @Builder.Default
    private Set<Integer> reminderDays = new HashSet<>();
    
    @Column(name = "auto_retry", nullable = false)
    @Builder.Default
    private boolean autoRetry = true;
    
    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private Integer maxRetryAttempts = 3;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_action", nullable = false)
    @Builder.Default
    private FailureAction failureAction = FailureAction.CONTINUE;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_payment_tags", 
                    joinColumns = @JoinColumn(name = "recurring_payment_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_payment_metadata", 
                    joinColumns = @JoinColumn(name = "recurring_payment_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "last_execution_date")
    private Instant lastExecutionDate;
    
    @Column(name = "last_failure_date")
    private Instant lastFailureDate;
    
    @Column(name = "last_failure_reason")
    private String lastFailureReason;
    
    @Column(name = "paused_at")
    private Instant pausedAt;
    
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
    
    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;
    
    @Version
    private Long version;
    
    // Business logic methods
    
    public boolean isActive() {
        return status == RecurringStatus.ACTIVE;
    }
    
    public boolean canBeExecuted() {
        return isActive() && nextExecutionDate != null && 
               nextExecutionDate.isBefore(Instant.now().plusSeconds(300)); // 5-minute window
    }
    
    public boolean hasReachedMaxOccurrences() {
        return maxOccurrences != null && successfulExecutions >= maxOccurrences;
    }
    
    public boolean hasExpired() {
        return endDate != null && Instant.now().isAfter(endDate);
    }
    
    public boolean shouldComplete() {
        return hasReachedMaxOccurrences() || hasExpired();
    }
    
    public void incrementExecutionStats(boolean successful) {
        totalExecutions++;
        if (successful) {
            successfulExecutions++;
            consecutiveFailures = 0;
            totalAmountPaid = totalAmountPaid.add(amount);
            lastExecutionDate = Instant.now();
        } else {
            failedExecutions++;
            consecutiveFailures++;
            lastFailureDate = Instant.now();
        }
    }
    
    public boolean hasExcessiveFailures() {
        return consecutiveFailures >= 5; // Configurable threshold
    }
    
    public double getSuccessRate() {
        if (totalExecutions == 0) return 0.0;
        return (double) successfulExecutions / totalExecutions * 100.0;
    }
}