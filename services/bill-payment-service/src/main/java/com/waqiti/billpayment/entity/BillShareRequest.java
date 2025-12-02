package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a request to split/share a bill with other users
 * Allows users to split bills with roommates, family members, etc.
 */
@Entity
@Table(name = "bill_share_requests", indexes = {
        @Index(name = "idx_share_bill_id", columnList = "bill_id"),
        @Index(name = "idx_share_creator", columnList = "creator_user_id"),
        @Index(name = "idx_share_participant", columnList = "participant_user_id"),
        @Index(name = "idx_share_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillShareRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "creator_user_id", nullable = false)
    private String creatorUserId;

    @Column(name = "participant_user_id", nullable = false)
    private String participantUserId;

    @Column(name = "share_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shareAmount;

    @Column(name = "share_percentage", precision = 5, scale = 2)
    private BigDecimal sharePercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShareStatus status;

    @Column(name = "invitation_message", columnDefinition = "TEXT")
    private String invitationMessage;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "reminder_count")
    private Integer reminderCount = 0;

    @Column(name = "last_reminder_sent_at")
    private LocalDateTime lastReminderSentAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

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
        return status == ShareStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == ShareStatus.ACCEPTED;
    }

    public boolean isPaid() {
        return status == ShareStatus.PAID;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void accept() {
        this.status = ShareStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public void reject(String reason) {
        this.status = ShareStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public void markAsPaid(UUID paymentId) {
        this.status = ShareStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.paymentId = paymentId;
    }

    public void recordReminderSent() {
        this.reminderCount++;
        this.lastReminderSentAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (reminderCount == null) {
            reminderCount = 0;
        }
        if (expiresAt == null) {
            // Default expiration: 30 days from creation
            expiresAt = LocalDateTime.now().plusDays(30);
        }
    }
}
