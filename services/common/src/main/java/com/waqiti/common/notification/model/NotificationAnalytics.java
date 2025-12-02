package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Analytics data for notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationAnalytics {
    
    /**
     * Analytics ID
     */
    private String analyticsId;
    
    /**
     * Time period
     */
    private TimePeriod timePeriod;
    
    /**
     * Overall metrics
     */
    private OverallMetrics overallMetrics;
    
    /**
     * Channel breakdown
     */
    private Map<NotificationChannel, ChannelMetrics> channelMetrics;
    
    /**
     * Category breakdown
     */
    private Map<String, CategoryMetrics> categoryMetrics;
    
    /**
     * Engagement analytics
     */
    private EngagementAnalytics engagementAnalytics;
    
    /**
     * Performance metrics
     */
    private PerformanceMetrics performanceMetrics;
    
    /**
     * Error analytics
     */
    private ErrorAnalytics errorAnalytics;
    
    /**
     * Cost analytics
     */
    private CostAnalytics costAnalytics;
    
    /**
     * Trends
     */
    private List<TrendData> trends;
    
    /**
     * Generated at
     */
    private Instant generatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePeriod {
        private LocalDate startDate;
        private LocalDate endDate;
        private String periodType;
        private String timezone;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallMetrics {
        private long totalSent;
        private long totalDelivered;
        private long totalFailed;
        private long totalBounced;
        private long totalComplained;
        private double deliveryRate;
        private double failureRate;
        private double bounceRate;
        private double complaintRate;
        private long uniqueRecipients;
        private double averageDeliveryTimeMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelMetrics {
        private long sent;
        private long delivered;
        private long failed;
        private double deliveryRate;
        private double openRate;
        private double clickRate;
        private double conversionRate;
        private Map<String, Long> providerBreakdown;
        private List<HourlyMetric> hourlyBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryMetrics {
        private String category;
        private long sent;
        private long delivered;
        private double engagementRate;
        private double unsubscribeRate;
        private Map<NotificationChannel, Long> channelBreakdown;
        private List<DailyMetric> dailyBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementAnalytics {
        private long totalOpens;
        private long uniqueOpens;
        private double openRate;
        private long totalClicks;
        private long uniqueClicks;
        private double clickRate;
        private double clickToOpenRate;
        private Map<String, ClickAnalytics> linkAnalytics;
        private List<DeviceAnalytics> deviceBreakdown;
        private Map<String, Long> clientBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClickAnalytics {
        private String url;
        private long clickCount;
        private long uniqueClicks;
        private double clickRate;
        private Map<String, Long> deviceBreakdown;
        private List<HourlyMetric> timeBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAnalytics {
        private String deviceType;
        private String platform;
        private long count;
        private double percentage;
        private double engagementRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private double averageQueueTimeMs;
        private double averageProcessingTimeMs;
        private double averageDeliveryTimeMs;
        private Map<Integer, Long> deliveryTimeDistribution;
        private double throughputPerSecond;
        private long peakThroughput;
        private Instant peakTime;
        private Map<String, ProviderPerformance> providerPerformance;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderPerformance {
        private String provider;
        private double averageResponseTimeMs;
        private double successRate;
        private long requestCount;
        private Map<String, Long> errorBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorAnalytics {
        private long totalErrors;
        private Map<String, ErrorMetric> errorsByType;
        private Map<String, Long> errorsByProvider;
        private List<ErrorTrend> errorTrends;
        private double errorRate;
        private Map<String, Long> retryAnalytics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorMetric {
        private String errorType;
        private long count;
        private double percentage;
        private boolean retryable;
        private double retrySuccessRate;
        private List<String> commonCauses;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorTrend {
        private Instant timestamp;
        private Map<String, Long> errorCounts;
        private double errorRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostAnalytics {
        private double totalCost;
        private String currency;
        private Map<NotificationChannel, Double> costByChannel;
        private Map<String, Double> costByCategory;
        private Map<String, Double> costByProvider;
        private double averageCostPerNotification;
        private double costPerDelivery;
        private List<DailyCost> dailyCosts;
        private CostOptimization optimization;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCost {
        private LocalDate date;
        private double cost;
        private long notificationCount;
        private double averageCost;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostOptimization {
        private double potentialSavings;
        private List<String> recommendations;
        private Map<String, Double> savingsByRecommendation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private String metric;
        private String trendDirection;
        private double changePercentage;
        private String insight;
        private List<DataPoint> dataPoints;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private Instant timestamp;
        private double value;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyMetric {
        private int hour;
        private long count;
        private double rate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMetric {
        private LocalDate date;
        private long count;
        private double rate;
        private Map<String, Long> breakdown;
    }
}