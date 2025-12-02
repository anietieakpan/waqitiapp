package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import com.waqiti.common.enums.RiskLevel;
import com.waqiti.common.enums.TrendDirection;
import com.waqiti.common.observability.dto.DataPoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive trend analysis for metrics with predictive insights
 * Provides statistical analysis, forecasting, and anomaly detection
 */
@Data
@Builder
public class TrendAnalysis {
    
    private String metricName;
    private String unit;
    private LocalDateTime analysisTimestamp;
    private int dataPointCount;
    private LocalDateTime timeRangeStart;
    private LocalDateTime timeRangeEnd;
    
    // Raw data and processed points
    private List<DataPoint> rawDataPoints;
    private List<DataPoint> smoothedDataPoints;
    private List<DataPoint> predictedDataPoints;
    
    // Statistical analysis
    private StatisticalSummary statistics;
    private TrendDirection overallTrend;
    private double trendStrength; // 0.0 to 1.0
    private double trendSlope;
    private double correlation; // R-squared value
    private double volatility;
    
    // Trend characteristics
    private boolean isSeasonalPattern;
    private SeasonalityInfo seasonality;
    private List<TrendSegment> trendSegments;
    private List<TrendChangePoint> changePoints;
    
    // Anomaly detection
    private List<AnomalyPoint> anomalies;
    private AnomalyDetectionMethod detectionMethod;
    private double anomalyThreshold;
    private int anomalyCount;
    
    // Forecasting
    private ForecastResult shortTermForecast; // Next 1-4 hours
    private ForecastResult mediumTermForecast; // Next 1-7 days
    private ForecastResult longTermForecast; // Next 1-4 weeks
    private double forecastConfidence;
    
    // Pattern analysis
    private List<Pattern> detectedPatterns;
    private CyclicalPattern dailyPattern;
    private CyclicalPattern weeklyPattern;
    private CyclicalPattern monthlyPattern;
    
    // Comparative analysis
    private ComparisonResult periodOverPeriodComparison;
    private ComparisonResult yearOverYearComparison;
    private List<String> similarMetrics;
    
    // Insights and recommendations
    private List<String> keyInsights;
    private List<String> recommendations;
    private List<String> alertRecommendations;
    private RiskAssessment riskAssessment;
    
    /**
     * Determine if the trend is indicating a potential issue
     */
    public boolean isIndicatingIssue() {
        if (overallTrend == TrendDirection.STABLE) return false;
        
        // High volatility with negative trend
        if (volatility > 0.7 && (overallTrend == TrendDirection.DECREASING || 
                                overallTrend == TrendDirection.WORSENING)) {
            return true;
        }
        
        // Strong negative trend
        if (trendStrength > 0.8 && trendSlope < -0.1) {
            return true;
        }
        
        // Multiple recent anomalies
        if (anomalyCount > 3 && getRecentAnomalies(60).size() > 2) {
            return true;
        }
        
        // Risk assessment indicates high risk
        return riskAssessment != null && riskAssessment.getRiskLevel() == RiskLevel.HIGH;
    }
    
    /**
     * Get recent anomalies within specified minutes
     */
    public List<AnomalyPoint> getRecentAnomalies(int withinMinutes) {
        if (anomalies == null || anomalies.isEmpty()) {
            return List.of();
        }
        
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(withinMinutes);
        return anomalies.stream()
            .filter(anomaly -> anomaly.getTimestamp().isAfter(cutoff))
            .toList();
    }
    
    /**
     * Get trend summary for display
     */
    public String getTrendSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Trend: %s", overallTrend.getDisplayName()));
        
        if (trendStrength > 0.0) {
            summary.append(String.format(" (Strength: %.1f%%)", trendStrength * 100));
        }
        
        if (statistics != null) {
            summary.append(String.format("\nCurrent: %.2f %s", statistics.getCurrent(), unit != null ? unit : ""));
            summary.append(String.format(" | Avg: %.2f", statistics.getAverage()));
        }
        
        if (volatility > 0.0) {
            summary.append(String.format(" | Volatility: %.1f%%", volatility * 100));
        }
        
