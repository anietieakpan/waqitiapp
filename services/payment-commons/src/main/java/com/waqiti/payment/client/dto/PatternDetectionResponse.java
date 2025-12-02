package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pattern Detection Response
 * 
 * Response containing detected fraud patterns and analysis results.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternDetectionResponse {
    
    /**
     * Analysis ID
     */
    private String analysisId;
    
    /**
     * Analysis status
     */
    private AnalysisStatus status;
    
    /**
     * Detected patterns
     */
    private List<DetectedPattern> patterns;
    
    /**
     * Pattern summary statistics
     */
    private PatternSummary summary;
    
    /**
     * Analysis metadata
     */
    private AnalysisMetadata analysisMetadata;
    
    /**
     * Recommendations based on patterns
     */
    private List<Recommendation> recommendations;
    
    /**
     * Risk assessment
     */
    private RiskAssessment riskAssessment;
    
    /**
     * Alerts generated
     */
    private List<GeneratedAlert> alerts;
    
    /**
     * Model performance metrics
     */
    private List<ModelPerformance> modelPerformance;
    
    /**
     * Data quality metrics
     */
    private DataQuality dataQuality;
    
    /**
     * Analysis errors (if any)
     */
    private List<AnalysisError> errors;
    
    /**
     * Confidence in overall analysis
     */
    private Double overallConfidence;
    
    /**
     * Next analysis suggestions
     */
    private List<String> nextAnalysisSuggestions;
    
    /**
     * Analysis started timestamp
     */
    private LocalDateTime startedAt;
    
    /**
     * Analysis completed timestamp
     */
    private LocalDateTime completedAt;
    
    /**
     * Analysis duration in milliseconds
     */
    private Long durationMs;
    
    public enum AnalysisStatus {
        COMPLETED,
        PARTIAL,
        FAILED,
        TIMEOUT,
        CANCELLED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedPattern {
        private String patternId;
        private PatternDetectionRequest.PatternType patternType;
        private String patternName;
        private String description;
        private Double confidence;
        private String severity;
        private PatternCharacteristics characteristics;
        private List<PatternInstance> instances;
        private PatternStats stats;
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private Map<String, Object> patternData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternCharacteristics {
        private String frequency;
        private String distribution;
        private String trend;
        private Map<String, Object> attributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternInstance {
        private String instanceId;
        private LocalDateTime timestamp;
        private List<String> entityIds;
        private Map<String, Object> instanceData;
        private Double instanceConfidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternStats {
        private Integer totalInstances;
        private Integer uniqueEntities;
        private Double avgConfidence;
        private String timeSpan;
        private Map<String, Integer> entityBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternSummary {
        private Integer totalPatterns;
        private Integer highConfidencePatterns;
        private Integer criticalPatterns;
        private Map<String, Integer> patternsByType;
        private Map<String, Integer> patternsBySeverity;
        private Double avgConfidence;
        private String mostCommonPattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisMetadata {
        private Integer entitiesAnalyzed;
        private Integer recordsProcessed;
        private String timeRangeAnalyzed;
        private List<String> dataSourcesUsed;
        private List<String> modelsUsed;
        private Map<String, Object> processingStats;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String recommendationType;
        private String description;
        private String priority;
        private List<String> actionItems;
        private Map<String, Object> recommendationData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private Double overallRiskScore;
        private String riskLevel;
        private List<RiskFactor> riskFactors;
        private String assessmentBasis;
        private Map<String, Double> riskByCategory;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorName;
        private Double impact;
        private String description;
        private String category;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedAlert {
        private String alertId;
        private String alertType;
        private String severity;
        private String description;
        private String patternId;
        private LocalDateTime triggeredAt;
        private Map<String, Object> alertData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformance {
        private String modelId;
        private String modelName;
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Integer patternsDetected;
        private Double avgConfidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataQuality {
        private Double completeness;
        private Double accuracy;
        private Double consistency;
        private Integer totalRecords;
        private Integer validRecords;
        private Integer invalidRecords;
        private List<String> qualityIssues;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisError {
        private String errorType;
        private String errorMessage;
        private String component;
        private LocalDateTime timestamp;
        private Map<String, Object> errorDetails;
    }
    
    /**
     * Check if analysis completed successfully
     */
    public boolean isSuccessful() {
        return status == AnalysisStatus.COMPLETED;
    }
    
    /**
     * Check if critical patterns were found
     */
    public boolean hasCriticalPatterns() {
        return summary != null && 
               summary.getCriticalPatterns() != null && 
               summary.getCriticalPatterns() > 0;
    }
    
    /**
     * Get high confidence patterns
     */
    public List<DetectedPattern> getHighConfidencePatterns() {
        if (patterns == null) {
            return List.of();
        }
        
        return patterns.stream()
                .filter(p -> p.getConfidence() != null && p.getConfidence() >= 0.8)
                .toList();
    }
    
    /**
     * Get critical alerts
     */
    public List<GeneratedAlert> getCriticalAlerts() {
        if (alerts == null) {
            return List.of();
        }
        
        return alerts.stream()
                .filter(a -> "CRITICAL".equals(a.getSeverity()))
                .toList();
    }
    
    /**
     * Check if high risk level
     */
    public boolean isHighRisk() {
        return riskAssessment != null && 
               riskAssessment.getOverallRiskScore() != null &&
               riskAssessment.getOverallRiskScore() >= 0.7;
    }
    
    /**
     * Get analysis duration in seconds
     */
    public Double getAnalysisDurationSeconds() {
        return durationMs != null ? durationMs / 1000.0 : null;
    }
    
    /**
     * Get patterns by type
     */
    public List<DetectedPattern> getPatternsByType(PatternDetectionRequest.PatternType type) {
        if (patterns == null) {
            return List.of();
        }
        
        return patterns.stream()
                .filter(p -> p.getPatternType() == type)
                .toList();
    }
    
    /**
     * Get top recommendation
     */
    public Recommendation getTopRecommendation() {
        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }
        
        return recommendations.stream()
                .filter(r -> "HIGH".equals(r.getPriority()) || "CRITICAL".equals(r.getPriority()))
                .findFirst()
                .orElse(recommendations.get(0));
    }
}