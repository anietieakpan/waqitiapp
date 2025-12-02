package com.waqiti.payment.client.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Risk score calculation result DTO
 * Comprehensive risk score calculation response with detailed breakdown
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RiskScoreCalculation {
    
    private UUID calculationId;
    
    private UUID requestId;
    
    private String entityId;
    
    private RiskScoreRequest.EntityType entityType;
    
    private RiskScoreRequest.RiskScoreType scoreType;
    
    private CalculationStatus status;
    
    private LocalDateTime calculatedAt;
    
    private Long processingTimeMs;
    
    // Core risk score
    @Builder.Default
    private BigDecimal riskScore = BigDecimal.ZERO; // 0-100
    
    private RiskLevel riskLevel;
    
    private Double confidence; // 0.0-1.0
    
    // Detailed calculation breakdown
    private ScoreBreakdown scoreBreakdown;
    
    // Model contributions
    @Builder.Default
    private List<ModelContribution> modelContributions = List.of();
    
    // Factor analysis
    @Builder.Default
    private List<RiskFactor> riskFactors = List.of();
    
    // Calculation methodology
    private CalculationMetadata calculationMetadata;
    
    // Explainability and transparency
    private ScoreExplanation explanation;
    
    // Historical comparison
    private HistoricalComparison historicalComparison;
    
    // Quality metrics
    private QualityMetrics qualityMetrics;
    
    // Additional context
    private Map<String, Object> additionalData;
    
    // Warnings and alerts
    @Builder.Default
    private List<CalculationWarning> warnings = List.of();
    
    public enum CalculationStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        TIMEOUT,
        INSUFFICIENT_DATA
    }
    
    public enum RiskLevel {
        VERY_LOW(0, 20),
        LOW(21, 40),
        MEDIUM(41, 60),
        HIGH(61, 80),
        VERY_HIGH(81, 95),
        CRITICAL(96, 100);
        
        private final int minScore;
        private final int maxScore;
        
        RiskLevel(int minScore, int maxScore) {
            this.minScore = minScore;
            this.maxScore = maxScore;
        }
        
        public static RiskLevel fromScore(BigDecimal score) {
            int scoreInt = score.intValue();
            for (RiskLevel level : values()) {
                if (scoreInt >= level.minScore && scoreInt <= level.maxScore) {
                    return level;
                }
            }
            return CRITICAL;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdown {
        private BigDecimal baseScore;
        private BigDecimal historicalAdjustment;
        private BigDecimal behavioralAdjustment;
        private BigDecimal contextualAdjustment;
        private BigDecimal externalDataAdjustment;
        private BigDecimal modelEnsembleScore;
        private BigDecimal ruleBasedScore;
        private Map<String, BigDecimal> categoryScores;
        private Map<String, BigDecimal> featureContributions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelContribution {
        private String modelName;
        private String modelVersion;
        private BigDecimal modelScore;
        private Double modelWeight;
        private Double modelConfidence;
        private BigDecimal weightedContribution;
        private Map<String, Object> modelFeatures;
        private String modelExplanation;
        private LocalDateTime modelLastTrained;
        private String modelPerformanceMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorName;
        private String factorCategory;
        private BigDecimal factorScore;
        private Double factorWeight;
        private BigDecimal factorContribution;
        private RiskFactorImpact impact;
        private String factorExplanation;
        private Object factorValue;
        private String factorSource; // HISTORICAL, REALTIME, EXTERNAL
        
        public enum RiskFactorImpact {
            VERY_NEGATIVE(-2),
            NEGATIVE(-1),
            NEUTRAL(0),
            POSITIVE(1),
            VERY_POSITIVE(2);
            
            private final int value;
            
            RiskFactorImpact(int value) {
                this.value = value;
            }
            
            public int getValue() {
                return value;
            }
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalculationMetadata {
        private String calculationMethod;
        @Builder.Default
        private List<String> modelsUsed = List.of();
        @Builder.Default
        private List<String> dataSources = List.of();
        private Integer totalFeatures;
        private Integer featuresUsed;
        private String aggregationMethod;
        private Map<String, Double> hyperParameters;
        private String calculationVersion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreExplanation {
        private String primaryReason;
        private String detailedExplanation;
        @Builder.Default
        private List<String> keyFactors = List.of();
        @Builder.Default
        private List<String> riskIndicators = List.of();
        @Builder.Default
        private List<String> protectiveFactors = List.of();
        private String businessImpactExplanation;
        private String recommendedActions;
        private Map<String, String> factorExplanations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalComparison {
        private BigDecimal previousScore;
        private LocalDateTime previousCalculationDate;
        private BigDecimal scoreChange;
        private String scoreTrend; // INCREASING, STABLE, DECREASING
        private BigDecimal averageScoreLast30Days;
        private BigDecimal averageScoreLast90Days;
        private Integer calculationCount;
        private String volatility; // HIGH, MEDIUM, LOW
        @Builder.Default
        private List<ScoreHistory> scoreHistory = List.of();
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ScoreHistory {
            private LocalDateTime calculationDate;
            private BigDecimal score;
            private String triggeringEvent;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics {
        private Double dataCompleteness; // 0.0-1.0
        private Double dataFreshness; // 0.0-1.0
        private Double calculationReliability; // 0.0-1.0
        private Double featureImportance; // 0.0-1.0
        private Integer missingFeatures;
        private Integer staleFeatures;
        @Builder.Default
        private List<String> qualityIssues = List.of();
        private Map<String, Double> dataSourceQuality;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalculationWarning {
        private WarningType type;
        private String message;
        private String description;
        private WarningLevel level;
        private Map<String, Object> warningData;
        
        public enum WarningType {
            INSUFFICIENT_DATA,
            STALE_DATA,
            MODEL_DEGRADATION,
            EXTERNAL_DATA_UNAVAILABLE,
            HIGH_UNCERTAINTY,
            CALCULATION_TIMEOUT,
            FEATURE_MISSING
        }
        
        public enum WarningLevel {
            INFO,
            WARNING,
            ERROR,
            CRITICAL
        }
    }
    
    // Business logic methods
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || 
               riskLevel == RiskLevel.VERY_HIGH || 
               riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean isReliableCalculation() {
        return status == CalculationStatus.SUCCESS &&
               confidence != null && confidence >= 0.7 &&
               (qualityMetrics == null || 
                qualityMetrics.getCalculationReliability() == null ||
                qualityMetrics.getCalculationReliability() >= 0.8);
    }
    
    public boolean hasQualityIssues() {
        return qualityMetrics != null && 
               qualityMetrics.getQualityIssues() != null &&
               !qualityMetrics.getQualityIssues().isEmpty();
    }
    
    public boolean hasCriticalWarnings() {
        return warnings != null && 
               warnings.stream()
                   .anyMatch(w -> w.getLevel() == CalculationWarning.WarningLevel.CRITICAL ||
                                w.getLevel() == CalculationWarning.WarningLevel.ERROR);
    }
    
    public boolean isScoreIncreasing() {
        return historicalComparison != null && 
               "INCREASING".equals(historicalComparison.getScoreTrend());
    }
    
    public boolean isVolatileScore() {
        return historicalComparison != null && 
               "HIGH".equals(historicalComparison.getVolatility());
    }
    
    public BigDecimal getScoreChangePercentage() {
        if (historicalComparison == null || 
            historicalComparison.getPreviousScore() == null || 
            historicalComparison.getScoreChange() == null ||
            historicalComparison.getPreviousScore().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return historicalComparison.getScoreChange()
            .divide(historicalComparison.getPreviousScore(), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    public List<String> getTopRiskFactors(int limit) {
        if (riskFactors == null) {
            return List.of();
        }
        
        return riskFactors.stream()
            .filter(factor -> factor.getImpact() == RiskFactor.RiskFactorImpact.VERY_NEGATIVE ||
                            factor.getImpact() == RiskFactor.RiskFactorImpact.NEGATIVE)
            .sorted((f1, f2) -> f2.getFactorContribution().compareTo(f1.getFactorContribution()))
            .limit(limit)
            .map(RiskFactor::getFactorName)
            .toList();
    }
}