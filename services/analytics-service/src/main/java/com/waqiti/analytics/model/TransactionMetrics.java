package com.waqiti.analytics.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Real-time transaction metrics for a user
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMetrics implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);
    
    private String userId;
    private long transactionCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private BigDecimal standardDeviation;
    
    // Percentiles
    private BigDecimal p50; // Median
    private BigDecimal p75;
    private BigDecimal p90;
    private BigDecimal p95;
    private BigDecimal p99;
    
    // Time-based metrics
    private Instant firstTransactionTime;
    private Instant lastTransactionTime;
    private Instant lastUpdated;
    private long transactionsPerHour;
    private long transactionsPerDay;
    
    // Status breakdown
    private Map<String, Long> statusCounts = new HashMap<>();
    
    // Type breakdown
    private Map<String, Long> typeCounts = new HashMap<>();
    
    // Currency breakdown
    private Map<String, BigDecimal> currencyTotals = new HashMap<>();
    
    // Historical data for calculations
    private transient ConcurrentLinkedQueue<BigDecimal> amountHistory = new ConcurrentLinkedQueue<>();
    
    // Trend indicators
    private TrendDirection volumeTrend;
    private TrendDirection amountTrend;
    private BigDecimal trendPercentage;
    
    public TransactionMetrics(String userId) {
        this.userId = userId;
        this.transactionCount = 0;
        this.totalAmount = BigDecimal.ZERO;
        this.averageAmount = BigDecimal.ZERO;
        this.maxAmount = BigDecimal.ZERO;
        this.minAmount = BigDecimal.valueOf(Long.MAX_VALUE);
        this.standardDeviation = BigDecimal.ZERO;
        this.lastUpdated = Instant.now();
    }
    
    /**
     * Update metrics from a transaction aggregate
     */
    public void updateFromAggregate(TransactionAggregate aggregate, Instant windowStart) {
        this.transactionCount += aggregate.getCount();
        this.totalAmount = this.totalAmount.add(aggregate.getTotalAmount());
        
        // Update average
        if (this.transactionCount > 0) {
            this.averageAmount = this.totalAmount.divide(
                    BigDecimal.valueOf(this.transactionCount), 2, RoundingMode.HALF_UP);
        }
        
        // Update max/min
        if (aggregate.getMaxAmount().compareTo(this.maxAmount) > 0) {
            this.maxAmount = aggregate.getMaxAmount();
        }
        if (aggregate.getMinAmount().compareTo(this.minAmount) < 0) {
            this.minAmount = aggregate.getMinAmount();
        }
        
        // Update time metrics
        if (this.firstTransactionTime == null) {
            this.firstTransactionTime = windowStart;
        }
        this.lastTransactionTime = Instant.now();
        this.lastUpdated = Instant.now();
        
        // Add to history
        aggregate.getAmounts().forEach(amount -> {
            amountHistory.offer(amount);
            if (amountHistory.size() > MAX_HISTORY_SIZE) {
                amountHistory.poll();
            }
        });
        
        // Update status counts
        aggregate.getStatusCounts().forEach((status, count) -> 
            statusCounts.merge(status, count, Long::sum));
        
        // Update type counts
        aggregate.getTypeCounts().forEach((type, count) -> 
            typeCounts.merge(type, count, Long::sum));
        
        // Update currency totals
        aggregate.getCurrencyTotals().forEach((currency, total) -> 
            currencyTotals.merge(currency, total, BigDecimal::add));
        
        // Calculate rates
        calculateTransactionRates();
        
        // Detect trends
        detectTrends(aggregate);
    }
    
    /**
     * Calculate percentiles from historical data
     */
    public void calculatePercentiles() {
        if (amountHistory.isEmpty()) {
            return;
        }
        
        List<BigDecimal> sortedAmounts = new ArrayList<>(amountHistory);
        Collections.sort(sortedAmounts);
        
        int size = sortedAmounts.size();
        this.p50 = getPercentile(sortedAmounts, 50);
        this.p75 = getPercentile(sortedAmounts, 75);
        this.p90 = getPercentile(sortedAmounts, 90);
        this.p95 = getPercentile(sortedAmounts, 95);
        this.p99 = getPercentile(sortedAmounts, 99);
        
        // Calculate standard deviation
        calculateStandardDeviation(sortedAmounts);
    }
    
    /**
     * Calculate standard deviation
     */
    private void calculateStandardDeviation(List<BigDecimal> amounts) {
        if (amounts.size() < 2) {
            this.standardDeviation = BigDecimal.ZERO;
            return;
        }
        
        BigDecimal mean = this.averageAmount;
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        
        for (BigDecimal amount : amounts) {
            BigDecimal diff = amount.subtract(mean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        
        BigDecimal variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(amounts.size() - 1), 10, RoundingMode.HALF_UP);
        
        // Calculate square root for standard deviation
        this.standardDeviation = sqrt(variance);
    }
    
    /**
     * Calculate transaction rates
     */
    private void calculateTransactionRates() {
        if (firstTransactionTime == null || lastTransactionTime == null) {
            return;
        }
        
        Duration duration = Duration.between(firstTransactionTime, lastTransactionTime);
        
        if (duration.toHours() > 0) {
            this.transactionsPerHour = transactionCount / duration.toHours();
        }
        
        if (duration.toDays() > 0) {
            this.transactionsPerDay = transactionCount / duration.toDays();
        } else {
            // Extrapolate for less than a day
            this.transactionsPerDay = (transactionCount * 24) / Math.max(1, duration.toHours());
        }
    }
    
    /**
     * Detect trends in the metrics
     */
    private void detectTrends(TransactionAggregate recentAggregate) {
        // Compare recent average with overall average
        if (averageAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal recentAvg = recentAggregate.getAverageAmount();
            BigDecimal diff = recentAvg.subtract(averageAmount);
            BigDecimal percentChange = diff.divide(averageAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            this.trendPercentage = percentChange;
            
            if (percentChange.compareTo(new BigDecimal("5")) > 0) {
                this.amountTrend = TrendDirection.UP;
            } else if (percentChange.compareTo(new BigDecimal("-5")) < 0) {
                this.amountTrend = TrendDirection.DOWN;
            } else {
                this.amountTrend = TrendDirection.STABLE;
            }
        }
        
        // Volume trend based on transaction count
        long recentRate = recentAggregate.getCount();
        long avgRate = transactionsPerHour > 0 ? transactionsPerHour : recentRate;
        
        if (recentRate > avgRate * 1.2) {
            this.volumeTrend = TrendDirection.UP;
        } else if (recentRate < avgRate * 0.8) {
            this.volumeTrend = TrendDirection.DOWN;
        } else {
            this.volumeTrend = TrendDirection.STABLE;
        }
    }
    
    /**
     * Check if metrics are stale
     */
    public boolean isStale() {
        return lastUpdated == null || 
               Duration.between(lastUpdated, Instant.now()).compareTo(STALE_THRESHOLD) > 0;
    }
    
    /**
     * Get percentile value from sorted list
     */
    private BigDecimal getPercentile(List<BigDecimal> sortedList, int percentile) {
        if (sortedList.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int index = (int) Math.ceil(percentile / 100.0 * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        
        return sortedList.get(index);
    }
    
    /**
     * Calculate square root using Newton's method
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x = value;
        BigDecimal root;
        
        int scale = 10;
        
        do {
            root = x;
            x = x.add(value.divide(x, scale, RoundingMode.HALF_UP))
                    .divide(BigDecimal.valueOf(2), scale, RoundingMode.HALF_UP);
        } while (x.subtract(root).abs().compareTo(new BigDecimal("0.0001")) > 0);
        
        return x;
    }
    
    /**
     * Get success rate
     */
    public BigDecimal getSuccessRate() {
        Long successful = statusCounts.getOrDefault("COMPLETED", 0L) + 
                         statusCounts.getOrDefault("SUCCESS", 0L);
        
        if (transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(successful)
                .divide(BigDecimal.valueOf(transactionCount), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    /**
     * Get failure rate
     */
    public BigDecimal getFailureRate() {
        Long failed = statusCounts.getOrDefault("FAILED", 0L) + 
                     statusCounts.getOrDefault("REJECTED", 0L);
        
        if (transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(failed)
                .divide(BigDecimal.valueOf(transactionCount), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    /**
     * Get dominant transaction type
     */
    public String getDominantType() {
        return typeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }
    
    /**
     * Get dominant currency
     */
    public String getDominantCurrency() {
        return currencyTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("USD");
    }
    
    /**
     * Get health score (0-100)
     */
    public int getHealthScore() {
        BigDecimal successRate = getSuccessRate();
        
        // Factors for health score
        int score = 50; // Base score
        
        // Success rate factor (up to 30 points)
        score += successRate.multiply(new BigDecimal("0.3")).intValue();
        
        // Volume consistency factor (up to 10 points)
        if (volumeTrend == TrendDirection.STABLE) {
            score += 10;
        } else if (volumeTrend == TrendDirection.UP) {
            score += 5;
        }
        
        // No recent failures (up to 10 points)
        if (getFailureRate().compareTo(new BigDecimal("5")) < 0) {
            score += 10;
        }
        
        return Math.min(100, Math.max(0, score));
    }
    
    public enum TrendDirection {
        UP, DOWN, STABLE
    }
}