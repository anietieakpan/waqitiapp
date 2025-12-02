package com.waqiti.business.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Email Outbox Entity - Transactional Outbox Pattern
 *
 * Ensures reliable email delivery by persisting emails before sending.
 * This prevents email loss in case of failures and enables retry mechanisms.
 *
 * Pattern Benefits:
 * - Transactional consistency (email persisted in same transaction as business logic)
 * - Automatic retry on failure
 * - Delivery tracking and audit trail
 * - No message loss even if SendGrid is down
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "idx_email_status_created", columnList = "status, created_at"),
        @Index(name = "idx_email_next_retry", columnList = "next_retry_at, status"),
        @Index(name = "idx_email_recipient", columnList = "recipient_email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "sender_email", nullable = false, length = 255)
    private String senderEmail;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "html_content", nullable = false, columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "plain_text_content", columnDefinition = "TEXT")
    private String plainTextContent;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Column(name = "template_data", columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private Map<String, Object> templateData;

    @Column(name = "attachments", columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private Map<String, String> attachments; // filename -> base64 content or URL

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 50)
    private EmailType emailType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EmailStatus status;

    @Column(name = "priority", nullable = false)
    private Integer priority = 5; // 1-10, lower is higher priority

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "sendgrid_message_id", length = 255)
    private String sendgridMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "bounce_reason", length = 500)
    private String bounceReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = EmailStatus.PENDING;
        }
        if (priority == null) {
            priority = 5;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count and calculate next retry time using exponential backoff
     * @return true if retry is allowed, false if max retries exceeded
     */
    public boolean incrementRetryCount() {
        this.retryCount++;

        if (this.retryCount >= this.maxRetries) {
            this.status = EmailStatus.FAILED;
            return false;
        }

        // Exponential backoff: 2^retryCount minutes, capped at 1440 (24 hours)
        int backoffMinutes = (int) Math.min(Math.pow(2, this.retryCount), 1440);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes);
        this.status = EmailStatus.RETRY_SCHEDULED;

        return true;
    }

    public enum EmailType {
        INVOICE,
        PAYMENT_CONFIRMATION,
        EXPENSE_APPROVAL_REQUEST,
        EXPENSE_APPROVED,
        EXPENSE_REJECTED,
        REIMBURSEMENT_PROCESSED,
        BUDGET_ALERT,
        ACCOUNT_VERIFICATION,
        PASSWORD_RESET,
        NOTIFICATION,
        MARKETING,
        TRANSACTIONAL
    }

    public enum EmailStatus {
        PENDING,           // Ready to send
        SENDING,           // Currently being sent
        SENT,              // Successfully sent to SendGrid
        DELIVERED,         // Confirmed delivered to recipient
        OPENED,            // Recipient opened email
        CLICKED,           // Recipient clicked link
        BOUNCED,           // Email bounced
        FAILED,            // Permanently failed
        RETRY_SCHEDULED,   // Failed but will retry
        CANCELLED          // Manually cancelled
    }
}
