package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MISDocument {
    
    private String reportId;
    private String reportType;
    private String reportTitle;
    private String managementLevel;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private ReportPeriod period;
    private ExecutiveSummary executiveSummary;
    private List<KPIMetric> keyMetrics;
    private List<BusinessSegment> segmentAnalysis;
    private List<PerformanceTrend> trends;
    private List<Alert> alerts;
    private List<Recommendation> recommendations;
    private Map<String, ChartData> visualizations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPeriod {
        private LocalDate startDate;
        private LocalDate endDate;
        private String frequency;
        private String comparisonPeriod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveSummary {
        private String summary;
        private List<String> keyHighlights;
        private List<String> criticalIssues;
        private String overallPerformance;
        private BigDecimal performanceScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KPIMetric {
        private String name;
        private String category;
        private BigDecimal actualValue;
        private BigDecimal targetValue;
        private BigDecimal previousValue;
        private String unit;
        private String status;
        private BigDecimal variance;
        private BigDecimal variancePercentage;
        private String trend;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessSegment {
        private String segmentName;
        private String segmentType;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private BigDecimal margin;
        private Integer transactionVolume;
        private Integer customerCount;
        private Map<String, BigDecimal> metrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceTrend {
        private String metric;
        private String period;
        private List<DataPoint> dataPoints;
        private String trendDirection;
        private BigDecimal growthRate;
        private String forecast;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private LocalDate date;
        private BigDecimal value;
        private String label;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private String severity;
        private String category;
        private String message;
        private LocalDateTime timestamp;
        private String impact;
        private String action;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String priority;
        private String area;
        private String recommendation;
        private String expectedBenefit;
        private String implementation;
        private BigDecimal estimatedImpact;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartData {
        private String chartType;
        private String title;
        private List<String> labels;
        private List<DataSeries> series;
        private Map<String, Object> options;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSeries {
        private String name;
        private List<BigDecimal> data;
        private String color;
        private String type;
    }
}