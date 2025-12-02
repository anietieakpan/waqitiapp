package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dead Letter Queue Message model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterMessage {
    
    private String id;
    private String originalTopic;
    private String messageKey;
    private String messageContent;
    private Map<String, Object> headers;
    
    // Failure information
    private String failureReason;
    private String failureType;
    private String failureClass;
    private String stackTrace;
    
    // Retry information
    private int retryCount;
    private int maxRetries;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lastRetryAt;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime firstFailedAt;
    private LocalDateTime lastFailedAt;
    
    // Quarantine information
    private boolean quarantined;
    private LocalDateTime quarantinedAt;
    private String quarantineReason;
    
    // Processing metadata
    private String processingNode;
    private String consumerGroup;
    private Map<String, Object> metadata;
    
    // Additional fields for recovery
    private Object originalMessage;
    private LocalDateTime retentionUntil;
    private Map<String, String> originalHeaders;
    private String dlqTopic;
    private int attemptCount;
    private boolean recoverable;
    
    /**
     * Check if message is recoverable (can be retried)
     */
    public boolean isRecoverable() {
        return !quarantined && 
               retryCount < maxRetries && 
               (retentionUntil == null || LocalDateTime.now().isBefore(retentionUntil));
    }
    
    /**
     * Get retry attempt count
     */
    public int getAttemptCount() {
        return retryCount;
    }
    
    /**
     * Get original message object
     */
    public Object getOriginalMessage() {
        return originalMessage != null ? originalMessage : messageContent;
    }
    
    /**
     * Get message content
     */
    public String getMessage() {
        return messageContent;
    }
}