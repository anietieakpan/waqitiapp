package com.waqiti.recurringpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced scheduled payment entity with advanced scheduling capabilities.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Entity
@Table(name = "scheduled_payments", indexes = {
    @Index(name = "idx_scheduled_payments_user_id", columnList = "user_id"),
    @Index(name = "idx_scheduled_payments_status", columnList = "status"),
    @Index(name = "idx_scheduled_payments_next_execution", columnList = "next_execution_date"),
    @Index(name = "idx_scheduled_payments_recipient", columnList = "recipient_id")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledPayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "recipient_id", nullable = false)
    private String recipientId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "description", nullable = false)
    private String description;
    
    @Embedded
    private ScheduleConfiguration schedule;
    
    @Column(name = "start_date", nullable = false)
    private Instant startDate;
    
    @Column(name = "end_date")
    private Instant endDate;
    
    @Column(name = "next_execution_date")
    private Instant nextExecutionDate;
    
    @Column(name = "last_execution_date")
    private Instant lastExecutionDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "last_execution_status")
    private com.waqiti.recurringpayment.domain.ExecutionStatus lastExecutionStatus;
    
    @Column(name = "timezone", nullable = false)
    private ZoneId timezone;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduledPaymentStatus status;
    
    @Column(name = "total_executions", nullable = false)
    @Builder.Default
    private Integer totalExecutions = 0;
    
    @Column(name = "successful_executions", nullable = false)
    @Builder.Default
    private Integer successfulExecutions = 0;
    
    @Column(name = "failed_executions", nullable = false)
    @Builder.Default
    private Integer failedExecutions = 0;
    
    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;
    
    @Column(name = "total_amount_paid", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmountPaid = BigDecimal.ZERO;
    
    @Column(name = "max_occurrences")
    private Integer maxOccurrences;
    
    @Embedded
    private ReminderSettings reminderSettings;
    
    @Embedded
    private RetrySettings retrySettings;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_handling")
    private HolidayHandling holidayHandling;
    
    @Column(name = "country_code", length = 2)
    private String countryCode;
    
    @Column(name = "paused_at")
    private Instant pausedAt;
    
    @Column(name = "paused_until")
    private Instant pausedUntil;
    
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    @Column(name = "last_failure_reason")
    private String lastFailureReason;
    
    @Column(name = "reminder_sent")
    @Builder.Default
    private boolean reminderSent = false;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "scheduled_payment_metadata", 
                    joinColumns = @JoinColumn(name = "payment_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if payment is active and ready for execution.
     */
    public boolean isActive() {
        return status == ScheduledPaymentStatus.ACTIVE && 
               (endDate == null || Instant.now().isBefore(endDate));
    }
    
    /**
     * Check if payment has reached maximum occurrences.
     */
    public boolean hasReachedMaxOccurrences() {
        return maxOccurrences != null && totalExecutions >= maxOccurrences;
    }
}

