package com.waqiti.familyaccount.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Attempt Domain Entity
 *
 * Records all transaction authorization attempts for audit trail and analysis
 * Includes both authorized and declined transactions
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Entity
@Table(name = "transaction_attempts",
    indexes = {
        @Index(name = "idx_transaction_attempts_member", columnList = "family_member_id"),
        @Index(name = "idx_transaction_attempts_status", columnList = "approval_status"),
        @Index(name = "idx_transaction_attempts_time", columnList = "attempt_time"),
        @Index(name = "idx_transaction_attempts_authorized", columnList = "authorized"),
        @Index(name = "idx_transaction_attempts_idempotency_key", columnList = "idempotency_key", unique = true)
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id", nullable = false)
    @NotNull(message = "Family member is required")
    private FamilyMember familyMember;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Column(name = "merchant_name", length = 200)
    private String merchantName;

    @Column(name = "merchant_category", length = 100)
    private String merchantCategory;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "attempt_time", nullable = false)
    @NotNull(message = "Attempt time is required")
    private LocalDateTime attemptTime;

    @Column(name = "authorized", nullable = false)
    @NotNull(message = "Authorized status is required")
    private Boolean authorized;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "requires_parent_approval", nullable = false)
    @Builder.Default
    private Boolean requiresParentApproval = false;

    @Column(name = "approval_status", length = 20)
    @Builder.Default
    private String approvalStatus = "NOT_REQUIRED";  // PENDING, APPROVED, DECLINED, NOT_REQUIRED

    @Column(name = "approved_by_user_id", length = 50)
    private String approvedByUserId;

    @Column(name = "approval_time")
    private LocalDateTime approvalTime;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;  // External transaction ID if processed

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;  // Unique key to prevent duplicate transaction processing

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (attemptTime == null) {
            attemptTime = LocalDateTime.now();
        }
    }
}
