package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprehensive user location information for fraud assessment
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class UserLocation {
    private double latitude;
    private double longitude;
    private String country;
    private String countryCode;
    private String region;
    private String city;
    private String postalCode;
    private String timeZone;
    private String isp;
    private String organization;
    private String asn;
    private boolean isProxy;
    private boolean isVpn;
    private boolean isTor;
    private boolean isRelay;
    private boolean isHosting;
    private double accuracy;
    private String source;
    private LocalDateTime timestamp;
    private List<String> registeredCountries;
    private String connectionType;
    private String userType;
    private boolean isAnonymous;
    private double riskScore;
    private boolean isHighRisk;
    private String threatType;
}