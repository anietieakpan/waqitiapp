package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User notification preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferences {
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Global preferences
     */
    private GlobalPreferences globalPreferences;
    
    /**
     * Channel-specific preferences
     */
    private Map<NotificationChannel, ChannelPreferences> channelPreferences;
    
    /**
     * Category preferences
     */
    private Map<String, CategoryPreferences> categoryPreferences;
    
    /**
     * Quiet hours settings
     */
    private QuietHours quietHours;
    
    /**
     * Language preference
     */
    private String preferredLanguage;
    
    /**
     * Timezone
     */
    private ZoneId timezone;
    
    /**
     * Frequency limits
     */
    private FrequencyLimits frequencyLimits;
    
    /**
     * Blocked senders
     */
    private Set<String> blockedSenders;
    
    /**
     * Custom preferences
     */
    private Map<String, Object> customPreferences;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalPreferences {
        @Builder.Default
        private boolean enabled = true;
        private boolean marketingEnabled;
        private boolean transactionalEnabled;
        private boolean securityAlertsEnabled;
        private boolean productUpdatesEnabled;
        private boolean researchEnabled;
        private Set<String> allowedCategories;
        private Set<String> blockedCategories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelPreferences {
        @Builder.Default
        private boolean enabled = true;
        private String primaryContact;
        private List<String> alternateContacts;
        private DeliveryPreferences deliveryPreferences;
        private Set<String> allowedCategories;
        private Set<String> blockedCategories;
        private Map<String, String> channelSettings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryPreferences {
        @Builder.Default
        private boolean enabled = true;
        private Set<NotificationChannel> enabledChannels;
        private NotificationPriority minimumPriority;
        private FrequencyLimit frequencyLimit;
        private boolean requireConfirmation;
        private Map<String, Object> categorySettings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuietHours {
        private boolean enabled;
        private LocalTime startTime;
        private LocalTime endTime;
        private Set<String> daysOfWeek;
        private Set<String> exemptCategories;
        private boolean allowCriticalAlerts;
        private ZoneId timezone;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequencyLimits {
        private FrequencyLimit dailyLimit;
        private FrequencyLimit weeklyLimit;
        private FrequencyLimit monthlyLimit;
        private Map<String, FrequencyLimit> categoryLimits;
        private Map<NotificationChannel, FrequencyLimit> channelLimits;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequencyLimit {
        private int maxCount;
        private boolean enabled;
        private Set<String> exemptCategories;
        private boolean rollover;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryPreferences {
        private boolean batchingEnabled;
        private int batchingIntervalMinutes;
        private boolean digestEnabled;
        private DigestFrequency digestFrequency;
        private boolean richContentEnabled;
        private boolean attachmentsEnabled;
        private int maxAttachmentSizeMb;
    }
    
    public enum DigestFrequency {
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY
    }
}