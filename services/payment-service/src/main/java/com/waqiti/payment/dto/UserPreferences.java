package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for user preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPreferences {
    
    private String userId;
    
    // Notification preferences
    @Builder.Default
    private boolean notificationsEnabled = true;
    
    @Builder.Default
    private boolean emailNotifications = true;
    
    @Builder.Default
    private boolean smsNotifications = true;
    
    @Builder.Default
    private boolean pushNotifications = true;
    
    // Transaction preferences
    @Builder.Default
    private boolean transactionAlerts = true;
    
    @Builder.Default
    private boolean securityAlerts = true;
    
    @Builder.Default
    private boolean marketingEmails = false;
    
    // Privacy preferences
    @Builder.Default
    private boolean shareDataForAnalytics = true;
    
    @Builder.Default
    private boolean shareDataForMarketing = false;
    
    // Security preferences
    @Builder.Default
    private boolean twoFactorEnabled = false;
    
    @Builder.Default
    private boolean biometricEnabled = false;
    
    private String preferredAuthMethod;
    
    // Regional preferences
    private String language;
    private String locale;
    private String timezone;
    private String currency;
    
    // Transaction limits
    private String dailyTransactionLimit;
    private String monthlyTransactionLimit;
    private String singleTransactionLimit;
    
    // App preferences
    private String theme; // LIGHT, DARK, AUTO
    private boolean faceIdEnabled;
    private boolean touchIdEnabled;
    
    // Metadata
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> customPreferences;
}