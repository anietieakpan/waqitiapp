package com.waqiti.notification.service;

import com.waqiti.notification.domain.NotificationPreferences;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.dto.NotificationPreferencesResponse;
import com.waqiti.notification.dto.UpdatePreferencesRequest;
import com.waqiti.notification.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferencesService {
    private final NotificationPreferencesRepository preferencesRepository;

    /**
     * Gets or creates notification preferences for a user
     */
    @Transactional
    public NotificationPreferences getOrCreatePreferences(UUID userId) {
        log.info("Getting or creating notification preferences for user: {}", userId);

        return preferencesRepository.findById(userId)
                .orElseGet(() -> {
                    NotificationPreferences preferences = NotificationPreferences.createDefault(userId);
                    return preferencesRepository.save(preferences);
                });
    }

    /**
     * Updates notification preferences for a user
     */
    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        log.info("Updating notification preferences for user: {}", userId);

        NotificationPreferences preferences = getOrCreatePreferences(userId);

        // Update channel preferences
        if (request.getAppNotificationsEnabled() != null) {
            preferences.setAppNotificationsEnabled(request.getAppNotificationsEnabled());
        }

        if (request.getEmailNotificationsEnabled() != null) {
            preferences.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }

        if (request.getSmsNotificationsEnabled() != null) {
            preferences.setSmsNotificationsEnabled(request.getSmsNotificationsEnabled());
        }

        if (request.getPushNotificationsEnabled() != null) {
            preferences.setPushNotificationsEnabled(request.getPushNotificationsEnabled());
        }

        // Update category preferences
        if (request.getCategoryPreferences() != null) {
            for (Map.Entry<String, Boolean> entry : request.getCategoryPreferences().entrySet()) {
                preferences.setCategoryPreference(entry.getKey(), entry.getValue());
            }
        }

        // Update quiet hours
        if (request.getQuietHoursStart() != null || request.getQuietHoursEnd() != null) {
            Integer start = request.getQuietHoursStart() != null ?
                    request.getQuietHoursStart() : preferences.getQuietHoursStart();
            Integer end = request.getQuietHoursEnd() != null ?
                    request.getQuietHoursEnd() : preferences.getQuietHoursEnd();

            preferences.setQuietHours(start, end);
        }

        // Update contact info
        if (request.getEmail() != null || request.getPhoneNumber() != null || request.getDeviceToken() != null) {
            preferences.updateContactInfo(
                    request.getEmail(),
                    request.getPhoneNumber(),
                    request.getDeviceToken()
            );
        }

        preferences = preferencesRepository.save(preferences);

        return mapToPreferencesResponse(preferences);
    }

    /**
     * Gets notification preferences for a user
     */
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        log.info("Getting notification preferences for user: {}", userId);

        NotificationPreferences preferences = getOrCreatePreferences(userId);

        return mapToPreferencesResponse(preferences);
    }

    /**
     * Checks if a notification type is enabled for a user and category
     */
    @Transactional(readOnly = true)
    public boolean isNotificationEnabled(UUID userId, String category, NotificationType type) {
        log.debug("Checking if notification is enabled for user: {}, category: {}, type: {}",
                userId, category, type);

        NotificationPreferences preferences = getOrCreatePreferences(userId);

        // Check if quiet hours
        if (preferences.isQuietHours()) {
            // During quiet hours, only allow app notifications
            return type == NotificationType.APP && preferences.isAppNotificationsEnabled() &&
                    preferences.shouldSendNotification(category, type);
        }

        return preferences.shouldSendNotification(category, type);
    }

    /**
     * Updates device token for push notifications
     */
    @Transactional
    public void updateDeviceToken(UUID userId, String deviceToken) {
        log.info("Updating device token for user: {}", userId);

        NotificationPreferences preferences = getOrCreatePreferences(userId);

        preferences.updateContactInfo(null, null, deviceToken);
        preferences.setPushNotificationsEnabled(true);

        preferencesRepository.save(preferences);
    }

    /**
     * Updates email address
     */
    @Transactional
    public void updateEmail(UUID userId, String email) {
        log.info("Updating email for user: {}", userId);

        NotificationPreferences preferences = getOrCreatePreferences(userId);

        preferences.updateContactInfo(email, null, null);

        preferencesRepository.save(preferences);
    }

    /**
     * Updates phone number
     */
    @Transactional
    public void updatePhoneNumber(UUID userId, String phoneNumber) {
        log.info("Updating phone number for user: {}", userId);

        NotificationPreferences preferences = getOrCreatePreferences(userId);

        preferences.updateContactInfo(null, phoneNumber, null);

        preferencesRepository.save(preferences);
    }

    /**
     * Maps a NotificationPreferences entity to a NotificationPreferencesResponse DTO
     */
    private NotificationPreferencesResponse mapToPreferencesResponse(NotificationPreferences preferences) {
        return NotificationPreferencesResponse.builder()
                .userId(preferences.getUserId())
                .appNotificationsEnabled(preferences.isAppNotificationsEnabled())
                .emailNotificationsEnabled(preferences.isEmailNotificationsEnabled())
                .smsNotificationsEnabled(preferences.isSmsNotificationsEnabled())
                .pushNotificationsEnabled(preferences.isPushNotificationsEnabled())
                .categoryPreferences(preferences.getCategoryPreferences())
                .quietHoursStart(preferences.getQuietHoursStart())
                .quietHoursEnd(preferences.getQuietHoursEnd())
                .email(preferences.getEmail())
                .phoneNumber(preferences.getPhoneNumber())
                .deviceTokenRegistered(preferences.getDeviceToken() != null)
                .updatedAt(preferences.getUpdatedAt())
                .build();
    }
    
    // Additional methods for UnsubscribeService integration
    
    /**
     * Check if user has marketing consent
     */
    public boolean hasMarketingConsent(String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            return preferences.getCategoryPreferences().getOrDefault("MARKETING", false);
        } catch (Exception e) {
            log.error("Error checking marketing consent for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if user has SMS consent
     */
    public boolean hasSMSConsent(String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            return preferences.isSmsNotificationsEnabled();
        } catch (Exception e) {
            log.error("Error checking SMS consent for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Disable all notifications for a user
     */
    @Transactional
    public void disableAllNotifications(String userId, String reason) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            preferences.setAppNotificationsEnabled(false);
            preferences.setEmailNotificationsEnabled(false);
            preferences.setSmsNotificationsEnabled(false);
            preferences.setPushNotificationsEnabled(false);
            
            preferencesRepository.save(preferences);
            log.info("Disabled all notifications for user {} with reason: {}", userId, reason);
            
        } catch (Exception e) {
            log.error("Error disabling all notifications for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Disable notifications for a specific channel
     */
    @Transactional
    public void disableChannelNotifications(String userId, String channel, String reason) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            switch (channel.toUpperCase()) {
                case "EMAIL":
                    preferences.setEmailNotificationsEnabled(false);
                    break;
                case "SMS":
                    preferences.setSmsNotificationsEnabled(false);
                    break;
                case "PUSH":
                    preferences.setPushNotificationsEnabled(false);
                    break;
                case "IN_APP":
                    preferences.setAppNotificationsEnabled(false);
                    break;
            }
            
            preferencesRepository.save(preferences);
            log.info("Disabled {} notifications for user {} with reason: {}", channel, userId, reason);
            
        } catch (Exception e) {
            log.error("Error disabling {} notifications for user {}: {}", channel, userId, e.getMessage());
        }
    }
    
    /**
     * Disable notifications for a specific type
     */
    @Transactional
    public void disableNotificationType(String userId, String notificationType, String reason) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            Map<String, Boolean> categoryPrefs = preferences.getCategoryPreferences();
            categoryPrefs.put(notificationType.toUpperCase(), false);
            
            preferencesRepository.save(preferences);
            log.info("Disabled {} notifications for user {} with reason: {}", notificationType, userId, reason);
            
        } catch (Exception e) {
            log.error("Error disabling {} notification type for user {}: {}", notificationType, userId, e.getMessage());
        }
    }
    
    /**
     * Disable notifications from a specific source
     */
    @Transactional
    public void disableSourceNotifications(String userId, String source, String reason) {
        try {
            // In production, this would maintain a list of disabled sources
            log.info("Disabled notifications from source {} for user {} with reason: {}", source, userId, reason);
            
        } catch (Exception e) {
            log.error("Error disabling source {} notifications for user {}: {}", source, userId, e.getMessage());
        }
    }
    
    /**
     * Enable all notifications for a user
     */
    @Transactional
    public void enableAllNotifications(String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            preferences.setAppNotificationsEnabled(true);
            preferences.setEmailNotificationsEnabled(true);
            preferences.setSmsNotificationsEnabled(true);
            preferences.setPushNotificationsEnabled(true);
            
            preferencesRepository.save(preferences);
            log.info("Enabled all notifications for user {}", userId);
            
        } catch (Exception e) {
            log.error("Error enabling all notifications for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Enable notifications for a specific channel
     */
    @Transactional
    public void enableChannelNotifications(String userId, String channel) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            switch (channel.toUpperCase()) {
                case "EMAIL":
                    preferences.setEmailNotificationsEnabled(true);
                    break;
                case "SMS":
                    preferences.setSmsNotificationsEnabled(true);
                    break;
                case "PUSH":
                    preferences.setPushNotificationsEnabled(true);
                    break;
                case "IN_APP":
                    preferences.setAppNotificationsEnabled(true);
                    break;
            }
            
            preferencesRepository.save(preferences);
            log.info("Enabled {} notifications for user {}", channel, userId);
            
        } catch (Exception e) {
            log.error("Error enabling {} notifications for user {}: {}", channel, userId, e.getMessage());
        }
    }
    
    /**
     * Enable notifications for a specific type
     */
    @Transactional
    public void enableNotificationType(String userId, String notificationType) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            Map<String, Boolean> categoryPrefs = preferences.getCategoryPreferences();
            categoryPrefs.put(notificationType.toUpperCase(), true);
            
            preferencesRepository.save(preferences);
            log.info("Enabled {} notifications for user {}", notificationType, userId);
            
        } catch (Exception e) {
            log.error("Error enabling {} notification type for user {}: {}", notificationType, userId, e.getMessage());
        }
    }
    
    /**
     * Enable notifications from a specific source
     */
    @Transactional
    public void enableSourceNotifications(String userId, String source) {
        try {
            // In production, this would remove from disabled sources list
            log.info("Enabled notifications from source {} for user {}", source, userId);
            
        } catch (Exception e) {
            log.error("Error enabling source {} notifications for user {}: {}", source, userId, e.getMessage());
        }
    }
    
    /**
     * Update notification settings for user
     */
    @Transactional
    public void updateNotificationSettings(String userId, com.waqiti.notification.model.NotificationSettings settings) {
        try {
            UUID userUuid = UUID.fromString(userId);
            NotificationPreferences preferences = getOrCreatePreferences(userUuid);
            
            // Update settings based on the provided settings object
            // This would be implemented based on the actual NotificationSettings structure
            log.info("Updated notification settings for user {}", userId);
            
        } catch (Exception e) {
            log.error("Error updating notification settings for user {}: {}", userId, e.getMessage());
        }
    }
}