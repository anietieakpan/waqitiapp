package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Notification history response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationHistory {
    
    /**
     * Total count of notifications
     */
    private long totalCount;
    
    /**
     * Current page
     */
    private int currentPage;
    
    /**
     * Total pages
     */
    private int totalPages;
    
    /**
     * Page size
     */
    private int pageSize;
    
    /**
     * List of notifications
     */
    private List<NotificationHistoryItem> notifications;
    
    /**
     * Aggregated statistics
     */
    private HistoryStatistics statistics;
    
    /**
     * Applied filters
     */
    private HistoryFilter appliedFilter;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationHistoryItem {
        private String notificationId;
        private String userId;
        private NotificationChannel channel;
        private String category;
        private String subject;
        private String preview;
        private NotificationResult.DeliveryStatus status;
        private java.time.Instant sentAt;
        private java.time.Instant deliveredAt;
        private java.time.Instant readAt;
        private boolean starred;
        private boolean archived;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryStatistics {
        private long totalSent;
        private long totalDelivered;
        private long totalRead;
        private long totalFailed;
        private Map<NotificationChannel, Long> countByChannel;
        private Map<String, Long> countByCategory;
        private Map<NotificationResult.DeliveryStatus, Long> countByStatus;
    }
}