        if (anomalyCount > 0) {
            summary.append(String.format("\nAnomalies detected: %d", anomalyCount));
        }
        
        if (shortTermForecast != null) {
            summary.append(String.format("\nShort-term forecast: %s", 
                shortTermForecast.getSummary()));
        }
        
        return summary.toString();
    }
    
    /**
     * Get the most significant change point
     */
    public TrendChangePoint getMostSignificantChangePoint() {
        if (changePoints == null || changePoints.isEmpty()) {
            return null;
        }
        
        return changePoints.stream()
            .max((cp1, cp2) -> Double.compare(cp1.getSignificance(), cp2.getSignificance()))
            .orElse(null);
    }
    
    /**
     * Check if trend is accelerating (getting worse or better faster)
     */
    public boolean isTrendAccelerating() {
        if (trendSegments == null || trendSegments.size() < 2) {
            return false;
        }
        
        // Compare recent segment slope with previous segment
        TrendSegment recent = trendSegments.get(trendSegments.size() - 1);
        TrendSegment previous = trendSegments.get(trendSegments.size() - 2);
        
        return Math.abs(recent.getSlope()) > Math.abs(previous.getSlope()) * 1.2;
    }
    
    /**
     * Get confidence level for the analysis
     */
    public ConfidenceLevel getAnalysisConfidence() {
        if (dataPointCount < 10) return ConfidenceLevel.LOW;
        if (correlation < 0.5) return ConfidenceLevel.LOW;
        if (dataPointCount >= 100 && correlation >= 0.8) return ConfidenceLevel.HIGH;
        return ConfidenceLevel.MEDIUM;
    }
    
    /**
     * Generate actionable insights based on trend analysis
     */
    public List<String> generateActionableInsights() {
        List<String> insights = List.of();
        
        if (isIndicatingIssue()) {
            insights.add("⚠️ Trend analysis indicates potential performance degradation");
            
            if (isTrendAccelerating()) {
                insights.add("📈 Issue is accelerating - immediate attention recommended");
            }
            
            if (shortTermForecast != null && shortTermForecast.getDirection() == TrendDirection.WORSENING) {
                insights.add("🔮 Short-term forecast predicts continued degradation");
            }
        }
        
        if (isSeasonalPattern && seasonality != null) {
            insights.add(String.format("🔄 Seasonal pattern detected with %s cycle", 
                seasonality.getCycleType().toLowerCase()));
        }
        
        if (volatility > 0.8) {
            insights.add("📊 High volatility detected - consider implementing smoothing or alerting adjustments");
        }
        
        if (anomalyCount > 0) {
            insights.add(String.format("🎯 %d anomalies detected - review for potential issues or data quality problems", anomalyCount));
        }
        
        return insights;
    }
    
    /**
     * Create a simple trend analysis from data points
     */
    public static TrendAnalysis analyze(String metricName, List<DataPoint> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return TrendAnalysis.builder()
                .metricName(metricName)
                .analysisTimestamp(LocalDateTime.now())
                .dataPointCount(0)
                .overallTrend(TrendDirection.UNKNOWN)
                .trendStrength(0.0)
                .build();
        }
        
        // Calculate basic statistics
        double sum = dataPoints.stream().mapToDouble(DataPoint::getValue).sum();
        double average = sum / dataPoints.size();
        double min = dataPoints.stream().mapToDouble(DataPoint::getValue).min().orElse(0);
        double max = dataPoints.stream().mapToDouble(DataPoint::getValue).max().orElse(0);
        
        StatisticalSummary stats = StatisticalSummary.builder()
            .average(average)
            .minimum(min)
            .maximum(max)
            .current(dataPoints.get(dataPoints.size() - 1).getValue())
            .build();
        
        // Determine basic trend direction
        TrendDirection trend = determineTrendDirection(dataPoints);
        
        return TrendAnalysis.builder()
            .metricName(metricName)
            .analysisTimestamp(LocalDateTime.now())
            .dataPointCount(dataPoints.size())
            .rawDataPoints(dataPoints)
            .statistics(stats)
            .overallTrend(trend)
            .trendStrength(0.5) // Default strength
            .anomalyCount(0)
            .build();
    }
    
    private static TrendDirection determineTrendDirection(List<DataPoint> dataPoints) {
        if (dataPoints.size() < 2) return TrendDirection.STABLE;
        
        double first = dataPoints.get(0).getValue();
        double last = dataPoints.get(dataPoints.size() - 1).getValue();
        double change = (last - first) / first;
        
        if (Math.abs(change) < 0.05) return TrendDirection.STABLE;
        return change > 0 ? TrendDirection.INCREASING : TrendDirection.DECREASING;
    }
}

