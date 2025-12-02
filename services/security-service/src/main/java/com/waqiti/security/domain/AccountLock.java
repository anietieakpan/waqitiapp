package com.waqiti.security.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Account Lock Entity
 *
 * Represents a security lock on a user account.
 * Tracks lock details, security actions taken, and unlock scheduling.
 */
@Entity
@Table(name = "account_locks", indexes = {
    @Index(name = "idx_account_lock_account_id", columnList = "account_id"),
    @Index(name = "idx_account_lock_status", columnList = "status"),
    @Index(name = "idx_account_lock_event_id", columnList = "event_id"),
    @Index(name = "idx_account_lock_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLock {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type", nullable = false)
    private LockType lockType;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_reason", nullable = false)
    private LockReason lockReason;

    @Column(name = "lock_description", length = 1000)
    private String lockDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LockStatus status = LockStatus.INITIATED;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "failed_attempts")
    private Integer failedAttempts;

    @Column(name = "account_locked")
    @Builder.Default
    private boolean accountLocked = false;

    @Column(name = "sessions_terminated")
    @Builder.Default
    private int sessionsTerminated = 0;

    @Column(name = "tokens_revoked")
    @Builder.Default
    private int tokensRevoked = 0;

    @Column(name = "api_keys_invalidated")
    @Builder.Default
    private int apiKeysInvalidated = 0;

    @Column(name = "transactions_blocked")
    @Builder.Default
    private int transactionsBlocked = 0;

    @Column(name = "password_reset_required")
    @Builder.Default
    private boolean passwordResetRequired = false;

    @Column(name = "mfa_required")
    @Builder.Default
    private boolean mfaRequired = false;

    @Column(name = "related_accounts_count")
    @Builder.Default
    private int relatedAccountsCount = 0;

    @Column(name = "related_accounts_locked")
    @Builder.Default
    private boolean relatedAccountsLocked = false;

    @Column(name = "scheduled_unlock_at")
    private LocalDateTime scheduledUnlockAt;

    @Column(name = "lock_duration_minutes")
    private Long lockDurationMinutes;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "upgraded_at")
    private LocalDateTime upgradedAt;

    @Column(name = "security_action_error", length = 500)
    private String securityActionError;

    @Column(name = "operation_error", length = 500)
    private String operationError;

    @Column(name = "related_accounts_error", length = 500)
    private String relatedAccountsError;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    @Column(name = "version")
    private Long version;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
