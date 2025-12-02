package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Location model for fraud detection and geolocation analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("accuracy")
    private Double accuracy;

    @JsonProperty("country")
    private String country;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("region")
    private String region;

    @JsonProperty("region_code")
    private String regionCode;

    @JsonProperty("city")
    private String city;

    @JsonProperty("postal_code")
    private String postalCode;

    @JsonProperty("timezone")
    private String timezone;

    @JsonProperty("street_address")
    private String streetAddress;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("isp")
    private String isp;

    @JsonProperty("asn")
    private String asn;

    @JsonProperty("organization")
    private String organization;

    @JsonProperty("connection_type")
    private String connectionType;

    @JsonProperty("is_vpn")
    private Boolean isVpn;

    @JsonProperty("is_proxy")
    private Boolean isProxy;

    @JsonProperty("is_tor")
    private Boolean isTor;

    @JsonProperty("is_hosting")
    private Boolean isHosting;

    @JsonProperty("is_mobile")
    private Boolean isMobile;

    @JsonProperty("is_high_risk_country")
    private Boolean isHighRiskCountry;

    @JsonProperty("is_sanctioned_country")
    private Boolean isSanctionedCountry;

    @JsonProperty("risk_score")
    private BigDecimal riskScore;

    @JsonProperty("fraud_score")
    private BigDecimal fraudScore;

    @JsonProperty("location_timestamp")
    private LocalDateTime locationTimestamp;

    @JsonProperty("device_location")
    private Boolean deviceLocation;

    @JsonProperty("gps_enabled")
    private Boolean gpsEnabled;

    /**
     * Calculate distance to another location in kilometers
     */
    public double calculateDistanceTo(Location other) {
        if (this.latitude == null || this.longitude == null || 
            other.latitude == null || other.longitude == null) {
            return -1;
        }
        
        double earthRadius = 6371; // kilometers
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(Math.toRadians(this.latitude)) * 
                  Math.cos(Math.toRadians(other.latitude)) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return earthRadius * c;
    }

    /**
     * Check if location is suspicious
     */
    public boolean isSuspicious() {
        return Boolean.TRUE.equals(isVpn) ||
               Boolean.TRUE.equals(isProxy) ||
               Boolean.TRUE.equals(isTor) ||
               Boolean.TRUE.equals(isHighRiskCountry) ||
               Boolean.TRUE.equals(isSanctionedCountry) ||
               (riskScore != null && riskScore.compareTo(new BigDecimal("70")) > 0);
    }

    /**
     * Check if location is trusted
     */
    public boolean isTrusted() {
        return !isSuspicious() &&
               Boolean.FALSE.equals(isVpn) &&
               Boolean.FALSE.equals(isProxy) &&
               Boolean.FALSE.equals(isTor) &&
               (riskScore != null && riskScore.compareTo(new BigDecimal("30")) < 0);
    }

    /**
     * Get location summary
     */
    public String getLocationSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (city != null) summary.append(city).append(", ");
        if (region != null) summary.append(region).append(", ");
        if (country != null) summary.append(country);
        
        return summary.toString();
    }

    /**
     * Check if location jump is suspicious
     */
    public boolean isSuspiciousJump(Location previousLocation, long minutesBetween) {
        if (previousLocation == null) return false;
        
        double distance = calculateDistanceTo(previousLocation);
        if (distance < 0) return false;
        
        // Calculate maximum reasonable speed (km/h)
        double hoursBetween = minutesBetween / 60.0;
        if (hoursBetween == 0) return distance > 100; // Same time, different location > 100km
        
        double speed = distance / hoursBetween;
        
        // Suspicious if speed > 900 km/h (typical flight speed)
        return speed > 900;
    }
}