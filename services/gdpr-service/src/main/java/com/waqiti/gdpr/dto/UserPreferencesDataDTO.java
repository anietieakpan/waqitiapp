package com.waqiti.gdpr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for user preferences in GDPR export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDataDTO {

    private String userId;
    private NotificationPreferencesDTO notificationPreferences;
    private PrivacyPreferencesDTO privacyPreferences;
    private AppPreferencesDTO appPreferences;
    private Map<String, Object> customPreferences;

    // Data retrieval metadata
    private boolean dataRetrievalFailed;
    private String failureReason;
    private boolean requiresManualReview;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferencesDTO {
        private boolean emailNotifications;
        private boolean smsNotifications;
        private boolean pushNotifications;
        private boolean marketingEmails;
        private boolean transactionAlerts;
        private boolean securityAlerts;
        private String preferredLanguage;
        private String preferredChannel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacyPreferencesDTO {
        private boolean profileVisible;
        private boolean dataSharing;
        private boolean analyticsTracking;
        private boolean thirdPartySharing;
        private String dataRetentionPreference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppPreferencesDTO {
        private String theme;
        private String currency;
        private String timezone;
        private String dateFormat;
        private boolean biometricAuth;
        private boolean twoFactorAuth;
        private Map<String, Object> dashboardLayout;
    }
}
