package com.waqiti.webhook.domain;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dead letter webhook for failed deliveries
 * Stores webhooks that could not be delivered after all retry attempts
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeadLetterWebhook {
    private String webhookId;
    private String eventId;
    private String eventType;
    private String payload;
    private String endpointUrl;
    private String failureReason;
    private String lastErrorMessage;
    private Integer attemptCount;
    private LocalDateTime movedToDlqAt;
    private LocalDateTime originalCreatedAt;
    private Map<String, Object> metadata;
    private boolean manualRetryRequested;
    private String retryRequestedBy;
    private LocalDateTime retryRequestedAt;
    private DeadLetterStatus status;
    
    public enum DeadLetterStatus {
        QUEUED,
        PROCESSING_RETRY,
        RETRY_SUCCESS,
        RETRY_FAILED,
        ARCHIVED,
        PURGED
    }
    
    public boolean isRetryable() {
        return status == DeadLetterStatus.QUEUED || 
               status == DeadLetterStatus.RETRY_FAILED;
    }
    
    public boolean hasBeenRetried() {
        return manualRetryRequested && retryRequestedAt != null;
    }
    
    public long getAgeInHours() {
        if (originalCreatedAt == null) return 0;
        return java.time.Duration.between(originalCreatedAt, LocalDateTime.now()).toHours();
    }
}