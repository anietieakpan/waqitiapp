package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Geographic location information
 * Used for location-based risk assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocationInfo {

    private String locationId;
    private Double latitude;
    private Double longitude;
    private Double accuracy; // meters

    // Location details
    private String country;
    private String countryCode; // ISO 3166-1 alpha-2
    private String region;
    private String city;
    private String postalCode;
    private String timezone;

    // IP-based location
    private String ipAddress;
    private String ipCountry;
    private String ipCity;
    private String isp;
    private String organization;

    // Location source
    private String source; // GPS, IP, WIFI, CELL_TOWER, MANUAL
    private Instant capturedAt;

    // Risk indicators
    private Boolean highRiskCountry;
    private Boolean highRiskCity;
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    private Boolean datacenterIp;

    // Mismatch detection
    private Boolean gpsIpMismatch;
    private Boolean timezoneIpMismatch;
    private Double distanceFromIpLocation; // kilometers

    // Travel analysis
    private Double distanceFromLastLocation; // kilometers
    private Double travelSpeed; // km/h
    private Boolean impossibleTravel; // Travel speed exceeds reasonable limits

    // Historical context
    private Boolean newLocation;
    private Boolean frequentLocation;
    private Integer visitCount;
    private Instant firstVisit;
    private Instant lastVisit;

    // Risk score
    private Double locationRiskScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH
}
