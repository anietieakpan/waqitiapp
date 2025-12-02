package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Statistics
 * 
 * Aggregated statistics and metrics for payment operations,
 * providing insights into system performance and health.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatistics {
    
    /**
     * Number of currently active/processing payments
     */
    private Long activePayments;
    
    /**
     * Total payments initiated today
     */
    private Double totalInitiatedToday;
    
    /**
     * Total successful payments today
     */
    private Double totalSuccessfulToday;
    
    /**
     * Total failed payments today
     */
    private Double totalFailedToday;
    
    /**
     * Total payment volume (amount) processed today
     */
    private Long totalVolumeToday;
    
    /**
     * Average payment processing time in milliseconds
     */
    private Double averageProcessingTime;
    
    /**
     * Current success rate percentage (0-100)
     */
    private Double successRate;
    
    /**
     * Provider-specific statistics
     */
    private Map<String, PaymentMetrics> providerStatistics;
    
    /**
     * When these statistics were generated
     */
    private LocalDateTime timestamp;
    
    /**
     * Peak processing time today
     */
    private Double peakProcessingTime;
    
    /**
     * Number of payments currently in retry
     */
    private Long paymentsInRetry;
    
    /**
     * Number of payments flagged for fraud review
     */
    private Long paymentsInFraudReview;
    
    /**
     * Average payment amount today
     */
    private Double averagePaymentAmount;
    
    /**
     * Total fees collected today
     */
    private Double totalFeesToday;
    
    /**
     * Number of unique users who made payments today
     */
    private Long uniquePayersToday;
    
    /**
     * System health score (0-100)
     */
    private Double systemHealthScore;
    
    /**
     * Calculate failure rate
     */
    public double getFailureRate() {
        if (totalInitiatedToday == null || totalInitiatedToday == 0) {
            return 0.0;
        }
        
        double failedToday = totalFailedToday != null ? totalFailedToday : 0.0;
        return (failedToday / totalInitiatedToday) * 100.0;
    }
    
    /**
     * Calculate total completed payments
     */
    public double getTotalCompletedToday() {
        double successful = totalSuccessfulToday != null ? totalSuccessfulToday : 0.0;
        double failed = totalFailedToday != null ? totalFailedToday : 0.0;
        return successful + failed;
    }
    
    /**
     * Check if system is healthy based on success rate
     */
    public boolean isSystemHealthy() {
        return successRate != null && successRate >= 95.0;
    }
    
    /**
     * Check if system is under high load
     */
    public boolean isHighLoad() {
        return activePayments != null && activePayments > 1000;
    }
    
    /**
     * Get performance classification
     */
    public PerformanceLevel getPerformanceLevel() {
        if (averageProcessingTime == null) {
            return PerformanceLevel.UNKNOWN;
        }
        
        if (averageProcessingTime < 1000) {
            return PerformanceLevel.EXCELLENT;
        } else if (averageProcessingTime < 3000) {
            return PerformanceLevel.GOOD;
        } else if (averageProcessingTime < 5000) {
            return PerformanceLevel.ACCEPTABLE;
        } else {
            return PerformanceLevel.POOR;
        }
    }
    
    public enum PerformanceLevel {
        EXCELLENT,
        GOOD,
        ACCEPTABLE,
        POOR,
        UNKNOWN
    }
}