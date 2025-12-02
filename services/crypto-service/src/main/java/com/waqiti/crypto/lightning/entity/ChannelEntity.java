package com.waqiti.crypto.lightning.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Entity representing a Lightning Network channel
 */
@Entity
@Table(name = "lightning_channels", indexes = {
    @Index(name = "idx_channel_user", columnList = "userId"),
    @Index(name = "idx_channel_remote", columnList = "remotePubkey"),
    @Index(name = "idx_channel_status", columnList = "status"),
    @Index(name = "idx_channel_active", columnList = "activeAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"remotePubkey"})
public class ChannelEntity extends BaseEntity {

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 66)
    private String remotePubkey;

    @Column
    private String remoteAlias;

    @Column(nullable = false)
    private Long capacity;

    @Column(nullable = false)
    private Long localBalance;

    @Column(nullable = false)
    private Long remoteBalance;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChannelStatus status;

    @Column
    private Boolean isPrivate;

    @Column
    private String fundingTxId;

    @Column
    private Integer outputIndex;

    @Column
    private Integer confirmationHeight;

    @Column
    private Integer estimatedOpenBlock;

    @Column
    private Instant openedAt;

    @Column
    private Instant activeAt;

    @Column
    private Instant closedAt;

    @Column
    private String closingTxId;

    @Column
    private Long totalSent;

    @Column
    private Long totalReceived;

    @Column
    private Long totalForwarded;

    @Column
    private Long totalFeesEarned;

    @Column
    private Integer forwardingCount;

    @Column
    private Long baseFee;

    @Column
    private Long feeRate;

    @Column
    private Integer timeLockDelta;

    @Column
    private Integer minHtlc;

    @Column
    private Long maxHtlc;

    @Column
    private Instant lastActivityAt;

    @Column
    private Instant lastRebalanceAt;

    @Column
    private Integer rebalanceCount;

    @Column
    private Instant lastFeePolicyUpdate;

    @Column
    private Double lifetimeEarningsRate;

    @Column
    private Boolean autoRebalanceEnabled;

    @Column
    private Double targetBalanceRatio;

    @Column
    private String backupData;

    @Column
    private Instant lastBackupAt;

    @Column
    private Integer csvDelay;

    @Column
    private Boolean isInitiator;

    @Column
    private Long pushSat;

    @Column
    private Long commitFee;

    @Column
    private Long commitWeight;

    @Column
    private Long feePerKw;

    @Column
    private Long unsettledBalance;

    @Column
    private Integer pendingHtlcs;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = ChannelStatus.PENDING_OPEN;
        }
        if (isPrivate == null) {
            isPrivate = false;
        }
        if (totalSent == null) {
            totalSent = 0L;
        }
        if (totalReceived == null) {
            totalReceived = 0L;
        }
        if (totalForwarded == null) {
            totalForwarded = 0L;
        }
        if (totalFeesEarned == null) {
            totalFeesEarned = 0L;
        }
        if (forwardingCount == null) {
            forwardingCount = 0;
        }
        if (rebalanceCount == null) {
            rebalanceCount = 0;
        }
        if (autoRebalanceEnabled == null) {
            autoRebalanceEnabled = false;
        }
        if (targetBalanceRatio == null) {
            targetBalanceRatio = 0.5;
        }
        if (pendingHtlcs == null) {
            pendingHtlcs = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Calculate the balance ratio (local / capacity)
     */
    public double getBalanceRatio() {
        if (capacity == null || capacity == 0) {
            return 0.0;
        }
        return (double) localBalance / capacity;
    }

    /**
     * Check if channel needs rebalancing
     */
    public boolean needsRebalancing(double threshold) {
        double ratio = getBalanceRatio();
        return ratio < (0.5 - threshold) || ratio > (0.5 + threshold);
    }

    /**
     * Calculate available balance for sending
     */
    public long getAvailableBalance() {
        if (localBalance == null || commitFee == null || unsettledBalance == null) {
            return 0;
        }
        return Math.max(0, localBalance - commitFee - unsettledBalance);
    }

    /**
     * Calculate available balance for receiving
     */
    public long getReceivableBalance() {
        if (remoteBalance == null || unsettledBalance == null) {
            return 0;
        }
        return Math.max(0, remoteBalance - unsettledBalance);
    }

    /**
     * Check if channel is active
     */
    public boolean isActive() {
        return status == ChannelStatus.ACTIVE;
    }

    /**
     * Calculate channel utilization percentage
     */
    public double getUtilization() {
        if (capacity == null || capacity == 0) {
            return 0.0;
        }
        long totalFlow = (totalSent != null ? totalSent : 0) + 
                        (totalReceived != null ? totalReceived : 0) + 
                        (totalForwarded != null ? totalForwarded : 0);
        return Math.min(100.0, (double) totalFlow / capacity * 100);
    }
}