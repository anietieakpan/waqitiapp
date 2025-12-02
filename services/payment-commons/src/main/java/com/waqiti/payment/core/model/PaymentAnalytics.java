package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payment Analytics Model
 * 
 * Comprehensive analytics data model containing metrics, trends,
 * breakdowns, and insights for payment performance analysis.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAnalytics {
    
    // Core metadata
    private String userId;
    private String merchantId;
    private AnalyticsFilter.TimePeriod period;
    private Instant generatedAt;
    private Instant periodStart;
    private Instant periodEnd;
    
    // Core metrics
    private Long totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal medianAmount;
    private Double successRate;
    private Double failureRate;
    private Double refundRate;
    private Double disputeRate;
    
    // Performance metrics
    private Long averageProcessingTimeMs;
    private Long medianProcessingTimeMs;
    private Double p95ProcessingTimeMs;
    private Double p99ProcessingTimeMs;
    
    // User metrics
    private Long uniqueUsers;
    private Long newUsers;
    private Long returningUsers;
    private Double userRetentionRate;
    private Double userChurnRate;
    
    // Financial metrics
    private BigDecimal totalRevenue;
    private BigDecimal totalFees;
    private BigDecimal averageOrderValue;
    private BigDecimal averageRevenuePerUser;
    private BigDecimal grossMerchandiseValue;
    private BigDecimal lifetimeValue;
    
    // Growth metrics
    private Double transactionGrowthRate;
    private Double volumeGrowthRate;
    private Double userGrowthRate;
    private Double revenueGrowthRate;
    
    // Risk metrics
    private Double averageRiskScore;
    private Long flaggedTransactions;
    private Double fraudRate;
    private Long chargebacks;
    private BigDecimal chargebackAmount;
    
    // Operational metrics
    private Double systemUptime;
    private Double errorRate;
    private Long apiCallsCount;
    private Double averageResponseTime;
    
    // Breakdown data
    private Map<String, Integer> topPaymentTypes;
    private Map<String, Integer> topProviders;
    private Map<String, Integer> topCountries;
    private Map<String, Integer> topCurrencies;
    private Map<String, Integer> topChannels;
    private Map<String, Integer> paymentMethodBreakdown;
    
    // Time-based data
    private List<Integer> hourlyDistribution;
    private List<DailyTrendData> dailyTrend;
    private List<WeeklyTrendData> weeklyTrend;
    private List<MonthlyTrendData> monthlyTrend;
    
    // Geographic data
    private List<GeographicData> geographicBreakdown;
    
    // Device and platform data
    private Map<String, Integer> deviceTypeBreakdown;
    private Map<String, Integer> platformBreakdown;
    private Map<String, Integer> browserBreakdown;
    
    // Comparison data (if comparison period is requested)
    private PaymentAnalyticsComparison comparison;
    
    // Advanced analytics
    private List<CohortData> cohortAnalysis;
    private List<SegmentData> segmentAnalysis;
    private List<FunnelData> conversionFunnel;
    private List<AnomalyData> anomalies;
    
    // Forecasting (if requested)
    private List<ForecastData> forecasts;
    
    // Additional insights
    private List<InsightData> insights;
    private List<RecommendationData> recommendations;
    
    // Performance indicators
    private List<KPIData> kpis;
    
    // Legacy fields for backward compatibility
    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    
    // Supporting data classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrendData {
        private LocalDate date;
        private int transactionCount;
        private BigDecimal totalAmount;
        private double successRate;
        private int uniqueUsers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyTrendData {
        private int weekNumber;
        private int year;
        private LocalDate startDate;
        private LocalDate endDate;
        private int transactionCount;
        private BigDecimal totalAmount;
        private double successRate;
        private int uniqueUsers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrendData {
        private int month;
        private int year;
        private int transactionCount;
        private BigDecimal totalAmount;
        private double successRate;
        private int uniqueUsers;
        private BigDecimal revenue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicData {
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private int transactionCount;
        private BigDecimal totalAmount;
        private double successRate;
        private int uniqueUsers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentAnalyticsComparison {
        private PaymentAnalytics previousPeriod;
        private Map<String, Double> growthRates;
        private Map<String, String> trends; // "up", "down", "stable"
        private List<String> significantChanges;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CohortData {
        private String cohortId;
        private LocalDate cohortDate;
        private int initialSize;
        private Map<Integer, Double> retentionRates; // period -> retention rate
        private Map<Integer, BigDecimal> revenuePerPeriod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentData {
        private String segmentId;
        private String segmentName;
        private int userCount;
        private int transactionCount;
        private BigDecimal totalAmount;
        private double conversionRate;
        private BigDecimal averageOrderValue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelData {
        private String stepName;
        private int stepOrder;
        private long userCount;
        private double conversionRate;
        private double dropOffRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyData {
        private Instant timestamp;
        private String metricName;
        private double expectedValue;
        private double actualValue;
        private double deviationScore;
        private String severity; // "low", "medium", "high", "critical"
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastData {
        private LocalDate date;
        private String metricName;
        private double predictedValue;
        private double lowerBound;
        private double upperBound;
        private double confidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightData {
        private String category; // "performance", "growth", "risk", "opportunity"
        private String title;
        private String description;
        private String severity; // "info", "warning", "critical"
        private Map<String, Object> supportingData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationData {
        private String category;
        private String title;
        private String description;
        private String priority; // "low", "medium", "high"
        private String impact; // "low", "medium", "high"
        private List<String> actionItems;
        private BigDecimal estimatedImpact;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KPIData {
        private String name;
        private double currentValue;
        private double targetValue;
        private double previousValue;
        private String unit;
        private String status; // "on_track", "at_risk", "behind"
        private double changePercentage;
    }
    
    // Helper methods
    public boolean hasComparison() {
        return comparison != null;
    }
    
    public boolean hasTrendData() {
        return (dailyTrend != null && !dailyTrend.isEmpty()) ||
               (weeklyTrend != null && !weeklyTrend.isEmpty()) ||
               (monthlyTrend != null && !monthlyTrend.isEmpty());
    }
    
    public boolean hasGeographicData() {
        return geographicBreakdown != null && !geographicBreakdown.isEmpty();
    }
    
    public boolean hasForecasts() {
        return forecasts != null && !forecasts.isEmpty();
    }
    
    public boolean hasAnomalies() {
        return anomalies != null && !anomalies.isEmpty();
    }
    
    public double getConversionRate() {
        if (totalTransactions == null || totalTransactions == 0) {
            return 0.0;
        }
        return successRate != null ? successRate : 0.0;
    }
    
    public BigDecimal getAverageTransactionValue() {
        return averageAmount != null ? averageAmount : BigDecimal.ZERO;
    }
    
    public double getGrowthRate(String metric) {
        if (comparison != null && comparison.getGrowthRates() != null) {
            return comparison.getGrowthRates().getOrDefault(metric, 0.0);
        }
        return 0.0;
    }
    
    // Legacy methods for backward compatibility
    public double getSuccessRate() {
        if (successRate != null) {
            return successRate;
        }
        if (totalPayments == 0) return 0.0;
        return (double) successfulPayments / totalPayments * 100.0;
    }
    
    public double getFailureRate() {
        if (failureRate != null) {
            return failureRate;
        }
        if (totalPayments == 0) return 0.0;
        return (double) failedPayments / totalPayments * 100.0;
    }
    
    // Static factory methods
    public static PaymentAnalytics empty(String userId) {
        return PaymentAnalytics.builder()
            .userId(userId)
            .generatedAt(Instant.now())
            .totalTransactions(0L)
            .totalAmount(BigDecimal.ZERO)
            .successRate(0.0)
            .failureRate(0.0)
            .build();
    }
}