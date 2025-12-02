package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Location Analysis Result DTO
 * 
 * Contains comprehensive analysis results for location-based security checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAnalysisResult {
    
    // Analysis overview
    private String analysisId;
    private LocalDateTime analysisTime;
    private String analysisType; // REAL_TIME, BATCH, HISTORICAL
    private Long processingTimeMs;
    
    // Basic validation fields
    private Boolean anomalyDetected;
    private String reason;
    private String decisionReason;
    private Boolean valid;
    private Boolean passed;
    private Double riskScore;
    private String riskLevel;
    
    // Additional analysis fields
    private LocationData currentLocation;
    private List<UserLocationEntry> locationHistory; // User location history
    private List<String> patterns;
    private String velocityAnalysis;
    private String riskAnalysis;
    private Boolean patternMatch;
    private Double travelTime;
    
    // Location intelligence
    private LocationIntelligence locationIntelligence;
    
    // Risk assessment
    private LocationRiskAssessment riskAssessment;
    
    // Pattern analysis
    private LocationPatternAnalysis patternAnalysis;
    
    // Behavioral analysis
    private LocationBehaviorAnalysis behaviorAnalysis;
    
    // Compliance analysis
    private LocationComplianceAnalysis complianceAnalysis;
    
    // Fraud indicators
    private List<LocationFraudIndicator> fraudIndicators;
    
    // Machine learning insights
    private MLLocationInsights mlInsights;
    
    // Recommendations
    private LocationRecommendations recommendations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationIntelligence {
        private String accuracy; // HIGH, MEDIUM, LOW
        private String source; // IP, GPS, CELL_TOWER, WIFI
        private Double confidence; // 0.0 to 1.0
        private String locationType; // RESIDENTIAL, COMMERCIAL, MOBILE, UNKNOWN
        private String economicStatus; // HIGH, MEDIUM, LOW
        private String crimeRate; // HIGH, MEDIUM, LOW
        private List<String> nearbyLandmarks;
        private String populationDensity;
        private Map<String, Object> demographicData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationRiskAssessment {
        private Double overallRiskScore; // 0.0 to 1.0
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private List<String> riskFactors;
        private Map<String, Double> riskComponentScores;
        private Boolean highRiskJurisdiction;
        private Boolean sanctionedTerritory;
        private Boolean taxHaven;
        private String regulatoryRisk;
        private String geopoliticalRisk;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPatternAnalysis {
        private String userPattern; // STATIONARY, MOBILE, TRAVELER, NOMADIC
        private List<String> frequentLocations;
        private String homeLocation;
        private String workLocation;
        private Map<String, Integer> locationFrequency;
        private Double mobilityScore; // 0.0 to 1.0
        private String travelFrequency; // NEVER, RARE, OCCASIONAL, FREQUENT
        private List<String> travelDestinations;
        private Boolean crossBorderActivity;
        
        public boolean isAnomalous() {
            // Consider it anomalous if mobility score is very high or cross-border activity detected
            return (mobilityScore != null && mobilityScore > 0.8) || 
                   Boolean.TRUE.equals(crossBorderActivity);
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationBehaviorAnalysis {
        private String typicalBehavior;
        private List<String> anomalies;
        private Double behaviorDeviationScore; // 0.0 to 1.0
        private Boolean unusualLocationAccess;
        private Boolean simultaneousLocations;
        private Boolean rapidLocationChanges;
        private String activityPattern;
        private Map<String, Object> behaviorMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationComplianceAnalysis {
        private Boolean compliant;
        private List<String> complianceIssues;
        private String jurisdictionalRequirements;
        private Boolean dataResidencyCompliant;
        private Boolean crossBorderRestrictions;
        private List<String> regulatoryFlags;
        private String complianceRisk;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationFraudIndicator {
        private String indicatorType;
        private String description;
        private Double severity; // 0.0 to 1.0
        private String fraudType;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
        private String mitigation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLLocationInsights {
        private Map<String, Double> modelScores;
        private String clusterAssignment;
        private Double anomalyScore;
        private List<String> predictedBehaviors;
        private String riskPrediction;
        private Double confidenceInterval;
        private String modelVersion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationRecommendations {
        private List<String> securityActions;
        private List<String> monitoringActions;
        private List<String> complianceActions;
        private String nextReviewDate;
        private Boolean escalateToAnalyst;
        private String recommendedAuthLevel;
    }
    
    /**
     * Check if any anomaly was detected
     */
    public boolean isAnomalyDetected() {
        if (patternAnalysis != null) {
            return patternAnalysis.isAnomalous();
        }
        return anomalyDetected != null ? anomalyDetected : false;
    }
    
    /**
     * Get overall risk score
     */
    public double getRiskScore() {
        if (riskAssessment != null && riskAssessment.getOverallRiskScore() != null) {
            return riskAssessment.getOverallRiskScore();
        }
        return 0.0;
    }
}