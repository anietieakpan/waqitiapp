package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Statistics for a DLQ topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqTopicStats {
    
    private String topicName;
    private int totalMessages;
    private int quarantinedMessages;
    private int retryingMessages;
    private int permanentlyFailedMessages;
    private LocalDateTime oldestMessage;
    private LocalDateTime newestMessage;
    private double averageRetryCount;
    private String mostCommonFailureType;
    private int totalRecoveredMessages;
    
    /**
     * Get health status of this topic
     */
    public HealthStatus getHealthStatus() {
        if (quarantinedMessages > totalMessages * 0.5) {
            return HealthStatus.CRITICAL;
        } else if (quarantinedMessages > totalMessages * 0.2) {
            return HealthStatus.WARNING;
        } else if (totalMessages == 0) {
            return HealthStatus.HEALTHY;
        } else {
            return HealthStatus.HEALTHY;
        }
    }
    
    /**
     * Calculate recovery rate
     */
    public double getRecoveryRate() {
        if (totalMessages == 0) return 1.0;
        return (double) totalRecoveredMessages / totalMessages;
    }
    
    public enum HealthStatus {
        HEALTHY, WARNING, CRITICAL
    }
}