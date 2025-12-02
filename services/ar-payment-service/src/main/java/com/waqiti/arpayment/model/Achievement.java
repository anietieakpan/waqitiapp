package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a gamification achievement in AR payments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {
    private UUID id;
    private String name;
    private String description;
    private AchievementType type;
    private int points;
    private int xpReward;
    private String badgeUrl;
    private String iconUrl;
    private String message;
    private Tier tier;
    private boolean isUnlocked;
    private Instant unlockedAt;
    private Progress progress;
    private List<Reward> rewards;
    private Map<String, Object> metadata;
    
    public enum AchievementType {
        FIRST_AR_PAYMENT,
        GESTURE_MASTER,
        SPEED_DEMON,
        SOCIAL_BUTTERFLY,
        MERCHANT_EXPLORER,
        DAILY_STREAK,
        WEEKLY_CHALLENGE,
        MONTHLY_MILESTONE,
        SPECIAL_EVENT,
        COMMUNITY_HELPER
    }
    
    public enum Tier {
        BRONZE,
        SILVER,
        GOLD,
        PLATINUM,
        DIAMOND
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private int current;
        private int target;
        private double percentage;
        private String displayText;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reward {
        private String type; // POINTS, BADGE, DISCOUNT, FEATURE_UNLOCK
        private String value;
        private String description;
    }
    
    public String getName() {
        return name != null ? name : "Mystery Achievement";
    }
    
    public int getPoints() {
        return points;
    }
    
    public String getBadgeUrl() {
        return badgeUrl != null ? badgeUrl : "default-badge.png";
    }
    
    public String getMessage() {
        return message != null ? message : "Achievement unlocked!";
    }
}