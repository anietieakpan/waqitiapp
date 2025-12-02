package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.RedemptionMethod;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating user rewards preferences
 */
@Data
@Builder
public class UpdatePreferencesRequest {
    
    /**
     * Enable/disable cashback earning
     */
    private Boolean cashbackEnabled;
    
    /**
     * Enable/disable points earning
     */
    private Boolean pointsEnabled;
    
    /**
     * Enable/disable notifications
     */
    private Boolean notificationsEnabled;
    
    /**
     * Enable/disable auto-redemption of cashback
     */
    private Boolean autoRedeemCashback;
    
    /**
     * Preferred redemption method
     */
    private RedemptionMethod preferredRedemptionMethod;
    
    /**
     * Minimum auto-redemption threshold
     */
    private java.math.BigDecimal autoRedeemThreshold;
    
    /**
     * Notification preferences
     */
    private NotificationPreferences notificationPreferences;
    
    @Data
    @Builder
    public static class NotificationPreferences {
        private Boolean emailNotifications;
        private Boolean pushNotifications;
        private Boolean smsNotifications;
        private Boolean tierUpgradeNotifications;
        private Boolean cashbackEarnedNotifications;
        private Boolean pointsEarnedNotifications;
        private Boolean promotionalNotifications;
    }
}