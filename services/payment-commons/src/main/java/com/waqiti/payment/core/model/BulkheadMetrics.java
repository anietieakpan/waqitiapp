package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bulkhead Metrics
 * 
 * Metrics for bulkhead pattern implementation including
 * available permissions, queue status, and utilization data.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkheadMetrics {
    
    /**
     * Number of currently available permissions
     */
    private Integer availablePermissions;
    
    /**
     * Maximum number of permissions configured
     */
    private Integer maxPermissions;
    
    /**
     * Current number of concurrent calls
     */
    private Integer currentConcurrentCalls;
    
    /**
     * Total number of permission acquisitions attempted
     */
    private Long totalPermissionAcquisitions;
    
    /**
     * Number of successful permission acquisitions
     */
    private Long successfulAcquisitions;
    
    /**
     * Number of rejected permission requests
     */
    private Long rejectedAcquisitions;
    
    /**
     * Average wait time for permission acquisition (milliseconds)
     */
    private Long averageWaitTimeMs;
    
    /**
     * Maximum wait time recorded (milliseconds)
     */
    private Long maxWaitTimeMs;
    
    /**
     * Peak concurrent calls reached
     */
    private Integer peakConcurrentCalls;
    
    /**
     * Total time spent executing calls (milliseconds)
     */
    private Long totalExecutionTimeMs;
    
    /**
     * Average execution time per call (milliseconds)
     */
    private Long averageExecutionTimeMs;
    
    /**
     * Number of calls that timed out waiting for permission
     */
    private Long timeoutCount;
    
    /**
     * Current queue size (if queuing is enabled)
     */
    private Integer currentQueueSize;
    
    /**
     * Maximum queue size configured
     */
    private Integer maxQueueSize;
    
    /**
     * When these metrics were last updated
     */
    private LocalDateTime lastUpdated;
    
    /**
     * Time period these metrics cover
     */
    private String metricsPeriod;
    
    /**
     * Calculate utilization percentage
     */
    public float getUtilizationPercentage() {
        if (maxPermissions == null || maxPermissions == 0) {
            return 0.0f;
        }
        
        int used = maxPermissions - (availablePermissions != null ? availablePermissions : 0);
        return ((float) used / maxPermissions) * 100;
    }
    
    /**
     * Calculate rejection rate percentage
     */
    public float getRejectionRate() {
        if (totalPermissionAcquisitions == null || totalPermissionAcquisitions == 0) {
            return 0.0f;
        }
        
        long rejected = rejectedAcquisitions != null ? rejectedAcquisitions : 0;
        return ((float) rejected / totalPermissionAcquisitions) * 100;
    }
    
    /**
     * Check if bulkhead is at capacity
     */
    public boolean isAtCapacity() {
        return availablePermissions != null && availablePermissions == 0;
    }
    
    /**
     * Check if bulkhead is under high load
     */
    public boolean isUnderHighLoad() {
        return getUtilizationPercentage() > 80.0f;
    }
    
    /**
     * Check if bulkhead is performing well
     */
    public boolean isPerformingWell() {
        return getRejectionRate() < 5.0f && 
               getUtilizationPercentage() < 90.0f &&
               (averageWaitTimeMs == null || averageWaitTimeMs < 100);
    }
    
    /**
     * Get capacity status
     */
    public CapacityStatus getCapacityStatus() {
        float utilization = getUtilizationPercentage();
        
        if (utilization >= 95) {
            return CapacityStatus.CRITICAL;
        } else if (utilization >= 80) {
            return CapacityStatus.HIGH;
        } else if (utilization >= 60) {
            return CapacityStatus.MEDIUM;
        } else {
            return CapacityStatus.LOW;
        }
    }
    
    /**
     * Calculate throughput (calls per second)
     */
    public double getThroughput() {
        if (totalExecutionTimeMs == null || totalExecutionTimeMs == 0 ||
            successfulAcquisitions == null || successfulAcquisitions == 0) {
            return 0.0;
        }
        
        // Convert to calls per second
        double totalSeconds = totalExecutionTimeMs / 1000.0;
        return successfulAcquisitions / totalSeconds;
    }
    
    /**
     * Check if queue is enabled and available
     */
    public boolean hasQueueCapacity() {
        return maxQueueSize != null && maxQueueSize > 0 &&
               (currentQueueSize == null || currentQueueSize < maxQueueSize);
    }
    
    /**
     * Get queue utilization percentage
     */
    public float getQueueUtilization() {
        if (maxQueueSize == null || maxQueueSize == 0) {
            return 0.0f;
        }
        
        int used = currentQueueSize != null ? currentQueueSize : 0;
        return ((float) used / maxQueueSize) * 100;
    }
    
    /**
     * Calculate efficiency score (0-100)
     */
    public int getEfficiencyScore() {
        int score = 100;
        
        // Deduct for high rejection rate
        float rejectionRate = getRejectionRate();
        score -= Math.min(40, (int)(rejectionRate * 2));
        
        // Deduct for long wait times
        if (averageWaitTimeMs != null && averageWaitTimeMs > 100) {
            score -= Math.min(30, (int)(averageWaitTimeMs / 50));
        }
        
        // Deduct for timeout occurrences
        if (timeoutCount != null && totalPermissionAcquisitions != null && totalPermissionAcquisitions > 0) {
            float timeoutRate = ((float) timeoutCount / totalPermissionAcquisitions) * 100;
            score -= Math.min(20, (int)(timeoutRate * 4));
        }
        
        // Bonus for good utilization (not too low, not too high)
        float utilization = getUtilizationPercentage();
        if (utilization >= 40 && utilization <= 80) {
            score += 10;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Get recommended action based on metrics
     */
    public String getRecommendedAction() {
        if (isAtCapacity() && getRejectionRate() > 20) {
            return "INCREASE_CAPACITY";
        } else if (getUtilizationPercentage() < 20 && getRejectionRate() < 1) {
            return "CONSIDER_REDUCING_CAPACITY";
        } else if (averageWaitTimeMs != null && averageWaitTimeMs > 1000) {
            return "OPTIMIZE_EXECUTION_TIME";
        } else if (getRejectionRate() > 10) {
            return "INVESTIGATE_HIGH_REJECTION_RATE";
        } else {
            return "NORMAL_OPERATION";
        }
    }
    
    public enum CapacityStatus {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Create default metrics
     */
    public static BulkheadMetrics createDefault(int maxPermissions) {
        return BulkheadMetrics.builder()
            .availablePermissions(maxPermissions)
            .maxPermissions(maxPermissions)
            .currentConcurrentCalls(0)
            .totalPermissionAcquisitions(0L)
            .successfulAcquisitions(0L)
            .rejectedAcquisitions(0L)
            .averageWaitTimeMs(0L)
            .maxWaitTimeMs(0L)
            .peakConcurrentCalls(0)
            .timeoutCount(0L)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
}