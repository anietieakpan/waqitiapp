package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for user preferences from User Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDto {
    
    private String userId;
    private Boolean notificationsEnabled;
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean smsNotifications;
    
    // Rewards preferences
    private Boolean autoRedeemEnabled;
    private String preferredRewardType; // CASHBACK, POINTS, MILES
    private Double minimumCashbackThreshold;
    private Integer minimumPointsThreshold;
    private Boolean consolidateRewards;
    
    // Communication preferences
    private String communicationLanguage;
    private String timezone;
    private Boolean marketingOptIn;
    private Boolean rewardsUpdateOptIn;
    
    // Additional preferences as key-value pairs
    private Map<String, String> customPreferences;
}