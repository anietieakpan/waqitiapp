package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to send notifications in batch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchNotificationRequest {
    
    /**
     * Batch ID for tracking
     */
    private String batchId;
    
    /**
     * List of notification requests
     */
    private List<NotificationRequest> requests;
    
    /**
     * Batch processing mode
     */
    @Builder.Default
    private ProcessingMode processingMode = ProcessingMode.PARALLEL;
    
    /**
     * Maximum concurrent requests
     */
    @Builder.Default
    private int maxConcurrency = 10;
    
    /**
     * Whether to continue on failure
     */
    @Builder.Default
    private boolean continueOnFailure = true;
    
    /**
     * Batch priority
     */
    @Builder.Default
    private BatchPriority priority = BatchPriority.NORMAL;
    
    /**
     * Rate limiting settings
     */
    private RateLimitSettings rateLimitSettings;
    
    /**
     * Deduplication settings
     */
    private DeduplicationSettings deduplicationSettings;
    
    /**
     * Batch metadata
     */
    private BatchMetadata metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitSettings {
        private int requestsPerSecond;
        private int burstSize;
        private long delayBetweenRequestsMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeduplicationSettings {
        private boolean enabled;
        private String deduplicationKey;
        private long deduplicationWindowMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchMetadata {
        private String source;
        private String campaign;
        private String category;
        private String initiatedBy;
    }
    
    public enum ProcessingMode {
        SEQUENTIAL,
        PARALLEL,
        SCHEDULED
    }
    
    public enum BatchPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
}