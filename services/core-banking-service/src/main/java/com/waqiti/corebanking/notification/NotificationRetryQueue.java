package com.waqiti.corebanking.notification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification Retry Queue Entity
 *
 * Stores failed notifications for retry processing.
 * Provides persistent queue for notifications when NotificationService is unavailable.
 *
 * Retry Strategy:
 * - Exponential backoff: 1min, 5min, 30min, 2hr, 6hr, 24hr
 * - Max 6 retry attempts
 * - Failed notifications stored for 7 days
 * - Scheduled worker processes queue every minute
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Entity
@Table(name = "notification_retry_queue", indexes = {
    @Index(name = "idx_status_next_retry", columnList = "status, nextRetryAt"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRetryQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "recipient_id", length = 255)
    private String recipientId;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 50)
    private String recipientPhone;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Column(name = "template_data", columnDefinition = "TEXT")
    private String templateData; // JSON

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RetryStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 6;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType; // e.g., "TRANSACTION", "ACCOUNT"

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    public enum NotificationType {
        EMAIL,
        SMS,
        PUSH_NOTIFICATION,
        IN_APP
    }

    public enum RetryStatus {
        PENDING,      // Initial state, awaiting first attempt
        RETRYING,     // Currently being retried
        COMPLETED,    // Successfully sent
        FAILED,       // Max retries exceeded
        CANCELLED     // Manually cancelled
    }

    /**
     * Calculate next retry time using exponential backoff
     * Delays: 1min, 5min, 30min, 2hr, 6hr, 24hr
     */
    public void calculateNextRetryTime() {
        long delayMinutes = switch (retryCount) {
            case 0 -> 1;      // 1 minute
            case 1 -> 5;      // 5 minutes
            case 2 -> 30;     // 30 minutes
            case 3 -> 120;    // 2 hours
            case 4 -> 360;    // 6 hours
            case 5 -> 1440;   // 24 hours
            default -> 1440;  // 24 hours (max)
        };

        this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }

    /**
     * Check if notification should be retried
     */
    public boolean shouldRetry() {
        return retryCount < maxRetryAttempts &&
               status != RetryStatus.COMPLETED &&
               status != RetryStatus.CANCELLED;
    }

    /**
     * Mark as failed after max retries
     */
    public void markAsFailed() {
        this.status = RetryStatus.FAILED;
        this.nextRetryAt = null;
    }

    /**
     * Mark as completed
     */
    public void markAsCompleted() {
        this.status = RetryStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    /**
     * Increment retry count and update status
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        this.status = RetryStatus.RETRYING;

        if (shouldRetry()) {
            calculateNextRetryTime();
        } else {
            markAsFailed();
        }
    }
}
