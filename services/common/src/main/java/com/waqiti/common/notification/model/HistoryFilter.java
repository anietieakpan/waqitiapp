package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Filter for notification history queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryFilter {
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Notification IDs
     */
    private List<String> notificationIds;
    
    /**
     * Channels to filter by
     */
    private List<NotificationChannel> channels;
    
    /**
     * Categories to filter by
     */
    private List<String> categories;
    
    /**
     * Delivery status
     */
    private List<NotificationResult.DeliveryStatus> statuses;
    
    /**
     * Start time
     */
    private Instant startTime;
    
    /**
     * End time
     */
    private Instant endTime;
    
    /**
     * Search text
     */
    private String searchText;
    
    /**
     * Include read notifications
     */
    @Builder.Default
    private boolean includeRead = true;
    
    /**
     * Include deleted notifications
     */
    @Builder.Default
    private boolean includeDeleted = false;
    
    /**
     * Sort by field
     */
    @Builder.Default
    private SortBy sortBy = SortBy.SENT_TIME;
    
    /**
     * Sort order
     */
    @Builder.Default
    private SortOrder sortOrder = SortOrder.DESC;
    
    /**
     * Page number
     */
    @Builder.Default
    private int page = 0;
    
    /**
     * Page size
     */
    @Builder.Default
    private int pageSize = 20;
    
    public enum SortBy {
        SENT_TIME,
        DELIVERED_TIME,
        READ_TIME,
        PRIORITY,
        CHANNEL
    }
    
    public enum SortOrder {
        ASC,
        DESC
    }
}