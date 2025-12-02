package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a reminder sent to users about upcoming or overdue bills
 */
@Entity
@Table(name = "bill_reminders", indexes = {
        @Index(name = "idx_reminder_user_id", columnList = "user_id"),
        @Index(name = "idx_reminder_bill_id", columnList = "bill_id"),
        @Index(name = "idx_reminder_scheduled", columnList = "scheduled_send_time"),
        @Index(name = "idx_reminder_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType reminderType;

    @Column(name = "scheduled_send_time", nullable = false)
    private LocalDateTime scheduledSendTime;

    @Column(name = "actual_send_time")
    private LocalDateTime actualSendTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReminderStatus status;

    @Column(name = "notification_channel", length = 30)
    private String notificationChannel;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Business logic methods

    public boolean isPending() {
        return status == ReminderStatus.PENDING;
    }

    public boolean isSent() {
        return status == ReminderStatus.SENT;
    }

    public boolean shouldSendNow() {
        return isPending() && LocalDateTime.now().isAfter(scheduledSendTime);
    }

    public void markAsSent(UUID notificationId) {
        this.status = ReminderStatus.SENT;
        this.actualSendTime = LocalDateTime.now();
        this.notificationId = notificationId;
    }

    public void markAsFailed(String reason) {
        this.status = ReminderStatus.FAILED;
        this.failureReason = reason;
        this.retryCount++;
    }

    @PrePersist
    protected void onCreate() {
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
