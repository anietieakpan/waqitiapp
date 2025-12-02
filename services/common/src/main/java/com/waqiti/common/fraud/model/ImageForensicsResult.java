package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of image forensics analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageForensicsResult {
    
    private String analysisId;
    private String imageId;
    private String imageHash;
    private LocalDateTime analysisTimestamp;
    
    // Overall assessment
    private boolean alterationDetected;
    private double overallRiskScore;
    private String riskLevel;
    private double confidence;
    
    // JPEG compression analysis
    private boolean hasInconsistentJpegCompression;
    private double jpegCompressionScore;
    private List<String> compressionAnomalies;
    
    // Error Level Analysis (ELA)
    private double errorLevelScore;
    private String elaAnalysisDetails;
    private boolean elaAnomalyDetected;
    
    // Metadata analysis
    private boolean hasMetadataInconsistencies;
    private Map<String, String> metadataFlags;
    private String originalCreationTime;
    private String lastModificationTime;
    private List<String> editingSoftwareDetected;
    
    // Copy-paste detection
    private boolean hasCopyPasteIndicators;
    private List<CopyPasteRegion> copyPasteRegions;
    private double duplicateRegionScore;
    
    // Statistical analysis
    private double statisticalAnomalyScore;
    private Map<String, Double> statisticalMetrics;
    private boolean pixelPatternAnomalies;
    private boolean colorHistogramAnomalies;
    
    // Noise analysis
    private double noisePatternScore;
    private boolean inconsistentNoise;
    private String noiseAnalysisDetails;
    
    // Geometric analysis
    private boolean geometricInconsistencies;
    private double perspectiveScore;
    private double lightingConsistencyScore;
    private double shadowAnalysisScore;
    
    // Text and number analysis (for checks)
    private boolean textAlterationDetected;
    private List<String> suspiciousTextRegions;
    private double ocrConfidenceScore;
    private Map<String, String> extractedText;
    
    // Technical details
    private String imageFormat;
    private String imageResolution;
    private long fileSizeBytes;
    private int colorDepth;
    private String colorSpace;
    
    // Performance metrics
    private long processingTimeMs;
    private String analysisEngine;
    private String engineVersion;
    
    /**
     * Copy-paste region detected in image
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CopyPasteRegion {
        private int x;
        private int y;
        private int width;
        private int height;
        private double confidence;
        private String description;
        private String matchingRegionLocation;
    }
    
    /**
     * Risk levels for image forensics
     */
    public static class RiskLevel {
        public static final String MINIMAL = "MINIMAL";
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";
        public static final String CRITICAL = "CRITICAL";
    }
    
    /**
     * Check if any alteration indicators are present
     */
    public boolean hasAnyAlterationIndicators() {
        return hasInconsistentJpegCompression ||
               (errorLevelScore > 0.5) ||
               hasMetadataInconsistencies ||
               hasCopyPasteIndicators ||
               (statisticalAnomalyScore > 0.6) ||
               inconsistentNoise ||
               geometricInconsistencies ||
               textAlterationDetected;
    }
    
    /**
     * Get the highest risk indicator score
     */
    public double getHighestRiskScore() {
        return Math.max(Math.max(errorLevelScore, statisticalAnomalyScore),
                       Math.max(jpegCompressionScore, noisePatternScore));
    }
    
    /**
     * Determine risk level based on scores
     */
    public String determineRiskLevel() {
        double maxScore = getHighestRiskScore();
        
        if (maxScore >= 0.9) return RiskLevel.CRITICAL;
        if (maxScore >= 0.7) return RiskLevel.HIGH;
        if (maxScore >= 0.5) return RiskLevel.MEDIUM;
        if (maxScore >= 0.3) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    /**
     * Get summary of all detected anomalies
     */
    public List<String> getAnomalySummary() {
        List<String> anomalies = new java.util.ArrayList<>();
        
        if (hasInconsistentJpegCompression) {
            anomalies.add("Inconsistent JPEG compression detected");
        }
        if (errorLevelScore > 0.7) {
            anomalies.add("High error level analysis score: " + String.format("%.2f", errorLevelScore));
        }
        if (hasMetadataInconsistencies) {
            anomalies.add("Image metadata inconsistencies found");
        }
        if (hasCopyPasteIndicators) {
            anomalies.add("Copy-paste manipulation indicators detected");
        }
        if (statisticalAnomalyScore > 0.8) {
            anomalies.add("Statistical anomalies in image data");
        }
        if (inconsistentNoise) {
            anomalies.add("Inconsistent noise patterns detected");
        }
        if (geometricInconsistencies) {
            anomalies.add("Geometric inconsistencies found");
        }
        if (textAlterationDetected) {
            anomalies.add("Text alteration detected");
        }
        
        return anomalies;
    }
}