@Data
@Builder
class StatisticalSummary {
    private double average;
    private double median;
    private double minimum;
    private double maximum;
    private double standardDeviation;
    private double variance;
    private double skewness;
    private double kurtosis;
    private double current;
    private double percentile25;
    private double percentile75;
    private double percentile95;
    private double percentile99;
}

@Data
@Builder
class SeasonalityInfo {
    private String cycleType; // "daily", "weekly", "monthly"
    private int periodLength;
    private double seasonalStrength;
    private List<String> peakPeriods;
    private List<String> lowPeriods;
}

@Data
@Builder
class TrendSegment {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double slope;
    private TrendDirection direction;
    private double correlation;
    private String description;
}

@Data
@Builder
class TrendChangePoint {
    private LocalDateTime timestamp;
    private double valueBefore;
    private double valueAfter;
    private double significance;
    private ChangePointType type;
    private String description;
}

@Data
@Builder
class AnomalyPoint {
    private LocalDateTime timestamp;
    private double value;
    private double expectedValue;
    private double deviation;
    private AnomalySeverity severity;
    private String description;
    private Map<String, Object> context;
}

@Data
@Builder
class ForecastResult {
    private List<DataPoint> forecastPoints;
    private TrendDirection direction;
    private double confidence;
    private String timeHorizon;
    private String methodology;
    private Map<String, Double> confidenceIntervals;
    
    public String getSummary() {
        if (forecastPoints == null || forecastPoints.isEmpty()) {
            return "No forecast available";
        }
        
        DataPoint lastPoint = forecastPoints.get(forecastPoints.size() - 1);
        return String.format("%s trend to %.2f (confidence: %.1f%%)", 
            direction.getDisplayName(), lastPoint.getValue(), confidence * 100);
    }
}

@Data
@Builder
class Pattern {
    private String name;
    private PatternType type;
    private double confidence;
    private String description;
    private LocalDateTime firstDetected;
    private int occurrenceCount;
}

@Data
@Builder
class CyclicalPattern {
    private String name;
    private int cycleLength;
    private double amplitude;
    private List<String> peakTimes;
    private List<String> troughTimes;
    private double confidence;
}

@Data
@Builder
class ComparisonResult {
    private String comparisonType;
    private double percentageChange;
    private String changeDescription;
    private ComparisonSignificance significance;
    private LocalDateTime comparisonPeriodStart;
    private LocalDateTime comparisonPeriodEnd;
}

@Data
@Builder
class RiskAssessment {
    private RiskLevel riskLevel;
    private double riskScore;
    private List<String> riskFactors;
    private List<String> mitigationRecommendations;
    private LocalDateTime nextReviewDate;
}


enum AnomalyDetectionMethod {
    STATISTICAL_OUTLIER,
    MACHINE_LEARNING,
    THRESHOLD_BASED,
    SEASONAL_HYBRID,
    ISOLATION_FOREST,
    Z_SCORE
}

enum ChangePointType {
    LEVEL_SHIFT,
    TREND_CHANGE,
    VARIANCE_CHANGE,
    SEASONAL_CHANGE
}

enum AnomalySeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO
}

enum PatternType {
    PERIODIC, SEASONAL, TRENDING, SPIKE, DIP, NOISE
}

enum ComparisonSignificance {
    HIGHLY_SIGNIFICANT, SIGNIFICANT, MODERATE, MINOR, INSIGNIFICANT
}


enum ConfidenceLevel {
    HIGH, MEDIUM, LOW
}