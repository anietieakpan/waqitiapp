package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Result of subscription operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResult {
    
    /**
     * Subscription ID
     */
    private String subscriptionId;
    
    /**
     * Operation type
     */
    private OperationType operationType;
    
    /**
     * Operation status
     */
    private OperationStatus status;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Topic/Category subscribed
     */
    private String topic;
    
    /**
     * Subscription timestamp
     */
    private Instant timestamp;
    
    /**
     * Subscription preferences
     */
    private Map<String, Object> preferences;
    
    /**
     * Error message if failed
     */
    private String errorMessage;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    public enum OperationType {
        SUBSCRIBE,
        UNSUBSCRIBE,
        UPDATE_PREFERENCES,
        BULK_SUBSCRIBE,
        BULK_UNSUBSCRIBE
    }
    
    public enum OperationStatus {
        SUCCESS,
        FAILED,
        PENDING_CONFIRMATION,
        ALREADY_EXISTS,
        NOT_FOUND
    }
}