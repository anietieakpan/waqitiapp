package com.waqiti.common.messaging.deadletter;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dead Letter Message Entity
 * 
 * Contains comprehensive information about failed messages including
 * original payload, failure context, and processing history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterMessage {
    
    // Message identification
    private String messageId;
    private String correlationId;
    private String originalTopic;
    private String dlqTopic;
    
    // Original message data
    private String originalPayload;
    private Map<String, String> originalHeaders;
    private String messageType;
    private String contentType;
    
    // Failure information
    private String failureReason;
    private String errorType;
    private String errorCode;
    private String stackTrace;
    private String failedConsumerGroup;
    private String failedPartition;
    private Long failedOffset;
    
    // Processing context
    private int retryCount;
    private boolean poisonMessage;
    private long processingDurationMs;
    private String processingNode;
    private String applicationVersion;
    
    // Timing information
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime originalTimestamp;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dlqTimestamp;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastRetryAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime firstFailedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextRetryAt;
    
    // Processing history
    private List<ProcessingHistory> processingHistory;
    
    // Business context (if available)
    private String userId;
    private String transactionId;
    private String accountId;
    private String customerId;
    private String sessionId;
    
    // Classification
    private MessagePriority priority;
    private MessageCategory category;
    private boolean financialMessage;
    private boolean personalDataMessage;
    
    // Recovery metadata
    private Map<String, String> recoveryMetadata;
    private String recoveryStrategy;
    private boolean manualInterventionRequired;
    
    public enum MessagePriority {
        CRITICAL, HIGH, MEDIUM, LOW
    }
    
    public enum MessageCategory {
        PAYMENT, TRANSACTION, NOTIFICATION, AUDIT, COMPLIANCE, INTEGRATION, OTHER
    }
    
    /**
     * Determines if this message requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return priority == MessagePriority.CRITICAL || 
               financialMessage || 
               poisonMessage ||
               retryCount >= 3;
    }
    
    /**
     * Gets the age of this message in hours
     */
    public long getAgeInHours() {
        if (originalTimestamp == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.HOURS.between(originalTimestamp, LocalDateTime.now());
    }
    
    /**
     * Gets the time since last retry attempt in minutes
     */
    public long getMinutesSinceLastRetry() {
        if (lastRetryAt == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.MINUTES.between(lastRetryAt, LocalDateTime.now());
    }
}