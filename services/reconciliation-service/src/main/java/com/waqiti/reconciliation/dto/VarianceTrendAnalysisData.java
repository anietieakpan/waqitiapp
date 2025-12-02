package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceTrendAnalysisData {

    private LocalDate analysisStartDate;
    
    private LocalDate analysisEndDate;
    
    private VarianceOverview overview;
    
    private List<DailyVarianceTrend> dailyTrends;
    
    private List<WeeklyVarianceTrend> weeklyTrends;
    
    private List<MonthlyVarianceTrend> monthlyTrends;
    
    private Map<String, CategoryVarianceTrend> categoryTrends;
    
    private VarianceStatistics statistics;
    
    private List<VariancePattern> identifiedPatterns;
    
    private PredictiveAnalysis predictiveAnalysis;
    
    private List<VarianceRecommendation> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceOverview {
        private BigDecimal totalVariance;
        private BigDecimal averageDailyVariance;
        private BigDecimal maxVariance;
        private BigDecimal minVariance;
        private LocalDate maxVarianceDate;
        private LocalDate minVarianceDate;
        private String trendDirection;
        private double trendStrength;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyVarianceTrend {
        private LocalDate date;
        private BigDecimal variance;
        private int transactionCount;
        private int breakCount;
        private String dayOfWeek;
        private boolean isHoliday;
        private boolean isMonthEnd;
        private Map<String, BigDecimal> varianceByCategory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyVarianceTrend {
        private int weekNumber;
        private LocalDate weekStartDate;
        private LocalDate weekEndDate;
        private BigDecimal totalVariance;
        private BigDecimal averageVariance;
        private int breakCount;
        private String trendComparedToPreviousWeek;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyVarianceTrend {
        private String month;
        private int year;
        private BigDecimal totalVariance;
        private BigDecimal averageDailyVariance;
        private int totalBreaks;
        private double breakResolutionRate;
        private String trendComparedToPreviousMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryVarianceTrend {
        private String category;
        private List<CategoryDataPoint> dataPoints;
        private BigDecimal totalVariance;
        private BigDecimal averageVariance;
        private String trendDirection;
        private double volatility;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDataPoint {
        private LocalDate date;
        private BigDecimal variance;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceStatistics {
        private BigDecimal mean;
        private BigDecimal median;
        private BigDecimal standardDeviation;
        private BigDecimal variance;
        private double skewness;
        private double kurtosis;
        private BigDecimal percentile25;
        private BigDecimal percentile75;
        private BigDecimal percentile95;
        private BigDecimal percentile99;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariancePattern {
        private String patternType;
        private String description;
        private double confidence;
        private String frequency;
        private List<LocalDate> occurrenceDates;
        private BigDecimal averageImpact;
        private String suggestedAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictiveAnalysis {
        private List<VariancePrediction> predictions;
        private double modelAccuracy;
        private String modelType;
        private LocalDate lastModelUpdate;
        private List<String> keyPredictors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariancePrediction {
        private LocalDate predictedDate;
        private BigDecimal predictedVariance;
        private BigDecimal confidenceIntervalLower;
        private BigDecimal confidenceIntervalUpper;
        private double confidenceLevel;
        private List<String> riskFactors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceRecommendation {
        private String recommendationType;
        private String description;
        private String expectedImpact;
        private String priority;
        private String implementationComplexity;
        private BigDecimal estimatedVarianceReduction;
    }

    public boolean hasIncreasingTrend() {
        return overview != null && "INCREASING".equalsIgnoreCase(overview.getTrendDirection());
    }

    public boolean hasSignificantVariance() {
        return overview != null && overview.getTotalVariance() != null &&
               overview.getTotalVariance().abs().compareTo(new BigDecimal("10000")) > 0;
    }

    public boolean hasPatterns() {
        return identifiedPatterns != null && !identifiedPatterns.isEmpty();
    }

    public boolean hasPredictions() {
        return predictiveAnalysis != null && 
               predictiveAnalysis.getPredictions() != null && 
               !predictiveAnalysis.getPredictions().isEmpty();
    }
}