package com.waqiti.websocket.service;

import com.waqiti.websocket.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String PENDING_NOTIFICATIONS_KEY = "notifications:pending:";
    private static final Duration NOTIFICATION_TTL = Duration.ofDays(7);
    
    /**
     * Broadcast notification to user
     */
    public void broadcastNotification(String userId, NotificationMessage notification) {
        log.debug("Broadcasting notification to user: {}", userId);
        
        // Send via WebSocket if user is connected
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/notifications",
            notification
        );
        
        // Store in Redis for offline delivery
        storeNotificationForOfflineDelivery(userId, notification);
    }
    
    /**
     * Send all pending notifications to user
     */
    public void sendPendingNotifications(String userId) {
        String key = PENDING_NOTIFICATIONS_KEY + userId;
        Set<Object> pendingNotifications = redisTemplate.opsForSet().members(key);
        
        if (pendingNotifications != null && !pendingNotifications.isEmpty()) {
            log.info("Sending {} pending notifications to user: {}", pendingNotifications.size(), userId);
            
            pendingNotifications.forEach(notification -> {
                messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/notifications",
                    notification
                );
            });
            
            // Clear pending notifications
            redisTemplate.delete(key);
        }
    }
    
    /**
     * Broadcast notification to multiple users
     */
    public void broadcastToUsers(List<String> userIds, NotificationMessage notification) {
        userIds.forEach(userId -> broadcastNotification(userId, notification));
    }
    
    /**
     * Broadcast notification to a topic
     */
    public void broadcastToTopic(String topic, NotificationMessage notification) {
        log.debug("Broadcasting notification to topic: {}", topic);
        messagingTemplate.convertAndSend("/topic/" + topic, notification);
    }
    
    /**
     * Store notification for offline delivery
     */
    private void storeNotificationForOfflineDelivery(String userId, NotificationMessage notification) {
        String key = PENDING_NOTIFICATIONS_KEY + userId;
        redisTemplate.opsForSet().add(key, notification);
        redisTemplate.expire(key, NOTIFICATION_TTL);
    }
    
    /**
     * Mark notification as read
     */
    public void markAsRead(String userId, String notificationId) {
        log.debug("Marking notification {} as read for user: {}", notificationId, userId);
        
        NotificationMessage readUpdate = NotificationMessage.builder()
            .id(notificationId)
            .type("NOTIFICATION_READ")
            .timestamp(Instant.now())
            .build();
        
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/notification-updates",
            readUpdate
        );
    }
    
    /**
     * Get notification count for user
     */
    public long getPendingNotificationCount(String userId) {
        String key = PENDING_NOTIFICATIONS_KEY + userId;
        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count : 0;
    }
}