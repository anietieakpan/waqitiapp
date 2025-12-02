package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of bulk recovery operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRecoveryResult {
    
    private String dlqTopic;
    private int totalMessages;
    private int successCount;
    private int failureCount;
    private List<String> errors;
    private List<String> recoveredMessageIds;
    private List<String> failedMessageIds;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long durationMs;
    
    public double getSuccessRate() {
        return totalMessages > 0 ? (double) successCount / totalMessages * 100 : 0;
    }
    
    public boolean isFullySuccessful() {
        return failureCount == 0 && successCount == totalMessages;
    }
}