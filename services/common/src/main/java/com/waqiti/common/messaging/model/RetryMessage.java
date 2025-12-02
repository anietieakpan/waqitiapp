package com.waqiti.common.messaging.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRITICAL PRODUCTION FIX - RetryMessage
 * Model for messages in retry queue with exponential backoff
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryMessage {
    
    private String id;
    private String originalTopic;
    private Object originalMessage;
    private int attemptNumber;
    private int maxAttempts;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextRetryAt;
    
    private String lastError;
    private Map<String, Object> originalHeaders;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    /**
     * Check if retry is due
     */
    public boolean isRetryDue() {
        return nextRetryAt != null && LocalDateTime.now().isAfter(nextRetryAt);
    }
    
    /**
     * Check if max attempts reached
     */
    public boolean isMaxAttemptsReached() {
        return attemptNumber >= maxAttempts;
    }
    
    /**
     * Get time until next retry
     */
    public long getSecondsUntilRetry() {
        if (nextRetryAt == null) return 0;
        return java.time.Duration.between(LocalDateTime.now(), nextRetryAt).getSeconds();
    }
}