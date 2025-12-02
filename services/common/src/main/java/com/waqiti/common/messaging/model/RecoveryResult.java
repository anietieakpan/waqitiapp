package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of message recovery operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResult {
    
    private String messageId;
    private boolean success;
    private String originalTopic;
    private String targetTopic;
    private LocalDateTime recoveredAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private int attemptNumber;
    private long processingTimeMs;
    private Map<String, Object> metadata;
    private RecoveryStrategy strategy;
    
    /**
     * Create successful recovery result with message
     */
    public static RecoveryResult success(String message) {
        return RecoveryResult.builder()
            .success(true)
            .failureReason(message)
            .recoveredAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create successful recovery result with ID and topic
     */
    public static RecoveryResult success(String messageId, String originalTopic) {
        return RecoveryResult.builder()
            .messageId(messageId)
            .success(true)
            .originalTopic(originalTopic)
            .recoveredAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create failed recovery result with reason
     */
    public static RecoveryResult failure(String reason) {
        return RecoveryResult.builder()
            .success(false)
            .failureReason(reason)
            .failedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create failed recovery result with ID, topic and reason
     */
    public static RecoveryResult failure(String messageId, String originalTopic, String reason) {
        return RecoveryResult.builder()
            .messageId(messageId)
            .success(false)
            .originalTopic(originalTopic)
            .failureReason(reason)
            .failedAt(LocalDateTime.now())
            .build();
    }
}

/**
 * Recovery strategy types
 */
enum RecoveryStrategy {
    IMMEDIATE_RETRY,
    DELAYED_RETRY,
    MANUAL_REVIEW,
    DEAD_LETTER,
    QUARANTINE
}