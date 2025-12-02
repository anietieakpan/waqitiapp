package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Analytics Filter Model
 * 
 * Comprehensive filtering and aggregation options for payment analytics
 * with support for time periods, grouping, and dimensional analysis.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsFilter {
    
    // Core filters
    private String userId;
    private List<String> userIds;
    
    // Time period
    private Instant startDate;
    private Instant endDate;
    private TimePeriod period;
    
    // Grouping dimensions
    private List<GroupByDimension> groupBy;
    private List<MetricType> metrics;
    
    // Filtering
    private List<PaymentStatus> statuses;
    private List<PaymentType> paymentTypes;
    private List<ProviderType> providerTypes;
    private List<String> currencies;
    private List<String> channels;
    private List<String> merchantIds;
    
    // Geographical filtering
    private List<String> countries;
    private List<String> regions;
    private List<String> cities;
    
    // Risk and compliance
    private Integer minRiskScore;
    private Integer maxRiskScore;
    private Boolean flaggedOnly;
    private Boolean complianceReviewedOnly;
    
    // Aggregation settings
    private AggregationLevel aggregationLevel;
    private List<PercentileLevel> percentiles;
    
    // Comparison settings
    private boolean compareWithPreviousPeriod;
    private Instant comparisonStartDate;
    private Instant comparisonEndDate;
    
    // Advanced filtering
    private String cohortId;
    private List<String> segments;
    private List<String> tags;
    
    // Output configuration
    private boolean includeSubMetrics;
    private boolean includeBreakdowns;
    private boolean includeDistributions;
    private boolean includeTrends;
    private boolean includeForecasts;
    
    // Sample settings
    private Double sampleRate;
    private Integer maxRecords;
    
    // Legacy fields for backward compatibility
    private PaymentType paymentType;
    private ProviderType providerType;
    private PaymentStatus status;
    private String groupByLegacy;
    
    // Enums
    public enum TimePeriod {
        HOUR, DAY, WEEK, MONTH, QUARTER, YEAR, CUSTOM
    }
    
    public enum GroupByDimension {
        TIME, PAYMENT_TYPE, PROVIDER_TYPE, CURRENCY, COUNTRY,
        CHANNEL, MERCHANT, USER_SEGMENT, DEVICE_TYPE, RISK_LEVEL,
        AMOUNT_RANGE, HOUR_OF_DAY, DAY_OF_WEEK, STATUS, COHORT
    }
    
    public enum MetricType {
        TRANSACTION_COUNT, TRANSACTION_VOLUME, SUCCESS_RATE, FAILURE_RATE,
        AVERAGE_AMOUNT, MEDIAN_AMOUNT, PROCESSING_TIME, UNIQUE_USERS,
        REVENUE, FEES_COLLECTED, REFUND_RATE, DISPUTE_RATE,
        CONVERSION_RATE, RETENTION_RATE, CHURN_RATE, LTV, AOV, ARPU,
        GMV, RISK_SCORE, FRAUD_RATE
    }
    
    public enum AggregationLevel {
        RAW, HOURLY, DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }
    
    public enum PercentileLevel {
        P50(50), P75(75), P90(90), P95(95), P99(99);
        
        private final int value;
        
        PercentileLevel(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    // Helper methods
    public boolean hasTimeFilter() {
        return startDate != null || endDate != null || period != null;
    }
    
    public boolean hasUserFilter() {
        return userId != null || (userIds != null && !userIds.isEmpty());
    }
    
    public void applyTimePeriod() {
        if (period != null && period != TimePeriod.CUSTOM) {
            Instant now = Instant.now();
            switch (period) {
                case DAY:
                    this.startDate = now.minus(java.time.Duration.ofDays(1));
                    this.endDate = now;
                    break;
                case WEEK:
                    this.startDate = now.minus(java.time.Duration.ofDays(7));
                    this.endDate = now;
                    break;
                case MONTH:
                    this.startDate = now.minus(java.time.Duration.ofDays(30));
                    this.endDate = now;
                    break;
            }
        }
    }
    
    // Validation
    public void validate() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        
        if (sampleRate != null && (sampleRate <= 0 || sampleRate > 1)) {
            throw new IllegalArgumentException("Sample rate must be between 0 and 1");
        }
    }
    
    // Legacy factory methods for backward compatibility
    public static AnalyticsFilter defaultFilter() {
        return AnalyticsFilter.builder()
                .startDate(Instant.now().minus(java.time.Duration.ofDays(30)))
                .endDate(Instant.now())
                .period(TimePeriod.DAY)
                .aggregationLevel(AggregationLevel.DAILY)
                .build();
    }
    
    public static AnalyticsFilter lastMonth() {
        return AnalyticsFilter.builder()
                .startDate(Instant.now().minus(java.time.Duration.ofDays(30)))
                .endDate(Instant.now())
                .period(TimePeriod.MONTH)
                .aggregationLevel(AggregationLevel.DAILY)
                .build();
    }
    
    public static AnalyticsFilter lastYear() {
        return AnalyticsFilter.builder()
                .startDate(Instant.now().minus(java.time.Duration.ofDays(365)))
                .endDate(Instant.now())
                .period(TimePeriod.YEAR)
                .aggregationLevel(AggregationLevel.MONTHLY)
                .build();
    }
}