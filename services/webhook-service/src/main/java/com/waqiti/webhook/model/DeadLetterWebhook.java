package com.waqiti.webhook.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dead letter webhook for permanently failed deliveries
 */
@Data
@Builder
public class DeadLetterWebhook {
    
    private String webhookId;
    private String eventId;
    private String eventType;
    private String endpointUrl;
    private String payload;
    private String failureReason;
    private int attemptCount;
    private String lastErrorMessage;
    private LocalDateTime originalCreatedAt;
    private LocalDateTime movedToDlqAt;
    private boolean manualRetryRequested;
    private String retryRequestedBy;
    private LocalDateTime retryRequestedAt;
    
    /**
     * Check if webhook can be manually retried
     */
    public boolean canManualRetry() {
        // Allow manual retry if not already requested or if requested more than 24 hours ago
        return !manualRetryRequested || 
               (retryRequestedAt != null && 
                java.time.Duration.between(retryRequestedAt, LocalDateTime.now()).toHours() >= 24);
    }
    
    /**
     * Get age in days since moved to DLQ
     */
    public long getAgeInDaysSinceDlq() {
        return java.time.Duration.between(movedToDlqAt, LocalDateTime.now()).toDays();
    }
    
    /**
     * Check if webhook should be purged (older than retention period)
     */
    public boolean shouldPurge(int retentionDays) {
        return getAgeInDaysSinceDlq() > retentionDays;
    }
}