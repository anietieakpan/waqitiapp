package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request for notification analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsRequest {
    
    /**
     * Start date for analytics
     */
    private LocalDate startDate;
    
    /**
     * End date for analytics
     */
    private LocalDate endDate;
    
    /**
     * Channels to include
     */
    private List<NotificationChannel> channels;
    
    /**
     * Categories to include
     */
    private List<String> categories;
    
    /**
     * User IDs to filter by
     */
    private List<String> userIds;
    
    /**
     * Group by option
     */
    private GroupBy groupBy;
    
    /**
     * Metrics to include
     */
    private List<Metric> metrics;
    
    /**
     * Timezone for date grouping
     */
    private String timezone;
    
    /**
     * Include detailed breakdown
     */
    @Builder.Default
    private boolean includeDetails = false;
    
    public enum GroupBy {
        DAY,
        WEEK,
        MONTH,
        CHANNEL,
        CATEGORY,
        USER
    }
    
    public enum Metric {
        SENT,
        DELIVERED,
        OPENED,
        CLICKED,
        BOUNCED,
        COMPLAINED,
        FAILED,
        COST
    }
}