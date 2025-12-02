package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Historical location data for a user
 * Used for travel pattern analysis and anomaly detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationHistory {

    private String userId;
    private Instant windowStart;
    private Instant windowEnd;

    // Recent locations
    private List<LocationPoint> recentLocations;
    private Integer totalLocations;

    // Geographic spread
    private List<String> countriesVisited;
    private List<String> citiesVisited;
    private Integer uniqueCountries;
    private Integer uniqueCities;

    // Home location (most frequent)
    private String homeCountry;
    private String homeCity;
    private GeoLocationInfo homeLocation;

    // Current location context
    private GeoLocationInfo currentLocation;
    private Boolean awayFromHome;
    private Double distanceFromHome; // kilometers

    // Travel patterns
    private Boolean frequentTraveler;
    private Double averageTravelDistance; // kilometers
    private Integer crossBorderTransactions;
    private List<String> suspiciousLocations;

    // Velocity analysis
    private Double maxTravelSpeed; // km/h
    private Boolean impossibleTravelDetected;
    private List<String> impossibleTravelPairs; // location1 -> location2

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPoint {
        private Double latitude;
        private Double longitude;
        private String country;
        private String city;
        private Instant timestamp;
        private String source; // GPS, IP, etc.
    }
}
