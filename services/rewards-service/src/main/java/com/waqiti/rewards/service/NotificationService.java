package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.CashbackTransaction;
import com.waqiti.rewards.domain.PointsTransaction;
import com.waqiti.rewards.domain.RedemptionTransaction;
import com.waqiti.rewards.enums.LoyaltyTier;

/**
 * Notification service interface for rewards-related notifications
 */
public interface NotificationService {
    
    /**
     * Send cashback earned notification
     */
    void sendCashbackEarnedNotification(String userId, CashbackTransaction cashback);
    
    /**
     * Send points earned notification
     */
    void sendPointsEarnedNotification(String userId, PointsTransaction points);
    
    /**
     * Send redemption notification
     */
    void sendRedemptionNotification(String userId, RedemptionTransaction redemption);
    
    /**
     * Send tier upgrade notification
     */
    void sendTierUpgradeNotification(String userId, LoyaltyTier newTier);
    
    /**
     * Send points expiry warning
     */
    void sendPointsExpiryWarning(String userId, long pointsExpiring, int daysUntilExpiry);
    
    /**
     * Send points expiry notification
     */
    void sendPointsExpiryNotification(String userId, long pointsExpired);
    
    /**
     * Send welcome notification
     */
    void sendWelcomeNotification(String userId);
    
    /**
     * Send campaign notification
     */
    void sendCampaignNotification(String userId, String campaignId, String campaignName);
    
    /**
     * Send daily/weekly rewards summary
     */
    void sendRewardsSummaryNotification(String userId, String period);
    
    /**
     * Send promotional notification
     */
    void sendPromotionalNotification(String userId, String title, String message);
    
    /**
     * Send auto-redemption notification
     */
    void sendAutoRedemptionNotification(String userId, RedemptionTransaction redemption);
    
    /**
     * Send account activity notification
     */
    void sendAccountActivityNotification(String userId, String activity);

    /**
     * Send rewards welcome notification with custom message
     */
    void sendRewardsWelcomeNotification(String userId, String message);
}