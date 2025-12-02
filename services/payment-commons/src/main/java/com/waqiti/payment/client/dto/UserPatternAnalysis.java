package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * User Pattern Analysis
 * 
 * Comprehensive analysis of a user's behavioral patterns for fraud detection.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPatternAnalysis {
    
    /**
     * Analysis ID
     */
    private String analysisId;
    
    /**
     * User ID being analyzed
     */
    private String userId;
    
    /**
     * Analysis period
     */
    private AnalysisPeriod analysisPeriod;
    
    /**
     * Overall behavior score
     */
    private Double behaviorScore;
    
    /**
     * Risk level assessment
     */
    private RiskLevel riskLevel;
    
    /**
     * Transaction patterns
     */
    private TransactionPatterns transactionPatterns;
    
    /**
     * Temporal patterns
     */
    private TemporalPatterns temporalPatterns;
    
    /**
     * Geographic patterns
     */
    private GeographicPatterns geographicPatterns;
    
    /**
     * Device patterns
     */
    private DevicePatterns devicePatterns;
    
    /**
     * Payment method patterns
     */
    private PaymentMethodPatterns paymentMethodPatterns;
    
    /**
     * Velocity patterns
     */
    private VelocityPatterns velocityPatterns;
    
    /**
     * Anomalies detected
     */
    private List<BehaviorAnomaly> anomalies;
    
    /**
     * Behavioral flags
     */
    private List<BehaviorFlag> flags;
    
    /**
     * Comparison with peer group
     */
    private PeerComparison peerComparison;
    
    /**
     * Historical baseline
     */
    private HistoricalBaseline baseline;
    
    /**
     * Confidence metrics
     */
    private ConfidenceMetrics confidence;
    
    /**
     * Analysis timestamp
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Analysis metadata
     */
    private Map<String, Object> metadata;
    
    public enum RiskLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisPeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Integer daysCovered;
        private Integer transactionsAnalyzed;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionPatterns {
        private AmountPattern amountPattern;
        private FrequencyPattern frequencyPattern;
        private TypePattern typePattern;
        private MerchantPattern merchantPattern;
        private Double patternStability;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountPattern {
        private BigDecimal avgAmount;
        private BigDecimal medianAmount;
        private BigDecimal maxAmount;
        private BigDecimal minAmount;
        private Double stdDeviation;
        private List<AmountRange> preferredRanges;
        private Double amountVariability;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountRange {
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Integer frequency;
        private Double percentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequencyPattern {
        private Double dailyAvg;
        private Double weeklyAvg;
        private Double monthlyAvg;
        private Integer maxDailyTransactions;
        private List<Integer> commonFrequencies;
        private String frequencyTrend;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypePattern {
        private Map<String, Integer> transactionTypeCounts;
        private String mostCommonType;
        private Double typeConsistency;
        private List<String> unusualTypes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantPattern {
        private List<String> frequentMerchants;
        private Integer uniqueMerchantCount;
        private Double merchantLoyalty;
        private List<String> newMerchants;
        private Map<String, Integer> merchantCategories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemporalPatterns {
        private HourlyPattern hourlyPattern;
        private WeeklyPattern weeklyPattern;
        private SeasonalPattern seasonalPattern;
        private Double temporalConsistency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyPattern {
        private Map<Integer, Integer> hourlyDistribution;
        private List<Integer> peakHours;
        private List<Integer> offPeakHours;
        private Double hourlyVariability;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyPattern {
        private Map<String, Integer> dayOfWeekDistribution;
        private List<String> activeDays;
        private Boolean weekendActivity;
        private Double weeklyConsistency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonalPattern {
        private Map<String, Integer> monthlyDistribution;
        private String seasonalTrend;
        private List<String> activePeriods;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicPatterns {
        private List<String> commonCountries;
        private List<String> commonCities;
        private Integer uniqueLocationCount;
        private Double locationConsistency;
        private List<String> unusualLocations;
        private Boolean frequentTravel;
        private Double geographicRadius;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DevicePatterns {
        private List<String> commonDevices;
        private Integer uniqueDeviceCount;
        private Map<String, Integer> deviceTypes;
        private Double deviceConsistency;
        private List<String> newDevices;
        private Boolean multipleDeviceUser;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodPatterns {
        private Map<String, Integer> methodUsage;
        private String preferredMethod;
        private Double methodConsistency;
        private List<String> newMethods;
        private Integer uniqueMethodCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityPatterns {
        private Double avgDailyAmount;
        private Double avgDailyCount;
        private Integer maxDailyCount;
        private BigDecimal maxDailyAmount;
        private VelocityTrend trend;
        private List<VelocitySpike> spikes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityTrend {
        private String direction; // INCREASING, DECREASING, STABLE
        private Double changeRate;
        private String timeframe;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocitySpike {
        private LocalDateTime timestamp;
        private Integer transactionCount;
        private BigDecimal totalAmount;
        private String spikeType;
        private Double severity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorAnomaly {
        private String anomalyType;
        private String description;
        private Double severity;
        private LocalDateTime detectedAt;
        private Double confidence;
        private Map<String, Object> anomalyData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorFlag {
        private String flagType;
        private String description;
        private String priority;
        private Double riskImpact;
        private Map<String, Object> flagData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeerComparison {
        private String peerGroupDefinition;
        private Integer peerGroupSize;
        private Double behaviorSimilarity;
        private List<String> differences;
        private Double percentileRank;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalBaseline {
        private LocalDateTime baselinePeriodStart;
        private LocalDateTime baselinePeriodEnd;
        private Double behaviorStability;
        private List<String> significantChanges;
        private Double deviationScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfidenceMetrics {
        private Double overallConfidence;
        private Double dataQuality;
        private Double sampleSize;
        private Double modelAccuracy;
        private List<String> confidenceLimiters;
    }
    
    /**
     * Check if user is high risk
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || 
               riskLevel == RiskLevel.VERY_HIGH || 
               riskLevel == RiskLevel.CRITICAL;
    }
    
    /**
     * Check if behavior has significantly changed
     */
    public boolean hasSignificantBehaviorChange() {
        return baseline != null && 
               baseline.getDeviationScore() != null &&
               baseline.getDeviationScore() > 0.7;
    }
    
    /**
     * Get critical anomalies
     */
    public List<BehaviorAnomaly> getCriticalAnomalies() {
        if (anomalies == null) {
            return List.of();
        }
        
        return anomalies.stream()
                .filter(a -> a.getSeverity() != null && a.getSeverity() >= 0.8)
                .toList();
    }
    
    /**
     * Get high priority flags
     */
    public List<BehaviorFlag> getHighPriorityFlags() {
        if (flags == null) {
            return List.of();
        }
        
        return flags.stream()
                .filter(f -> "HIGH".equals(f.getPriority()) || "CRITICAL".equals(f.getPriority()))
                .toList();
    }
    
    /**
     * Check if analysis has sufficient confidence
     */
    public boolean hasSufficientConfidence() {
        return confidence != null && 
               confidence.getOverallConfidence() != null &&
               confidence.getOverallConfidence() >= 0.7;
    }
    
    /**
     * Get behavior summary
     */
    public String getBehaviorSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Risk Level: ").append(riskLevel);
        summary.append(", Behavior Score: ").append(String.format("%.2f", behaviorScore));
        
        if (hasSignificantBehaviorChange()) {
            summary.append(", Significant Change Detected");
        }
        
        if (getCriticalAnomalies().size() > 0) {
            summary.append(", ").append(getCriticalAnomalies().size()).append(" Critical Anomalies");
        }
        
        return summary.toString();
    }
}