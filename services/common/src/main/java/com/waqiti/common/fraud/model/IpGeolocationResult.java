package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

/**
 * IP geolocation result with comprehensive location risk analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class IpGeolocationResult {
    private String ipAddress;
    private String country;
    private String region;
    private String city;
    private double latitude;
    private double longitude;
    private String timezone;
    private boolean isHighRiskCountry;
    private String ispName;
    private String isp;
    private boolean isHighRiskIsp;
    private int accuracyRadius;
    private double locationRisk;
    private double riskScore; // Overall risk score for this IP location

    /**
     * Get location risk score based on country and ISP
     */
    public double getLocationRisk() {
        if (locationRisk > 0) {
            return locationRisk;
        }
        double risk = 0.0;
        if (isHighRiskCountry) risk += 0.5;
        if (isHighRiskIsp) risk += 0.3;
        return Math.min(risk, 1.0);
    }
}