package com.waqiti.rewards.enums;

/**
 * Referral Status enumeration
 */
public enum ReferralStatus {
    PENDING,      // Referral created, rewards not yet awarded
    COMPLETED,    // Rewards awarded to both parties
    EXPIRED,      // Referral expired before completion
    CANCELLED,    // Referral cancelled (fraud, etc.)
    FAILED        // Reward distribution failed
}
