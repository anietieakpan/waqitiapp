package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a batch of email notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailBatch {
    
    private String batchId;
    private String campaignId;
    private String templateId;
    
    // Batch details
    private String batchName;
    private String batchType; // marketing, transactional, system
    private int totalRecipients;
    private List<String> recipientIds;
    private List<String> emailIds;
    
    // Processing status
    private String status; // created, processing, completed, failed, partial
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Processing metrics
    private int processedCount;
    private int successCount;
    private int failedCount;
    private int pendingCount;
    private int skippedCount;
    
    // Performance metrics
    private long processingTimeMs;
    private double successRate;
    private double averageDeliveryTimeMs;
    
    // Segmentation
    private Map<String, Object> segmentCriteria;
    private String segmentQuery;
    
    // Scheduling
    private boolean scheduled;
    private LocalDateTime scheduledAt;
    private String scheduleTimezone;
    
    // Rate limiting
    private int rateLimit; // emails per second
    private int throttleDelay; // ms between sends
    
    // Error tracking
    private List<BatchError> errors;
    private String lastError;
    private LocalDateTime lastErrorAt;
    
    // Metadata
    private Map<String, Object> metadata;
    private Map<String, String> tags;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchError {
        private String emailId;
        private String recipientId;
        private String errorCode;
        private String errorMessage;
        private LocalDateTime occurredAt;
    }
}