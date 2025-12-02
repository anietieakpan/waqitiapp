package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * SMS service metrics and statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsMetrics {
    
    private long totalSent;
    private long totalDelivered;
    private long totalFailed;
    private long totalPending;
    private double deliveryRate;
    private double failureRate;
    private double averageDeliveryTimeMs;
    private Map<String, Long> sentByType;
    private Map<String, Long> sentByCountry;
    private Map<String, Long> failuresByReason;
    private Map<String, Double> costByProvider;
    private double totalCost;
    private String currency;
    private Instant periodStart;
    private Instant periodEnd;
    private long uniqueRecipients;
    private long rateLimitedCount;
    private long complianceViolationCount;
    
    /**
     * Calculate overall success rate
     */
    public double getSuccessRate() {
        long total = totalSent;
        if (total == 0) return 0.0;
        return (double) totalDelivered / total * 100;
    }
    
    /**
     * Get pending percentage
     */
    public double getPendingPercentage() {
        long total = totalSent;
        if (total == 0) return 0.0;
        return (double) totalPending / total * 100;
    }
    
    /**
     * Check if metrics indicate issues
     */
    public boolean hasIssues() {
        return failureRate > 10.0 || deliveryRate < 90.0;
    }
    
    /**
     * Get most common failure reason
     */
    public String getMostCommonFailureReason() {
        if (failuresByReason == null || failuresByReason.isEmpty()) {
            return "NONE";
        }
        
        return failuresByReason.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }
    
    /**
     * Get average cost per SMS
     */
    public double getAverageCostPerSms() {
        if (totalSent == 0) return 0.0;
        return totalCost / totalSent;
    }
}