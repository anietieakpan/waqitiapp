package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Location Pattern Analysis DTO
 * 
 * Contains detailed analysis of user location patterns for security assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPatternAnalysis {
    
    // Analysis metadata
    private String analysisId;
    private String userId;
    private LocalDateTime analysisDate;
    private String analysisType; // PERIODIC, TRIGGERED, ON_DEMAND
    private String timeWindow; // DAILY, WEEKLY, MONTHLY, YEARLY
    
    // Pattern summary
    private LocationPatternSummary patternSummary;
    
    // Geographic analysis
    private GeographicPatterns geographicPatterns;
    
    // Temporal patterns
    private TemporalPatterns temporalPatterns;
    
    // Mobility analysis
    private MobilityAnalysis mobilityAnalysis;
    
    // Behavioral patterns
    private BehavioralPatterns behavioralPatterns;
    
    // Anomaly detection
    private PatternAnomalies anomalies;
    
    // Risk indicators
    private List<LocationPatternRisk> riskIndicators;
    
    // Predictions and insights
    private LocationPredictions predictions;
    
    // Risk assessment
    private RiskAssessment riskAssessment;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPatternSummary {
        private String userType; // STATIONARY, COMMUTER, TRAVELER, NOMADIC
        private Integer totalLocations;
        private Integer uniqueCountries;
        private Integer uniqueCities;
        private String primaryLocation;
        private String secondaryLocation;
        private Double stabilityScore; // 0.0 to 1.0
        private String patternConfidence; // HIGH, MEDIUM, LOW
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicPatterns {
        private String homeCountry;
        private String homeCity;
        private String homeTimezone;
        private List<String> frequentCountries;
        private List<String> frequentCities;
        private Map<String, Integer> countryVisitCounts;
        private Map<String, Integer> cityVisitCounts;
        private Double geographicSpread; // Standard deviation of locations
        private String travelRadius; // LOCAL, REGIONAL, NATIONAL, INTERNATIONAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemporalPatterns {
        private Map<String, Integer> hourlyActivity; // Hour -> count
        private Map<String, Integer> dailyActivity; // Day of week -> count
        private Map<String, Integer> monthlyActivity; // Month -> count
        private String mostActiveHour;
        private String mostActiveDay;
        private String mostActiveMonth;
        private Boolean workHoursPattern;
        private Boolean weekendPattern;
        private Boolean nightActivity;
        private String seasonalPattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MobilityAnalysis {
        private Double mobilityScore; // 0.0 to 1.0 (0 = stationary, 1 = highly mobile)
        private String mobilityType; // STATIONARY, LOCAL, REGIONAL, GLOBAL
        private Double averageDailyDistance; // kilometers
        private Double maxDailyDistance; // kilometers
        private Integer travelDaysPerMonth;
        private String transportationMode; // WALKING, DRIVING, FLYING, MIXED
        private Boolean businessTraveler;
        private Boolean leisureTraveler;
        private Boolean commuter;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralPatterns {
        private String routineStrength; // STRONG, MODERATE, WEAK, NONE
        private List<String> establishedRoutes;
        private Map<String, String> locationPurpose; // Location -> Purpose
        private Boolean predictableBehavior;
        private Double routineConsistency; // 0.0 to 1.0
        private String workLifeBalance;
        private Boolean remoteWorker;
        private Boolean frequentFlyer;
        private String lifestylePattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternAnomalies {
        private List<LocationAnomaly> recentAnomalies;
        private Integer anomalyCount;
        private Double anomalyRate; // Anomalies per time period
        private String anomalyTrend; // INCREASING, STABLE, DECREASING
        private LocalDateTime lastAnomalyDate;
        private String mostCommonAnomalyType;
        private Double anomalySeverityScore; // 0.0 to 1.0
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationAnomaly {
        private String anomalyId;
        private String anomalyType; // VELOCITY, DISTANCE, TIME, FREQUENCY
        private String description;
        private LocalDateTime detectedAt;
        private LocationData location;
        private Double severity; // 0.0 to 1.0
        private String anomalyReason;
        private Boolean resolved;
        private String resolution;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPatternRisk {
        private String riskType;
        private String description;
        private Double riskScore; // 0.0 to 1.0
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private List<String> riskFactors;
        private String mitigation;
        private Boolean requiresAction;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPredictions {
        private List<String> likelyNextLocations;
        private Map<String, Double> locationProbabilities;
        private String predictedTravelPattern;
        private LocalDateTime nextPredictedTravel;
        private String riskPrediction;
        private Double predictionConfidence; // 0.0 to 1.0
        private String modelVersion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private Double overallRiskScore; // 0.0 to 1.0
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Double> riskComponents;
        private List<String> riskFactors;
        private String riskCategory;
        private LocalDateTime assessedAt;
        private String assessmentMethod;
    }
    
    /**
     * Check if any anomaly was detected
     */
    public boolean isAnomalyDetected() {
        if (anomalies != null) {
            return anomalies.getAnomalyCount() != null && anomalies.getAnomalyCount() > 0;
        }
        return false;
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
    
    /**
     * Alias for isAnomalyDetected()
     */
    public boolean isAnomalous() {
        return isAnomalyDetected();
    }
    
    /**
     * Get reason for the analysis result
     */
    public String getReason() {
        if (anomalies != null && anomalies.getRecentAnomalies() != null && !anomalies.getRecentAnomalies().isEmpty()) {
            LocationAnomaly recent = anomalies.getRecentAnomalies().get(0);
            return recent.getAnomalyReason() != null ? recent.getAnomalyReason() : recent.getDescription();
        }
        if (riskAssessment != null && riskAssessment.getRiskLevel() != null) {
            return "Risk level: " + riskAssessment.getRiskLevel();
        }
        return "Normal location pattern";
    }
    
    /**
     * Get detected patterns
     */
    public List<String> getPatterns() {
        List<String> patterns = new java.util.ArrayList<>();
        
        if (geographicPatterns != null) {
            if (geographicPatterns.getFrequentCountries() != null) {
                patterns.addAll(geographicPatterns.getFrequentCountries());
            }
            if (geographicPatterns.getTravelRadius() != null) {
                patterns.add("Travel pattern: " + geographicPatterns.getTravelRadius());
            }
        }
        
        if (temporalPatterns != null) {
            if (temporalPatterns.getMostActiveHour() != null) {
                patterns.add("Most active hour: " + temporalPatterns.getMostActiveHour());
            }
            if (temporalPatterns.getMostActiveDay() != null) {
                patterns.add("Most active day: " + temporalPatterns.getMostActiveDay());
            }
        }
        
        return patterns;
    }
}