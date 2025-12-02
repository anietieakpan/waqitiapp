package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of batch notification processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchNotificationResult {
    
    /**
     * Batch ID
     */
    private String batchId;
    
    /**
     * Individual results
     */
    private List<NotificationResult> results;
    
    /**
     * Batch status
     */
    private BatchStatus status;
    
    /**
     * Total requests in batch
     */
    private int totalRequests;
    
    /**
     * Successful deliveries
     */
    private int successCount;
    
    /**
     * Failed deliveries
     */
    private int failureCount;
    
    /**
     * Pending deliveries
     */
    private int pendingCount;
    
    /**
     * Skipped requests
     */
    private int skippedCount;
    
    /**
     * Start time
     */
    private Instant startTime;
    
    /**
     * End time
     */
    private Instant endTime;
    
    /**
     * Total processing time
     */
    private long processingTimeMs;
    
    /**
     * Error summary
     */
    private Map<String, Integer> errorSummary;
    
    /**
     * Processing statistics
     */
    private ProcessingStats processingStats;
    
    /**
     * Batch metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStats {
        private double averageProcessingTimeMs;
        private long minProcessingTimeMs;
        private long maxProcessingTimeMs;
        private double successRate;
        private int retryCount;
        private Map<NotificationChannel, ChannelStats> channelStats;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelStats {
        private int totalSent;
        private int successful;
        private int failed;
        private double averageDeliveryTimeMs;
    }
    
    public enum BatchStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED,
        CANCELLED
    }
}