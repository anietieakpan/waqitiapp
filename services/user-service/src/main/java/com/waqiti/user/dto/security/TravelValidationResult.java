package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Travel Validation Result DTO
 * 
 * Contains the result of travel pattern validation for security analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelValidationResult {
    
    // Validation overview
    private String validationId;
    private LocalDateTime validationTime;
    private String validationType; // VELOCITY, PATTERN, FEASIBILITY
    private Boolean valid;
    private String validationStatus; // VALID, SUSPICIOUS, IMPOSSIBLE, BLOCKED
    
    // Travel details
    private TravelDetails travelDetails;
    
    // Velocity analysis
    private VelocityAnalysis velocityAnalysis;
    
    // Feasibility check
    private TravelFeasibility feasibility;
    
    // Pattern analysis
    private TravelPatternAnalysis patternAnalysis;
    
    // Risk assessment
    private TravelRiskAssessment riskAssessment;
    
    // Historical context
    private TravelHistoryContext historyContext;
    
    // Recommendations
    private List<String> recommendations;
    private List<String> securityActions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelDetails {
        private LocationData fromLocation;
        private LocationData toLocation;
        private Double distance; // kilometers
        private Long timeDifference; // milliseconds
        private String travelType; // DOMESTIC, INTERNATIONAL, CROSS_CONTINENT
        private List<String> countryBorders;
        private List<String> timeZones;
        private String estimatedTransportMode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityAnalysis {
        private Double calculatedSpeed; // km/h
        private Double maximumHumanSpeed; // km/h
        private Double maximumCommercialSpeed; // km/h
        private Boolean velocityPossible;
        private Boolean velocityReasonable;
        private String velocityCategory; // WALKING, DRIVING, FLYING, IMPOSSIBLE
        private Double velocityRiskScore; // 0.0 to 1.0
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelFeasibility {
        private Boolean physicallyPossible;
        private Boolean commerciallyViable;
        private Boolean timezonePlausible;
        private Boolean bordersCrossable;
        private Boolean transportationAvailable;
        private List<String> feasibilityIssues;
        private String feasibilityScore; // HIGH, MEDIUM, LOW, IMPOSSIBLE
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelPatternAnalysis {
        private String userTravelProfile; // STATIONARY, OCCASIONAL, FREQUENT, NOMADIC
        private Boolean matchesHistoricalPattern;
        private String travelFrequency;
        private List<String> commonDestinations;
        private Boolean businessTraveler;
        private Boolean leisureTraveler;
        private String travelSeason;
        private Double patternDeviationScore; // 0.0 to 1.0
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelRiskAssessment {
        private Double overallRiskScore; // 0.0 to 1.0
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private List<String> riskFactors;
        private Boolean highRiskDestination;
        private Boolean sanctionedCountry;
        private Boolean conflictZone;
        private Boolean moneyLaunderingRisk;
        private Boolean terrorismRisk;
        private String geopoliticalRisk;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelHistoryContext {
        private Integer totalTrips;
        private Integer internationalTrips;
        private Integer domesticTrips;
        private LocalDateTime lastTravel;
        private String frequentDestination;
        private List<String> recentDestinations;
        private Double averageTripDistance;
        private String travelTrend; // INCREASING, STABLE, DECREASING
    }
    
    /**
     * Create a result indicating travel is possible
     */
    public static TravelValidationResult possible(String reason) {
        return TravelValidationResult.builder()
            .valid(true)
            .validationStatus("VALID")
            .validationTime(LocalDateTime.now())
            .recommendations(java.util.List.of(reason))
            .build();
    }
    
    /**
     * Create a result indicating travel is impossible
     */
    public static TravelValidationResult impossible(String reason, double distance, long timeDifference, double speed) {
        TravelDetails details = TravelDetails.builder()
            .distance(distance)
            .timeDifference(timeDifference)
            .build();
            
        VelocityAnalysis velocity = VelocityAnalysis.builder()
            .calculatedSpeed(speed)
            .velocityPossible(false)
            .velocityCategory("IMPOSSIBLE")
            .velocityRiskScore(1.0)
            .build();
            
        return TravelValidationResult.builder()
            .valid(false)
            .validationStatus("IMPOSSIBLE")
            .validationTime(LocalDateTime.now())
            .travelDetails(details)
            .velocityAnalysis(velocity)
            .recommendations(java.util.List.of(reason))
            .build();
    }
    
    /**
     * Check if travel is possible
     */
    public boolean isPossible() {
        return Boolean.TRUE.equals(valid) && !"IMPOSSIBLE".equals(validationStatus);
    }
    
    /**
     * Check if travel is suspicious
     */
    public boolean isSuspicious() {
        return "SUSPICIOUS".equals(validationStatus) || 
               (riskAssessment != null && "HIGH".equals(riskAssessment.getRiskLevel()));
    }
}