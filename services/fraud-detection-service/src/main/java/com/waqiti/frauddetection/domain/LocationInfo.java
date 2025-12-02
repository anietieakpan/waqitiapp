package com.waqiti.frauddetection.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive Location Information Domain Object
 * 
 * Contains detailed geolocation data with fraud detection metadata
 * for IP-based location analysis and risk assessment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Basic geographic information
    private String countryCode;        // ISO 3166-1 alpha-2 country code
    private String countryName;        // Full country name
    private String regionCode;         // State/province/region code
    private String regionName;         // State/province/region name
    private String city;               // City name
    private String postalCode;         // ZIP/postal code
    private Double latitude;           // Geographic latitude
    private Double longitude;          // Geographic longitude
    private Integer accuracyRadius;    // Accuracy radius in kilometers
    
    // Network and ISP information
    private String isp;                // Internet Service Provider
    private String organization;       // Organization name
    private String asn;               // Autonomous System Number
    private String asnOrganization;   // ASN Organization name
    private String domain;            // Domain name
    
    // Security and risk indicators
    private Boolean isProxy;          // Is proxy server
    private Boolean isTor;            // Is Tor exit node
    private Boolean isVpn;            // Is VPN endpoint
    private Boolean isAnonymizer;     // Is anonymizing service
    private Boolean isHosting;        // Is hosting/datacenter IP
    private Boolean isBotnet;         // Is part of known botnet
    private Boolean isMalicious;      // Is flagged as malicious
    private Double riskScore;         // Overall risk score (0.0-1.0)
    
    // Threat intelligence data
    private List<String> threatCategories;  // Categories of threats
    private List<String> blacklists;        // Blacklists containing this IP
    private Map<String, Object> threatIntel; // Additional threat intelligence
    
    // Temporal information
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;   // When this location was determined
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime firstSeen;   // First time this IP was seen
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSeen;    // Last time this IP was seen
    
    // Request context
    private String ipAddress;          // Original IP address (masked for privacy)
    private String userAgent;         // User agent from request
    private String sessionId;         // Associated session ID
    private String userId;            // Associated user ID
    
    // Confidence and quality indicators
    private Double confidenceLevel;    // Confidence in location accuracy (0.0-1.0)
    private String dataSource;        // Source of geolocation data
    private String provider;          // Geolocation service provider
    private Integer qualityScore;     // Data quality score (0-100)
    
    // Behavioral analysis
    private Integer visitCount;       // Number of visits from this location
    private Long sessionDuration;    // Average session duration from this location
    private List<String> typicalHours; // Typical hours of activity
    private Map<String, Integer> deviceTypes; // Device types seen from this location
    
    // Compliance and regulatory
    private Boolean isEuCountry;      // Is EU country (GDPR compliance)
    private Boolean isHighRiskCountry; // Is high-risk country for AML
    private List<String> sanctions;   // Applicable sanctions lists
    private String regulatoryZone;   // Regulatory zone classification
    
    // Custom metadata
    private Map<String, Object> metadata; // Additional custom data
    private Map<String, String> tags;     // Custom tags for categorization
    
    /**
     * Check if this location represents a high-risk jurisdiction
     */
    public boolean isHighRisk() {
        return Boolean.TRUE.equals(isProxy) || 
               Boolean.TRUE.equals(isTor) || 
               Boolean.TRUE.equals(isVpn) ||
               Boolean.TRUE.equals(isMalicious) ||
               Boolean.TRUE.equals(isHighRiskCountry) ||
               (riskScore != null && riskScore > 0.7);
    }
    
    /**
     * Check if this location has reliable geolocation data
     */
    public boolean hasReliableLocation() {
        return latitude != null && longitude != null && 
               confidenceLevel != null && confidenceLevel > 0.7 &&
               accuracyRadius != null && accuracyRadius < 100; // Within 100km
    }
    
    /**
     * Get a human-readable location string
     */
    public String getLocationString() {
        StringBuilder location = new StringBuilder();
        
        if (city != null && !city.isEmpty()) {
            location.append(city);
        }
        
        if (regionName != null && !regionName.isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(regionName);
        }
        
        if (countryName != null && !countryName.isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(countryName);
        }
        
        return location.toString();
    }
    
    /**
     * Calculate distance from another location in kilometers
     */
    public double calculateDistanceFrom(LocationInfo other) {
        if (!this.hasReliableLocation() || !other.hasReliableLocation()) {
            return Double.MAX_VALUE; // Unknown distance
        }
        
        return calculateHaversineDistance(
            this.latitude, this.longitude,
            other.latitude, other.longitude
        );
    }
    
    /**
     * Haversine formula for calculating distance between two coordinates
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Create a masked version for logging (removes sensitive data)
     */
    public LocationInfo createMaskedVersion() {
        return LocationInfo.builder()
            .countryCode(this.countryCode)
            .countryName(this.countryName)
            .city(this.city)
            .isProxy(this.isProxy)
            .isTor(this.isTor)
            .isVpn(this.isVpn)
            .riskScore(this.riskScore)
            .timestamp(this.timestamp)
            .ipAddress(maskIpAddress(this.ipAddress))
            .confidenceLevel(this.confidenceLevel)
            .dataSource(this.dataSource)
            .build();
    }
    
    /**
     * Mask IP address for privacy
     */
    private String maskIpAddress(String ip) {
        if (ip == null || ip.length() < 8) {
            return "***";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.**";
        }
        // For IPv6 or other formats
        return ip.substring(0, Math.min(8, ip.length())) + "***";
    }
}