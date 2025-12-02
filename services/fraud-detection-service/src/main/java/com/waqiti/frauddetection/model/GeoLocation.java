package com.waqiti.frauddetection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GeoLocation Model - PRODUCTION READY
 *
 * Represents geolocation data from IP address lookup
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocation {

    private String ipAddress;
    private String countryCode;
    private String countryName;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String timeZone;
    private String postalCode;
    private Integer accuracyRadius;

    // Risk flags
    private Boolean isAnonymousProxy;
    private Boolean isHostingProvider;
    private Boolean isTorExitNode;
    private Boolean isVpn;
    private Boolean isHighRiskCountry;
    private Boolean isSanctionedCountry;
    private Double countryRiskScore;

    /**
     * Create unknown location (when geolocation fails)
     */
    public static GeoLocation unknown() {
        return GeoLocation.builder()
            .countryCode("XX")
            .countryName("Unknown")
            .isAnonymousProxy(false)
            .isHostingProvider(false)
            .isTorExitNode(false)
            .isVpn(false)
            .isHighRiskCountry(false)
            .isSanctionedCountry(false)
            .countryRiskScore(0.5) // Moderate risk when unknown
            .build();
    }

    /**
     * Create localhost location (for local/private IPs)
     */
    public static GeoLocation localhost() {
        return GeoLocation.builder()
            .ipAddress("127.0.0.1")
            .countryCode("US")
            .countryName("United States")
            .city("Localhost")
            .latitude(0.0)
            .longitude(0.0)
            .isAnonymousProxy(false)
            .isHostingProvider(false)
            .isTorExitNode(false)
            .isVpn(false)
            .isHighRiskCountry(false)
            .isSanctionedCountry(false)
            .countryRiskScore(0.0) // No risk for localhost
            .build();
    }

    /**
     * Check if location is known
     */
    public boolean isKnown() {
        return latitude != null && longitude != null;
    }

    /**
     * Check if location is high risk
     */
    public boolean isHighRisk() {
        return (isHighRiskCountry != null && isHighRiskCountry) ||
               (isSanctionedCountry != null && isSanctionedCountry) ||
               (isTorExitNode != null && isTorExitNode) ||
               (countryRiskScore != null && countryRiskScore > 0.7);
    }

    /**
     * Check if location requires enhanced screening
     */
    public boolean requiresEnhancedScreening() {
        return (isSanctionedCountry != null && isSanctionedCountry) ||
               (isTorExitNode != null && isTorExitNode) ||
               (isAnonymousProxy != null && isAnonymousProxy);
    }

    /**
     * Get location description for logging
     */
    public String getLocationDescription() {
        if (city != null && countryName != null) {
            return city + ", " + countryName;
        } else if (countryName != null) {
            return countryName;
        } else {
            return "Unknown Location";
        }
    }
}
