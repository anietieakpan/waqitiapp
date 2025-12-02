package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for user preference changes
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPreferenceChangeEvent extends UserEvent {
    
    private String preferenceCategory; // NOTIFICATIONS, PRIVACY, DISPLAY, COMMUNICATION, SECURITY
    private String preferenceKey;
    private String previousValue;
    private String newValue;
    private String changeReason;
    private String changedBy; // USER, ADMIN, SYSTEM
    private LocalDateTime changedAt;
    private String ipAddress;
    private String deviceId;
    private Map<String, Object> allPreferences;
    private boolean requiresConfirmation;
    private boolean confirmed;
    private String confirmationMethod;
    private LocalDateTime confirmationTime;
    private boolean affectsCompliance;
    private String complianceImpact;
    
    public UserPreferenceChangeEvent() {
        super("USER_PREFERENCE_CHANGE");
    }
    
    public static UserPreferenceChangeEvent notificationPreference(String userId, String key, String previousValue, 
                                                                 String newValue, String ipAddress) {
        UserPreferenceChangeEvent event = new UserPreferenceChangeEvent();
        event.setUserId(userId);
        event.setPreferenceCategory("NOTIFICATIONS");
        event.setPreferenceKey(key);
        event.setPreviousValue(previousValue);
        event.setNewValue(newValue);
        event.setChangedBy("USER");
        event.setIpAddress(ipAddress);
        event.setChangedAt(LocalDateTime.now());
        return event;
    }
    
    public static UserPreferenceChangeEvent privacyPreference(String userId, String key, String previousValue, 
                                                            String newValue, boolean requiresConfirmation) {
        UserPreferenceChangeEvent event = new UserPreferenceChangeEvent();
        event.setUserId(userId);
        event.setPreferenceCategory("PRIVACY");
        event.setPreferenceKey(key);
        event.setPreviousValue(previousValue);
        event.setNewValue(newValue);
        event.setChangedBy("USER");
        event.setRequiresConfirmation(requiresConfirmation);
        event.setChangedAt(LocalDateTime.now());
        return event;
    }
    
    public static UserPreferenceChangeEvent securityPreference(String userId, String key, String previousValue, 
                                                             String newValue, String deviceId) {
        UserPreferenceChangeEvent event = new UserPreferenceChangeEvent();
        event.setUserId(userId);
        event.setPreferenceCategory("SECURITY");
        event.setPreferenceKey(key);
        event.setPreviousValue(previousValue);
        event.setNewValue(newValue);
        event.setChangedBy("USER");
        event.setDeviceId(deviceId);
        event.setRequiresConfirmation(true);
        event.setChangedAt(LocalDateTime.now());
        return event;
    }
}