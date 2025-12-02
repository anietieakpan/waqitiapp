package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Location Validation Result DTO
 * 
 * Contains the result of location-based security validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationValidationResult {
    
    // Validation result
    private Boolean valid;
    private Boolean passed; // Alias for valid
    private String validationStatus; // VALID, SUSPICIOUS, BLOCKED, UNKNOWN
    private String reason; // Validation reason/message
    private String decisionReason; // Alias for reason
    private Double confidenceScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Double riskScore; // 0.0 to 1.0 numeric risk score
    
    // Location information
    private LocationData currentLocation;
    private LocationData expectedLocation;
    private LocationData lastKnownLocation;
    private LocationData previousLocation; // Previous known location
    
    // Additional validation fields
    private Boolean suspicious; // Is location suspicious
    private Double distance; // Distance from previous location in km
    private Boolean anomalyDetected; // Location anomaly detected
    private Boolean restrictedCountry; // Location is in restricted country
    
    // Validation checks
    private List<LocationCheck> validationChecks;
    private Map<String, Boolean> checkResults;
    
    // Risk assessment
    private Double locationRiskScore;
    private List<String> riskFactors;
    private List<String> suspiciousIndicators;
    
    // Geographic analysis
    private GeographicAnalysis geographicAnalysis;
    
    // Travel validation
    private TravelValidation travelValidation;
    
    // Network analysis
    private NetworkAnalysis networkAnalysis;
    
    // Velocity checks
    private VelocityCheck velocityCheck;
    
    // Historical context
    private HistoricalLocationContext historicalContext;
    
    // Recommendations
    private List<String> securityActions;
    private List<String> userActions;
    private Boolean requiresAdditionalVerification;
    
    // Validation metadata
    private String validationId;
    private LocalDateTime validatedAt;
    private Long processingTimeMs;
    private String validationMethod;
    
    // Additional analysis results
    private String velocityAnalysis;
    private String riskAnalysis;
    private Boolean patternMatch;
    private Double travelTime;
    private String ipAddress;
    private String countryCode;
    private String cityName;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationCheck {
        private String checkType;
        private Boolean passed;
        private String result;
        private String description;
        private Double confidence;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicAnalysis {
        private Double distanceFromExpected; // kilometers
        private Double distanceFromLastKnown; // kilometers
        private String countryMatch;
        private String regionMatch;
        private String cityMatch;
        private String timezoneMatch;
        private Boolean withinExpectedRadius;
        private String geographicRisk;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelValidation {
        private Boolean travelDetected;
        private String travelType; // DOMESTIC, INTERNATIONAL, IMPOSSIBLE
        private Double travelDistance; // kilometers
        private Double travelTime; // hours
        private Double travelSpeed; // km/h
        private Boolean physicallyPossible;
        private Boolean reasonableTravel;
        private String travelPattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAnalysis {
        private String ipType; // RESIDENTIAL, BUSINESS, MOBILE, HOSTING, VPN
        private Boolean vpnDetected;
        private String vpnProvider;
        private Boolean proxyDetected;
        private String proxyType;
        private Boolean torDetected;
        private Boolean datacenterIp;
        private String isp;
        private String organization;
        private String asn;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityCheck {
        private Boolean velocityViolation;
        private Double calculatedSpeed; // km/h
        private Double maximumPossibleSpeed; // km/h
        private String transportationMode;
        private Boolean impossibleVelocity;
        private String velocityRisk;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalLocationContext {
        private Integer totalLocations;
        private List<String> recentLocations;
        private List<String> frequentLocations;
        private String homeLocation;
        private String workLocation;
        private Boolean newLocation;
        private LocalDateTime firstSeenAt;
        private Integer visitCount;
        private String locationPattern;
    }
    
    /**
     * Check if location validation is valid
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(valid);
    }
    
    /**
     * Check if anomaly is detected
     */
    public boolean isAnomalyDetected() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) || 
               (riskScore != null && riskScore > 0.7);
    }
    
    /**
     * Get risk score (0.0 to 1.0)
     */
    public Double getRiskScore() {
        if (riskScore != null) {
            return riskScore;
        }
        
        // Calculate risk score from risk level
        if ("CRITICAL".equals(riskLevel)) return 1.0;
        if ("HIGH".equals(riskLevel)) return 0.8;
        if ("MEDIUM".equals(riskLevel)) return 0.5;
        if ("LOW".equals(riskLevel)) return 0.2;
        
        return 0.0;
    }
    
    /**
     * Get reason (with fallback to validationStatus)
     */
    public String getReason() {
        if (reason != null) return reason;
        if (decisionReason != null) return decisionReason;
        if (validationStatus != null) return validationStatus;
        return "Unknown";
    }
}