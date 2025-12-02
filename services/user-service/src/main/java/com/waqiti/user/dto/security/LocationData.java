package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Location Data DTO
 * 
 * Contains geographical and network location information for security validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationData {
    
    // IP-based location
    private String ipAddress;
    private String country;
    private String countryName; // Alias for country
    private String countryCode;
    private String region;
    private String regionCode;
    private String city;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String isp;
    private String organization;
    private String asn; // Autonomous System Number
    
    // GPS location (if available)
    private Double gpsLatitude;
    private Double gpsLongitude;
    private Double accuracy;
    private Double altitude;
    private Double speed;
    private Double heading;
    private LocalDateTime gpsTimestamp;
    
    // Network information
    private String networkType; // WIFI, CELLULAR, ETHERNET, UNKNOWN
    private String connectionType; // 2G, 3G, 4G, 5G, WIFI, BROADBAND
    private String networkOperator;
    private String networkCountryIso;
    private Boolean roaming;
    
    // VPN/Proxy detection
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    private Boolean hostingProvider;
    private Boolean datacenterDetected;
    private String vpnProvider;
    private String proxyType;
    
    // Risk indicators
    private Double riskScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Boolean anonymousProxy;
    private Boolean maliciousIp;
    private Boolean knownAttacker;
    private Boolean botnet;
    
    // Geolocation accuracy
    private String locationSource; // IP, GPS, WIFI, CELL_TOWER, MANUAL
    private Double locationAccuracy; // Accuracy radius in kilometers
    private String locationMethod;
    private Double confidenceScore;
    
    // Historical context
    private Boolean newLocation;
    private Double distanceFromLastLocation; // kilometers
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Integer usageCount;
    
    // Time zone analysis
    private String expectedTimezone;
    private Boolean timezoneConsistent;
    private Integer timezoneOffset;
    private Boolean daylightSavingTime;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private LocalDateTime collectedAt;
    private String collectionMethod;
    private Long processingTimeMs;
    
    /**
     * Check if VPN is detected
     */
    public boolean isVpn() {
        return Boolean.TRUE.equals(vpnDetected);
    }
    
    /**
     * Check if proxy is detected
     */
    public boolean isProxy() {
        return Boolean.TRUE.equals(proxyDetected);
    }
    
    /**
     * Get country name (returns country or countryName)
     */
    public String getCountryName() {
        return countryName != null ? countryName : country;
    }
}