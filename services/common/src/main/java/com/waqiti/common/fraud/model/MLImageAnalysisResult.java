package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of ML-based image analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLImageAnalysisResult {
    
    private String analysisId;
    private String modelId;
    private String modelVersion;
    private LocalDateTime analysisTimestamp;
    
    // Alteration detection
    private double alterationProbability;
    private double alterationConfidence;
    private boolean alterationDetected;
    private String alterationType;
    
    // Classification results
    private String imageClassification;
    private double classificationConfidence;
    private List<String> detectedObjects;
    private Map<String, Double> objectConfidences;
    
    // Authenticity assessment
    private double authenticityScore;
    private boolean likelyAuthentic;
    private List<String> authenticityIndicators;
    
    // Tampering detection
    private double tamperingProbability;
    private List<TamperingRegion> tamperingRegions;
    private String tamperingMethod;
    
    // Document-specific analysis (for checks/IDs)
    private boolean validDocumentFormat;
    private double documentQualityScore;
    private List<String> documentAnomalies;
    private Map<String, String> extractedFields;
    
    // Feature analysis
    private Map<String, Double> featureScores;
    private List<String> suspiciousFeatures;
    private double overallFeatureScore;
    
    // Model metadata
    private Map<String, Object> modelMetadata;
    private long inferenceTimeMs;
    private String processingMode;
    
    // Risk assessment
    private String riskLevel;
    private double riskScore;
    private List<String> riskFactors;
    
    /**
     * Tampering region detected by ML model
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TamperingRegion {
        private int x;
        private int y;
        private int width;
        private int height;
        private double probability;
        private String tamperingType;
        private String description;
    }
    
    /**
     * Risk levels for ML analysis
     */
    public static class RiskLevel {
        public static final String MINIMAL = "MINIMAL";
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";
        public static final String CRITICAL = "CRITICAL";
    }
    
    /**
     * Alteration types
     */
    public static class AlterationType {
        public static final String COPY_PASTE = "COPY_PASTE";
        public static final String SPLICING = "SPLICING";
        public static final String RETOUCHING = "RETOUCHING";
        public static final String DEEPFAKE = "DEEPFAKE";
        public static final String TEXT_MODIFICATION = "TEXT_MODIFICATION";
        public static final String BACKGROUND_CHANGE = "BACKGROUND_CHANGE";
        public static final String UNKNOWN = "UNKNOWN";
    }
    
    /**
     * Check if alteration is detected with high confidence
     */
    public boolean isHighConfidenceAlteration() {
        return alterationDetected && alterationConfidence >= 0.8;
    }
    
    /**
     * Get the highest tampering probability from all regions
     */
    public double getMaxTamperingProbability() {
        if (tamperingRegions == null || tamperingRegions.isEmpty()) {
            return 0.0;
        }
        
        return tamperingRegions.stream()
                .mapToDouble(TamperingRegion::getProbability)
                .max()
                .orElse(0.0);
    }
    
    /**
     * Determine overall risk level
     */
    public String determineRiskLevel() {
        double maxRisk = Math.max(alterationProbability, tamperingProbability);
        maxRisk = Math.max(maxRisk, 1.0 - authenticityScore);
        
        if (maxRisk >= 0.9) return RiskLevel.CRITICAL;
        if (maxRisk >= 0.75) return RiskLevel.HIGH;
        if (maxRisk >= 0.5) return RiskLevel.MEDIUM;
        if (maxRisk >= 0.25) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    /**
     * Get all risk indicators
     */
    public List<String> getAllRiskIndicators() {
        List<String> indicators = new java.util.ArrayList<>();
        
        if (alterationDetected) {
            indicators.add("ML model detected image alteration (confidence: " + 
                          String.format("%.2f", alterationConfidence) + ")");
        }
        
        if (tamperingProbability > 0.5) {
            indicators.add("High tampering probability: " + 
                          String.format("%.2f", tamperingProbability));
        }
        
        if (authenticityScore < 0.5) {
            indicators.add("Low authenticity score: " + 
                          String.format("%.2f", authenticityScore));
        }
        
        if (!validDocumentFormat) {
            indicators.add("Invalid document format detected");
        }
        
        if (documentQualityScore < 0.6) {
            indicators.add("Poor document quality score: " + 
                          String.format("%.2f", documentQualityScore));
        }
        
        if (suspiciousFeatures != null && !suspiciousFeatures.isEmpty()) {
            indicators.add("Suspicious features detected: " + 
                          String.join(", ", suspiciousFeatures));
        }
        
        return indicators;
    }
    
    /**
     * Check if result indicates fraud
     */
    public boolean indicatesFraud() {
        return alterationDetected ||
               tamperingProbability > 0.7 ||
               authenticityScore < 0.3 ||
               !validDocumentFormat ||
               (documentAnomalies != null && documentAnomalies.size() > 2);
    }
}