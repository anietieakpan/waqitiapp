package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MongoDB document for recipient profiles and preferences
 */
@Document(collection = "recipient_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientProfile {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String userId;
    
    @Indexed
    private String email;
    
    private String firstName;
    private String lastName;
    private String fullName;
    
    // Contact preferences
    private String preferredChannel; // email, sms, push, in-app
    private String preferredLanguage;
    private String timezone;
    
    // Email preferences
    private boolean emailEnabled;
    private Set<String> subscribedCategories;
    private Set<String> unsubscribedCategories;
    private String emailFrequency; // immediate, daily, weekly, monthly
    
    // SMS preferences
    private boolean smsEnabled;
    private String phoneNumber;
    private String phoneCountryCode;
    
    // Push preferences
    private boolean pushEnabled;
    private List<DeviceToken> deviceTokens;
    
    // In-app preferences
    private boolean inAppEnabled;
    
    // Delivery settings
    private List<String> blockedSenders;
    private List<String> allowedSenders;
    
    // Activity tracking
    private LocalDateTime lastEmailSent;
    private LocalDateTime lastSmsSent;
    private LocalDateTime lastPushSent;
    private LocalDateTime lastInAppSent;
    
    private int totalEmailsSent;
    private int totalSmsSent;
    private int totalPushSent;
    private int totalInAppSent;
    
    // Engagement metrics
    private double emailOpenRate;
    private double emailClickRate;
    private double smsClickRate;
    private double pushClickRate;
    
    // Status
    private String status; // active, inactive, bounced, unsubscribed
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    
    // Custom attributes
    private Map<String, Object> customAttributes;
    
    // Compliance
    private boolean gdprConsent;
    private LocalDateTime gdprConsentDate;
    private String gdprConsentVersion;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceToken {
        private String token;
        private String platform; // ios, android, web
        private String deviceId;
        private String appVersion;
        private LocalDateTime registeredAt;
        private LocalDateTime lastUsedAt;
        private boolean active;
    }
}