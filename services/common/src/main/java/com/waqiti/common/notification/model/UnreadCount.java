package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Unread notification count
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCount {
    
    /**
     * Total unread count
     */
    private long totalUnread;
    
    /**
     * Unread count by channel
     */
    private Map<NotificationChannel, Long> unreadByChannel;
    
    /**
     * Unread count by category
     */
    private Map<String, Long> unreadByCategory;
    
    /**
     * Unread count by priority
     */
    private Map<NotificationRequest.Priority, Long> unreadByPriority;
    
    /**
     * Timestamp of oldest unread
     */
    private Instant oldestUnreadAt;
    
    /**
     * Timestamp of newest unread
     */
    private Instant newestUnreadAt;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Last updated timestamp
     */
    private Instant lastUpdated;
}