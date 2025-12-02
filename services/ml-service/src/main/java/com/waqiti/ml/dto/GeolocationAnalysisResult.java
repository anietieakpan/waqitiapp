package com.waqiti.ml.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Production-ready DTO for geolocation analysis results.
 * Contains comprehensive location risk assessment and fraud indicators.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeolocationAnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "Transaction ID is required")
    @JsonProperty("transaction_id")
    private String transactionId;

    @NotNull(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Min(value = 0, message = "Risk score must be between 0 and 100")
    @Max(value = 100, message = "Risk score must be between 0 and 100")
    @JsonProperty("risk_score")
    private Double riskScore;

    /**
     * Overall geolocation risk score (0.0 - 1.0 scale for fraud detection engine)
     */
    @Min(value = 0, message = "Overall geo score must be between 0 and 1")
    @Max(value = 1, message = "Overall geo score must be between 0 and 1")
    @JsonProperty("overall_geo_score")
    @Builder.Default
    private Double overallGeoScore = 0.0;

    @Pattern(regexp = "^(MINIMAL|LOW|MEDIUM|HIGH|CRITICAL)$",
            message = "Risk level must be one of: MINIMAL, LOW, MEDIUM, HIGH, CRITICAL")
    @JsonProperty("risk_level")
    private String riskLevel;

    /**
     * Get overall geo score - converts from 0-100 riskScore if needed
     */
    public Double getOverallGeoScore() {
        if (overallGeoScore != null && overallGeoScore > 0.0) {
            return overallGeoScore;
        }
        // Fallback: normalize riskScore (0-100) to 0.0-1.0 scale
        return riskScore != null ? riskScore / 100.0 : 0.0;
    }

    @JsonProperty("location_risk_score")
    @Min(0)
    @Max(100)
    private Double locationRiskScore;

    @JsonProperty("velocity_risk_score")
    @Min(0)
    @Max(100)
    private Double velocityRiskScore;

    @JsonProperty("geographic_anomaly_score")
    @Min(0)
    @Max(100)
    private Double geographicAnomalyScore;

    @JsonProperty("threat_intel_score")
    @Min(0)
    @Max(100)
    private Double threatIntelScore;

    @JsonProperty("country_risk_level")
    private String countryRiskLevel;

    @JsonProperty("mock_location_detected")
    @Builder.Default
    private Boolean mockLocationDetected = false;

    @JsonProperty("velocity_impossible")
    @Builder.Default
    private Boolean velocityImpossible = false;

    @JsonProperty("distance_from_last_location")
    @PositiveOrZero
    private Double distanceFromLastLocation;

    @JsonProperty("estimated_travel_speed")
    @PositiveOrZero
    private Double estimatedTravelSpeed;

    @JsonProperty("no_location_data")
    @Builder.Default
    private Boolean noLocationData = false;

    @JsonProperty("processing_time_ms")
    @PositiveOrZero
    private Long processingTimeMs;

    @JsonProperty("location_details")
    @Builder.Default
    private LocationDetails locationDetails = new LocationDetails();

    @JsonProperty("risk_indicators")
    @Builder.Default
    private RiskIndicators riskIndicators = new RiskIndicators();

    @JsonProperty("recommendations")
    @Builder.Default
    private Recommendations recommendations = new Recommendations();

    @JsonProperty("metadata")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Nested class for location details
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationDetails {
        
        @JsonProperty("country")
        private String country;
        
        @JsonProperty("country_name")
        private String countryName;
        
        @JsonProperty("region")
        private String region;
        
        @JsonProperty("city")
        private String city;
        
        @JsonProperty("postal_code")
        private String postalCode;
        
        @JsonProperty("latitude")
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        private Double latitude;
        
        @JsonProperty("longitude")
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        private Double longitude;
        
        @JsonProperty("accuracy_meters")
        @PositiveOrZero
        private Double accuracyMeters;
        
        @JsonProperty("time_zone")
        private String timeZone;
        
        @JsonProperty("is_usual_location")
        @Builder.Default
        private Boolean isUsualLocation = false;
        
        @JsonProperty("location_frequency")
        @PositiveOrZero
        private Integer locationFrequency;
        
        @JsonProperty("first_seen_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime firstSeenAt;
        
        @JsonProperty("last_seen_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastSeenAt;
        
        @JsonProperty("cluster_id")
        private String clusterId;
        
        @JsonProperty("cluster_distance")
        @PositiveOrZero
        private Double clusterDistance;
    }

    /**
     * Nested class for risk indicators
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskIndicators {
        
        @JsonProperty("high_risk_country")
        @Builder.Default
        private Boolean highRiskCountry = false;
        
        @JsonProperty("sanctioned_country")
        @Builder.Default
        private Boolean sanctionedCountry = false;
        
        @JsonProperty("fraud_hotspot")
        @Builder.Default
        private Boolean fraudHotspot = false;
        
        @JsonProperty("impossible_travel")
        @Builder.Default
        private Boolean impossibleTravel = false;
        
        @JsonProperty("location_spoofing")
        @Builder.Default
        private Boolean locationSpoofing = false;
        
        @JsonProperty("ip_geo_mismatch")
        @Builder.Default
        private Boolean ipGeoMismatch = false;
        
        @JsonProperty("time_zone_mismatch")
        @Builder.Default
        private Boolean timeZoneMismatch = false;
        
        @JsonProperty("unusual_location")
        @Builder.Default
        private Boolean unusualLocation = false;
        
        @JsonProperty("rapid_location_changes")
        @Builder.Default
        private Boolean rapidLocationChanges = false;
        
        @JsonProperty("concurrent_locations")
        @Builder.Default
        private Boolean concurrentLocations = false;
        
        @JsonProperty("vpn_detected")
        @Builder.Default
        private Boolean vpnDetected = false;
        
        @JsonProperty("tor_detected")
        @Builder.Default
        private Boolean torDetected = false;
        
        @JsonProperty("proxy_detected")
        @Builder.Default
        private Boolean proxyDetected = false;
        
        @JsonProperty("datacenter_ip")
        @Builder.Default
        private Boolean datacenterIp = false;
        
        @JsonProperty("residential_proxy")
        @Builder.Default
        private Boolean residentialProxy = false;
    }

    /**
     * Nested class for recommendations
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Recommendations {
        
        @JsonProperty("require_additional_authentication")
        @Builder.Default
        private Boolean requireAdditionalAuthentication = false;
        
        @JsonProperty("require_manual_review")
        @Builder.Default
        private Boolean requireManualReview = false;
        
        @JsonProperty("block_transaction")
        @Builder.Default
        private Boolean blockTransaction = false;
        
        @JsonProperty("flag_for_monitoring")
        @Builder.Default
        private Boolean flagForMonitoring = false;
        
        @JsonProperty("request_location_verification")
        @Builder.Default
        private Boolean requestLocationVerification = false;
        
        @JsonProperty("apply_transaction_limit")
        @Builder.Default
        private Boolean applyTransactionLimit = false;
        
        @JsonProperty("notify_user")
        @Builder.Default
        private Boolean notifyUser = false;
        
        @JsonProperty("notify_security_team")
        @Builder.Default
        private Boolean notifySecurityTeam = false;
        
        @JsonProperty("recommended_action")
        private String recommendedAction;
        
        @JsonProperty("recommended_action_reason")
        private String recommendedActionReason;
        
        @JsonProperty("risk_mitigation_steps")
        private String[] riskMitigationSteps;
        
        @JsonProperty("confidence_level")
        @Min(0)
        @Max(100)
        private Double confidenceLevel;
    }

    /**
     * Check if location is high risk
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 60.0;
    }

    /**
     * Check if location is critical risk
     */
    public boolean isCriticalRisk() {
        return riskScore != null && riskScore >= 80.0;
    }

    /**
     * Check if any velocity violations detected
     */
    public boolean hasVelocityViolations() {
        return Boolean.TRUE.equals(velocityImpossible) || 
               (estimatedTravelSpeed != null && estimatedTravelSpeed > 900);
    }

    /**
     * Check if location is anomalous
     */
    public boolean isAnomalous() {
        return (geographicAnomalyScore != null && geographicAnomalyScore >= 40.0) ||
               Boolean.TRUE.equals(riskIndicators.getUnusualLocation());
    }

    /**
     * Check if fraud indicators present
     */
    public boolean hasFraudIndicators() {
        return Boolean.TRUE.equals(mockLocationDetected) ||
               Boolean.TRUE.equals(riskIndicators.getFraudHotspot()) ||
               Boolean.TRUE.equals(riskIndicators.getLocationSpoofing()) ||
               Boolean.TRUE.equals(velocityImpossible);
    }

    /**
     * Check if requires manual review
     */
    public boolean requiresManualReview() {
        return Boolean.TRUE.equals(recommendations.getRequireManualReview()) ||
               isCriticalRisk() ||
               hasFraudIndicators();
    }

    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlockTransaction() {
        return Boolean.TRUE.equals(recommendations.getBlockTransaction()) ||
               (riskScore != null && riskScore >= 90.0);
    }

    /**
     * Get overall risk assessment
     */
    public String getOverallAssessment() {
        if (shouldBlockTransaction()) {
            return "BLOCK";
        } else if (requiresManualReview()) {
            return "REVIEW";
        } else if (isHighRisk()) {
            return "MONITOR";
        } else if (riskScore != null && riskScore >= 40.0) {
            return "CAUTION";
        }
        return "APPROVE";
    }

    /**
     * Get risk summary for logging
     */
    public String getRiskSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Location Risk Analysis - ");
        summary.append("Score: ").append(riskScore != null ? String.format("%.1f", riskScore) : "N/A");
        summary.append(", Level: ").append(riskLevel != null ? riskLevel : "UNKNOWN");
        
        if (Boolean.TRUE.equals(velocityImpossible)) {
            summary.append(" [IMPOSSIBLE_TRAVEL]");
        }
        if (Boolean.TRUE.equals(mockLocationDetected)) {
            summary.append(" [MOCK_LOCATION]");
        }
        if (Boolean.TRUE.equals(riskIndicators.getFraudHotspot())) {
            summary.append(" [FRAUD_HOTSPOT]");
        }
        
        summary.append(" - Action: ").append(getOverallAssessment());
        
        return summary.toString();
    }

    /**
     * Add metadata entry
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Get metadata value
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        return (T) metadata.get(key);
    }

    /**
     * Check if location data is complete
     */
    public boolean hasCompleteLocationData() {
        return locationDetails != null &&
               locationDetails.getLatitude() != null &&
               locationDetails.getLongitude() != null &&
               locationDetails.getCountry() != null &&
               locationDetails.getCity() != null;
    }

    /**
     * Check if location is verified
     */
    public boolean isLocationVerified() {
        return !Boolean.TRUE.equals(noLocationData) &&
               !Boolean.TRUE.equals(mockLocationDetected) &&
               locationDetails != null &&
               locationDetails.getAccuracyMeters() != null &&
               locationDetails.getAccuracyMeters() <= 100;
    }

    /**
     * Get location description
     */
    public String getLocationDescription() {
        if (locationDetails == null) {
            return "Unknown Location";
        }
        
        StringBuilder desc = new StringBuilder();
        
        if (locationDetails.getCity() != null) {
            desc.append(locationDetails.getCity());
        }
        
        if (locationDetails.getRegion() != null) {
            if (desc.length() > 0) desc.append(", ");
            desc.append(locationDetails.getRegion());
        }
        
        if (locationDetails.getCountryName() != null) {
            if (desc.length() > 0) desc.append(", ");
            desc.append(locationDetails.getCountryName());
        } else if (locationDetails.getCountry() != null) {
            if (desc.length() > 0) desc.append(", ");
            desc.append(locationDetails.getCountry());
        }
        
        return desc.length() > 0 ? desc.toString() : "Unknown Location";
    }

    /**
     * Calculate confidence score for the analysis
     */
    public double calculateConfidenceScore() {
        double confidence = 1.0;
        
        // Reduce confidence for missing data
        if (Boolean.TRUE.equals(noLocationData)) {
            return 0.1;
        }
        
        // Reduce confidence for poor accuracy
        if (locationDetails != null && locationDetails.getAccuracyMeters() != null) {
            if (locationDetails.getAccuracyMeters() > 1000) {
                confidence -= 0.3;
            } else if (locationDetails.getAccuracyMeters() > 500) {
                confidence -= 0.2;
            } else if (locationDetails.getAccuracyMeters() > 100) {
                confidence -= 0.1;
            }
        }
        
        // Reduce confidence for mock locations
        if (Boolean.TRUE.equals(mockLocationDetected)) {
            confidence -= 0.4;
        }
        
        // Reduce confidence for missing threat intel
        if (threatIntelScore == null || threatIntelScore == 0.0) {
            confidence -= 0.1;
        }
        
        return Math.max(confidence, 0.1);
    }
}