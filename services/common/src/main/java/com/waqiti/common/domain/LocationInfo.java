package com.waqiti.common.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Comprehensive Location Information Domain Object
 * 
 * Standardized location data structure used across all Waqiti services
 * for geographic information, fraud detection, compliance, and analytics.
 * 
 * This class follows enterprise fintech standards for:
 * - Geographic precision and validation
 * - Fraud detection metadata
 * - Compliance and regulatory requirements
 * - Privacy and data masking
 * - Performance optimization
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Comprehensive location information with fraud detection and compliance metadata")
public class LocationInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Geographic coordinates with high precision validation
    @Schema(description = "Geographic latitude", example = "37.7749295", minimum = "-90.0", maximum = "90.0")
    @DecimalMin(value = "-90.0", message = "Invalid latitude: must be between -90.0 and 90.0")
    @DecimalMax(value = "90.0", message = "Invalid latitude: must be between -90.0 and 90.0")
    @JsonProperty("latitude")
    private BigDecimal latitude;
    
    @Schema(description = "Geographic longitude", example = "-122.4194155", minimum = "-180.0", maximum = "180.0")
    @DecimalMin(value = "-180.0", message = "Invalid longitude: must be between -180.0 and 180.0")
    @DecimalMax(value = "180.0", message = "Invalid longitude: must be between -180.0 and 180.0")
    @JsonProperty("longitude")
    private BigDecimal longitude;
    
    @Schema(description = "Location accuracy in meters", example = "10.0")
    @JsonProperty("accuracy")
    private BigDecimal accuracy;
    
    @Schema(description = "Accuracy radius in kilometers for fraud detection", example = "5")
    @Min(value = 0, message = "Accuracy radius must be non-negative")
    @Max(value = 1000, message = "Accuracy radius cannot exceed 1000km")
    private Integer accuracyRadius;
    
    // Address components with validation
    @Schema(description = "Street address", example = "123 Market Street")
    @JsonProperty("address")
    private String address;
    
    @Schema(description = "City name", example = "San Francisco")
    @JsonProperty("city")
    private String city;
    
    @Schema(description = "State or province", example = "California")
    @JsonProperty("state")
    private String state;
    
    @Schema(description = "State or region code", example = "CA")
    @JsonProperty("region_code")
    private String regionCode;
    
    @Schema(description = "Postal or ZIP code", example = "94105")
    @JsonProperty("postal_code")
    @Pattern(regexp = "^[A-Za-z0-9\\s\\-]{3,10}$", message = "Invalid postal code format")
    private String postalCode;
    
    @Schema(description = "Country name", example = "United States")
    @JsonProperty("country")
    private String country;
    
    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "US")
    @JsonProperty("country_code")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be 2-letter ISO format")
    private String countryCode;
    
    // Network and ISP information for fraud detection
    @Schema(description = "Internet Service Provider", example = "Comcast Cable Communications")
    private String isp;
    
    @Schema(description = "Organization name", example = "Comcast Cable Communications, LLC")
    private String organization;
    
    @Schema(description = "Autonomous System Number", example = "AS7922")
    private String asn;
    
    @Schema(description = "ASN Organization name", example = "COMCAST-7922")
    private String asnOrganization;
    
    @Schema(description = "Domain name", example = "comcast.com")
    private String domain;
    
    // Security and risk indicators for fraud detection
    @Schema(description = "Whether IP is a proxy server")
    @JsonProperty("is_proxy")
    @Builder.Default
    private Boolean isProxy = false;
    
    @Schema(description = "Whether IP is a Tor exit node")
    @JsonProperty("is_tor")
    @Builder.Default
    private Boolean isTor = false;
    
    @Schema(description = "Whether IP is a VPN endpoint")
    @JsonProperty("is_vpn")
    @Builder.Default
    private Boolean isVpn = false;
    
    @Schema(description = "Whether IP is an anonymizing service")
    @JsonProperty("is_anonymizer")
    @Builder.Default
    private Boolean isAnonymizer = false;
    
    @Schema(description = "Whether IP is from hosting/datacenter")
    @JsonProperty("is_hosting")
    @Builder.Default
    private Boolean isHosting = false;
    
    @Schema(description = "Whether IP is part of known botnet")
    @JsonProperty("is_botnet")
    @Builder.Default
    private Boolean isBotnet = false;
    
    @Schema(description = "Whether IP is flagged as malicious")
    @JsonProperty("is_malicious")
    @Builder.Default
    private Boolean isMalicious = false;
    
    @Schema(description = "Overall fraud risk score (0.0-1.0)", example = "0.15")
    @JsonProperty("risk_score")
    @DecimalMin(value = "0.0", message = "Risk score must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Risk score must be between 0.0 and 1.0")
    private BigDecimal riskScore;
    
    // Threat intelligence data
    @Schema(description = "Categories of threats associated with this location")
    @JsonProperty("threat_categories")
    private List<String> threatCategories;
    
    @Schema(description = "Blacklists containing this IP")
    private List<String> blacklists;
    
    @Schema(description = "Additional threat intelligence metadata")
    @JsonProperty("threat_intel")
    private Map<String, Object> threatIntel;
    
    // Temporal information
    @Schema(description = "Timestamp when location was captured", example = "2024-01-15T10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("location_timestamp")
    private LocalDateTime locationTimestamp;
    
    @Schema(description = "When this location data was determined")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Schema(description = "First time this IP was seen")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("first_seen")
    private LocalDateTime firstSeen;
    
    @Schema(description = "Last time this IP was seen")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("last_seen")
    private LocalDateTime lastSeen;
    
    // Request context (masked for privacy)
    @Schema(description = "Masked IP address for privacy", example = "192.168.***.***")
    @JsonProperty("ip_address")
    private String ipAddress;
    
    @Schema(description = "User agent from request")
    @JsonProperty("user_agent")
    private String userAgent;
    
    @Schema(description = "Associated session ID")
    @JsonProperty("session_id")
    private String sessionId;
    
    @Schema(description = "Associated user ID")
    @JsonProperty("user_id")
    private String userId;
    
    // Confidence and quality indicators
    @Schema(description = "Confidence in location accuracy (0.0-1.0)", example = "0.95")
    @JsonProperty("confidence_level")
    @DecimalMin(value = "0.0", message = "Confidence level must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Confidence level must be between 0.0 and 1.0")
    private BigDecimal confidenceLevel;
    
    @Schema(description = "Source of geolocation data", example = "MaxMind")
    @JsonProperty("data_source")
    private String dataSource;
    
    @Schema(description = "Geolocation service provider", example = "GeoIP2")
    private String provider;
    
    @Schema(description = "Data quality score (0-100)", example = "95")
    @JsonProperty("quality_score")
    @Min(value = 0, message = "Quality score must be between 0 and 100")
    @Max(value = 100, message = "Quality score must be between 0 and 100")
    private Integer qualityScore;
    
    // Behavioral analysis
    @Schema(description = "Number of visits from this location")
    @JsonProperty("visit_count")
    @Min(value = 0, message = "Visit count must be non-negative")
    private Integer visitCount;
    
    @Schema(description = "Average session duration from this location (milliseconds)")
    @JsonProperty("session_duration")
    private Long sessionDuration;
    
    @Schema(description = "Typical hours of activity from this location")
    @JsonProperty("typical_hours")
    private List<String> typicalHours;
    
    @Schema(description = "Device types seen from this location")
    @JsonProperty("device_types")
    private Map<String, Integer> deviceTypes;
    
    // Compliance and regulatory
    @Schema(description = "Whether location is in EU (GDPR compliance)")
    @JsonProperty("is_eu_country")
    @Builder.Default
    private Boolean isEuCountry = false;
    
    @Schema(description = "Whether location is high-risk for AML")
    @JsonProperty("is_high_risk_country")
    @Builder.Default
    private Boolean isHighRiskCountry = false;
    
    @Schema(description = "Applicable sanctions lists")
    private List<String> sanctions;
    
    @Schema(description = "Regulatory zone classification")
    @JsonProperty("regulatory_zone")
    private String regulatoryZone;
    
    // Device and browser fingerprinting
    @Schema(description = "Device fingerprint for fraud detection")
    @JsonProperty("device_fingerprint")
    private String deviceFingerprint;
    
    @Schema(description = "Screen resolution", example = "1920x1080")
    @JsonProperty("screen_resolution")
    private String screenResolution;
    
    @Schema(description = "Device timezone", example = "America/Los_Angeles")
    private String timezone;
    
    // Custom metadata and tags
    @Schema(description = "Additional custom metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Custom tags for categorization")
    private Map<String, String> tags;
    
    /**
     * Determines if this location represents a high fraud risk
     * 
     * @return true if location has high fraud risk indicators
     */
    public boolean isHighRisk() {
        return Boolean.TRUE.equals(isProxy) || 
               Boolean.TRUE.equals(isTor) || 
               Boolean.TRUE.equals(isVpn) ||
               Boolean.TRUE.equals(isMalicious) ||
               Boolean.TRUE.equals(isHighRiskCountry) ||
               Boolean.TRUE.equals(isBotnet) ||
               (riskScore != null && riskScore.compareTo(new BigDecimal("0.7")) > 0);
    }
    
    /**
     * Checks if location data is reliable for business decisions
     * 
     * @return true if location has high confidence and accuracy
     */
    public boolean hasReliableLocation() {
        return latitude != null && longitude != null && 
               confidenceLevel != null && confidenceLevel.compareTo(new BigDecimal("0.7")) > 0 &&
               accuracyRadius != null && accuracyRadius < 100; // Within 100km accuracy
    }
    
    /**
     * Generates human-readable location string
     * 
     * @return formatted location string (e.g., "San Francisco, CA, United States")
     */
    public String getLocationString() {
        StringBuilder location = new StringBuilder();
        
        if (city != null && !city.trim().isEmpty()) {
            location.append(city);
        }
        
        if (state != null && !state.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(state);
        }
        
        if (country != null && !country.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(country);
        }
        
        return location.toString();
    }
    
    /**
     * Calculates great-circle distance from another location using Haversine formula
     * 
     * @param other The other LocationInfo to calculate distance to
     * @return distance in kilometers, or Double.MAX_VALUE if coordinates unavailable
     */
    public double calculateDistanceFrom(LocationInfo other) {
        if (!this.hasReliableLocation() || !other.hasReliableLocation()) {
            return Double.MAX_VALUE; // Unknown distance
        }
        
        return calculateHaversineDistance(
            this.latitude.doubleValue(), this.longitude.doubleValue(),
            other.latitude.doubleValue(), other.longitude.doubleValue()
        );
    }
    
    /**
     * Haversine formula implementation for great-circle distance calculation
     * 
     * @param lat1 First latitude in degrees
     * @param lon1 First longitude in degrees
     * @param lat2 Second latitude in degrees
     * @param lon2 Second longitude in degrees
     * @return distance in kilometers
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Creates privacy-safe version for logging and analytics
     * Removes or masks sensitive information while preserving useful metadata
     * 
     * @return masked LocationInfo suitable for logging
     */
    public LocationInfo createMaskedVersion() {
        return LocationInfo.builder()
            .countryCode(this.countryCode)
            .country(this.country)
            .state(this.state)
            .city(this.city)
            .isProxy(this.isProxy)
            .isTor(this.isTor)
            .isVpn(this.isVpn)
            .isAnonymizer(this.isAnonymizer)
            .isHosting(this.isHosting)
            .isMalicious(this.isMalicious)
            .riskScore(this.riskScore)
            .timestamp(this.timestamp)
            .locationTimestamp(this.locationTimestamp)
            .ipAddress(maskIpAddress(this.ipAddress))
            .confidenceLevel(this.confidenceLevel)
            .dataSource(this.dataSource)
            .provider(this.provider)
            .qualityScore(this.qualityScore)
            .isEuCountry(this.isEuCountry)
            .isHighRiskCountry(this.isHighRiskCountry)
            .regulatoryZone(this.regulatoryZone)
            .threatCategories(this.threatCategories)
            .build();
    }
    
    /**
     * Masks IP address for privacy compliance while maintaining some utility
     * 
     * @param ip Original IP address
     * @return masked IP address
     */
    private String maskIpAddress(String ip) {
        if (ip == null || ip.length() < 8) {
            return "***";
        }
        
        // Handle IPv4 addresses
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***.**";
            }
        }
        
        // Handle IPv6 addresses
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length >= 4) {
                return parts[0] + ":" + parts[1] + ":***:***";
            }
        }
        
        // Fallback for other formats
        return ip.substring(0, Math.min(8, ip.length())) + "***";
    }
    
    /**
     * Validates geographic coordinates
     * 
     * @return true if coordinates are valid
     */
    public boolean hasValidCoordinates() {
        if (latitude == null || longitude == null) {
            return false;
        }
        
        return latitude.compareTo(new BigDecimal("-90")) >= 0 &&
               latitude.compareTo(new BigDecimal("90")) <= 0 &&
               longitude.compareTo(new BigDecimal("-180")) >= 0 &&
               longitude.compareTo(new BigDecimal("180")) <= 0;
    }
    
    /**
     * Checks if location requires special compliance handling
     * 
     * @return true if location has compliance implications
     */
    public boolean requiresComplianceHandling() {
        return Boolean.TRUE.equals(isEuCountry) ||
               Boolean.TRUE.equals(isHighRiskCountry) ||
               (sanctions != null && !sanctions.isEmpty()) ||
               isHighRisk();
    }
    
    /**
     * Gets risk level as enumerated value for business logic
     * 
     * @return risk level classification
     */
    public RiskLevel getRiskLevel() {
        if (riskScore == null) {
            return RiskLevel.UNKNOWN;
        }
        
        BigDecimal score = riskScore;
        if (score.compareTo(new BigDecimal("0.1")) <= 0) {
            return RiskLevel.LOW;
        } else if (score.compareTo(new BigDecimal("0.3")) <= 0) {
            return RiskLevel.MEDIUM;
        } else if (score.compareTo(new BigDecimal("0.7")) <= 0) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.CRITICAL;
        }
    }
    
    /**
     * Risk level enumeration for business logic
     */
    public enum RiskLevel {
        UNKNOWN, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationInfo that = (LocationInfo) o;
        return Objects.equals(latitude, that.latitude) &&
               Objects.equals(longitude, that.longitude) &&
               Objects.equals(countryCode, that.countryCode) &&
               Objects.equals(city, that.city) &&
               Objects.equals(ipAddress, that.ipAddress) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, countryCode, city, ipAddress, timestamp);
    }
    
    @Override
    public String toString() {
        return "LocationInfo{" +
               "location='" + getLocationString() + "'" +
               ", coordinates=[" + latitude + "," + longitude + "]" +
               ", riskScore=" + riskScore +
               ", confidence=" + confidenceLevel +
               ", timestamp=" + locationTimestamp +
               '}';
    }
}