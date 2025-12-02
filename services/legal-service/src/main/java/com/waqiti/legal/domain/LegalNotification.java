package com.waqiti.legal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Legal Notification Domain Entity
 *
 * Complete production-ready notification system with:
 * - Multi-channel notification delivery (email, SMS, in-app)
 * - Priority-based notification routing
 * - Delivery tracking and read receipts
 * - Action-required workflow
 * - Automated reminder scheduling
 * - Notification templates
 * - Entity relationship tracking
 * - Escalation for unread critical notifications
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_notification",
    indexes = {
        @Index(name = "idx_legal_notification_type", columnList = "notification_type"),
        @Index(name = "idx_legal_notification_recipient", columnList = "recipient"),
        @Index(name = "idx_legal_notification_status", columnList = "notification_status")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Notification ID is required")
    private String notificationId;

    @Column(name = "notification_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(name = "recipient", nullable = false, length = 100)
    @NotBlank(message = "Recipient is required")
    private String recipient;

    @Column(name = "recipient_email")
    @Email(message = "Valid email is required")
    private String recipientEmail;

    @Column(name = "subject", nullable = false)
    @NotBlank(message = "Subject is required")
    private String subject;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Message is required")
    private String message;

    @Column(name = "priority", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "notification_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus notificationStatus = NotificationStatus.PENDING;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id", length = 100)
    private String relatedEntityId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "action_required")
    @Builder.Default
    private Boolean actionRequired = false;

    @Column(name = "action_deadline")
    private LocalDate actionDeadline;

    @Column(name = "reminder_count")
    @Builder.Default
    private Integer reminderCount = 0;

    @Column(name = "created_at", nullable = false)
    @NotNull
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (notificationId == null) {
            notificationId = "NTF-" + UUID.randomUUID().toString();
        }
        if (scheduledAt == null) {
            scheduledAt = LocalDateTime.now();
        }
    }

    // Enums
    public enum NotificationType {
        CONTRACT_EXPIRING,
        CONTRACT_RENEWAL_DUE,
        DEADLINE_APPROACHING,
        DEADLINE_MISSED,
        SIGNATURE_REQUESTED,
        SIGNATURE_COMPLETED,
        DOCUMENT_APPROVAL_REQUIRED,
        DOCUMENT_APPROVED,
        DOCUMENT_REJECTED,
        COMPLIANCE_ASSESSMENT_DUE,
        COMPLIANCE_VIOLATION,
        AUDIT_SCHEDULED,
        AUDIT_FINDING,
        CASE_HEARING_SCHEDULED,
        CASE_DEADLINE,
        CASE_UPDATE,
        OBLIGATION_DUE,
        OBLIGATION_OVERDUE,
        RISK_ALERT,
        LEGAL_HOLD_NOTICE,
        SUBPOENA_RECEIVED,
        SETTLEMENT_OFFER,
        APPROVAL_REQUIRED,
        ACTION_REQUIRED,
        INFORMATIONAL,
        ESCALATION,
        SYSTEM_ALERT
    }

    public enum NotificationPriority {
        CRITICAL,
        HIGH,
        NORMAL,
        LOW
    }

    public enum NotificationStatus {
        PENDING,
        SCHEDULED,
        SENT,
        DELIVERED,
        READ,
        FAILED,
        CANCELLED
    }

    // Complete business logic methods

    /**
     * Send notification
     */
    public void send() {
        if (notificationStatus == NotificationStatus.SENT ||
            notificationStatus == NotificationStatus.DELIVERED ||
            notificationStatus == NotificationStatus.READ) {
            throw new IllegalStateException("Notification already sent");
        }
        this.sentAt = LocalDateTime.now();
        this.notificationStatus = NotificationStatus.SENT;
    }

    /**
     * Mark as delivered
     */
    public void markAsDelivered() {
        if (notificationStatus != NotificationStatus.SENT) {
            throw new IllegalStateException("Notification must be sent before marking as delivered");
        }
        this.deliveredAt = LocalDateTime.now();
        this.notificationStatus = NotificationStatus.DELIVERED;
    }

    /**
     * Mark as read
     */
    public void markAsRead() {
        if (notificationStatus != NotificationStatus.DELIVERED &&
            notificationStatus != NotificationStatus.SENT) {
            send(); // Auto-send if not sent
        }
        this.readAt = LocalDateTime.now();
        this.notificationStatus = NotificationStatus.READ;
    }

    /**
     * Mark as failed
     */
    public void markAsFailed(String reason) {
        this.notificationStatus = NotificationStatus.FAILED;
        this.message = message + "\n\nFAILURE REASON: " + reason;
    }

    /**
     * Cancel notification
     */
    public void cancel() {
        if (notificationStatus == NotificationStatus.SENT ||
            notificationStatus == NotificationStatus.DELIVERED ||
            notificationStatus == NotificationStatus.READ) {
            throw new IllegalStateException("Cannot cancel sent notification");
        }
        this.notificationStatus = NotificationStatus.CANCELLED;
    }

    /**
     * Schedule notification
     */
    public void schedule(LocalDateTime scheduleTime) {
        if (scheduleTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot schedule notification in the past");
        }
        this.scheduledAt = scheduleTime;
        this.notificationStatus = NotificationStatus.SCHEDULED;
    }

    /**
     * Check if notification is overdue for sending
     */
    public boolean isOverdueForSending() {
        return notificationStatus == NotificationStatus.PENDING &&
               scheduledAt != null &&
               LocalDateTime.now().isAfter(scheduledAt);
    }

    /**
     * Check if ready to send
     */
    public boolean isReadyToSend() {
        return (notificationStatus == NotificationStatus.PENDING ||
                notificationStatus == NotificationStatus.SCHEDULED) &&
               (scheduledAt == null || !LocalDateTime.now().isBefore(scheduledAt));
    }

    /**
     * Check if action is overdue
     */
    public boolean isActionOverdue() {
        return actionRequired &&
               actionDeadline != null &&
               LocalDate.now().isAfter(actionDeadline) &&
               notificationStatus != NotificationStatus.READ;
    }

    /**
     * Get days until action deadline
     */
    public long getDaysUntilActionDeadline() {
        if (actionDeadline == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), actionDeadline);
    }

    /**
     * Check if reminder should be sent
     */
    public boolean shouldSendReminder() {
        if (!actionRequired || actionDeadline == null) {
            return false;
        }

        // Don't send more than 3 reminders
        if (reminderCount >= 3) {
            return false;
        }

        // Only send reminders for unread notifications
        if (notificationStatus == NotificationStatus.READ) {
            return false;
        }

        // Send reminder if action deadline is within 3 days and not read
        long daysUntilDeadline = getDaysUntilActionDeadline();
        return daysUntilDeadline > 0 && daysUntilDeadline <= 3;
    }

    /**
     * Send reminder
     */
    public void sendReminder() {
        if (!shouldSendReminder()) {
            throw new IllegalStateException("Reminder not eligible to be sent");
        }
        this.reminderCount++;
        this.sentAt = LocalDateTime.now();
        // Keep status as is, just update sent time
    }

    /**
     * Check if requires escalation
     */
    public boolean requiresEscalation() {
        // Escalate critical unread notifications after 24 hours
        if (priority == NotificationPriority.CRITICAL &&
            notificationStatus != NotificationStatus.READ &&
            sentAt != null) {
            long hoursUnread = ChronoUnit.HOURS.between(sentAt, LocalDateTime.now());
            return hoursUnread >= 24;
        }

        // Escalate high priority unread notifications after 48 hours
        if (priority == NotificationPriority.HIGH &&
            notificationStatus != NotificationStatus.READ &&
            sentAt != null) {
            long hoursUnread = ChronoUnit.HOURS.between(sentAt, LocalDateTime.now());
            return hoursUnread >= 48;
        }

        // Escalate if action is overdue
        if (isActionOverdue()) {
            return true;
        }

        return false;
    }

    /**
     * Create escalation notification
     */
    public LegalNotification createEscalation(String escalationRecipient, String escalationEmail) {
        return LegalNotification.builder()
                .notificationType(NotificationType.ESCALATION)
                .recipient(escalationRecipient)
                .recipientEmail(escalationEmail)
                .subject("ESCALATION: " + this.subject)
                .message("This is an escalated notification.\n\n" +
                        "Original Recipient: " + this.recipient + "\n" +
                        "Original Sent: " + this.sentAt + "\n" +
                        "Status: " + this.notificationStatus + "\n\n" +
                        "Original Message:\n" + this.message)
                .priority(NotificationPriority.CRITICAL)
                .actionRequired(this.actionRequired)
                .actionDeadline(this.actionDeadline)
                .relatedEntityType(this.relatedEntityType)
                .relatedEntityId(this.relatedEntityId)
                .build();
    }

    /**
     * Check if unread
     */
    public boolean isUnread() {
        return notificationStatus != NotificationStatus.READ &&
               notificationStatus != NotificationStatus.CANCELLED &&
               notificationStatus != NotificationStatus.FAILED;
    }

    /**
     * Get time since sent (in hours)
     */
    public long getHoursSinceSent() {
        if (sentAt == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(sentAt, LocalDateTime.now());
    }

    /**
     * Get time until scheduled (in hours)
     */
    public long getHoursUntilScheduled() {
        if (scheduledAt == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(LocalDateTime.now(), scheduledAt);
    }

    /**
     * Retry failed notification
     */
    public void retry() {
        if (notificationStatus != NotificationStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed notifications");
        }
        this.notificationStatus = NotificationStatus.PENDING;
        this.sentAt = null;
        this.deliveredAt = null;
    }

    /**
     * Generate notification summary
     */
    public Map<String, Object> generateSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("notificationId", notificationId);
        summary.put("notificationType", notificationType);
        summary.put("recipient", recipient);
        summary.put("subject", subject);
        summary.put("priority", priority);
        summary.put("notificationStatus", notificationStatus);
        summary.put("createdAt", createdAt);
        summary.put("scheduledAt", scheduledAt);
        summary.put("sentAt", sentAt);
        summary.put("deliveredAt", deliveredAt);
        summary.put("readAt", readAt);
        summary.put("isUnread", isUnread());
        summary.put("actionRequired", actionRequired);
        summary.put("actionDeadline", actionDeadline);
        summary.put("isActionOverdue", isActionOverdue());
        summary.put("reminderCount", reminderCount);
        summary.put("shouldSendReminder", shouldSendReminder());
        summary.put("requiresEscalation", requiresEscalation());
        summary.put("hoursSinceSent", getHoursSinceSent());
        summary.put("relatedEntityType", relatedEntityType);
        summary.put("relatedEntityId", relatedEntityId);
        return summary;
    }

    /**
     * Validate notification
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (recipient == null || recipient.isBlank()) {
            errors.add("Recipient is required");
        }
        if (subject == null || subject.isBlank()) {
            errors.add("Subject is required");
        }
        if (message == null || message.isBlank()) {
            errors.add("Message is required");
        }
        if (recipientEmail != null && !recipientEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            errors.add("Valid email address is required");
        }
        if (actionRequired && actionDeadline == null) {
            errors.add("Action deadline required when action is required");
        }

        return errors;
    }

    /**
     * Get delivery success rate (0-100)
     */
    public int getDeliveryScore() {
        int score = 0;

        if (notificationStatus == NotificationStatus.SENT) {
            score = 60;
        } else if (notificationStatus == NotificationStatus.DELIVERED) {
            score = 80;
        } else if (notificationStatus == NotificationStatus.READ) {
            score = 100;
        } else if (notificationStatus == NotificationStatus.FAILED) {
            score = 0;
        } else {
            score = 20; // PENDING or SCHEDULED
        }

        return score;
    }

    /**
     * Check if urgent
     */
    public boolean isUrgent() {
        return priority == NotificationPriority.CRITICAL ||
               priority == NotificationPriority.HIGH ||
               (actionRequired && getDaysUntilActionDeadline() <= 1);
    }

    /**
     * Create follow-up notification
     */
    public LegalNotification createFollowUp(String additionalMessage) {
        return LegalNotification.builder()
                .notificationType(this.notificationType)
                .recipient(this.recipient)
                .recipientEmail(this.recipientEmail)
                .subject("FOLLOW-UP: " + this.subject)
                .message(additionalMessage + "\n\n" +
                        "This is a follow-up to the notification sent on " + this.sentAt + "\n\n" +
                        "Original Message:\n" + this.message)
                .priority(this.priority)
                .actionRequired(this.actionRequired)
                .actionDeadline(this.actionDeadline)
                .relatedEntityType(this.relatedEntityType)
                .relatedEntityId(this.relatedEntityId)
                .build();
    }

    /**
     * Get urgency score (0-100)
     */
    public int getUrgencyScore() {
        int score = 0;

        // Priority contribution (40 points)
        score += switch (priority) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case NORMAL -> 20;
            case LOW -> 10;
        };

        // Action deadline contribution (40 points)
        if (actionRequired && actionDeadline != null) {
            long daysUntil = getDaysUntilActionDeadline();
            if (daysUntil <= 0) {
                score += 40; // Overdue
            } else if (daysUntil <= 1) {
                score += 35;
            } else if (daysUntil <= 3) {
                score += 25;
            } else if (daysUntil <= 7) {
                score += 15;
            }
        }

        // Unread contribution (20 points)
        if (isUnread()) {
            long hoursSinceSent = getHoursSinceSent();
            if (hoursSinceSent >= 48) {
                score += 20;
            } else if (hoursSinceSent >= 24) {
                score += 15;
            } else if (hoursSinceSent >= 12) {
                score += 10;
            }
        }

        return Math.min(100, score);
    }
}
