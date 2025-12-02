package com.waqiti.ml.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Embeddable behavior metrics for comprehensive behavioral analysis.
 * Contains statistical measures and behavioral patterns.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorMetrics {

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "average_amount", precision = 19, scale = 4)
    private BigDecimal averageAmount = BigDecimal.ZERO;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "median_amount", precision = 19, scale = 4)
    private BigDecimal medianAmount;

    @Column(name = "amount_variance", precision = 19, scale = 4)
    private BigDecimal amountVariance = BigDecimal.ZERO;

    @Column(name = "amount_std_deviation", precision = 19, scale = 4)
    private BigDecimal amountStdDeviation = BigDecimal.ZERO;

    @Column(name = "total_transactions")
    private Long totalTransactions = 0L;

    @Column(name = "successful_transactions")
    private Long successfulTransactions = 0L;

    @Column(name = "failed_transactions")
    private Long failedTransactions = 0L;

    @Column(name = "average_hourly_transaction_rate", precision = 8, scale = 4)
    private Double averageHourlyTransactionRate = 0.0;

    @Column(name = "average_daily_transaction_rate", precision = 8, scale = 4)
    private Double averageDailyTransactionRate = 0.0;

    @Column(name = "current_hourly_rate")
    private Long currentHourlyRate = 0L;

    @Column(name = "current_daily_rate")
    private Long currentDailyRate = 0L;

    @Column(name = "peak_hourly_rate")
    private Long peakHourlyRate = 0L;

    @Column(name = "peak_daily_rate")
    private Long peakDailyRate = 0L;

    @Column(name = "last_transaction_timestamp")
    private LocalDateTime lastTransactionTimestamp;

    @Column(name = "most_active_hour")
    private Integer mostActiveHour;

    @Column(name = "most_active_day")
    private Integer mostActiveDay;

    @Column(name = "unique_recipients_count")
    private Long uniqueRecipientsCount = 0L;

    @Column(name = "unique_merchants_count")
    private Long uniqueMerchantsCount = 0L;

    @Column(name = "international_transaction_count")
    private Long internationalTransactionCount = 0L;

    @Column(name = "crypto_transaction_count")
    private Long cryptoTransactionCount = 0L;

    @Column(name = "weekend_transaction_count")
    private Long weekendTransactionCount = 0L;

    @Column(name = "night_transaction_count")
    private Long nightTransactionCount = 0L;

    @Column(name = "mobile_transaction_count")
    private Long mobileTransactionCount = 0L;

    @Column(name = "web_transaction_count")
    private Long webTransactionCount = 0L;

    @Column(name = "average_session_duration_minutes", precision = 8, scale = 2)
    private Double averageSessionDurationMinutes = 0.0;

    @Column(name = "failed_authentication_attempts")
    private Long failedAuthenticationAttempts = 0L;

    @Column(name = "device_changes_count")
    private Long deviceChangesCount = 0L;

    @Column(name = "location_changes_count")
    private Long locationChangesCount = 0L;

    // JSON columns for complex data structures
    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "hourly_patterns", columnDefinition = "jsonb")
    private Map<Integer, Long> hourlyPatterns = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "daily_patterns", columnDefinition = "jsonb")
    private Map<Integer, Long> dailyPatterns = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "monthly_patterns", columnDefinition = "jsonb")
    private Map<Integer, Long> monthlyPatterns = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "amount_distribution", columnDefinition = "jsonb")
    private Map<String, Long> amountDistribution = new HashMap<>(); // "0-100", "100-500", etc.

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "recipient_frequency", columnDefinition = "jsonb")
    private Map<String, Long> recipientFrequency = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "merchant_frequency", columnDefinition = "jsonb")
    private Map<String, Long> merchantFrequency = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "device_usage_patterns", columnDefinition = "jsonb")
    private Map<String, Object> deviceUsagePatterns = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "location_patterns", columnDefinition = "jsonb")
    private Map<String, Object> locationPatterns = new HashMap<>();

    /**
     * Increment total transaction count
     */
    public void incrementTotalTransactions() {
        if (this.totalTransactions == null) {
            this.totalTransactions = 1L;
        } else {
            this.totalTransactions++;
        }
    }

    /**
     * Increment successful transaction count
     */
    public void incrementSuccessfulTransactions() {
        if (this.successfulTransactions == null) {
            this.successfulTransactions = 1L;
        } else {
            this.successfulTransactions++;
        }
        incrementTotalTransactions();
    }

    /**
     * Increment failed transaction count
     */
    public void incrementFailedTransactions() {
        if (this.failedTransactions == null) {
            this.failedTransactions = 1L;
        } else {
            this.failedTransactions++;
        }
        incrementTotalTransactions();
    }

    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        if (totalTransactions == null || totalTransactions == 0) return 0.0;
        if (successfulTransactions == null) return 0.0;
        return (double) successfulTransactions / totalTransactions;
    }

    /**
     * Update hourly pattern
     */
    public void updateHourlyPattern(int hour) {
        if (hourlyPatterns == null) {
            hourlyPatterns = new HashMap<>();
        }
        hourlyPatterns.merge(hour, 1L, Long::sum);
        
        // Find most active hour
        this.mostActiveHour = hourlyPatterns.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(hour);
    }

    /**
     * Update daily pattern
     */
    public void updateDailyPattern(int dayOfWeek) {
        if (dailyPatterns == null) {
            dailyPatterns = new HashMap<>();
        }
        dailyPatterns.merge(dayOfWeek, 1L, Long::sum);
        
        // Find most active day
        this.mostActiveDay = dailyPatterns.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(dayOfWeek);
    }

    /**
     * Update amount distribution
     */
    public void updateAmountDistribution(BigDecimal amount) {
        if (amountDistribution == null) {
            amountDistribution = new HashMap<>();
        }
        
        String range = getAmountRange(amount);
        amountDistribution.merge(range, 1L, Long::sum);
    }

    /**
     * Update recipient frequency
     */
    public void updateRecipientFrequency(String recipientId) {
        if (recipientId == null) return;
        
        if (recipientFrequency == null) {
            recipientFrequency = new HashMap<>();
        }
        recipientFrequency.merge(recipientId, 1L, Long::sum);
        
        // Update unique recipients count
        this.uniqueRecipientsCount = (long) recipientFrequency.size();
    }

    /**
     * Update merchant frequency
     */
    public void updateMerchantFrequency(String merchantId) {
        if (merchantId == null) return;
        
        if (merchantFrequency == null) {
            merchantFrequency = new HashMap<>();
        }
        merchantFrequency.merge(merchantId, 1L, Long::sum);
        
        // Update unique merchants count
        this.uniqueMerchantsCount = (long) merchantFrequency.size();
    }

    /**
     * Check if user transacts frequently with recipient
     */
    public boolean isFrequentRecipient(String recipientId) {
        if (recipientFrequency == null || recipientId == null) return false;
        Long frequency = recipientFrequency.get(recipientId);
        return frequency != null && frequency >= 5; // 5+ transactions = frequent
    }

    /**
     * Get transaction velocity (transactions per hour)
     */
    public double getCurrentVelocity() {
        if (currentHourlyRate == null) return 0.0;
        return currentHourlyRate.doubleValue();
    }

    /**
     * Check if current activity is unusual
     */
    public boolean hasUnusualActivity() {
        if (averageHourlyTransactionRate == null || currentHourlyRate == null) return false;
        return currentHourlyRate > (averageHourlyTransactionRate * 3);
    }

    /**
     * Get diversity score (how varied are the user's transactions)
     */
    public double getDiversityScore() {
        double recipientDiversity = uniqueRecipientsCount != null ? 
            Math.min(uniqueRecipientsCount.doubleValue() / 20.0, 1.0) : 0.0;
        
        double merchantDiversity = uniqueMerchantsCount != null ? 
            Math.min(uniqueMerchantsCount.doubleValue() / 10.0, 1.0) : 0.0;
        
        double channelDiversity = calculateChannelDiversity();
        
        return (recipientDiversity + merchantDiversity + channelDiversity) / 3.0;
    }

    private double calculateChannelDiversity() {
        long totalChannelTransactions = (mobileTransactionCount != null ? mobileTransactionCount : 0) +
                                       (webTransactionCount != null ? webTransactionCount : 0);
        
        if (totalChannelTransactions == 0) return 0.0;
        
        double entropy = 0.0;
        if (mobileTransactionCount != null && mobileTransactionCount > 0) {
            double probability = (double) mobileTransactionCount / totalChannelTransactions;
            entropy -= probability * Math.log(probability) / Math.log(2);
        }
        if (webTransactionCount != null && webTransactionCount > 0) {
            double probability = (double) webTransactionCount / totalChannelTransactions;
            entropy -= probability * Math.log(probability) / Math.log(2);
        }
        
        return entropy / Math.log(2); // Normalize to 0-1
    }

    private String getAmountRange(BigDecimal amount) {
        if (amount == null) return "unknown";
        
        double value = amount.doubleValue();
        if (value <= 50) return "0-50";
        if (value <= 100) return "50-100";
        if (value <= 500) return "100-500";
        if (value <= 1000) return "500-1000";
        if (value <= 5000) return "1000-5000";
        if (value <= 10000) return "5000-10000";
        return "10000+";
    }
}