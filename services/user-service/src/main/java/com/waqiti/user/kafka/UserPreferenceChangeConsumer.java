package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.event.UserPreferenceChangeEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.UserPreferenceService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.UserActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Production-grade Kafka consumer for user preference change events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPreferenceChangeConsumer {

    private final UserService userService;
    private final UserPreferenceService preferenceService;
    private final NotificationService notificationService;
    private final UserActivityLogService activityLogService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "user-preference-changes", groupId = "preference-processor")
    public void processPreferenceChange(@Payload UserPreferenceChangeEvent event,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment) {
        try {
            log.info("Processing preference change for user: {} category: {} key: {}", 
                    event.getUserId(), event.getPreferenceCategory(), event.getPreferenceKey());
            
            // Validate event
            validatePreferenceChangeEvent(event);
            
            // Process preference based on category
            switch (event.getPreferenceCategory()) {
                case "NOTIFICATION" -> handleNotificationPreference(event);
                case "PRIVACY" -> handlePrivacyPreference(event);
                case "DISPLAY" -> handleDisplayPreference(event);
                case "LANGUAGE" -> handleLanguagePreference(event);
                case "SECURITY" -> handleSecurityPreference(event);
                case "PAYMENT" -> handlePaymentPreference(event);
                case "COMMUNICATION" -> handleCommunicationPreference(event);
                case "ACCESSIBILITY" -> handleAccessibilityPreference(event);
                default -> handleGenericPreference(event);
            }
            
            // Sync preferences across devices if enabled
            if (event.isSyncAcrossDevices()) {
                preferenceService.syncPreferencesAcrossDevices(
                    event.getUserId(),
                    event.getPreferenceCategory(),
                    event.getPreferenceKey(),
                    event.getNewValue()
                );
            }
            
            // Apply dependent preferences
            applyDependentPreferences(event);
            
            // Send confirmation if significant change
            if (event.isRequiresConfirmation()) {
                sendPreferenceChangeConfirmation(event);
            }
            
            // Log preference change activity
            activityLogService.logPreferenceChange(
                event.getUserId(),
                event.getPreferenceCategory(),
                event.getPreferenceKey(),
                event.getPreviousValue(),
                event.getNewValue(),
                event.getChangedBy(),
                event.getChangedAt()
            );
            
            // Track preference analytics
            preferenceService.trackPreferenceAnalytics(
                event.getUserId(),
                event.getPreferenceCategory(),
                event.getPreferenceKey(),
                event.getNewValue()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed preference change for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process preference change for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Preference change processing failed", e);
        }
    }

    private void validatePreferenceChangeEvent(UserPreferenceChangeEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for preference change");
        }
        
        if (event.getPreferenceCategory() == null || event.getPreferenceCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Preference category is required");
        }
        
        if (event.getPreferenceKey() == null || event.getPreferenceKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Preference key is required");
        }
        
        if (event.getNewValue() == null) {
            throw new IllegalArgumentException("New value is required for preference change");
        }
    }

    private void handleNotificationPreference(UserPreferenceChangeEvent event) {
        // Update notification settings
        preferenceService.updateNotificationPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Handle specific notification preferences
        switch (event.getPreferenceKey()) {
            case "email_notifications" -> {
                if ("false".equals(event.getNewValue())) {
                    notificationService.unsubscribeFromEmails(event.getUserId());
                } else {
                    notificationService.resubscribeToEmails(event.getUserId());
                }
            }
            case "push_notifications" -> {
                if ("false".equals(event.getNewValue())) {
                    userService.disablePushNotifications(event.getUserId());
                } else {
                    userService.enablePushNotifications(event.getUserId());
                }
            }
            case "sms_notifications" -> {
                if ("false".equals(event.getNewValue())) {
                    notificationService.disableSmsNotifications(event.getUserId());
                }
            }
            case "do_not_disturb" -> {
                if ("true".equals(event.getNewValue())) {
                    Map<String, Object> dndSettings = event.getMetadata();
                    preferenceService.enableDoNotDisturb(
                        event.getUserId(),
                        (String) dndSettings.get("startTime"),
                        (String) dndSettings.get("endTime"),
                        (String) dndSettings.get("timezone")
                    );
                }
            }
            case "notification_frequency" -> {
                preferenceService.updateNotificationFrequency(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
        }
    }

    private void handlePrivacyPreference(UserPreferenceChangeEvent event) {
        // Update privacy settings
        preferenceService.updatePrivacyPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Handle specific privacy preferences
        switch (event.getPreferenceKey()) {
            case "profile_visibility" -> {
                userService.updateProfileVisibility(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
            case "data_sharing" -> {
                if ("false".equals(event.getNewValue())) {
                    userService.optOutOfDataSharing(event.getUserId());
                }
            }
            case "activity_tracking" -> {
                if ("false".equals(event.getNewValue())) {
                    activityLogService.disableActivityTracking(event.getUserId());
                }
            }
            case "search_history" -> {
                if ("disabled".equals(event.getNewValue())) {
                    userService.clearSearchHistory(event.getUserId());
                }
            }
            case "location_sharing" -> {
                userService.updateLocationSharing(
                    event.getUserId(),
                    "true".equals(event.getNewValue())
                );
            }
        }
    }

    private void handleDisplayPreference(UserPreferenceChangeEvent event) {
        // Update display settings
        preferenceService.updateDisplayPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Handle specific display preferences
        switch (event.getPreferenceKey()) {
            case "theme" -> {
                userService.updateTheme(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
            case "font_size" -> {
                userService.updateFontSize(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
            case "date_format" -> {
                preferenceService.updateDateFormat(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
            case "currency_display" -> {
                preferenceService.updateCurrencyDisplay(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
            case "dashboard_layout" -> {
                userService.updateDashboardLayout(
                    event.getUserId(),
                    event.getNewValue(),
                    event.getMetadata()
                );
            }
        }
    }

    private void handleLanguagePreference(UserPreferenceChangeEvent event) {
        // Update language settings
        String previousLanguage = event.getPreviousValue();
        String newLanguage = event.getNewValue();
        
        userService.updateLanguage(
            event.getUserId(),
            newLanguage
        );
        
        // Update all language-dependent settings
        preferenceService.updateLanguageDependentSettings(
            event.getUserId(),
            newLanguage
        );
        
        // Send confirmation in new language
        notificationService.sendLanguageChangeConfirmation(
            event.getUserId(),
            previousLanguage,
            newLanguage
        );
    }

    private void handleSecurityPreference(UserPreferenceChangeEvent event) {
        // Update security settings
        preferenceService.updateSecurityPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Handle specific security preferences
        switch (event.getPreferenceKey()) {
            case "two_factor_auth" -> {
                if ("true".equals(event.getNewValue())) {
                    userService.enableTwoFactorAuth(
                        event.getUserId(),
                        (String) event.getMetadata().get("method")
                    );
                } else {
                    userService.disableTwoFactorAuth(event.getUserId());
                }
            }
            case "biometric_auth" -> {
                userService.updateBiometricAuth(
                    event.getUserId(),
                    "true".equals(event.getNewValue())
                );
            }
            case "session_timeout" -> {
                userService.updateSessionTimeout(
                    event.getUserId(),
                    Integer.parseInt(event.getNewValue())
                );
            }
            case "login_alerts" -> {
                preferenceService.updateLoginAlerts(
                    event.getUserId(),
                    "true".equals(event.getNewValue())
                );
            }
            case "device_management" -> {
                if ("strict".equals(event.getNewValue())) {
                    userService.enableStrictDeviceManagement(event.getUserId());
                }
            }
        }
        
        // Log security preference change
        activityLogService.logSecurityPreferenceChange(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue(),
            event.getChangedBy()
        );
    }

    private void handlePaymentPreference(UserPreferenceChangeEvent event) {
        // Update payment preferences
        preferenceService.updatePaymentPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Handle specific payment preferences
        switch (event.getPreferenceKey()) {
            case "default_payment_method" -> {
                userService.updateDefaultPaymentMethod(
                    event.getUserId(),
                    event.getNewValue()
                );
            }
            case "auto_reload" -> {
                if ("true".equals(event.getNewValue())) {
                    Map<String, Object> reloadSettings = event.getMetadata();
                    userService.enableAutoReload(
                        event.getUserId(),
                        (String) reloadSettings.get("amount"),
                        (String) reloadSettings.get("threshold")
                    );
                }
            }
            case "spending_limit" -> {
                userService.updateSpendingLimit(
                    event.getUserId(),
                    event.getNewValue(),
                    (String) event.getMetadata().get("period")
                );
            }
            case "transaction_notifications" -> {
                preferenceService.updateTransactionNotifications(
                    event.getUserId(),
                    event.getNewValue(),
                    event.getMetadata()
                );
            }
        }
    }

    private void handleCommunicationPreference(UserPreferenceChangeEvent event) {
        // Update communication preferences
        preferenceService.updateCommunicationPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Handle marketing preferences
        if ("marketing_emails".equals(event.getPreferenceKey())) {
            if ("false".equals(event.getNewValue())) {
                notificationService.unsubscribeFromMarketing(event.getUserId());
            }
        }
        
        // Handle newsletter preferences
        if ("newsletter".equals(event.getPreferenceKey())) {
            notificationService.updateNewsletterSubscription(
                event.getUserId(),
                event.getNewValue(),
                event.getMetadata()
            );
        }
    }

    private void handleAccessibilityPreference(UserPreferenceChangeEvent event) {
        // Update accessibility settings
        preferenceService.updateAccessibilityPreference(
            event.getUserId(),
            event.getPreferenceKey(),
            event.getNewValue()
        );
        
        // Apply accessibility features
        switch (event.getPreferenceKey()) {
            case "screen_reader" -> {
                userService.updateScreenReaderMode(
                    event.getUserId(),
                    "true".equals(event.getNewValue())
                );
            }
            case "high_contrast" -> {
                userService.updateHighContrastMode(
                    event.getUserId(),
                    "true".equals(event.getNewValue())
                );
            }
            case "keyboard_navigation" -> {
                userService.updateKeyboardNavigation(
                    event.getUserId(),
                    "true".equals(event.getNewValue())
                );
            }
        }
    }

    private void handleGenericPreference(UserPreferenceChangeEvent event) {
        // Store generic preference
        preferenceService.updateUserPreference(
            event.getUserId(),
            event.getPreferenceCategory(),
            event.getPreferenceKey(),
            event.getNewValue(),
            event.getMetadata()
        );
    }

    private void applyDependentPreferences(UserPreferenceChangeEvent event) {
        // Get dependent preferences
        Map<String, String> dependentPrefs = preferenceService.getDependentPreferences(
            event.getPreferenceCategory(),
            event.getPreferenceKey()
        );
        
        // Apply each dependent preference
        for (Map.Entry<String, String> entry : dependentPrefs.entrySet()) {
            preferenceService.updateUserPreference(
                event.getUserId(),
                event.getPreferenceCategory(),
                entry.getKey(),
                entry.getValue(),
                null
            );
        }
    }

    private void sendPreferenceChangeConfirmation(UserPreferenceChangeEvent event) {
        // Send confirmation based on preference importance
        if ("SECURITY".equals(event.getPreferenceCategory()) || 
            "PRIVACY".equals(event.getPreferenceCategory())) {
            
            notificationService.sendSecurityPreferenceChangeAlert(
                event.getUserId(),
                event.getPreferenceCategory(),
                event.getPreferenceKey(),
                event.getPreviousValue(),
                event.getNewValue(),
                event.getChangedAt(),
                event.getDeviceInfo()
            );
        } else {
            notificationService.sendPreferenceChangeConfirmation(
                event.getUserId(),
                event.getPreferenceCategory(),
                event.getPreferenceKey(),
                event.getNewValue()
            );
        }
    }
}