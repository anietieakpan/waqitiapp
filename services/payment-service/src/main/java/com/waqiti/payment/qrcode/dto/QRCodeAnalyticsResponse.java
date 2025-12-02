package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for QR code analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing QR code analytics")
public class QRCodeAnalyticsResponse {

    @Schema(description = "Overview statistics")
    private Overview overview;

    @Schema(description = "Trend analysis")
    private Trends trends;

    @Schema(description = "Performance metrics")
    private Performance performance;

    @Schema(description = "Demographic analysis")
    private Demographics demographics;

    @Schema(description = "Conversion rates")
    @JsonProperty("conversion_rates")
    private ConversionRates conversionRates;

    @Schema(description = "Top performers")
    @JsonProperty("top_performers")
    private TopPerformers topPerformers;

    @Schema(description = "Risk analysis")
    @JsonProperty("risk_analysis")
    private RiskAnalysis riskAnalysis;

    @Schema(description = "Geographic analysis")
    @JsonProperty("geographic_analysis")
    private Map<String, Object> geographicAnalysis;

    @Schema(description = "Time series data")
    @JsonProperty("time_series_data")
    private Map<String, Object> timeSeriesData;

    @Schema(description = "Recommendations")
    private List<String> recommendations;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Report generation timestamp")
    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;

    @Schema(description = "Report period")
    @JsonProperty("report_period")
    private String reportPeriod;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Overview {
        @JsonProperty("total_qr_codes")
        private Long totalQRCodes;
        
        @JsonProperty("completed_payments")
        private Long completedPayments;
        
        @JsonProperty("total_value")
        private BigDecimal totalValue;
        
        @JsonProperty("average_value")
        private BigDecimal averageValue;
        
        @JsonProperty("conversion_rate")
        private Double conversionRate;
        
        @JsonProperty("status_breakdown")
        private Map<String, Long> statusBreakdown;
        
        @JsonProperty("type_breakdown")
        private Map<String, Long> typeBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Trends {
        @JsonProperty("growth_rates")
        private Map<String, Object> growthRates;
        
        @JsonProperty("seasonal_patterns")
        private Map<String, Object> seasonalPatterns;
        
        @JsonProperty("peak_usage_times")
        private Map<String, Object> peakUsageTimes;
        
        @JsonProperty("trend_indicators")
        private Map<String, Object> trendIndicators;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Performance {
        @JsonProperty("average_processing_time")
        private Double averageProcessingTime;
        
        @JsonProperty("success_rates_by_type")
        private Map<String, Double> successRatesByType;
        
        @JsonProperty("error_breakdown")
        private Map<String, Long> errorBreakdown;
        
        @JsonProperty("performance_scores")
        private Map<String, Object> performanceScores;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Demographics {
        @JsonProperty("user_segments")
        private Map<String, Long> userSegments;
        
        @JsonProperty("merchant_categories")
        private Map<String, Long> merchantCategories;
        
        @JsonProperty("device_types")
        private Map<String, Long> deviceTypes;
        
        @JsonProperty("geographic_distribution")
        private Map<String, Object> geographicDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionRates {
        @JsonProperty("overall_rate")
        private Double overallRate;
        
        @JsonProperty("by_type")
        private Map<String, Double> byType;
        
        @JsonProperty("by_time_period")
        private Map<String, Double> byTimePeriod;
        
        @JsonProperty("funnel_analysis")
        private Map<String, Object> funnelAnalysis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPerformers {
        @JsonProperty("top_merchants")
        private List<Object> topMerchants;
        
        @JsonProperty("top_users")
        private List<Object> topUsers;
        
        @JsonProperty("top_qr_types")
        private Map<String, Object> topQRTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAnalysis {
        @JsonProperty("high_risk_transactions")
        private Integer highRiskTransactions;
        
        @JsonProperty("fraud_indicators")
        private Map<String, Object> fraudIndicators;
        
        @JsonProperty("risk_distribution")
        private Map<String, Long> riskDistribution;
        
        @JsonProperty("suspicious_patterns")
        private List<Object> suspiciousPatterns;
    }
}