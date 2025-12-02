package com.waqiti.common.messaging.deadletter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dead Letter Queue Metrics
 * 
 * Comprehensive metrics and statistics for DLQ monitoring
 * and operational visibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterQueueMetrics {
    
    private String topic;
    private LocalDateTime lastUpdated;
    
    // Message counts
    private long totalMessages;
    private long pendingMessages;
    private long poisonMessages;
    private long reprocessedMessages;
    private long quarantinedMessages;
    
    // Success rates
    private double successRate;
    private double reprocessingRate;
    private double poisonRate;
    
    // Timing metrics
    private double averageRetryCount;
    private long oldestMessageAgeHours;
    private long newestMessageAgeMinutes;
    
    // Error analysis
    private List<String> recentFailureReasons;
    private List<ErrorTypeCount> errorTypeBreakdown;
    private List<DlqHourlyStats> hourlyStatistics;
    
    // Trends
    private TrendData messagesTrend;
    private TrendData poisonMessagesTrend;
    private TrendData reprocessingTrend;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorTypeCount {
        private String errorType;
        private long count;
        private double percentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private long current;
        private long previous;
        private double changePercent;
        private TrendDirection direction;
    }
    
    public enum TrendDirection {
        UP, DOWN, STABLE
    }
    
    /**
     * Calculate health score (0-100)
     */
    public double getHealthScore() {
        if (totalMessages == 0) {
            return 100.0; // Perfect health if no messages
        }
        
        double baseScore = 100.0;
        
        // Reduce score for poison messages
        double poisonPenalty = (poisonRate * 50); // Up to 50 point penalty
        baseScore -= poisonPenalty;
        
        // Reduce score for low reprocessing success
        if (reprocessedMessages > 0) {
            double reprocessingPenalty = (1.0 - successRate) * 30; // Up to 30 point penalty
            baseScore -= reprocessingPenalty;
        }
        
        // Reduce score for high message volume
        if (pendingMessages > 1000) {
            double volumePenalty = Math.min(20, pendingMessages / 100.0); // Up to 20 point penalty
            baseScore -= volumePenalty;
        }
        
        return Math.max(0.0, Math.min(100.0, baseScore));
    }
    
    /**
     * Get health status based on score
     */
    public HealthStatus getHealthStatus() {
        double score = getHealthScore();
        
        if (score >= 90) return HealthStatus.EXCELLENT;
        if (score >= 75) return HealthStatus.GOOD;
        if (score >= 50) return HealthStatus.WARNING;
        if (score >= 25) return HealthStatus.CRITICAL;
        return HealthStatus.EMERGENCY;
    }
    
    public enum HealthStatus {
        EXCELLENT, GOOD, WARNING, CRITICAL, EMERGENCY
    }
}