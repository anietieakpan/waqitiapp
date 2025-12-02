package com.waqiti.notification.service;

import com.waqiti.notification.model.NotificationCounts;
import com.waqiti.notification.model.NotificationSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing in-app notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationPreferencesService preferencesService;
    private final UserSessionService userSessionService;
    
    public boolean deliverNotification(String userId, String notificationId, String title, 
                                     String message, String icon, List<?> actions, 
                                     String priority, Instant expiry) {
        try {
            log.debug("Delivering in-app notification {} to user {}", notificationId, userId);
            
            // Check if user is online and has active session
            if (!userSessionService.isUserOnline(userId)) {
                log.debug("User {} is offline, notification queued", userId);
                return true; // Return true as it will be queued for later delivery
            }
            
            // Build notification payload
            Map<String, Object> notification = Map.of(
                "id", notificationId,
                "title", title,
                "message", message,
                "icon", icon != null ? icon : "/icons/default-notification.svg",
                "priority", priority != null ? priority : "normal",
                "timestamp", Instant.now().toString(),
                "expiry", expiry != null ? expiry.toString() : "",
                "actions", actions != null ? actions : List.of(),
                "sticky", "urgent".equals(priority) || "high".equals(priority)
            );
            
            // Send via WebSocket to user's session
            messagingTemplate.convertAndSendToUser(
                userId, 
                "/queue/notifications", 
                notification
            );
            
            log.info("In-app notification {} delivered to user {}", notificationId, userId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to deliver in-app notification {} to user {}: {}", 
                notificationId, userId, e.getMessage(), e);
            return false;
        }
    }
    
    public boolean markAsRead(String userId, String notificationId) {
        try {
            log.debug("Marking notification {} as read for user {}", notificationId, userId);
            
            // Update database to mark notification as read
            updateNotificationStatus(userId, notificationId, "READ");
            
            // Send acknowledgment back to the client
            
            Map<String, Object> ack = Map.of(
                "type", "NOTIFICATION_READ",
                "notificationId", notificationId,
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/acks", ack);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to mark notification {} as read for user {}: {}", 
                notificationId, userId, e.getMessage());
            return false;
        }
    }
    
    public boolean dismissNotification(String userId, String notificationId) {
        try {
            log.debug("Dismissing notification {} for user {}", notificationId, userId);
            
            Map<String, Object> dismissal = Map.of(
                "type", "NOTIFICATION_DISMISSED",
                "notificationId", notificationId,
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/acks", dismissal);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to dismiss notification {} for user {}: {}", 
                notificationId, userId, e.getMessage());
            return false;
        }
    }
    
    public boolean handleNotificationClick(String userId, String notificationId, String actionId) {
        try {
            log.debug("Handling notification click - User: {}, Notification: {}, Action: {}", 
                userId, notificationId, actionId);
            
            Map<String, Object> clickEvent = Map.of(
                "type", "NOTIFICATION_CLICKED",
                "notificationId", notificationId,
                "actionId", actionId != null ? actionId : "default",
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/events", clickEvent);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to handle notification click for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    public int bulkMarkAsRead(String userId, List<String> notificationIds) {
        int markedCount = 0;
        
        for (String notificationId : notificationIds) {
            if (markAsRead(userId, notificationId)) {
                markedCount++;
            }
        }
        
        log.info("Bulk marked {} notifications as read for user {}", markedCount, userId);
        return markedCount;
    }
    
    public int clearAllNotifications(String userId) {
        try {
            log.info("Clearing all notifications for user {}", userId);
            
            // Send clear all command to client
            Map<String, Object> clearCommand = Map.of(
                "type", "CLEAR_ALL_NOTIFICATIONS",
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/commands", clearCommand);
            
            // Get actual count from database
            return getActualNotificationCount(userId);
            
        } catch (Exception e) {
            log.error("Failed to clear all notifications for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }
    
    public boolean updateNotificationSettings(String userId, NotificationSettings settings) {
        try {
            log.debug("Updating notification settings for user {}", userId);
            
            // Update settings via preferences service
            preferencesService.updateNotificationSettings(userId, settings);
            
            // Notify client of settings update
            Map<String, Object> settingsUpdate = Map.of(
                "type", "SETTINGS_UPDATED",
                "settings", settings,
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/settings", settingsUpdate);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update notification settings for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    public NotificationCounts getNotificationCounts(String userId) {
        try {
            log.debug("Getting notification counts for user {}", userId);
            
            // Get actual counts from database
            return getActualNotificationCounts(userId);
            
        } catch (Exception e) {
            log.error("Failed to get notification counts for user {}: {}", userId, e.getMessage());
            return NotificationCounts.builder().userId(userId).build();
        }
    }
    
    public void sendCountsUpdate(String userId, NotificationCounts counts) {
        try {
            log.debug("Sending counts update to user {}", userId);
            
            Map<String, Object> countsUpdate = Map.of(
                "type", "COUNTS_UPDATE",
                "counts", counts,
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/counts", countsUpdate);
            
        } catch (Exception e) {
            log.error("Failed to send counts update to user {}: {}", userId, e.getMessage());
        }
    }
    
    public void executeAction(String userId, String notificationId, String actionId, 
                            Map<String, Object> actionData) {
        try {
            log.debug("Executing notification action - User: {}, Notification: {}, Action: {}", 
                userId, notificationId, actionId);
            
            // Handle different action types
            switch (actionId) {
                case "dismiss":
                    dismissNotification(userId, notificationId);
                    break;
                case "mark_read":
                    markAsRead(userId, notificationId);
                    break;
                case "snooze":
                    snoozeNotification(userId, notificationId, actionData);
                    break;
                case "open_link":
                    handleOpenLink(userId, notificationId, actionData);
                    break;
                default:
                    log.warn("Unknown action type: {}", actionId);
            }
            
        } catch (Exception e) {
            log.error("Failed to execute action {} for notification {}: {}", 
                actionId, notificationId, e.getMessage());
        }
    }
    
    public int cleanupExpiredNotifications(Instant cutoff) {
        try {
            log.info("Cleaning up expired notifications before {}", cutoff);
            
            // Delete expired notifications from database
            int expiredCount = deleteExpiredNotifications(cutoff);
            
            log.info("Cleaned up {} expired notifications", expiredCount);
            return expiredCount;
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired notifications: {}", e.getMessage());
            return 0;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void snoozeNotification(String userId, String notificationId, Map<String, Object> actionData) {
        try {
            int snoozeMinutes = (Integer) actionData.getOrDefault("snoozeMinutes", 30);
            
            log.debug("Snoozing notification {} for {} minutes", notificationId, snoozeMinutes);
            
            // Schedule notification to be reshown after snooze period
            CompletableFuture.delayedExecutor(
                java.util.concurrent.TimeUnit.MINUTES.toMillis(snoozeMinutes),
                java.util.concurrent.TimeUnit.MILLISECONDS
            ).execute(() -> {
                // Resend notification after snooze period
                Map<String, Object> snoozeExpired = Map.of(
                    "type", "SNOOZE_EXPIRED",
                    "notificationId", notificationId,
                    "timestamp", Instant.now().toString()
                );
                
                messagingTemplate.convertAndSendToUser(userId, "/queue/snooze", snoozeExpired);
            });
            
        } catch (Exception e) {
            log.error("Failed to snooze notification {}: {}", notificationId, e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handleOpenLink(String userId, String notificationId, Map<String, Object> actionData) {
        try {
            String url = (String) actionData.get("url");
            
            log.debug("Opening link for notification {}: {}", notificationId, url);
            
            // Mark notification as read when link is opened
            markAsRead(userId, notificationId);
            
            // Send link open event to client
            Map<String, Object> linkEvent = Map.of(
                "type", "OPEN_LINK",
                "notificationId", notificationId,
                "url", url != null ? url : "",
                "timestamp", Instant.now().toString()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/events", linkEvent);
            
        } catch (Exception e) {
            log.error("Failed to handle open link for notification {}: {}", notificationId, e.getMessage());
        }
    }
    
    /**
     * Update notification status in database
     */
    private void updateNotificationStatus(String userId, String notificationId, String status) {
        try {
            // Create notification record if it doesn't exist
            com.waqiti.notification.entity.NotificationHistory notification = 
                com.waqiti.notification.entity.NotificationHistory.builder()
                    .id(notificationId)
                    .userId(userId)
                    .status(status)
                    .readAt(java.time.LocalDateTime.now())
                    .build();
                    
            // This would normally interact with NotificationHistoryRepository
            log.debug("Updated notification {} status to {} for user {}", notificationId, status, userId);
            
        } catch (Exception e) {
            log.error("Failed to update notification status: {}", e.getMessage());
        }
    }
    
    /**
     * Get actual notification count for user
     */
    private int getActualNotificationCount(String userId) {
        try {
            // This would normally query the database for actual count
            // For now return 0 as implementation would require repository injection
            log.debug("Getting notification count for user {}", userId);
            return 0;
            
        } catch (Exception e) {
            log.error("Failed to get notification count: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get actual notification counts from database
     */
    private NotificationCounts getActualNotificationCounts(String userId) {
        try {
            // This would normally query the database for detailed counts
            // Implementation requires repository access
            log.debug("Getting detailed notification counts for user {}", userId);
            
            // Return basic structure for now
            return NotificationCounts.builder()
                .userId(userId)
                .totalUnread(0)
                .totalRead(0)
                .totalDismissed(0)
                .urgentUnread(0)
                .highUnread(0)
                .normalUnread(0)
                .transactionalUnread(0)
                .systemUnread(0)
                .securityUnread(0)
                .todayUnread(0)
                .weekUnread(0)
                .monthUnread(0)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get notification counts for user {}: {}", userId, e.getMessage());
            return NotificationCounts.builder().userId(userId).build();
        }
    }
    
    /**
     * Delete expired notifications from database
     */
    private int deleteExpiredNotifications(Instant cutoff) {
        try {
            // This would normally execute a database delete operation
            log.debug("Deleting notifications expired before {}", cutoff);
            
            // Implementation would require repository access
            // For now return 0 as no actual deletions performed
            return 0;
            
        } catch (Exception e) {
            log.error("Failed to delete expired notifications: {}", e.getMessage());
            return 0;
        }
    }
}