package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * User notification settings and preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {
    
    private String userId;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean pushEnabled;
    private boolean inAppEnabled;
    private boolean whatsAppEnabled;
    
    // Category-specific settings
    private boolean transactionalEnabled;
    private boolean marketingEnabled;
    private boolean systemEnabled;
    private boolean securityEnabled;
    private boolean promotionalEnabled;
    
    // Quiet hours
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private Set<String> quietHoursDays; // MON, TUE, etc.
    
    // Frequency settings
    private String emailFrequency; // INSTANT, HOURLY, DAILY, WEEKLY
    private String smsFrequency;
    private String pushFrequency;
    
    // Channel preferences by category
    private Map<String, ChannelPreferences> categoryPreferences;
    
    // Language and localization
    private String locale;
    private String timezone;
    
    // Advanced settings
    private boolean digestEnabled; // Combine notifications into digest
    private int maxNotificationsPerHour;
    private boolean deduplicationEnabled;
    
    public boolean isChannelEnabled(String channel) {
        switch (channel.toLowerCase()) {
            case "email": return emailEnabled;
            case "sms": return smsEnabled;
            case "push": return pushEnabled;
            case "inapp": return inAppEnabled;
            case "whatsapp": return whatsAppEnabled;
            default: return false;
        }
    }
    
    public boolean isCategoryEnabled(String category) {
        switch (category.toLowerCase()) {
            case "transactional": return transactionalEnabled;
            case "marketing": return marketingEnabled;
            case "system": return systemEnabled;
            case "security": return securityEnabled;
            case "promotional": return promotionalEnabled;
            default: return true; // Default to enabled for unknown categories
        }
    }
    
    public boolean isWithinQuietHours() {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        
        LocalTime now = LocalTime.now();
        
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return now.isAfter(quietHoursStart) && now.isBefore(quietHoursEnd);
        } else {
            // Quiet hours span midnight
            return now.isAfter(quietHoursStart) || now.isBefore(quietHoursEnd);
        }
    }
    
    public ChannelPreferences getPreferencesForCategory(String category) {
        if (categoryPreferences == null) {
            return ChannelPreferences.getDefault();
        }
        return categoryPreferences.getOrDefault(category, ChannelPreferences.getDefault());
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelPreferences {
        private boolean emailEnabled;
        private boolean smsEnabled;
        private boolean pushEnabled;
        private boolean inAppEnabled;
        private boolean whatsAppEnabled;
        private String priority; // low, normal, high
        
        public static ChannelPreferences getDefault() {
            return ChannelPreferences.builder()
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(true)
                .inAppEnabled(true)
                .whatsAppEnabled(false)
                .priority("normal")
                .build();
        }
    }
}