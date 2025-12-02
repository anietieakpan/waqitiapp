/**
 * Scheduled Payment Entity
 * Represents a recurring or future payment
 */
package com.waqiti.payment.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledPayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    
    @Column(name = "recipient_type", nullable = false)
    private String recipientType; // USER, MERCHANT, BILL_PAYEE
    
    @Column(name = "recipient_name", nullable = false)
    private String recipientName;
    
    @Column(name = "recipient_account")
    private String recipientAccount;
    
    // Payment Details
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "payment_method", nullable = false)
    private String paymentMethod; // WALLET, BANK_ACCOUNT, CARD
    
    @Column(name = "payment_method_id")
    private String paymentMethodId;
    
    // Schedule Configuration
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleType scheduleType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrencePattern recurrencePattern;
    
    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval; // For custom intervals
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(name = "next_payment_date", nullable = false)
    private LocalDate nextPaymentDate;
    
    @Column(name = "preferred_time")
    private LocalTime preferredTime;
    
    // Execution Details
    @Column(name = "total_payments")
    private Integer totalPayments;
    
    @Column(name = "completed_payments")
    private Integer completedPayments;
    
    @Column(name = "failed_payments")
    private Integer failedPayments;
    
    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;
    
    @Column(name = "last_payment_status")
    private String lastPaymentStatus;
    
    @Column(name = "last_payment_id")
    private UUID lastPaymentId;
    
    // Notification Settings
    @Column(name = "send_reminder")
    private Boolean sendReminder;
    
    @Column(name = "reminder_days_before")
    private Integer reminderDaysBefore;
    
    @Column(name = "notify_on_success")
    private Boolean notifyOnSuccess;
    
    @Column(name = "notify_on_failure")
    private Boolean notifyOnFailure;
    
    // Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduledPaymentStatus status;
    
    @Column(name = "pause_reason")
    private String pauseReason;
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    // Metadata
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;
    
    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ScheduledPaymentStatus.ACTIVE;
        }
        if (completedPayments == null) {
            completedPayments = 0;
        }
        if (failedPayments == null) {
            failedPayments = 0;
        }
        if (sendReminder == null) {
            sendReminder = true;
        }
        if (reminderDaysBefore == null) {
            reminderDaysBefore = 1;
        }
        if (notifyOnSuccess == null) {
            notifyOnSuccess = true;
        }
        if (notifyOnFailure == null) {
            notifyOnFailure = true;
        }
        if (preferredTime == null) {
            preferredTime = LocalTime.of(9, 0); // Default to 9 AM
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return status == ScheduledPaymentStatus.ACTIVE;
    }
    
    public boolean isDue() {
        return isActive() && nextPaymentDate != null && 
               !nextPaymentDate.isAfter(LocalDate.now());
    }
    
    public boolean hasReachedLimit() {
        return totalPayments != null && completedPayments >= totalPayments;
    }
    
    public boolean hasExpired() {
        return endDate != null && endDate.isBefore(LocalDate.now());
    }
    
    public void incrementCompletedPayments() {
        this.completedPayments++;
    }
    
    public void incrementFailedPayments() {
        this.failedPayments++;
    }
    
    public LocalDate calculateNextPaymentDate() {
        if (nextPaymentDate == null) {
            return startDate;
        }
        
        LocalDate nextDate = nextPaymentDate;
        
        switch (recurrencePattern) {
            case DAILY:
                nextDate = nextDate.plusDays(recurrenceInterval != null ? recurrenceInterval : 1);
                break;
            case WEEKLY:
                nextDate = nextDate.plusWeeks(recurrenceInterval != null ? recurrenceInterval : 1);
                break;
            case BIWEEKLY:
                nextDate = nextDate.plusWeeks(2);
                break;
            case MONTHLY:
                nextDate = nextDate.plusMonths(recurrenceInterval != null ? recurrenceInterval : 1);
                break;
            case QUARTERLY:
                nextDate = nextDate.plusMonths(3);
                break;
            case ANNUALLY:
                nextDate = nextDate.plusYears(1);
                break;
            case CUSTOM:
                if (recurrenceInterval != null) {
                    nextDate = nextDate.plusDays(recurrenceInterval);
                }
                break;
        }
        
        // Check if next date exceeds end date
        if (endDate != null && nextDate.isAfter(endDate)) {
            return null;
        }
        
        return nextDate;
    }
    
    public enum ScheduleType {
        RECURRING,
        ONE_TIME
    }
    
    public enum RecurrencePattern {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        ANNUALLY,
        CUSTOM
    }
    
    public enum ScheduledPaymentStatus {
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}