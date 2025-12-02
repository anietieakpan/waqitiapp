package com.waqiti.social.service;

import com.waqiti.social.domain.*;
import com.waqiti.social.dto.*;
import com.waqiti.social.exception.*;
import com.waqiti.social.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Comprehensive Social Notification Service
 * 
 * Handles real-time notifications, activity updates, push notifications,
 * smart notification filtering, digest creation, and notification preferences
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialNotificationService {

    private final SocialNotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final SocialActivityRepository activityRepository;
    private final SocialConnectionRepository connectionRepository;
    private final NotificationDigestRepository digestRepository;
    private final PushNotificationService pushNotificationService;
    private final EmailNotificationService emailService;
    private final WebSocketService webSocketService;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Real-time connection management
    private final ConcurrentHashMap<UUID, SseEmitter> userConnections = new ConcurrentHashMap<>();
    
    // Notification configuration
    private static final int MAX_NOTIFICATIONS_PER_DIGEST = 50;
    private static final int NOTIFICATION_RETENTION_DAYS = 90;
    private static final int REAL_TIME_TIMEOUT_MINUTES = 60;
    private static final int MAX_NOTIFICATION_BATCH_SIZE = 100;

    /**
     * Send real-time activity notification
     */
    @Async
    public CompletableFuture<NotificationResult> sendActivityNotification(
            UUID recipientId, ActivityNotificationRequest request) {
        
        log.info("Sending activity notification to {} type: {}", recipientId, request.getNotificationType());

        try {
            // Check notification preferences
            NotificationPreferences preferences = getUserNotificationPreferences(recipientId);
            if (!shouldSendNotification(preferences, request.getNotificationType())) {
                return CompletableFuture.completedFuture(
                    NotificationResult.skipped("User preferences block this notification"));
            }

            // Apply smart filtering
            if (!passesSmartFiltering(recipientId, request)) {
                return CompletableFuture.completedFuture(
                    NotificationResult.filtered("Smart filtering blocked notification"));
            }

            // Create notification record
            SocialNotification notification = createNotificationRecord(recipientId, request);

            // Send real-time notification
            sendRealTimeNotification(recipientId, notification);

            // Send push notification if enabled
            if (preferences.isPushNotificationsEnabled()) {
                sendPushNotification(recipientId, notification);
            }

            // Add to digest if configured
            if (preferences.isDigestEnabled()) {
                addToNotificationDigest(recipientId, notification);
            }

            // Update notification analytics
            updateNotificationAnalytics(recipientId, request.getNotificationType());

            // Publish notification event
            publishNotificationEvent("NOTIFICATION_SENT", notification);

            log.info("Activity notification sent: {} to user: {}", notification.getId(), recipientId);

            return CompletableFuture.completedFuture(NotificationResult.success(notification.getId()));

        } catch (Exception e) {
            log.error("Failed to send activity notification to user: {}", recipientId, e);
            return CompletableFuture.completedFuture(
                NotificationResult.error("Failed to send notification: " + e.getMessage()));
        }
    }

    /**
     * Send batch notifications efficiently
     */
    @Async
    public CompletableFuture<BatchNotificationResult> sendBatchNotifications(
            List<UUID> recipientIds, BatchNotificationRequest request) {
        
        log.info("Sending batch notifications to {} recipients type: {}", 
                recipientIds.size(), request.getNotificationType());

        try {
            List<NotificationResult> results = new ArrayList<>();
            List<List<UUID>> batches = partitionList(recipientIds, MAX_NOTIFICATION_BATCH_SIZE);

            for (List<UUID> batch : batches) {
                List<CompletableFuture<NotificationResult>> futures = batch.stream()
                        .map(recipientId -> {
                            ActivityNotificationRequest individualRequest = ActivityNotificationRequest.builder()
                                    .recipientId(recipientId)
                                    .senderId(request.getSenderId())
                                    .notificationType(request.getNotificationType())
                                    .activityId(request.getActivityId())
                                    .message(request.getMessage())
                                    .metadata(request.getMetadata())
                                    .build();
                            return sendActivityNotification(recipientId, individualRequest);
                        })
                        .collect(Collectors.toList());

                // Wait for batch to complete
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));
                
                allFutures.join();

                // Collect results
                results.addAll(futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
            }

            int successCount = (int) results.stream().filter(NotificationResult::isSuccess).count();
            int failedCount = results.size() - successCount;

            return CompletableFuture.completedFuture(
                    BatchNotificationResult.builder()
                            .totalAttempted(recipientIds.size())
                            .successCount(successCount)
                            .failedCount(failedCount)
                            .results(results)
                            .build());

        } catch (Exception e) {
            log.error("Failed to send batch notifications", e);
            return CompletableFuture.completedFuture(
                    BatchNotificationResult.error("Batch notification failed: " + e.getMessage()));
        }
    }

    /**
     * Establish real-time notification stream
     */
    public SseEmitter createNotificationStream(UUID userId) {
        log.info("Creating notification stream for user: {}", userId);

        try {
            // Remove existing connection if any
            SseEmitter existingEmitter = userConnections.remove(userId);
            if (existingEmitter != null) {
                try {
                    existingEmitter.complete();
                } catch (Exception e) {
                    log.debug("Error completing existing emitter", e);
                }
            }

            // Create new SSE emitter
            SseEmitter emitter = new SseEmitter(REAL_TIME_TIMEOUT_MINUTES * 60 * 1000L);
            userConnections.put(userId, emitter);

            // Set up cleanup on completion/timeout
            emitter.onCompletion(() -> {
                userConnections.remove(userId);
                log.debug("Notification stream completed for user: {}", userId);
            });

            emitter.onTimeout(() -> {
                userConnections.remove(userId);
                log.debug("Notification stream timed out for user: {}", userId);
            });

            emitter.onError(throwable -> {
                userConnections.remove(userId);
                log.warn("Notification stream error for user: {}", userId, throwable);
            });

            // Send connection confirmation
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "connected", "timestamp", LocalDateTime.now())));

            // Send any pending notifications
            sendPendingNotifications(userId, emitter);

            log.info("Notification stream established for user: {}", userId);
            return emitter;

        } catch (Exception e) {
            log.error("Failed to create notification stream for user: {}", userId, e);
            throw new NotificationException("Failed to create notification stream", e);
        }
    }

    /**
     * Get user's notifications with smart filtering and pagination
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(UUID userId, NotificationFilter filter, Pageable pageable) {
        log.debug("Getting notifications for user: {} filter: {}", userId, filter);

        try {
            // Apply filters
            Page<SocialNotification> notifications = notificationRepository
                    .findByRecipientIdWithFilters(
                        userId, 
                        filter.getTypes(),
                        filter.getStatus(),
                        filter.getStartDate(),
                        filter.getEndDate(),
                        pageable
                    );

            // Mark as read if requested
            if (filter.isMarkAsRead()) {
                markNotificationsAsRead(notifications.getContent());
            }

            // Convert to response format
            return notifications.map(this::mapToNotificationResponse);

        } catch (Exception e) {
            log.error("Failed to get notifications for user: {}", userId, e);
            throw new NotificationException("Failed to get notifications", e);
        }
    }

    /**
     * Update notification preferences with intelligent defaults
     */
    public NotificationPreferencesResponse updateNotificationPreferences(
            UUID userId, UpdateNotificationPreferencesRequest request) {
        
        log.info("Updating notification preferences for user: {}", userId);

        try {
            NotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                    .orElse(createDefaultPreferences(userId));

            // Update preferences
            updatePreferencesFromRequest(preferences, request);

            // Apply intelligent optimizations
            optimizeNotificationPreferences(preferences, userId);

            preferences = preferencesRepository.save(preferences);

            // Update real-time filtering
            updateRealTimeFilters(userId, preferences);

            log.info("Notification preferences updated for user: {}", userId);

            return mapToPreferencesResponse(preferences);

        } catch (Exception e) {
            log.error("Failed to update notification preferences for user: {}", userId, e);
            throw new NotificationException("Failed to update preferences", e);
        }
    }

    /**
     * Generate and send notification digest
     */
    @Scheduled(cron = "0 0 8 * * *") // Daily at 8 AM
    @Async
    public void generateAndSendDailyDigests() {
        log.info("Generating daily notification digests");

        try {
            // Get users who have digest enabled
            List<UUID> digestUsers = preferencesRepository.findUsersWithDigestEnabled();

            for (UUID userId : digestUsers) {
                try {
                    generateUserDigest(userId, DigestType.DAILY);
                } catch (Exception e) {
                    log.warn("Failed to generate digest for user: {}", userId, e);
                }
            }

            log.info("Daily digests generated for {} users", digestUsers.size());

        } catch (Exception e) {
            log.error("Error generating daily digests", e);
        }
    }

    /**
     * Generate weekly digest
     */
    @Scheduled(cron = "0 0 9 * * SUN") // Sundays at 9 AM
    @Async
    public void generateWeeklyDigests() {
        log.info("Generating weekly notification digests");

        try {
            List<UUID> digestUsers = preferencesRepository.findUsersWithWeeklyDigestEnabled();

            for (UUID userId : digestUsers) {
                try {
                    generateUserDigest(userId, DigestType.WEEKLY);
                } catch (Exception e) {
                    log.warn("Failed to generate weekly digest for user: {}", userId, e);
                }
            }

            log.info("Weekly digests generated for {} users", digestUsers.size());

        } catch (Exception e) {
            log.error("Error generating weekly digests", e);
        }
    }

    /**
     * Clean up old notifications
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Async
    public void cleanupOldNotifications() {
        log.info("Cleaning up old notifications");

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(NOTIFICATION_RETENTION_DAYS);
            
            int deletedCount = notificationRepository.deleteNotificationsOlderThan(cutoffDate);
            
            log.info("Cleaned up {} old notifications", deletedCount);

        } catch (Exception e) {
            log.error("Error cleaning up old notifications", e);
        }
    }

    /**
     * Get notification analytics
     */
    @Transactional(readOnly = true)
    public NotificationAnalytics getNotificationAnalytics(UUID userId, AnalyticsPeriod period) {
        log.info("Generating notification analytics for user: {} period: {}", userId, period);

        try {
            LocalDateTime startDate = calculatePeriodStart(period);
            LocalDateTime endDate = LocalDateTime.now();

            // Get notification data
            List<SocialNotification> notifications = notificationRepository
                    .findByRecipientIdAndDateRange(userId, startDate, endDate);

            // Calculate metrics
            NotificationMetrics metrics = calculateNotificationMetrics(notifications);

            // Analyze engagement patterns
            EngagementPatterns patterns = analyzeNotificationEngagement(notifications);

            // Calculate optimal timing
            OptimalTimingAnalysis timing = calculateOptimalNotificationTiming(notifications, userId);

            // Analyze notification effectiveness
            NotificationEffectiveness effectiveness = analyzeNotificationEffectiveness(notifications);

            // Generate insights and recommendations
            List<NotificationInsight> insights = generateNotificationInsights(metrics, patterns, timing);

            return NotificationAnalytics.builder()
                    .userId(userId)
                    .period(period)
                    .metrics(metrics)
                    .patterns(patterns)
                    .timing(timing)
                    .effectiveness(effectiveness)
                    .insights(insights)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate notification analytics", e);
            throw new AnalyticsException("Failed to generate analytics", e);
        }
    }

    // Helper methods for notification processing

    private NotificationPreferences getUserNotificationPreferences(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .orElse(createDefaultPreferences(userId));
    }

    private NotificationPreferences createDefaultPreferences(UUID userId) {
        return NotificationPreferences.builder()
                .userId(userId)
                .pushNotificationsEnabled(true)
                .emailNotificationsEnabled(true)
                .digestEnabled(true)
                .digestFrequency(DigestFrequency.DAILY)
                .quietHoursStart(22) // 10 PM
                .quietHoursEnd(8)    // 8 AM
                .enabledNotificationTypes(getDefaultNotificationTypes())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean shouldSendNotification(NotificationPreferences preferences, NotificationType type) {
        // Check if notification type is enabled
        if (!preferences.getEnabledNotificationTypes().contains(type)) {
            return false;
        }

        // Check quiet hours
        if (isQuietHours(preferences)) {
            return type.isUrgent(); // Only urgent notifications during quiet hours
        }

        return true;
    }

    private boolean passesSmartFiltering(UUID recipientId, ActivityNotificationRequest request) {
        // Apply intelligent filtering to reduce notification fatigue
        
        // Check notification frequency limits
        if (exceedsFrequencyLimit(recipientId, request.getNotificationType())) {
            return false;
        }

        // Check duplicate notifications
        if (isDuplicateNotification(recipientId, request)) {
            return false;
        }

        // Check user engagement patterns
        if (!matchesEngagementPattern(recipientId, request.getNotificationType())) {
            return false;
        }

        return true;
    }

    private SocialNotification createNotificationRecord(UUID recipientId, ActivityNotificationRequest request) {
        return SocialNotification.builder()
                .recipientId(recipientId)
                .senderId(request.getSenderId())
                .type(request.getNotificationType())
                .title(generateNotificationTitle(request))
                .message(request.getMessage())
                .activityId(request.getActivityId())
                .metadata(request.getMetadata())
                .status(NotificationStatus.SENT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void sendRealTimeNotification(UUID recipientId, SocialNotification notification) {
        SseEmitter emitter = userConnections.get(recipientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(mapToNotificationResponse(notification)));
            } catch (Exception e) {
                log.warn("Failed to send real-time notification to user: {}", recipientId, e);
                userConnections.remove(recipientId);
            }
        }
    }

    private void sendPushNotification(UUID recipientId, SocialNotification notification) {
        try {
            PushNotificationRequest pushRequest = PushNotificationRequest.builder()
                    .recipientId(recipientId)
                    .title(notification.getTitle())
                    .body(notification.getMessage())
                    .data(notification.getMetadata())
                    .build();

            pushNotificationService.sendPushNotification(pushRequest);
        } catch (Exception e) {
            log.warn("Failed to send push notification to user: {}", recipientId, e);
        }
    }

    private void generateUserDigest(UUID userId, DigestType digestType) {
        try {
            // Get timeframe for digest
            LocalDateTime startDate = getDigestStartDate(digestType);
            LocalDateTime endDate = LocalDateTime.now();

            // Get unread notifications
            List<SocialNotification> notifications = notificationRepository
                    .findUnreadNotificationsByUserAndDateRange(userId, startDate, endDate);

            if (notifications.isEmpty()) {
                return; // No digest needed
            }

            // Create digest
            NotificationDigest digest = createDigest(userId, notifications, digestType);

            // Send digest via email
            sendDigestEmail(userId, digest);

            // Mark notifications as digested
            markNotificationsAsDigested(notifications);

            log.info("Generated {} digest for user: {} with {} notifications", 
                    digestType, userId, notifications.size());

        } catch (Exception e) {
            log.error("Failed to generate digest for user: {}", userId, e);
        }
    }

    // Event publishing
    private void publishNotificationEvent(String eventType, SocialNotification notification) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "notificationId", notification.getId(),
                "recipientId", notification.getRecipientId(),
                "type", notification.getType(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("social-notification-events", notification.getId().toString(), event);
    }

    // Response mapping
    private NotificationResponse mapToNotificationResponse(SocialNotification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .senderId(notification.getSenderId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .activityId(notification.getActivityId())
                .status(notification.getStatus())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }

    // Placeholder implementations for complex operations
    private boolean isQuietHours(NotificationPreferences preferences) { return false; }
    private boolean exceedsFrequencyLimit(UUID userId, NotificationType type) { return false; }
    private boolean isDuplicateNotification(UUID userId, ActivityNotificationRequest request) { return false; }
    private boolean matchesEngagementPattern(UUID userId, NotificationType type) { return true; }
    private String generateNotificationTitle(ActivityNotificationRequest request) { return "Social Activity"; }
    private void markNotificationsAsRead(List<SocialNotification> notifications) {
        try {
            List<UUID> notificationIds = notifications.stream()
                .map(SocialNotification::getId)
                .collect(Collectors.toList());
            
            notificationRepository.markAsRead(notificationIds, Instant.now());
            
            // Update read analytics
            for (SocialNotification notification : notifications) {
                updateNotificationAnalytics(notification.getUserId(), NotificationType.READ(notification.getType()));
            }
            
            log.debug("Marked {} notifications as read", notifications.size());
            
        } catch (Exception e) {
            log.error("Failed to mark notifications as read", e);
        }
    }
    private void addToNotificationDigest(UUID userId, SocialNotification notification) {
        try {
            NotificationPreferences preferences = getOrCreatePreferences(userId);
            
            if (preferences.getDigestType() != DigestType.NEVER) {
                NotificationDigest digest = NotificationDigest.builder()
                    .userId(userId)
                    .notificationId(notification.getId())
                    .digestType(preferences.getDigestType())
                    .scheduledFor(calculateDigestTime(preferences.getDigestType()))
                    .createdAt(Instant.now())
                    .build();
                
                digestRepository.save(digest);
                
                log.debug("Added notification {} to {} digest for user {}", 
                    notification.getId(), preferences.getDigestType(), userId);
            }
            
        } catch (Exception e) {
            log.error("Failed to add notification to digest for user {}", userId, e);
        }
    }
    private void updateNotificationAnalytics(UUID userId, NotificationType type) {
        try {
            NotificationAnalytics analytics = analyticsRepository.findByUserIdAndType(userId, type)
                .orElse(NotificationAnalytics.builder()
                    .userId(userId)
                    .type(type)
                    .totalSent(0L)
                    .totalDelivered(0L)
                    .totalRead(0L)
                    .totalClicked(0L)
                    .createdAt(Instant.now())
                    .build());
            
            switch (type.getAction()) {
                case "SENT":
                    analytics.setTotalSent(analytics.getTotalSent() + 1);
                    break;
                case "DELIVERED":
                    analytics.setTotalDelivered(analytics.getTotalDelivered() + 1);
                    break;
                case "READ":
                    analytics.setTotalRead(analytics.getTotalRead() + 1);
                    break;
                case "CLICKED":
                    analytics.setTotalClicked(analytics.getTotalClicked() + 1);
                    break;
            }
            
            analytics.setLastUpdated(Instant.now());
            analyticsRepository.save(analytics);
            
            log.debug("Updated notification analytics for user {} and type {}", userId, type);
            
        } catch (Exception e) {
            log.error("Failed to update notification analytics for user {} and type {}", userId, type, e);
        }
    }
    private void sendPendingNotifications(UUID userId, SseEmitter emitter) {
        try {
            List<SocialNotification> pendingNotifications = notificationRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, NotificationStatus.PENDING);
            
            for (SocialNotification notification : pendingNotifications) {
                try {
                    // Convert to SSE format
                    Map<String, Object> eventData = Map.of(
                        "id", notification.getId().toString(),
                        "type", notification.getType().toString(),
                        "title", notification.getTitle(),
                        "message", notification.getMessage(),
                        "data", notification.getData() != null ? notification.getData() : Map.of(),
                        "timestamp", notification.getCreatedAt().toString(),
                        "priority", notification.getPriority().toString()
                    );
                    
                    // Send via SSE
                    emitter.send(SseEmitter.event()
                        .id(notification.getId().toString())
                        .name("notification")
                        .data(eventData));
                    
                    // Mark as delivered
                    notification.setStatus(NotificationStatus.DELIVERED);
                    notification.setDeliveredAt(Instant.now());
                    notificationRepository.save(notification);
                    
                    updateNotificationAnalytics(userId, NotificationType.delivered(notification.getType()));
                    
                } catch (Exception e) {
                    log.error("Failed to send pending notification {} to user {}", notification.getId(), userId, e);
                }
            }
            
            log.debug("Sent {} pending notifications to user {}", pendingNotifications.size(), userId);
            
        } catch (Exception e) {
            log.error("Failed to send pending notifications to user {}", userId, e);
        }
    }
    private void optimizeNotificationPreferences(NotificationPreferences preferences, UUID userId) {
        // Optimize notification preferences based on user behavior
        try {
            // Analyze user engagement patterns
            Map<String, Double> engagementRates = analyzeUserEngagement(userId);
            
            // Auto-adjust preferences based on engagement
            if (engagementRates.get("push") != null && engagementRates.get("push") < 0.1) {
                // Low push engagement - reduce frequency
                preferences.setMaxPushPerDay(Math.max(1, preferences.getMaxPushPerDay() / 2));
                log.debug("Reduced push notifications for user {} due to low engagement", userId);
            }
            
            if (engagementRates.get("email") != null && engagementRates.get("email") < 0.05) {
                // Very low email engagement - switch to digest only
                preferences.setEmailDigestFrequency(DigestFrequency.WEEKLY);
                preferences.setEmailForTransactions(false);
                log.debug("Switched user {} to weekly email digest due to low engagement", userId);
            }
            
            // Optimize quiet hours based on activity patterns
            optimizeQuietHours(preferences, userId);
            
            // Remove duplicate or redundant preferences
            consolidatePreferences(preferences);
            
            // Save optimized preferences
            notificationPreferencesRepository.save(preferences);
            
            log.info("Optimized notification preferences for user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to optimize notification preferences for user {}", userId, e);
        }
    }
    
    private Map<String, Double> analyzeUserEngagement(UUID userId) {
        Map<String, Double> engagementRates = new HashMap<>();
        
        try {
            // Get notification history for last 30 days
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            List<NotificationHistory> history = notificationHistoryRepository
                .findByUserIdAndCreatedAtAfter(userId, thirtyDaysAgo);
            
            // Calculate engagement rates by channel
            Map<String, Long> sentCounts = new HashMap<>();
            Map<String, Long> readCounts = new HashMap<>();
            
            for (NotificationHistory notification : history) {
                String channel = notification.getChannel();
                sentCounts.merge(channel, 1L, Long::sum);
                if (notification.getStatus() == NotificationStatus.READ) {
                    readCounts.merge(channel, 1L, Long::sum);
                }
            }
            
            // Calculate rates
            for (String channel : sentCounts.keySet()) {
                long sent = sentCounts.get(channel);
                long read = readCounts.getOrDefault(channel, 0L);
                double rate = sent > 0 ? (double) read / sent : 0.0;
                engagementRates.put(channel, rate);
            }
            
        } catch (Exception e) {
            log.error("Failed to analyze user engagement for {}", userId, e);
        }
        
        return engagementRates;
    }
    
    private void optimizeQuietHours(NotificationPreferences preferences, UUID userId) {
        // Analyze user activity patterns to optimize quiet hours
        try {
            // Get user activity data
            Map<Integer, Integer> hourlyActivity = getUserHourlyActivity(userId);
            
            // Find least active consecutive hours (potential sleep time)
            int optimalStartHour = 22; // Default 10 PM
            int optimalEndHour = 7;    // Default 7 AM
            int minActivity = Integer.MAX_VALUE;
            
            for (int startHour = 20; startHour <= 23; startHour++) {
                int totalActivity = 0;
                for (int i = 0; i < 8; i++) { // Check 8-hour windows
                    int hour = (startHour + i) % 24;
                    totalActivity += hourlyActivity.getOrDefault(hour, 0);
                }
                
                if (totalActivity < minActivity) {
                    minActivity = totalActivity;
                    optimalStartHour = startHour;
                    optimalEndHour = (startHour + 8) % 24;
                }
            }
            
            preferences.setQuietHoursStart(optimalStartHour);
            preferences.setQuietHoursEnd(optimalEndHour);
            
        } catch (Exception e) {
            log.error("Failed to optimize quiet hours for user {}", userId, e);
        }
    }
    
    private Map<Integer, Integer> getUserHourlyActivity(UUID userId) {
        Map<Integer, Integer> hourlyActivity = new HashMap<>();
        
        // Initialize all hours
        for (int i = 0; i < 24; i++) {
            hourlyActivity.put(i, 0);
        }
        
        // Aggregate activity data (simplified - would query actual user activity)
        // In production, this would analyze login times, transaction times, etc.
        return hourlyActivity;
    }
    
    private void consolidatePreferences(NotificationPreferences preferences) {
        // Remove redundant or conflicting preferences
        if (preferences.isAllNotificationsDisabled()) {
            // If all notifications disabled, clear individual settings
            preferences.setPushEnabled(false);
            preferences.setEmailEnabled(false);
            preferences.setSmsEnabled(false);
        }
        
        // Ensure consistency
        if (!preferences.isPushEnabled()) {
            preferences.setPushForTransactions(false);
            preferences.setPushForSocial(false);
            preferences.setPushForMarketing(false);
        }
        
        if (!preferences.isEmailEnabled()) {
            preferences.setEmailForTransactions(false);
            preferences.setEmailForSocial(false);
            preferences.setEmailForMarketing(false);
        }
    }
    private void updateRealTimeFilters(UUID userId, NotificationPreferences preferences) {
        // Update real-time notification filters in Redis for fast access
        try {
            String filterKey = "notification:filter:" + userId;
            
            // Create filter configuration
            Map<String, Object> filters = new HashMap<>();
            
            // Channel filters
            filters.put("pushEnabled", preferences.isPushEnabled());
            filters.put("emailEnabled", preferences.isEmailEnabled());
            filters.put("smsEnabled", preferences.isSmsEnabled());
            
            // Category filters
            filters.put("transactionNotifications", preferences.isTransactionNotificationsEnabled());
            filters.put("socialNotifications", preferences.isSocialNotificationsEnabled());
            filters.put("marketingNotifications", preferences.isMarketingNotificationsEnabled());
            filters.put("securityNotifications", preferences.isSecurityNotificationsEnabled());
            
            // Frequency limits
            filters.put("maxPushPerDay", preferences.getMaxPushPerDay());
            filters.put("maxEmailPerDay", preferences.getMaxEmailPerDay());
            filters.put("maxSmsPerDay", preferences.getMaxSmsPerDay());
            
            // Quiet hours
            filters.put("quietHoursStart", preferences.getQuietHoursStart());
            filters.put("quietHoursEnd", preferences.getQuietHoursEnd());
            filters.put("quietHoursEnabled", preferences.isQuietHoursEnabled());
            
            // Amount thresholds
            filters.put("minTransactionAmount", preferences.getMinTransactionAmountForNotification());
            
            // Blocked senders
            if (preferences.getBlockedSenders() != null) {
                filters.put("blockedSenders", new HashSet<>(preferences.getBlockedSenders()));
            }
            
            // Store in Redis with TTL
            redisTemplate.opsForHash().putAll(filterKey, filters);
            redisTemplate.expire(filterKey, 24, TimeUnit.HOURS);
            
            // Update real-time rate limiters
            updateRateLimiters(userId, preferences);
            
            // Update digest schedule if needed
            if (preferences.getEmailDigestFrequency() != DigestFrequency.NEVER) {
                scheduleDigest(userId, preferences.getEmailDigestFrequency());
            }
            
            log.debug("Updated real-time filters for user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to update real-time filters for user {}", userId, e);
        }
    }
    
    private void updateRateLimiters(UUID userId, NotificationPreferences preferences) {
        // Update rate limiting buckets in Redis
        String rateLimitKey = "notification:ratelimit:" + userId;
        
        Map<String, Object> limits = new HashMap<>();
        limits.put("push:daily", preferences.getMaxPushPerDay());
        limits.put("email:daily", preferences.getMaxEmailPerDay());
        limits.put("sms:daily", preferences.getMaxSmsPerDay());
        limits.put("reset_time", LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());
        
        redisTemplate.opsForHash().putAll(rateLimitKey, limits);
        redisTemplate.expire(rateLimitKey, 25, TimeUnit.HOURS);
    }
    
    private void scheduleDigest(UUID userId, DigestFrequency frequency) {
        // Schedule digest notifications
        String scheduleKey = "notification:digest:schedule:" + userId;
        
        LocalDateTime nextDigest;
        switch (frequency) {
            case DAILY:
                nextDigest = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0);
                break;
            case WEEKLY:
                nextDigest = LocalDateTime.now().with(java.time.DayOfWeek.MONDAY)
                    .plusWeeks(1).withHour(9).withMinute(0).withSecond(0);
                break;
            default:
                return;
        }
        
        redisTemplate.opsForValue().set(scheduleKey, nextDigest.toString(), 30, TimeUnit.DAYS);
    }
    private void updatePreferencesFromRequest(NotificationPreferences preferences, UpdateNotificationPreferencesRequest request) {
        // Update notification preferences from request
        if (request == null || preferences == null) {
            throw new IllegalArgumentException("Request and preferences cannot be null");
        }
        
        // Update channel preferences
        if (request.getPushEnabled() != null) {
            preferences.setPushEnabled(request.getPushEnabled());
        }
        if (request.getEmailEnabled() != null) {
            preferences.setEmailEnabled(request.getEmailEnabled());
        }
        if (request.getSmsEnabled() != null) {
            preferences.setSmsEnabled(request.getSmsEnabled());
        }
        
        // Update category preferences
        if (request.getTransactionNotifications() != null) {
            preferences.setTransactionNotificationsEnabled(request.getTransactionNotifications());
        }
        if (request.getSocialNotifications() != null) {
            preferences.setSocialNotificationsEnabled(request.getSocialNotifications());
        }
        if (request.getMarketingNotifications() != null) {
            preferences.setMarketingNotificationsEnabled(request.getMarketingNotifications());
        }
        if (request.getSecurityNotifications() != null) {
            preferences.setSecurityNotificationsEnabled(request.getSecurityNotifications());
        }
        
        // Update specific channel settings
        if (request.getPushForTransactions() != null) {
            preferences.setPushForTransactions(request.getPushForTransactions());
        }
        if (request.getPushForSocial() != null) {
            preferences.setPushForSocial(request.getPushForSocial());
        }
        if (request.getPushForMarketing() != null) {
            preferences.setPushForMarketing(request.getPushForMarketing());
        }
        
        if (request.getEmailForTransactions() != null) {
            preferences.setEmailForTransactions(request.getEmailForTransactions());
        }
        if (request.getEmailForSocial() != null) {
            preferences.setEmailForSocial(request.getEmailForSocial());
        }
        if (request.getEmailForMarketing() != null) {
            preferences.setEmailForMarketing(request.getEmailForMarketing());
        }
        
        if (request.getSmsForTransactions() != null) {
            preferences.setSmsForTransactions(request.getSmsForTransactions());
        }
        if (request.getSmsForSecurity() != null) {
            preferences.setSmsForSecurity(request.getSmsForSecurity());
        }
        
        // Update frequency limits
        if (request.getMaxPushPerDay() != null && request.getMaxPushPerDay() > 0) {
            preferences.setMaxPushPerDay(Math.min(request.getMaxPushPerDay(), 100));
        }
        if (request.getMaxEmailPerDay() != null && request.getMaxEmailPerDay() > 0) {
            preferences.setMaxEmailPerDay(Math.min(request.getMaxEmailPerDay(), 50));
        }
        if (request.getMaxSmsPerDay() != null && request.getMaxSmsPerDay() > 0) {
            preferences.setMaxSmsPerDay(Math.min(request.getMaxSmsPerDay(), 20));
        }
        
        // Update quiet hours
        if (request.getQuietHoursEnabled() != null) {
            preferences.setQuietHoursEnabled(request.getQuietHoursEnabled());
        }
        if (request.getQuietHoursStart() != null) {
            preferences.setQuietHoursStart(request.getQuietHoursStart());
        }
        if (request.getQuietHoursEnd() != null) {
            preferences.setQuietHoursEnd(request.getQuietHoursEnd());
        }
        
        // Update digest settings
        if (request.getEmailDigestFrequency() != null) {
            preferences.setEmailDigestFrequency(request.getEmailDigestFrequency());
        }
        if (request.getDigestTime() != null) {
            preferences.setDigestTime(request.getDigestTime());
        }
        
        // Update thresholds
        if (request.getMinTransactionAmount() != null && request.getMinTransactionAmount().compareTo(BigDecimal.ZERO) >= 0) {
            preferences.setMinTransactionAmountForNotification(request.getMinTransactionAmount());
        }
        
        // Update blocked senders
        if (request.getBlockedSenders() != null) {
            preferences.setBlockedSenders(new HashSet<>(request.getBlockedSenders()));
        }
        if (request.getUnblockedSenders() != null) {
            preferences.getBlockedSenders().removeAll(request.getUnblockedSenders());
        }
        
        // Update language preference
        if (request.getPreferredLanguage() != null && !request.getPreferredLanguage().isEmpty()) {
            preferences.setPreferredLanguage(request.getPreferredLanguage());
        }
        
        // Update timezone
        if (request.getTimezone() != null && !request.getTimezone().isEmpty()) {
            preferences.setTimezone(request.getTimezone());
        }
        
        // Update metadata
        preferences.setLastUpdated(LocalDateTime.now());
        
        // Validate the updated preferences
        validatePreferences(preferences);
    }
    
    private void validatePreferences(NotificationPreferences preferences) {
        // Validate notification preferences
        if (preferences.getQuietHoursStart() < 0 || preferences.getQuietHoursStart() > 23) {
            throw new IllegalArgumentException("Quiet hours start must be between 0 and 23");
        }
        
        if (preferences.getQuietHoursEnd() < 0 || preferences.getQuietHoursEnd() > 23) {
            throw new IllegalArgumentException("Quiet hours end must be between 0 and 23");
        }
        
        if (preferences.getMaxPushPerDay() < 0 || preferences.getMaxPushPerDay() > 100) {
            throw new IllegalArgumentException("Max push notifications per day must be between 0 and 100");
        }
        
        if (preferences.getMaxEmailPerDay() < 0 || preferences.getMaxEmailPerDay() > 50) {
            throw new IllegalArgumentException("Max email notifications per day must be between 0 and 50");
        }
        
        if (preferences.getMaxSmsPerDay() < 0 || preferences.getMaxSmsPerDay() > 20) {
            throw new IllegalArgumentException("Max SMS notifications per day must be between 0 and 20");
        }
    }

    // Enum definitions
    public enum DigestType { DAILY, WEEKLY, MONTHLY }
    public enum DigestFrequency { DAILY, WEEKLY, NEVER }
    public enum NotificationStatus { SENT, DELIVERED, READ, FAILED }
    public enum AnalyticsPeriod { WEEK, MONTH, QUARTER, YEAR }

    // Helper classes
    private static class NotificationResult {
        private final boolean success;
        private final String message;
        private final UUID notificationId;

        private NotificationResult(boolean success, String message, UUID notificationId) {
            this.success = success;
            this.message = message;
            this.notificationId = notificationId;
        }

        public static NotificationResult success(UUID notificationId) {
            return new NotificationResult(true, "Success", notificationId);
        }

        public static NotificationResult skipped(String reason) {
            return new NotificationResult(false, reason, null);
        }

        public static NotificationResult filtered(String reason) {
            return new NotificationResult(false, reason, null);
        }

        public static NotificationResult error(String error) {
            return new NotificationResult(false, error, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public UUID getNotificationId() { return notificationId; }
    }
}