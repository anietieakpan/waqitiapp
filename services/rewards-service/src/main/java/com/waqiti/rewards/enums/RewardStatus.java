package com.waqiti.rewards.enums;

/**
 * Reward Status Enumeration
 *
 * Tracks the lifecycle status of rewards
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-08
 */
public enum RewardStatus {
    /**
     * Reward has been earned but not yet approved
     */
    PENDING,

    /**
     * Reward has been approved for issuance
     */
    APPROVED,

    /**
     * Reward has been issued to the recipient
     */
    ISSUED,

    /**
     * Reward has been redeemed by the recipient
     */
    REDEEMED,

    /**
     * Reward has expired without being redeemed
     */
    EXPIRED,

    /**
     * Reward was rejected (e.g., fraud detection)
     */
    REJECTED,

    /**
     * Reward has been canceled
     */
    CANCELED,

    /**
     * Reward is on hold pending review
     */
    ON_HOLD
}
