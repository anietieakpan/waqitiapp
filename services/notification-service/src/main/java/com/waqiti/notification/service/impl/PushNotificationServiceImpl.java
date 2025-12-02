package com.waqiti.notification.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.waqiti.notification.domain.*;
import com.waqiti.notification.dto.*;
import com.waqiti.notification.domain.NotificationCategory;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.exception.*;
import com.waqiti.notification.repository.*;
import com.waqiti.notification.domain.NotificationTopic;
import com.waqiti.notification.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PushNotificationServiceImpl implements PushNotificationService {
    
    private final DeviceTokenRepository deviceTokenRepository;
    private final TopicSubscriptionRepository topicSubscriptionRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final PushNotificationLogRepository notificationLogRepository;
    private final NotificationTopicRepository notificationTopicRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${firebase.credentials.path}")
    private String firebaseCredentialsPath;
    
    @Value("${firebase.database.url}")
    private String firebaseDatabaseUrl;
    
    @Value("${push.notification.batch.size:500}")
    private int batchSize;
    
    @Value("${push.notification.retry.attempts:3}")
    private int maxRetryAttempts;
    
    private static final String DEVICE_CACHE_PREFIX = "device:";
    private static final String TOPIC_CACHE_PREFIX = "topic:";
    private static final String PREFERENCES_CACHE_PREFIX = "preferences:";
    
    @PostConstruct
    public void initializeFirebase() {
        try (FileInputStream serviceAccount = new FileInputStream(firebaseCredentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl(firebaseDatabaseUrl)
                    .build();
            
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase", e);
            throw new PushNotificationException("Failed to initialize Firebase", e);
        }
    }
    
    @Override
    public DeviceRegistrationResponse registerDevice(String userId, DeviceRegistrationRequest request) {
        log.debug("Registering device for user: {}, platform: {}", userId, request.getPlatform());
        
        // Check if device already exists
        Optional<DeviceToken> existingDevice = deviceTokenRepository
                .findByUserIdAndDeviceId(userId, request.getDeviceId());
        
        DeviceToken deviceToken;
        if (existingDevice.isPresent()) {
            // Update existing device
            deviceToken = existingDevice.get();
            deviceToken.setToken(request.getToken());
            deviceToken.setPlatform(request.getPlatform());
            deviceToken.setAppVersion(request.getAppVersion());
            deviceToken.setOsVersion(request.getOsVersion());
            deviceToken.setModel(request.getModel());
            deviceToken.setManufacturer(request.getManufacturer());
            deviceToken.setLastUpdated(LocalDateTime.now());
            deviceToken.setActive(true);
        } else {
            // Create new device
            deviceToken = DeviceToken.builder()
                    .userId(userId)
                    .deviceId(request.getDeviceId())
                    .token(request.getToken())
                    .platform(request.getPlatform())
                    .appVersion(request.getAppVersion())
                    .osVersion(request.getOsVersion())
                    .model(request.getModel())
                    .manufacturer(request.getManufacturer())
                    .timezone(request.getTimezone())
                    .language(request.getLanguage())
                    .active(true)
                    .build();
        }
        
        deviceToken = deviceTokenRepository.save(deviceToken);
        
        // Clear cache
        evictDeviceCache(userId);
        
        // Subscribe to default topics
        subscribeToDefaultTopics(deviceToken);
        
        return DeviceRegistrationResponse.builder()
                .deviceId(deviceToken.getDeviceId())
                .userId(userId)
                .registered(true)
                .registeredAt(deviceToken.getCreatedAt())
                .build();
    }
    
    @Override
    public DeviceRegistrationResponse updateDeviceToken(String userId, String deviceId, String newToken) {
        log.debug("Updating device token for device: {}", deviceId);
        
        DeviceToken deviceToken = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found: " + deviceId));
        
        String oldToken = deviceToken.getToken();
        deviceToken.setToken(newToken);
        deviceToken.setLastUpdated(LocalDateTime.now());
        deviceToken = deviceTokenRepository.save(deviceToken);
        
        // Update FCM token mappings
        updateFCMTokenMapping(oldToken, newToken, deviceToken);
        
        // Clear cache
        evictDeviceCache(userId);
        
        return DeviceRegistrationResponse.builder()
                .deviceId(deviceToken.getDeviceId())
                .userId(userId)
                .registered(true)
                .registeredAt(deviceToken.getCreatedAt())
                .build();
    }
    
    @Override
    public void unregisterDevice(String userId, String deviceId) {
        log.info("Unregistering device: {} for user: {}", deviceId, userId);
        
        DeviceToken deviceToken = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found: " + deviceId));
        
        // Mark as inactive instead of deleting
        deviceToken.setActive(false);
        deviceToken.setLastUpdated(LocalDateTime.now());
        deviceTokenRepository.save(deviceToken);
        
        // Unsubscribe from all topics
        unsubscribeFromAllTopics(deviceToken);
        
        // Clear cache
        evictDeviceCache(userId);
    }
    
    @Override
    public TopicSubscriptionResponse subscribeToTopic(String userId, String deviceId, String topic) {
        log.debug("Subscribing user: {} device: {} to topic: {}", userId, deviceId, topic);
        
        // Validate topic name
        validateTopicName(topic);
        
        DeviceToken deviceToken = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found: " + deviceId));
        
        // Check if already subscribed
        Optional<TopicSubscription> existing = topicSubscriptionRepository
                .findByDeviceTokenAndTopic(deviceToken, topic);
        
        if (existing.isPresent() && existing.get().isActive()) {
            return TopicSubscriptionResponse.builder()
                    .topic(topic)
                    .subscribed(true)
                    .subscribedAt(existing.get().getSubscribedAt())
                    .build();
        }
        
        // Subscribe in FCM
        try {
            FirebaseMessaging.getInstance()
                    .subscribeToTopic(Collections.singletonList(deviceToken.getToken()), topic);
            
            // Save subscription
            TopicSubscription subscription = existing.orElse(TopicSubscription.builder()
                    .deviceToken(deviceToken)
                    .topic(topic)
                    .build());
            
            subscription.setActive(true);
            subscription.setSubscribedAt(LocalDateTime.now());
            topicSubscriptionRepository.save(subscription);
            
            // Update topic subscriber count
            updateTopicSubscriberCount(topic, 1);
            
            // Clear cache
            evictTopicCache(userId);
            
            return TopicSubscriptionResponse.builder()
                    .topic(topic)
                    .subscribed(true)
                    .subscribedAt(subscription.getSubscribedAt())
                    .build();
            
        } catch (FirebaseMessagingException e) {
            log.error("Failed to subscribe to topic: {}", topic, e);
            throw new PushNotificationException("Failed to subscribe to topic", e);
        }
    }
    
    @Override
    public void unsubscribeFromTopic(String userId, String deviceId, String topic) {
        log.debug("Unsubscribing user: {} device: {} from topic: {}", userId, deviceId, topic);
        
        DeviceToken deviceToken = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found: " + deviceId));
        
        TopicSubscription subscription = topicSubscriptionRepository
                .findByDeviceTokenAndTopic(deviceToken, topic)
                .orElseThrow(() -> new TopicNotFoundException("Topic subscription not found"));
        
        try {
            // Unsubscribe in FCM
            FirebaseMessaging.getInstance()
                    .unsubscribeFromTopic(Collections.singletonList(deviceToken.getToken()), topic);
            
            // Mark as inactive
            subscription.setActive(false);
            subscription.setUnsubscribedAt(LocalDateTime.now());
            topicSubscriptionRepository.save(subscription);
            
            // Update topic subscriber count
            updateTopicSubscriberCount(topic, -1);
            
            // Clear cache
            evictTopicCache(userId);
            
        } catch (FirebaseMessagingException e) {
            log.error("Failed to unsubscribe from topic: {}", topic, e);
            throw new PushNotificationException("Failed to unsubscribe from topic", e);
        }
    }
    
    @Override
    @Cacheable(value = "userTopics", key = "#userId")
    public UserTopicsResponse getUserTopics(String userId, String deviceId) {
        List<TopicSubscription> subscriptions;
        
        if (deviceId != null) {
            DeviceToken deviceToken = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                    .orElseThrow(() -> new DeviceNotFoundException("Device not found: " + deviceId));
            subscriptions = topicSubscriptionRepository.findActiveByDeviceToken(deviceToken);
        } else {
            List<DeviceToken> userDevices = deviceTokenRepository.findActiveByUserId(userId);
            subscriptions = topicSubscriptionRepository.findActiveByDeviceTokens(userDevices);
        }
        
        Map<String, List<String>> topicsByDevice = subscriptions.stream()
                .collect(Collectors.groupingBy(
                    sub -> sub.getDeviceToken().getDeviceId(),
                    Collectors.mapping(TopicSubscription::getTopic, Collectors.toList())
                ));
        
        Set<String> allTopics = subscriptions.stream()
                .map(TopicSubscription::getTopic)
                .collect(Collectors.toSet());
        
        return UserTopicsResponse.builder()
                .userId(userId)
                .topics(new ArrayList<>(allTopics))
                .topicsByDevice(topicsByDevice)
                .totalTopics(allTopics.size())
                .build();
    }
    
    @Override
    @Cacheable(value = "notificationPreferences", key = "#userId")
    public NotificationPreferencesDto getPreferences(String userId) {
        NotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));
        
        return mapToDto(preferences);
    }
    
    @Override
    @CacheEvict(value = "notificationPreferences", key = "#userId")
    public NotificationPreferencesDto updatePreferences(String userId, UpdatePreferencesRequest request) {
        NotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));
        
        // Update preferences
        if (request.getPushNotificationsEnabled() != null) {
            preferences.setPushNotificationsEnabled(request.getPushNotificationsEnabled());
        }
        if (request.getEmailNotificationsEnabled() != null) {
            preferences.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        if (request.getSmsNotificationsEnabled() != null) {
            preferences.setSmsNotificationsEnabled(request.getSmsNotificationsEnabled());
        }
        if (request.getCategoryPreferences() != null) {
            request.getCategoryPreferences().forEach((category, enabled) -> 
                preferences.setCategoryPreference(category, enabled)
            );
        }
        if (request.getQuietHoursStart() != null && request.getQuietHoursEnd() != null) {
            preferences.setQuietHours(request.getQuietHoursStart(), request.getQuietHoursEnd());
        }
        if (request.getEmail() != null || request.getPhoneNumber() != null || request.getDeviceToken() != null) {
            preferences.updateContactInfo(request.getEmail(), request.getPhoneNumber(), request.getDeviceToken());
        }
        
        preferences = preferencesRepository.save(preferences);
        
        return mapToDto(preferences);
    }
    
    @Override
    @Async
    public CompletableFuture<PushNotificationResponse> sendToUser(String userId, PushSendNotificationRequest request) {
        log.debug("Sending notification to user: {}, type: {}", userId, request.getType());
        
        // Get user's active devices
        List<DeviceToken> devices = deviceTokenRepository.findActiveByUserId(userId);
        
        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", userId);
            return CompletableFuture.completedFuture(PushNotificationResponse.builder()
                    .notificationId(UUID.randomUUID().toString())
                    .status("NO_DEVICES")
                    .sentCount(0)
                    .build());
        }
        
        // Check user preferences
        NotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElse(null);
        
        if (!shouldSendNotification(preferences, request.getType())) {
            log.debug("Notification blocked by user preferences");
            return CompletableFuture.completedFuture(PushNotificationResponse.builder()
                    .notificationId(UUID.randomUUID().toString())
                    .status("BLOCKED_BY_PREFERENCES")
                    .sentCount(0)
                    .build());
        }
        
        // Build and send notification
        String notificationId = UUID.randomUUID().toString();
        List<String> tokens = devices.stream()
                .map(DeviceToken::getToken)
                .collect(Collectors.toList());
        
        MulticastMessage message = buildMulticastMessage(tokens, request, notificationId);
        
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            
            // Log results
            logNotificationResults(notificationId, userId, request, response, devices);
            
            // Handle failed tokens
            handleFailedTokens(response, devices);
            
            return CompletableFuture.completedFuture(PushNotificationResponse.builder()
                    .notificationId(notificationId)
                    .status("SENT")
                    .sentCount(response.getSuccessCount())
                    .failedCount(response.getFailureCount())
                    .build());
            
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send notification to user: {}", userId, e);
            throw new RuntimeException(new PushNotificationException("Failed to send notification", e));
        }
    }
    
    @Override
    @Async
    public CompletableFuture<PushNotificationResponse> sendToTopic(String topic, PushSendNotificationRequest request) {
        log.debug("Sending notification to topic: {}, type: {}", topic, request.getType());
        
        String notificationId = UUID.randomUUID().toString();
        Message message = buildTopicMessage(topic, request, notificationId);
        
        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            
            // Log notification
            PushNotificationLog notificationLog = PushNotificationLog.builder()
                    .notificationId(notificationId)
                    .type(request.getType())
                    .topic(topic)
                    .title(request.getTitle())
                    .body(request.getBody())
                    .data(request.getData())
                    .status("SENT")
                    .fcmMessageId(messageId)
                    .build();
            
            notificationLogRepository.save(notificationLog);
            
            return CompletableFuture.completedFuture(PushNotificationResponse.builder()
                    .notificationId(notificationId)
                    .status("SENT")
                    .messageId(messageId)
                    .build());
            
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send notification to topic: {}", topic, e);
            throw new RuntimeException(new PushNotificationException("Failed to send topic notification", e));
        }
    }
    
    @Override
    @Async
    public CompletableFuture<BulkNotificationResponse> sendBulkNotifications(BulkNotificationRequest request) {
        log.info("Sending bulk notifications to {} users", request.getUserIds().size());
        
        List<CompletableFuture<PushNotificationResponse>> futures = new ArrayList<>();
        
        for (String userId : request.getUserIds()) {
            CompletableFuture<PushNotificationResponse> future = sendToUser(userId, request.getNotification());
            futures.add(future);
        }
        
        // Wait for all to complete with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Batch push notification timed out after 30 seconds for {} users", request.getUserIds().size(), e);
            // Cancel remaining futures
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            log.error("Batch push notification failed", e);
        }

        // Collect results
        int totalSent = 0;
        int totalFailed = 0;
        List<String> failedUsers = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                PushNotificationResponse response = futures.get(i).get(1, java.util.concurrent.TimeUnit.SECONDS);
                totalSent += response.getSentCount();
                totalFailed += response.getFailedCount();

                if (response.getSentCount() == 0) {
                    failedUsers.add(request.getUserIds().get(i));
                }
            } catch (Exception e) {
                totalFailed++;
                failedUsers.add(request.getUserIds().get(i));
            }
        }
        
        return CompletableFuture.completedFuture(BulkNotificationResponse.builder()
                .totalUsers(request.getUserIds().size())
                .successCount(totalSent)
                .failureCount(totalFailed)
                .failedUsers(failedUsers)
                .build());
    }
    
    @Override
    public TestNotificationResponse sendTestNotification(String userId, String deviceId) {
        SendNotificationRequest testRequest = SendNotificationRequest.builder()
                .type("TEST")
                .title("Test Notification")
                .body("This is a test notification from Waqiti")
                .data(Map.of(
                    "type", "test",
                    "timestamp", String.valueOf(System.currentTimeMillis())
                ))
                .priority("high")
                .build();
        
        PushNotificationResponse response;
        if (deviceId != null) {
            // Send to specific device
            DeviceToken device = deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                    .orElseThrow(() -> new DeviceNotFoundException("Device not found"));
            
            response = sendToDevice(device, testRequest);
        } else {
            // Send to all user devices
            try {
                response = sendToUser(userId, testRequest)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Test notification timed out after 10 seconds for user: {}", userId, e);
                response = PushNotificationResponse.builder()
                        .status("TIMEOUT")
                        .sentCount(0)
                        .failedCount(1)
                        .build();
            } catch (Exception e) {
                log.error("Failed to send test notification", e);
                response = PushNotificationResponse.builder()
                        .status("FAILED")
                        .sentCount(0)
                        .failedCount(1)
                        .build();
            }
        }
        
        return TestNotificationResponse.builder()
                .sent(response.getSentCount() > 0)
                .deviceCount(response.getSentCount())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    @Override
    public DeviceStatisticsDto getDeviceStatistics(String userId) {
        List<DeviceToken> devices = deviceTokenRepository.findByUserId(userId);
        
        long activeDevices = devices.stream().filter(DeviceToken::isActive).count();
        Map<String, Long> devicesByPlatform = devices.stream()
                .filter(DeviceToken::isActive)
                .collect(Collectors.groupingBy(DeviceToken::getPlatform, Collectors.counting()));
        
        LocalDateTime lastActivity = devices.stream()
                .map(DeviceToken::getLastUpdated)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        
        return DeviceStatisticsDto.builder()
                .totalDevices(devices.size())
                .activeDevices(activeDevices)
                .devicesByPlatform(devicesByPlatform)
                .lastActivity(lastActivity)
                .build();
    }
    
    @Override
    public SystemPushStatsDto getSystemStatistics() {
        // Get system-wide statistics
        long totalDevices = deviceTokenRepository.count();
        long activeDevices = deviceTokenRepository.countByActive(true);
        
        Map<String, Long> devicesByPlatform = deviceTokenRepository.countByPlatformGrouped();
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        long notificationsSent = notificationLogRepository.countBySentAtAfter(since);
        long successfulNotifications = notificationLogRepository.countByStatusAndSentAtAfter("SENT", since);
        
        double successRate = notificationsSent > 0 ? 
            (double) successfulNotifications / notificationsSent * 100 : 0;
        
        return SystemPushStatsDto.builder()
                .totalDevices(totalDevices)
                .activeDevices(activeDevices)
                .devicesByPlatform(devicesByPlatform)
                .notificationsSentLast7Days(notificationsSent)
                .successRate(successRate)
                .build();
    }
    
    @Override
    public CleanupResponse cleanInvalidTokens() {
        log.info("Starting invalid token cleanup");
        
        LocalDateTime cutoff = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        List<DeviceToken> staleDevices = deviceTokenRepository.findStaleDevices(cutoff);
        
        int cleaned = 0;
        for (DeviceToken device : staleDevices) {
            device.setActive(false);
            device.setInvalidatedAt(LocalDateTime.now());
            deviceTokenRepository.save(device);
            cleaned++;
        }
        
        log.info("Cleaned {} invalid tokens", cleaned);
        
        return CleanupResponse.builder()
                .tokensRemoved(cleaned)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // Helper methods
    
    private void subscribeToDefaultTopics(DeviceToken deviceToken) {
        // Subscribe to system topics
        List<String> systemTopics = Arrays.asList(
            "all_users",
            "platform_" + deviceToken.getPlatform().toLowerCase()
        );
        
        // Subscribe to auto-subscribe topics from the database
        List<NotificationTopic> autoSubscribeTopics = notificationTopicRepository.findByActiveTrueAndAutoSubscribeTrue();
        
        // Combine system topics with auto-subscribe topics
        List<String> allTopics = new ArrayList<>(systemTopics);
        allTopics.addAll(autoSubscribeTopics.stream()
                .map(NotificationTopic::getName)
                .collect(Collectors.toList()));
        
        for (String topic : allTopics) {
            try {
                subscribeToTopic(deviceToken.getUserId(), deviceToken.getDeviceId(), topic);
                log.debug("Subscribed device {} to default topic: {}", deviceToken.getDeviceId(), topic);
            } catch (Exception e) {
                log.warn("Failed to subscribe to default topic: {}", topic, e);
            }
        }
        
        log.info("Subscribed device {} to {} default topics", deviceToken.getDeviceId(), allTopics.size());
    }
    
    private void validateTopicName(String topic) {
        // Check if it's a valid FCM topic name
        if (!topic.matches("^[a-zA-Z][a-zA-Z0-9_-]{0,899}$")) {
            throw new InvalidTopicException("Invalid topic name: " + topic + 
                ". Topic names must start with a letter and contain only letters, numbers, underscores, and hyphens.");
        }
        
        // Check if the topic exists and is active in our system (optional check for managed topics)
        Optional<NotificationTopic> topicEntity = notificationTopicRepository.findByName(topic);
        if (topicEntity.isPresent() && !topicEntity.get().isActive()) {
            throw new InvalidTopicException("Topic is not active: " + topic);
        }
    }
    
    private boolean shouldSendNotification(NotificationPreferences preferences, String type) {
        if (preferences == null || !preferences.isPushNotificationsEnabled()) {
            return false;
        }
        
        // Check quiet hours
        if (preferences.isQuietHours()) {
            return false;
        }
        
        // Check notification type preferences by category
        String category = mapTypeToCategory(type);
        return preferences.shouldSendNotification(category, NotificationType.PUSH);
    }
    
    private String mapTypeToCategory(String type) {
        return switch (type) {
            case "PAYMENT", "MONEY_RECEIVED", "MONEY_SENT" -> "PAYMENT";
            case "SECURITY", "LOGIN", "PASSWORD_CHANGE" -> "SECURITY";
            case "PROMOTION", "OFFER", "REWARD" -> "PROMOTION";
            case "SOCIAL", "FRIEND_REQUEST", "MESSAGE" -> "SOCIAL";
            default -> "GENERAL";
        };
    }
    
    // This method is no longer needed as NotificationPreferences has its own isQuietHours() method
    
    private MulticastMessage buildMulticastMessage(List<String> tokens, PushSendNotificationRequest request, String notificationId) {
        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .setImage(request.getImageUrl())
                .build();
        
        Map<String, String> data = new HashMap<>(request.getData());
        data.put("notificationId", notificationId);
        data.put("type", request.getType());
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                    .setSound(request.getSound() != null ? request.getSound() : "default")
                    .setChannelId(getAndroidChannel(request.getType()))
                    .build())
                .build();
        
        ApnsConfig apnsConfig = ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setSound(request.getSound() != null ? request.getSound() : "default")
                    .setBadge(request.getBadge())
                    .setCategory(request.getType())
                    .build())
                .build();
        
        return MulticastMessage.builder()
                .setNotification(notification)
                .putAllData(data)
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
                .addAllTokens(tokens)
                .build();
    }
    
    private Message buildTopicMessage(String topic, PushSendNotificationRequest request, String notificationId) {
        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .setImage(request.getImageUrl())
                .build();
        
        Map<String, String> data = new HashMap<>(request.getData());
        data.put("notificationId", notificationId);
        data.put("type", request.getType());
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return Message.builder()
                .setNotification(notification)
                .putAllData(data)
                .setTopic(topic)
                .build();
    }
    
    private String getAndroidChannel(String type) {
        return switch (type) {
            case "PAYMENT", "MONEY_RECEIVED", "MONEY_SENT" -> "payment-channel";
            case "SECURITY", "LOGIN", "PASSWORD_CHANGE" -> "security-channel";
            case "PROMOTION", "OFFER", "REWARD" -> "promotion-channel";
            case "SOCIAL", "FRIEND_REQUEST", "MESSAGE" -> "message-channel";
            default -> "default-channel";
        };
    }
    
    private void handleFailedTokens(BatchResponse response, List<DeviceToken> devices) {
        if (response.getFailureCount() > 0) {
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isSuccessful()) {
                    DeviceToken device = devices.get(i);
                    MessagingErrorCode errorCode = responses.get(i).getException().getMessagingErrorCode();
                    
                    if (errorCode == MessagingErrorCode.INVALID_REGISTRATION ||
                        errorCode == MessagingErrorCode.REGISTRATION_TOKEN_NOT_REGISTERED) {
                        // Mark token as invalid
                        device.setActive(false);
                        device.setInvalidatedAt(LocalDateTime.now());
                        deviceTokenRepository.save(device);
                        log.warn("Marked token as invalid for device: {}", device.getDeviceId());
                    }
                }
            }
        }
    }
    
    // Cache eviction methods
    
    @CacheEvict(value = "userDevices", key = "#userId")
    private void evictDeviceCache(String userId) {
        redisTemplate.delete(DEVICE_CACHE_PREFIX + userId);
    }
    
    @CacheEvict(value = "userTopics", key = "#userId")
    private void evictTopicCache(String userId) {
        redisTemplate.delete(TOPIC_CACHE_PREFIX + userId);
    }
    
    private NotificationPreferences createDefaultPreferences(UUID userId) {
        return NotificationPreferences.createDefault(userId);
    }
    
    private NotificationPreferencesDto mapToDto(NotificationPreferences preferences) {
        return NotificationPreferencesDto.builder()
                .userId(preferences.getUserId().toString())
                .pushEnabled(preferences.isPushNotificationsEnabled())
                .emailEnabled(preferences.isEmailNotificationsEnabled())
                .smsEnabled(preferences.isSmsNotificationsEnabled())
                .paymentNotifications(preferences.getCategoryPreferences().getOrDefault("PAYMENT", true))
                .securityAlerts(preferences.getCategoryPreferences().getOrDefault("SECURITY", true))
                .promotionalNotifications(preferences.getCategoryPreferences().getOrDefault("PROMOTION", true))
                .socialNotifications(preferences.getCategoryPreferences().getOrDefault("SOCIAL", true))
                .quietHoursEnabled(preferences.getQuietHoursStart() != null && preferences.getQuietHoursEnd() != null)
                .quietHoursStart(preferences.getQuietHoursStart() != null ? preferences.getQuietHoursStart() + ":00" : "22:00")
                .quietHoursEnd(preferences.getQuietHoursEnd() != null ? preferences.getQuietHoursEnd() + ":00" : "07:00")
                .lastUpdated(preferences.getUpdatedAt())
                .build();
    }
    
    private void logNotificationResults(String notificationId, String userId, 
                                       PushSendNotificationRequest request, BatchResponse response,
                                       List<DeviceToken> devices) {
        PushNotificationLog log = PushNotificationLog.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(request.getType())
                .title(request.getTitle())
                .body(request.getBody())
                .data(request.getData())
                .status(response.getSuccessCount() > 0 ? "SENT" : "FAILED")
                .sentCount(response.getSuccessCount())
                .failedCount(response.getFailureCount())
                .deviceCount(devices.size())
                .build();
        
        notificationLogRepository.save(log);
    }
    
    private PushNotificationResponse sendToDevice(DeviceToken device, PushSendNotificationRequest request) {
        Message message = Message.builder()
                .setToken(device.getToken())
                .setNotification(Notification.builder()
                    .setTitle(request.getTitle())
                    .setBody(request.getBody())
                    .build())
                .putAllData(request.getData())
                .build();
        
        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            return PushNotificationResponse.builder()
                    .notificationId(UUID.randomUUID().toString())
                    .status("SENT")
                    .sentCount(1)
                    .messageId(messageId)
                    .build();
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send to device: {}", device.getDeviceId(), e);
            return PushNotificationResponse.builder()
                    .notificationId(UUID.randomUUID().toString())
                    .status("FAILED")
                    .sentCount(0)
                    .failedCount(1)
                    .build();
        }
    }
    
    private void unsubscribeFromAllTopics(DeviceToken deviceToken) {
        List<TopicSubscription> subscriptions = topicSubscriptionRepository
                .findActiveByDeviceToken(deviceToken);
        
        for (TopicSubscription subscription : subscriptions) {
            try {
                unsubscribeFromTopic(deviceToken.getUserId(), 
                                   deviceToken.getDeviceId(), 
                                   subscription.getTopic());
            } catch (Exception e) {
                log.warn("Failed to unsubscribe from topic: {}", subscription.getTopic(), e);
            }
        }
    }
    
    private void updateFCMTokenMapping(String oldToken, String newToken, DeviceToken device) {
        // Get all topic subscriptions for this device
        List<TopicSubscription> subscriptions = topicSubscriptionRepository
                .findActiveByDeviceToken(device);
        
        // Unsubscribe old token and subscribe new token to all topics
        for (TopicSubscription subscription : subscriptions) {
            try {
                FirebaseMessaging.getInstance()
                        .unsubscribeFromTopic(Collections.singletonList(oldToken), 
                                            subscription.getTopic());
                
                FirebaseMessaging.getInstance()
                        .subscribeToTopic(Collections.singletonList(newToken), 
                                        subscription.getTopic());
            } catch (FirebaseMessagingException e) {
                log.warn("Failed to update token mapping for topic: {}", 
                        subscription.getTopic(), e);
            }
        }
    }
    
    private void updateTopicSubscriberCount(String topicName, long increment) {
        try {
            if (increment > 0) {
                notificationTopicRepository.incrementSubscriberCount(topicName, increment);
            } else if (increment < 0) {
                notificationTopicRepository.decrementSubscriberCount(topicName, Math.abs(increment));
            }
        } catch (Exception e) {
            log.warn("Failed to update subscriber count for topic: {}", topicName, e);
        }
    }
}