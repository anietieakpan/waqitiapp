package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.LoyaltyTier;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for system-wide rewards metrics
 */
@Data
@Builder
public class SystemRewardsMetricsDto {
    
    /**
     * Total number of active users
     */
    private Long totalActiveUsers;
    
    /**
     * Total cashback paid out
     */
    private BigDecimal totalCashbackPaid;
    
    /**
     * Total points redeemed
     */
    private Long totalPointsRedeemed;
    
    /**
     * Distribution of users across tiers
     */
    private Map<LoyaltyTier, Long> tierDistribution;
    
    /**
     * Average daily cashback amount
     */
    private BigDecimal dailyAverageCashback;
    
    /**
     * Average daily transaction count
     */
    private Long dailyAverageTransactions;
    
    /**
     * Total points outstanding (not redeemed)
     */
    private Long totalPointsOutstanding;
    
    /**
     * Total cashback outstanding (not redeemed)
     */
    private BigDecimal totalCashbackOutstanding;
    
    /**
     * Number of new users this month
     */
    private Long newUsersThisMonth;
    
    /**
     * Number of active users this month
     */
    private Long activeUsersThisMonth;
    
    /**
     * Top performing merchants by cashback volume
     */
    private java.util.List<MerchantMetrics> topMerchants;
    
    /**
     * Most popular redemption methods
     */
    private Map<String, Long> redemptionMethodStats;
    
    /**
     * Average time to first redemption
     */
    private Double averageTimeToFirstRedemption;
    
    /**
     * User retention rate
     */
    private BigDecimal userRetentionRate;
    
    /**
     * System liability (total outstanding rewards)
     */
    private BigDecimal systemLiability;
    
    /**
     * When these metrics were generated
     */
    private Instant generatedAt;
    
    @Data
    @Builder
    public static class MerchantMetrics {
        private String merchantId;
        private String merchantName;
        private BigDecimal totalCashbackGenerated;
        private Long totalTransactions;
        private BigDecimal averageTransactionAmount;
        private Long uniqueUsers;
    }
}