package com.waqiti.common.messaging.deadletter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dead Letter Queue Hourly Statistics
 * 
 * Provides aggregated statistics for DLQ messages on an hourly basis
 * for monitoring, alerting, and capacity planning purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqHourlyStats {
    
    /**
     * Hour of the day (0-23)
     */
    private Integer hour;
    
    /**
     * Total number of messages in DLQ for this hour
     */
    private Long messageCount;
    
    /**
     * Number of poison messages (repeatedly failed) for this hour
     */
    private Long poisonCount;
    
    /**
     * Hour timestamp for better tracking
     */
    private LocalDateTime hourTimestamp;
    
    /**
     * Average processing time for messages in this hour (milliseconds)
     */
    private Double averageProcessingTime;
    
    /**
     * Peak message count within this hour
     */
    private Long peakMessageCount;
    
    /**
     * Calculate the poison message ratio
     */
    public double getPoisonRatio() {
        if (messageCount == null || messageCount == 0) {
            return 0.0;
        }
        if (poisonCount == null) {
            return 0.0;
        }
        return (double) poisonCount / messageCount;
    }
    
    /**
     * Check if this hour has concerning metrics
     */
    public boolean isConcerning() {
        return (messageCount != null && messageCount > 1000) || 
               getPoisonRatio() > 0.1; // More than 10% poison messages
    }
    
    /**
     * Get severity level based on metrics
     */
    public String getSeverityLevel() {
        if (messageCount == null) {
            return "UNKNOWN";
        }
        
        if (messageCount > 5000 || getPoisonRatio() > 0.2) {
            return "CRITICAL";
        } else if (messageCount > 2000 || getPoisonRatio() > 0.1) {
            return "HIGH";
        } else if (messageCount > 500 || getPoisonRatio() > 0.05) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    @Override
    public String toString() {
        return String.format("DlqHourlyStats{hour=%d, messages=%d, poison=%d, ratio=%.2f%%, severity=%s}", 
                           hour, messageCount, poisonCount, getPoisonRatio() * 100, getSeverityLevel());
    }
}