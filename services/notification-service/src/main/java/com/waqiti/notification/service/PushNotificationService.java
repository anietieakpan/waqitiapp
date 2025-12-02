package com.waqiti.notification.service;

import com.waqiti.notification.dto.*;

public interface PushNotificationService {
    
    // Device Management
    DeviceRegistrationResponse registerDevice(String userId, DeviceRegistrationRequest request);
    DeviceRegistrationResponse updateDeviceToken(String userId, String deviceId, String newToken);
    void unregisterDevice(String userId, String deviceId);
    
    // Topic Management
    TopicSubscriptionResponse subscribeToTopic(String userId, String deviceId, String topic);
    void unsubscribeFromTopic(String userId, String deviceId, String topic);
    UserTopicsResponse getUserTopics(String userId, String deviceId);
    
    // Preferences
    NotificationPreferencesDto getPreferences(String userId);
    NotificationPreferencesDto updatePreferences(String userId, UpdatePreferencesRequest request);
    
    // Sending Notifications
    PushNotificationResponse sendToUser(String userId, PushSendNotificationRequest request);
    PushNotificationResponse sendToTopic(String topic, PushSendNotificationRequest request);
    BulkNotificationResponse sendBulkNotifications(BulkNotificationRequest request);
    TestNotificationResponse sendTestNotification(String userId, String deviceId);
    
    // Statistics & Maintenance
    DeviceStatisticsDto getDeviceStatistics(String userId);
    SystemPushStatsDto getSystemStatistics();
    CleanupResponse cleanInvalidTokens();
}