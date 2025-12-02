package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for User Analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnalyticsDTO {
    
    private UUID userId;
    private String username;
    private String email;
    
    // Activity metrics
    private LocalDateTime lastActiveAt;
    private Long totalSessions;
    private Long totalPageViews;
    private Double averageSessionDuration;
    private Double bounceRate;
    private Long totalActions;
    
    // Transaction analytics
    private Long totalTransactions;
    private BigDecimal totalTransactionVolume;
    private BigDecimal averageTransactionAmount;
    private String mostFrequentTransactionType;
    private Map<String, Long> transactionsByType;
    private List<TransactionTrend> transactionTrends;
    private List<com.waqiti.common.dto.SpendingCategoryDTO> topSpendingCategories;
    
    // Financial analytics
    private BigDecimal totalRevenue;
    private BigDecimal totalSpent;
    private BigDecimal netBalance;
    private BigDecimal monthlyRevenue;
    private BigDecimal monthlySpent;
    private BigDecimal yearlyRevenue;
    private BigDecimal yearlySpent;
    
    // Engagement metrics
    private Double engagementScore;
    private String userSegment;
    private String lifetimeValue;
    private Double churnProbability;
    private Integer daysSinceLastActivity;
    private Integer activityStreak;
    
    // Device and platform analytics
    private Map<String, Long> deviceUsage;
    private Map<String, Long> platformUsage;
    private String primaryDevice;
    private String primaryPlatform;
    private List<String> browserTypes;
    
    // Geographic analytics
    private Map<String, Long> locationActivity;
    private String primaryLocation;
    private List<String> uniqueLocations;
    private Long internationalActivityCount;
    
    // Feature usage
    private Map<String, Long> featureUsage;
    private List<String> mostUsedFeatures;
    private List<String> unusedFeatures;
    private Map<String, LocalDateTime> featureFirstUsed;
    
    // Risk and compliance analytics
    private Double riskScore;
    private Long suspiciousActivityCount;
    private Long failedAuthenticationCount;
    private Boolean hasComplianceIssues;
    private List<String> complianceFlags;
    
    // Customer support analytics
    private Long supportTicketsCreated;
    private Long supportTicketsResolved;
    private Double averageResponseTime;
    private Double customerSatisfactionScore;
    
    // Referral analytics
    private Long referralsMade;
    private Long successfulReferrals;
    private BigDecimal referralRevenue;
    private String referralCode;
    
    // Time-based analytics
    private Map<Integer, Long> activityByHour;
    private Map<String, Long> activityByDayOfWeek;
    private Map<Integer, Long> activityByMonth;
    private List<String> peakActivityTimes;
    
    // Conversion metrics
    private Double conversionRate;
    private Long conversions;
    private Map<String, Double> conversionsByFunnel;
    private BigDecimal conversionValue;
    
    // Retention metrics
    private Boolean isRetained;
    private Integer retentionDays;
    private String retentionCohort;
    private Double retentionRate;
    
    // Metadata
    private LocalDateTime calculatedAt;
    private String calculationPeriod;
    private Map<String, Object> customMetrics;
    
    /**
     * Transaction trend data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionTrend {
        private String period;
        private Long transactionCount;
        private BigDecimal transactionVolume;
        private BigDecimal averageAmount;
        private Double growthRate;
    }
    
    /**
     * Check if user is active
     */
    public boolean isActive() {
        return daysSinceLastActivity != null && daysSinceLastActivity <= 30;
    }
    
    /**
     * Check if user is at risk of churning
     */
    public boolean isChurnRisk() {
        return churnProbability != null && churnProbability > 0.7;
    }
    
    /**
     * Check if user is high value
     */
    public boolean isHighValue() {
        return totalRevenue != null && totalRevenue.compareTo(new BigDecimal("1000")) > 0;
    }
    
    /**
     * Get engagement level
     */
    public String getEngagementLevel() {
        if (engagementScore == null) {
            return "UNKNOWN";
        }
        if (engagementScore >= 80) {
            return "VERY_HIGH";
        } else if (engagementScore >= 60) {
            return "HIGH";
        } else if (engagementScore >= 40) {
            return "MEDIUM";
        } else if (engagementScore >= 20) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }
    
}