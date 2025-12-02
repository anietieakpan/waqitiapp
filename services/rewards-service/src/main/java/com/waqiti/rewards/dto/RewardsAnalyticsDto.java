package com.waqiti.rewards.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for user rewards analytics
 */
@Data
@Builder
public class RewardsAnalyticsDto {
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Period in days for analytics
     */
    private Integer periodDays;
    
    /**
     * Total spending in the period
     */
    private BigDecimal totalSpending;
    
    /**
     * Total cashback earned in the period
     */
    private BigDecimal totalCashbackEarned;
    
    /**
     * Total points earned in the period
     */
    private Long totalPointsEarned;
    
    /**
     * Number of transactions in the period
     */
    private Long transactionCount;
    
    /**
     * Average transaction amount
     */
    private BigDecimal averageTransactionAmount;
    
    /**
     * Effective cashback rate
     */
    private BigDecimal effectiveCashbackRate;
    
    /**
     * Spending by category
     */
    private List<CategoryBreakdown> categoryBreakdown;
    
    /**
     * Spending by merchant
     */
    private List<MerchantBreakdown> merchantBreakdown;
    
    /**
     * Daily spending and rewards data
     */
    private List<DailyAnalytics> dailyAnalytics;
    
    /**
     * Comparison with previous period
     */
    private PeriodComparison periodComparison;
    
    @Data
    @Builder
    public static class CategoryBreakdown {
        private String category;
        private String categoryName;
        private BigDecimal spending;
        private BigDecimal cashbackEarned;
        private Long transactionCount;
        private BigDecimal averageTransactionAmount;
        private BigDecimal effectiveRate;
    }
    
    @Data
    @Builder
    public static class MerchantBreakdown {
        private String merchantId;
        private String merchantName;
        private BigDecimal spending;
        private BigDecimal cashbackEarned;
        private Long transactionCount;
        private BigDecimal averageTransactionAmount;
        private BigDecimal effectiveRate;
    }
    
    @Data
    @Builder
    public static class DailyAnalytics {
        private String date;
        private BigDecimal spending;
        private BigDecimal cashbackEarned;
        private Long pointsEarned;
        private Long transactionCount;
    }
    
    @Data
    @Builder
    public static class PeriodComparison {
        private BigDecimal spendingChange;
        private BigDecimal cashbackChange;
        private Long pointsChange;
        private Long transactionChange;
        private BigDecimal spendingChangePercentage;
        private BigDecimal cashbackChangePercentage;
    }
}

