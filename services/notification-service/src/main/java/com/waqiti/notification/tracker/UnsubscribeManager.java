package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages unsubscribe preferences and suppression lists
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnsubscribeManager {
    
    private String managerId;
    private String userId;
    private String email;
    
    // Subscription status
    private boolean globalUnsubscribe;
    private LocalDateTime globalUnsubscribeDate;
    
    // Category preferences
    private Set<String> subscribedCategories;
    private Set<String> unsubscribedCategories;
    private Map<String, LocalDateTime> categoryUnsubscribeDates;
    
    // Channel preferences
    private Map<String, Boolean> channelPreferences; // email, sms, push, etc.
    private Map<String, LocalDateTime> channelUnsubscribeDates;
    
    // Frequency preferences
    private String emailFrequency; // immediate, daily, weekly, monthly, never
    private String smsFrequency;
    private String pushFrequency;
    
    // Suppression reasons
    private List<SuppressionReason> suppressionReasons;
    private boolean hardBounced;
    private boolean complained;
    private boolean manualSuppression;
    
    // Re-engagement
    private boolean reEngagementEligible;
    private LocalDateTime lastEngagementDate;
    private int reEngagementAttempts;
    
    // Compliance
    private String unsubscribeMethod; // link, email, api, admin
    private String unsubscribeSource;
    private String unsubscribeReason;
    private String unsubscribeComments;
    
    // Audit trail
    private List<PreferenceChange> changeHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Metadata
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuppressionReason {
        private String reason;
        private String source;
        private LocalDateTime suppressedAt;
        private boolean permanent;
        private LocalDateTime expiresAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferenceChange {
        private String changeType;
        private String field;
        private Object oldValue;
        private Object newValue;
        private LocalDateTime changedAt;
        private String changedBy;
        private String changeReason;
    }
}