package com.waqiti.wallet.entity;

import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.enums.FreezeReason;
import com.waqiti.wallet.enums.FreezeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking the history of wallet freeze and unfreeze operations.
 * Critical for compliance, audit trails, and dispute resolution.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "wallet_freeze_history", indexes = {
    @Index(name = "idx_wallet_freeze_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_wallet_freeze_user_id", columnList = "user_id"),
    @Index(name = "idx_wallet_freeze_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_wallet_freeze_frozen_at", columnList = "frozen_at"),
    @Index(name = "idx_wallet_freeze_type_reason", columnList = "freeze_type, freeze_reason")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFreezeHistory {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_type", nullable = false, length = 50)
    private FreezeType freezeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_reason", nullable = false, length = 100)
    private FreezeReason freezeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 50)
    private WalletStatus previousStatus;

    @Column(name = "frozen_by", nullable = false, length = 255)
    private String frozenBy;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt;

    @Column(name = "unfrozen_by", length = 255)
    private String unfrozenBy;

    @Column(name = "unfrozen_at")
    private Instant unfrozenAt;

    @Column(name = "event_id", unique = true)
    private UUID eventId;  // For idempotency check

    @Column(name = "correlation_id")
    private UUID correlationId;  // For distributed tracing

    @Column(name = "freeze_duration_seconds")
    private Long freezeDurationSeconds;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;  // JSON metadata

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (frozenAt == null) {
            frozenAt = Instant.now();
        }
    }

    /**
     * Calculate freeze duration when wallet is unfrozen
     */
    public void calculateFreezeDuration() {
        if (frozenAt != null && unfrozenAt != null) {
            freezeDurationSeconds = unfrozenAt.getEpochSecond() - frozenAt.getEpochSecond();
        }
    }

    /**
     * Check if this freeze record has been resolved (unfrozen)
     */
    public boolean isResolved() {
        return unfrozenAt != null && unfrozenBy != null;
    }

    /**
     * Get freeze duration in hours
     */
    public Long getFreezeDurationHours() {
        if (freezeDurationSeconds == null) {
            return null;
        }
        return freezeDurationSeconds / 3600;
    }
}
