package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of multi-channel notification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiChannelNotificationResult {
    
    /**
     * Overall request ID
     */
    private String requestId;
    
    /**
     * Results for each channel
     */
    private Map<NotificationChannel, NotificationResult> channelResults;
    
    /**
     * Overall status
     */
    private OverallStatus overallStatus;
    
    /**
     * Successful channels
     */
    private List<NotificationChannel> successfulChannels;
    
    /**
     * Failed channels
     */
    private List<NotificationChannel> failedChannels;
    
    /**
     * Channels not attempted
     */
    private List<NotificationChannel> skippedChannels;
    
    /**
     * Total execution time
     */
    private long totalExecutionTimeMs;
    
    /**
     * Start time
     */
    private Instant startTime;
    
    /**
     * End time
     */
    private Instant endTime;
    
    /**
     * Fallback used
     */
    private boolean fallbackUsed;
    
    /**
     * Summary statistics
     */
    private SummaryStats summaryStats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private int totalChannels;
        private int successfulDeliveries;
        private int failedDeliveries;
        private int pendingDeliveries;
        private double successRate;
        private long averageDeliveryTimeMs;
    }
    
    public enum OverallStatus {
        ALL_SUCCESS,
        PARTIAL_SUCCESS,
        ALL_FAILED,
        IN_PROGRESS
    }
}