package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Geographic location information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocation {
    
    private String country;
    private String countryCode;
    private String region;
    private String city;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String isp;
    private String organization;
    private String asNumber;
    private String ipAddress;
    private boolean vpnDetected;
    private boolean proxyDetected;
    private boolean torDetected;
    private String riskLevel;
    private Double riskScore;
    
    /**
     * Check if location is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskLevel) || 
               (riskScore != null && riskScore > 0.7) ||
               vpnDetected || proxyDetected || torDetected;
    }
    
    /**
     * Get location display string
     */
    public String getDisplayLocation() {
        StringBuilder location = new StringBuilder();
        if (city != null) location.append(city).append(", ");
        if (region != null) location.append(region).append(", ");
        if (country != null) location.append(country);
        return location.toString().replaceAll(", $", "");
    }
    
    /**
     * Calculate distance to another location in kilometers
     */
    public double distanceTo(GeoLocation other) {
        if (latitude == null || longitude == null || 
            other.latitude == null || other.longitude == null) {
            return -1;
        }
        
        double lat1Rad = Math.toRadians(latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - latitude);
        double deltaLon = Math.toRadians(other.longitude - longitude);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return 6371 * c; // Earth radius in kilometers
    }
}