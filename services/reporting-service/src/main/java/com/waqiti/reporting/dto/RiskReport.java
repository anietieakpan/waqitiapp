package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskReport {
    
    private String reportId;
    private String reportType;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private ReportPeriod period;
    private RiskSummary summary;
    private List<RiskMetric> metrics;
    private List<RiskAlert> alerts;
    private List<RiskRecommendation> recommendations;
    private Map<String, Object> rawData;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String frequency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskSummary {
        private String overallRiskLevel;
        private BigDecimal riskScore;
        private Integer totalAlerts;
        private Integer criticalAlerts;
        private Integer highRiskTransactions;
        private BigDecimal totalExposure;
        private Map<String, BigDecimal> exposureByCategory;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMetric {
        private String name;
        private String category;
        private BigDecimal value;
        private BigDecimal threshold;
        private String status;
        private BigDecimal changeFromPrevious;
        private String trend;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAlert {
        private String alertId;
        private String severity;
        private String category;
        private String description;
        private LocalDateTime detectedAt;
        private String status;
        private List<String> affectedEntities;
        private Map<String, Object> details;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskRecommendation {
        private String recommendationId;
        private String priority;
        private String category;
        private String title;
        private String description;
        private List<String> actions;
        private BigDecimal estimatedImpact;
        private String implementationTimeframe;
    }
}