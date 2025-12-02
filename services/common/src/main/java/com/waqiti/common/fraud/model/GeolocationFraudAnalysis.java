package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Geolocation fraud analysis
 */
@Data
@Builder
@Jacksonized
public class GeolocationFraudAnalysis {
    private String ipAddress;
    private String userId;
    private double locationScore;
    private boolean isHighRisk;
    private String country;
    private String city;
    private boolean isVpn;
    private boolean isTor;
    private boolean isProxy;
    private boolean impossibleTravel;
    private List<String> anomalies;
    private Instant analysisTimestamp;
    
    // Legacy fields for backward compatibility
    private double latitude;
    private double longitude;
    private double distanceFromLastLocation;
    private List<String> locationRiskFactors;

    /**
     * Get risk score based on location
     */
    public double getRiskScore() {
        return locationScore;
    }
}