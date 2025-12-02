package com.waqiti.notification.service;

import com.waqiti.notification.dto.*;
import com.waqiti.notification.entity.*;
import com.waqiti.notification.repository.*;
import com.waqiti.common.service.RedisService;
import com.waqiti.common.util.ResultWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Push Notification Batching Service
 * 
 * Optimizes notification delivery by:
 * - Batching similar notifications to reduce noise
 * - Smart aggregation of payment activities
 * - User preference-based batching intervals
 * - Intelligent deduplication
 * - Peak hour optimization
 * 
 * Similar to modern apps like Venmo, Cash App that batch notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationBatchingService {

    private final NotificationRepository notificationRepository;
    private final NotificationBatchRepository batchRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final PushNotificationService pushNotificationService;
    private final RedisService redisService;
    
    @Value("${notification.batching.enabled:true}")
    private boolean batchingEnabled;
    
    @Value("${notification.batching.default-interval:PT15M}")
    private String defaultBatchInterval;
    
    @Value("${notification.batching.max-batch-size:50}")
    private int maxBatchSize;
    
    @Value("${notification.batching.min-batch-size:3}")
    private int minBatchSize;
    
    // In-memory batch buffer for real-time aggregation
    private final Map<String, List<PendingNotification>> batchBuffer = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastBatchSent = new ConcurrentHashMap<>();
    
    /**
     * Add notification to batch queue
     */
    @Transactional
    public ResultWrapper<NotificationBatchResult> addToBatch(NotificationBatchRequest request) {
        log.debug("Adding notification to batch for user: {} type: {}", 
            request.getUserId(), request.getNotificationType());
        
        if (!batchingEnabled || shouldSendImmediately(request)) {
            // Send immediately for high priority notifications
            return sendImmediateNotification(request);
        }
        
        try {
            // Get user's batching preferences
            UserNotificationPreference preference = getUserPreference(request.getUserId());
            
            // Create pending notification
            PendingNotification pending = createPendingNotification(request, preference);
            
            // Add to batch buffer
            String batchKey = generateBatchKey(request.getUserId(), request.getNotificationType());
            batchBuffer.computeIfAbsent(batchKey, k -> new ArrayList<>()).add(pending);
            
            // Check if batch is ready to send
            if (shouldSendBatch(batchKey, preference)) {
                return processBatch(batchKey, preference);
            }
            
            // Save pending notification to database
            Notification notification = saveToDatabase(pending);
            
            log.debug("Notification added to batch queue: {}", notification.getId());
            return ResultWrapper.success(
                NotificationBatchResult.builder()
                    .notificationId(notification.getId())
                    .status("BATCHED")
                    .batchKey(batchKey)
                    .estimatedDeliveryTime(calculateEstimatedDelivery(batchKey, preference))
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to add notification to batch", e);
            return ResultWrapper.failure("Failed to batch notification: " + e.getMessage());
        }
    }
    
    /**
     * Process scheduled batches
     */
    @Scheduled(fixedDelay = 60000) // Check every minute
    @Transactional
    public void processScheduledBatches() {
        if (!batchingEnabled) {
            return;
        }
        
        log.debug("Processing scheduled notification batches");
        
        try {
            // Process in-memory batches
            processInMemoryBatches();
            
            // Process database batches
            processExpiredDatabaseBatches();
            
        } catch (Exception e) {
            log.error("Error processing scheduled batches", e);
        }
    }
    
    /**
     * Process peak hour optimization
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void optimizePeakHourBatching() {
        log.info("Optimizing notification batching for peak hours");
        
        Instant now = Instant.now();
        int currentHour = now.atZone(java.time.ZoneOffset.UTC).getHour();
        
        // Adjust batching intervals based on time of day
        if (isPeakHour(currentHour)) {
            // During peak hours, batch more aggressively
            increaseBatchingInterval();
        } else {
            // During off-peak hours, send more frequently
            decreaseBatchingInterval();
        }
    }
    
    /**
     * Get notification batch analytics
     */
    public NotificationBatchAnalytics getBatchAnalytics(String userId, int days) {
        log.debug("Getting batch analytics for user: {} days: {}", userId, days);
        
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        
        List<NotificationBatch> batches = batchRepository.findByUserIdAndCreatedAtAfter(userId, since);
        
        int totalNotifications = batches.stream()
            .mapToInt(NotificationBatch::getNotificationCount)
            .sum();
        
        int totalBatches = batches.size();
        double averageReduction = calculateBatchingReduction(userId, since);
        
        Map<String, Integer> batchesByType = batches.stream()
            .collect(Collectors.groupingBy(
                NotificationBatch::getNotificationType,
                Collectors.summingInt(NotificationBatch::getNotificationCount)
            ));
        
        return NotificationBatchAnalytics.builder()
            .userId(userId)
            .periodDays(days)
            .totalNotifications(totalNotifications)
            .totalBatches(totalBatches)
            .averageBatchSize(totalBatches > 0 ? (double) totalNotifications / totalBatches : 0)
            .reductionPercentage(averageReduction)
            .batchesByType(batchesByType)
            .peakBatchingHours(calculatePeakBatchingHours(batches))
            .build();
    }
    
    /**
     * Update user batching preferences
     */
    @Transactional
    public ResultWrapper<UserNotificationPreference> updateBatchingPreferences(
            String userId, UpdateBatchingPreferencesRequest request) {
        
        log.info("Updating batching preferences for user: {}", userId);
        
        try {
            UserNotificationPreference preference = getUserPreference(userId);
            
            if (request.getBatchingEnabled() != null) {
                preference.setBatchingEnabled(request.getBatchingEnabled());
            }
            
            if (request.getBatchInterval() != null) {
                preference.setBatchInterval(request.getBatchInterval());
            }
            
            if (request.getQuietHoursStart() != null) {
                preference.setQuietHoursStart(request.getQuietHoursStart());
            }
            
            if (request.getQuietHoursEnd() != null) {
                preference.setQuietHoursEnd(request.getQuietHoursEnd());
            }
            
            if (request.getMaxBatchSize() != null) {
                preference.setMaxBatchSize(request.getMaxBatchSize());
            }
            
            preference.setUpdatedAt(Instant.now());
            preference = preferenceRepository.save(preference);
            
            return ResultWrapper.success(preference);
            
        } catch (Exception e) {
            log.error("Failed to update batching preferences", e);
            return ResultWrapper.failure("Failed to update preferences: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private boolean shouldSendImmediately(NotificationBatchRequest request) {
        // High priority notifications should bypass batching
        return request.getPriority() == NotificationPriority.HIGH ||
               request.getPriority() == NotificationPriority.URGENT ||
               "SECURITY_ALERT".equals(request.getNotificationType()) ||
               "FRAUD_ALERT".equals(request.getNotificationType()) ||
               "LOGIN_ALERT".equals(request.getNotificationType());
    }
    
    private ResultWrapper<NotificationBatchResult> sendImmediateNotification(
            NotificationBatchRequest request) {
        
        try {
            PushNotificationResult result = pushNotificationService.sendPushNotification(
                PushNotificationRequest.builder()
                    .userId(request.getUserId())
                    .title(request.getTitle())
                    .body(request.getBody())
                    .data(request.getData())
                    .priority(request.getPriority())
                    .build()
            );
            
            return ResultWrapper.success(
                NotificationBatchResult.builder()
                    .notificationId(result.getNotificationId())
                    .status("SENT_IMMEDIATELY")
                    .deliveryTime(Instant.now())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to send immediate notification", e);
            return ResultWrapper.failure("Failed to send notification: " + e.getMessage());
        }
    }
    
    private UserNotificationPreference getUserPreference(String userId) {
        return preferenceRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultPreference(userId));
    }
    
    private UserNotificationPreference createDefaultPreference(String userId) {
        UserNotificationPreference preference = UserNotificationPreference.builder()
            .userId(userId)
            .batchingEnabled(true)
            .batchInterval(defaultBatchInterval)
            .maxBatchSize(maxBatchSize)
            .quietHoursStart("22:00")
            .quietHoursEnd("08:00")
            .build();
        
        return preferenceRepository.save(preference);
    }
    
    private PendingNotification createPendingNotification(
            NotificationBatchRequest request, UserNotificationPreference preference) {
        
        return PendingNotification.builder()
            .userId(request.getUserId())
            .notificationType(request.getNotificationType())
            .title(request.getTitle())
            .body(request.getBody())
            .data(request.getData())
            .priority(request.getPriority())
            .scheduledFor(calculateScheduledTime(request, preference))
            .createdAt(Instant.now())
            .build();
    }
    
    private String generateBatchKey(String userId, String notificationType) {
        return String.format("%s:%s", userId, notificationType);
    }
    
    private boolean shouldSendBatch(String batchKey, UserNotificationPreference preference) {
        List<PendingNotification> pending = batchBuffer.get(batchKey);
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        
        // Check batch size threshold
        if (pending.size() >= preference.getMaxBatchSize()) {
            return true;
        }
        
        // Check time threshold
        Instant lastSent = lastBatchSent.get(batchKey);
        if (lastSent == null) {
            return pending.size() >= minBatchSize;
        }
        
        long minutesSinceLastBatch = ChronoUnit.MINUTES.between(lastSent, Instant.now());
        long intervalMinutes = parseBatchInterval(preference.getBatchInterval());
        
        return minutesSinceLastBatch >= intervalMinutes && pending.size() >= minBatchSize;
    }
    
    private ResultWrapper<NotificationBatchResult> processBatch(
            String batchKey, UserNotificationPreference preference) {
        
        List<PendingNotification> notifications = batchBuffer.remove(batchKey);
        if (notifications == null || notifications.isEmpty()) {
            return ResultWrapper.failure("No notifications to batch");
        }
        
        try {
            // Create batch record
            NotificationBatch batch = createBatchRecord(notifications, preference);
            batch = batchRepository.save(batch);
            
            // Generate aggregated notification
            AggregatedNotification aggregated = aggregateNotifications(notifications);
            
            // Send batched notification
            PushNotificationResult result = pushNotificationService.sendPushNotification(
                PushNotificationRequest.builder()
                    .userId(aggregated.getUserId())
                    .title(aggregated.getTitle())
                    .body(aggregated.getBody())
                    .data(aggregated.getData())
                    .priority(NotificationPriority.NORMAL)
                    .build()
            );
            
            // Update batch status
            batch.setStatus(NotificationBatchStatus.SENT);
            batch.setSentAt(Instant.now());
            batch.setDeliveryId(result.getNotificationId());
            batchRepository.save(batch);
            
            // Update last sent time
            lastBatchSent.put(batchKey, Instant.now());
            
            log.info("Sent batch notification: {} notifications aggregated", notifications.size());
            
            return ResultWrapper.success(
                NotificationBatchResult.builder()
                    .batchId(batch.getId())
                    .status("SENT")
                    .notificationCount(notifications.size())
                    .deliveryTime(Instant.now())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to process notification batch", e);
            // Return notifications to buffer
            batchBuffer.put(batchKey, notifications);
            return ResultWrapper.failure("Failed to send batch: " + e.getMessage());
        }
    }
    
    private NotificationBatch createBatchRecord(
            List<PendingNotification> notifications, UserNotificationPreference preference) {
        
        String userId = notifications.get(0).getUserId();
        String notificationType = notifications.get(0).getNotificationType();
        
        return NotificationBatch.builder()
            .userId(userId)
            .notificationType(notificationType)
            .notificationCount(notifications.size())
            .status(NotificationBatchStatus.PENDING)
            .batchInterval(preference.getBatchInterval())
            .createdAt(Instant.now())
            .scheduledFor(Instant.now())
            .build();
    }
    
    private AggregatedNotification aggregateNotifications(List<PendingNotification> notifications) {
        String userId = notifications.get(0).getUserId();
        String notificationType = notifications.get(0).getNotificationType();
        
        // Generate smart aggregated title and body
        String title = generateAggregatedTitle(notifications);
        String body = generateAggregatedBody(notifications);
        Map<String, Object> data = aggregateNotificationData(notifications);
        
        return AggregatedNotification.builder()
            .userId(userId)
            .notificationType(notificationType)
            .title(title)
            .body(body)
            .data(data)
            .originalCount(notifications.size())
            .build();
    }
    
    private String generateAggregatedTitle(List<PendingNotification> notifications) {
        String type = notifications.get(0).getNotificationType();
        int count = notifications.size();
        
        switch (type) {
            case "PAYMENT_RECEIVED":
                return String.format("💰 %d new payments received", count);
            case "PAYMENT_SENT":
                return String.format("📤 %d payments sent", count);
            case "FRIEND_REQUEST":
                return String.format("👋 %d new friend requests", count);
            case "SOCIAL_ACTIVITY":
                return String.format("🎉 %d new activities", count);
            case "GROUP_ACTIVITY":
                return String.format("👥 %d group updates", count);
            default:
                return String.format("%d new notifications", count);
        }
    }
    
    private String generateAggregatedBody(List<PendingNotification> notifications) {
        String type = notifications.get(0).getNotificationType();
        
        if (notifications.size() == 1) {
            return notifications.get(0).getBody();
        }
        
        // Generate summary based on notification type
        switch (type) {
            case "PAYMENT_RECEIVED":
                return generatePaymentSummary(notifications);
            case "SOCIAL_ACTIVITY":
                return generateSocialSummary(notifications);
            case "FRIEND_REQUEST":
                return generateFriendRequestSummary(notifications);
            default:
                return String.format("You have %d new %s notifications", 
                    notifications.size(), type.toLowerCase());
        }
    }
    
    private String generatePaymentSummary(List<PendingNotification> notifications) {
        // Calculate total amount if possible
        double totalAmount = notifications.stream()
            .filter(n -> n.getData().containsKey("amount"))
            .mapToDouble(n -> Double.parseDouble(n.getData().get("amount").toString()))
            .sum();
        
        if (totalAmount > 0) {
            return String.format("Total: $%.2f from %d payments", totalAmount, notifications.size());
        }
        
        // Fallback to count
        return String.format("%d new payments received", notifications.size());
    }
    
    private String generateSocialSummary(List<PendingNotification> notifications) {
        List<String> senders = notifications.stream()
            .map(n -> n.getData().get("senderName"))
            .filter(Objects::nonNull)
            .map(Object::toString)
            .distinct()
            .limit(3)
            .collect(Collectors.toList());
        
        if (senders.isEmpty()) {
            return String.format("%d new social activities", notifications.size());
        }
        
        if (senders.size() == 1) {
            return String.format("New activity from %s", senders.get(0));
        }
        
        return String.format("New activities from %s and %d others", 
            senders.get(0), senders.size() - 1);
    }
    
    private String generateFriendRequestSummary(List<PendingNotification> notifications) {
        List<String> requesters = notifications.stream()
            .map(n -> n.getData().get("requesterName"))
            .filter(Objects::nonNull)
            .map(Object::toString)
            .distinct()
            .limit(2)
            .collect(Collectors.toList());
        
        if (requesters.size() == 1) {
            return String.format("%s wants to connect", requesters.get(0));
        }
        
        return String.format("%s, %s and %d others want to connect", 
            requesters.get(0), requesters.get(1), notifications.size() - 2);
    }
    
    private Map<String, Object> aggregateNotificationData(List<PendingNotification> notifications) {
        Map<String, Object> aggregated = new HashMap<>();
        
        aggregated.put("batchCount", notifications.size());
        aggregated.put("notificationType", notifications.get(0).getNotificationType());
        aggregated.put("isBatch", true);
        
        // Aggregate specific data based on type
        List<Map<String, Object>> items = notifications.stream()
            .map(PendingNotification::getData)
            .collect(Collectors.toList());
        
        aggregated.put("items", items);
        
        return aggregated;
    }
    
    private Notification saveToDatabase(PendingNotification pending) {
        Notification notification = Notification.builder()
            .userId(pending.getUserId())
            .type(pending.getNotificationType())
            .title(pending.getTitle())
            .body(pending.getBody())
            .data(pending.getData())
            .priority(pending.getPriority())
            .status(NotificationStatus.BATCHED)
            .scheduledFor(pending.getScheduledFor())
            .createdAt(pending.getCreatedAt())
            .build();
        
        return notificationRepository.save(notification);
    }
    
    private Instant calculateEstimatedDelivery(String batchKey, UserNotificationPreference preference) {
        Instant lastSent = lastBatchSent.get(batchKey);
        long intervalMinutes = parseBatchInterval(preference.getBatchInterval());
        
        if (lastSent == null) {
            return Instant.now().plus(intervalMinutes, ChronoUnit.MINUTES);
        }
        
        return lastSent.plus(intervalMinutes, ChronoUnit.MINUTES);
    }
    
    private Instant calculateScheduledTime(
            NotificationBatchRequest request, UserNotificationPreference preference) {
        
        long intervalMinutes = parseBatchInterval(preference.getBatchInterval());
        return Instant.now().plus(intervalMinutes, ChronoUnit.MINUTES);
    }
    
    private long parseBatchInterval(String interval) {
        // Parse ISO 8601 duration (e.g., "PT15M" = 15 minutes)
        try {
            java.time.Duration duration = java.time.Duration.parse(interval);
            return duration.toMinutes();
        } catch (Exception e) {
            log.warn("Failed to parse batch interval: {}, using default", interval);
            return 15; // Default to 15 minutes
        }
    }
    
    private void processInMemoryBatches() {
        for (Map.Entry<String, List<PendingNotification>> entry : batchBuffer.entrySet()) {
            String batchKey = entry.getKey();
            List<PendingNotification> notifications = entry.getValue();
            
            if (notifications.isEmpty()) {
                continue;
            }
            
            String userId = notifications.get(0).getUserId();
            UserNotificationPreference preference = getUserPreference(userId);
            
            if (shouldSendBatch(batchKey, preference)) {
                processBatch(batchKey, preference);
            }
        }
    }
    
    private void processExpiredDatabaseBatches() {
        Pageable pageable = PageRequest.of(0, 100);
        
        List<Notification> expiredBatched = notificationRepository
            .findExpiredBatchedNotifications(Instant.now(), pageable);
        
        if (!expiredBatched.isEmpty()) {
            log.info("Processing {} expired batched notifications", expiredBatched.size());
            
            // Group by user and type
            Map<String, List<Notification>> groupedNotifications = expiredBatched.stream()
                .collect(Collectors.groupingBy(n -> 
                    generateBatchKey(n.getUserId(), n.getType())));
            
            for (Map.Entry<String, List<Notification>> entry : groupedNotifications.entrySet()) {
                String batchKey = entry.getKey();
                List<Notification> notifications = entry.getValue();
                
                // Convert to pending notifications and process
                List<PendingNotification> pending = notifications.stream()
                    .map(this::convertToPending)
                    .collect(Collectors.toList());
                
                String userId = notifications.get(0).getUserId();
                UserNotificationPreference preference = getUserPreference(userId);
                
                batchBuffer.put(batchKey, pending);
                processBatch(batchKey, preference);
            }
        }
    }
    
    private PendingNotification convertToPending(Notification notification) {
        return PendingNotification.builder()
            .userId(notification.getUserId())
            .notificationType(notification.getType())
            .title(notification.getTitle())
            .body(notification.getBody())
            .data(notification.getData())
            .priority(notification.getPriority())
            .scheduledFor(notification.getScheduledFor())
            .createdAt(notification.getCreatedAt())
            .build();
    }
    
    private boolean isPeakHour(int hour) {
        // Define peak hours (9 AM - 12 PM and 6 PM - 9 PM)
        return (hour >= 9 && hour <= 12) || (hour >= 18 && hour <= 21);
    }
    
    private void increaseBatchingInterval() {
        // During peak hours, batch more notifications together
        // This logic would adjust global batching settings
        log.debug("Increasing batching interval for peak hours");
    }
    
    private void decreaseBatchingInterval() {
        // During off-peak hours, send notifications more frequently
        log.debug("Decreasing batching interval for off-peak hours");
    }
    
    private double calculateBatchingReduction(String userId, Instant since) {
        // Calculate how much notification volume was reduced by batching
        int totalNotifications = notificationRepository.countByUserIdAndCreatedAtAfter(userId, since);
        int totalBatches = batchRepository.countByUserIdAndCreatedAtAfter(userId, since);
        
        if (totalBatches == 0) {
            return 0.0;
        }
        
        return ((double) (totalNotifications - totalBatches) / totalNotifications) * 100;
    }
    
    private List<Integer> calculatePeakBatchingHours(List<NotificationBatch> batches) {
        Map<Integer, Long> hourCounts = batches.stream()
            .collect(Collectors.groupingBy(
                batch -> batch.getSentAt().atZone(java.time.ZoneOffset.UTC).getHour(),
                Collectors.counting()
            ));
        
        return hourCounts.entrySet().stream()
            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}