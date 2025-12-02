package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakAnalysisReportData {

    private LocalDate reportDate;
    
    private BreakOverview overview;
    
    private List<BreakDetail> breakDetails;
    
    private Map<String, BreakTypeAnalysis> breakTypeAnalyses;
    
    private TrendAnalysis trendAnalysis;
    
    private RootCauseAnalysisSummary rootCauseAnalysis;
    
    private ResolutionPerformance resolutionPerformance;
    
    private List<RecurringBreakPattern> recurringPatterns;
    
    private List<RecommendedAction> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakOverview {
        private int totalBreaks;
        private int newBreaks;
        private int resolvedBreaks;
        private int pendingBreaks;
        private int escalatedBreaks;
        private BigDecimal totalVarianceAmount;
        private double averageResolutionTimeHours;
        private double autoResolutionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakDetail {
        private UUID breakId;
        private String breakType;
        private String severity;
        private String status;
        private BigDecimal varianceAmount;
        private LocalDateTime detectedAt;
        private LocalDateTime resolvedAt;
        private String resolutionMethod;
        private String rootCause;
        private String assignedTo;
        private boolean isRecurring;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakTypeAnalysis {
        private String breakType;
        private int count;
        private BigDecimal totalVariance;
        private double averageVariance;
        private double resolutionRate;
        private double averageResolutionTimeHours;
        private List<String> commonCauses;
        private String recommendedAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysis {
        private List<DailyBreakTrend> dailyTrends;
        private String trendDirection;
        private double trendPercentageChange;
        private String seasonalPattern;
        private List<String> correlatedFactors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBreakTrend {
        private LocalDate date;
        private int breakCount;
        private BigDecimal totalVariance;
        private int resolvedCount;
        private double resolutionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootCauseAnalysisSummary {
        private Map<String, Integer> rootCauseFrequency;
        private List<String> topRootCauses;
        private Map<String, BigDecimal> rootCauseImpact;
        private List<SystemicIssue> systemicIssues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemicIssue {
        private String issueType;
        private String description;
        private int occurrenceCount;
        private BigDecimal totalImpact;
        private String proposedSolution;
        private String priority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionPerformance {
        private double overallResolutionRate;
        private double autoResolutionRate;
        private double manualResolutionRate;
        private Map<String, Double> resolutionRateByType;
        private Map<String, Double> averageResolutionTimeByType;
        private List<ResolutionMethodEffectiveness> methodEffectiveness;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionMethodEffectiveness {
        private String method;
        private int attemptCount;
        private int successCount;
        private double successRate;
        private double averageTimeMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecurringBreakPattern {
        private String patternType;
        private String description;
        private int occurrenceCount;
        private String frequency;
        private List<UUID> affectedBreaks;
        private String suggestedPrevention;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedAction {
        private String actionType;
        private String description;
        private String expectedBenefit;
        private String priority;
        private String estimatedEffort;
        private String responsibleParty;
    }

    public boolean hasBreaks() {
        return overview != null && overview.getTotalBreaks() > 0;
    }

    public boolean hasRecurringPatterns() {
        return recurringPatterns != null && !recurringPatterns.isEmpty();
    }

    public boolean hasSystemicIssues() {
        return rootCauseAnalysis != null && 
               rootCauseAnalysis.getSystemicIssues() != null && 
               !rootCauseAnalysis.getSystemicIssues().isEmpty();
    }
}