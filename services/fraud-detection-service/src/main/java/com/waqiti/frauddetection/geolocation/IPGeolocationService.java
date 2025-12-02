package com.waqiti.frauddetection.geolocation;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Location;
import com.waqiti.frauddetection.model.GeoLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;

/**
 * IP Geolocation Service - PRODUCTION READY
 *
 * FINAL 5% ENHANCEMENT - Real IP geolocation with MaxMind GeoIP2
 *
 * Features:
 * - Real-time IP → Location mapping
 * - VPN/Tor/Proxy detection
 * - Impossible travel detection
 * - High-risk country identification
 * - Distance calculation (Haversine formula)
 */
@Service
@Slf4j
public class IPGeolocationService {

    private final DatabaseReader geoIPReader;

    // High-risk countries based on fraud statistics
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "KP", // North Korea
        "IR", // Iran
        "SY", // Syria
        "SD", // Sudan
        "CU", // Cuba
        "BY", // Belarus
        "VE", // Venezuela
        "MM"  // Myanmar
    );

    // Sanctioned countries requiring enhanced screening
    private static final Set<String> SANCTIONED_COUNTRIES = Set.of(
        "KP", "IR", "SY", "CU", "SD"
    );

    // Countries with elevated fraud rates
    private static final Set<String> ELEVATED_FRAUD_COUNTRIES = Set.of(
        "NG", "GH", "PK", "BD", "ID", "PH", "UA", "RO", "BG"
    );

    public IPGeolocationService(@Value("${maxmind.database.path:/opt/maxmind/GeoLite2-City.mmdb}") String databasePath) {
        try {
            File database = new File(databasePath);
            if (!database.exists()) {
                log.warn("GEOLOCATION: MaxMind database not found at: {}. Using fallback mode.", databasePath);
                this.geoIPReader = null;
            } else {
                this.geoIPReader = new DatabaseReader.Builder(database).build();
                log.info("GEOLOCATION: MaxMind GeoIP2 database loaded successfully from: {}", databasePath);
            }
        } catch (IOException e) {
            log.error("GEOLOCATION: Failed to load MaxMind database from: {}", databasePath, e);
            throw new RuntimeException("Failed to initialize IP geolocation service", e);
        }
    }

    /**
     * Geolocate IP address with caching (1 hour TTL)
     *
     * @param ipAddress IP address to geolocate
     * @return GeoLocation with country, city, coordinates, risk flags
     */
    @Cacheable(value = "ip-geolocation", key = "#ipAddress", unless = "#result == null")
    public GeoLocation geolocateIP(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            log.warn("GEOLOCATION: Empty IP address provided");
            return GeoLocation.unknown();
        }

        // Handle localhost/private IPs
        if (isPrivateIP(ipAddress)) {
            log.debug("GEOLOCATION: Private IP detected: {}", ipAddress);
            return GeoLocation.localhost();
        }

        if (geoIPReader == null) {
            log.warn("GEOLOCATION: Database not available, using fallback for: {}", ipAddress);
            return createFallbackLocation(ipAddress);
        }

        try {
            InetAddress address = InetAddress.getByName(ipAddress);

            // Try City database first (most detailed)
            try {
                CityResponse cityResponse = geoIPReader.city(address);
                Location location = cityResponse.getLocation();

                String countryCode = cityResponse.getCountry().getIsoCode();

                return GeoLocation.builder()
                    .ipAddress(ipAddress)
                    .countryCode(countryCode)
                    .countryName(cityResponse.getCountry().getName())
                    .region(cityResponse.getMostSpecificSubdivision().getName())
                    .city(cityResponse.getCity().getName())
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .timeZone(location.getTimeZone())
                    .postalCode(cityResponse.getPostal().getCode())
                    .accuracyRadius(location.getAccuracyRadius())
                    .isAnonymousProxy(cityResponse.getTraits().isAnonymousProxy())
                    .isHostingProvider(cityResponse.getTraits().isHostingProvider())
                    .isTorExitNode(cityResponse.getTraits().isTorExitNode())
                    .isVpn(isLikelyVPN(cityResponse))
                    .isHighRiskCountry(HIGH_RISK_COUNTRIES.contains(countryCode))
                    .isSanctionedCountry(SANCTIONED_COUNTRIES.contains(countryCode))
                    .countryRiskScore(calculateCountryRiskScore(countryCode))
                    .build();

            } catch (AddressNotFoundException e) {
                // Fall back to Country database if City not found
                log.debug("GEOLOCATION: City data not found for IP: {}, trying country lookup", ipAddress);

                CountryResponse countryResponse = geoIPReader.country(address);
                String countryCode = countryResponse.getCountry().getIsoCode();

                return GeoLocation.builder()
                    .ipAddress(ipAddress)
                    .countryCode(countryCode)
                    .countryName(countryResponse.getCountry().getName())
                    .isAnonymousProxy(countryResponse.getTraits().isAnonymousProxy())
                    .isHostingProvider(countryResponse.getTraits().isHostingProvider())
                    .isTorExitNode(countryResponse.getTraits().isTorExitNode())
                    .isHighRiskCountry(HIGH_RISK_COUNTRIES.contains(countryCode))
                    .isSanctionedCountry(SANCTIONED_COUNTRIES.contains(countryCode))
                    .countryRiskScore(calculateCountryRiskScore(countryCode))
                    .build();
            }

        } catch (Exception e) {
            log.error("GEOLOCATION: Failed to geolocate IP: {}", ipAddress, e);
            return GeoLocation.unknown();
        }
    }

    /**
     * Check if IP is from a high-risk country
     */
    public boolean isHighRiskCountry(String countryCode) {
        return HIGH_RISK_COUNTRIES.contains(countryCode);
    }

    /**
     * Check if IP is from a sanctioned country (OFAC)
     */
    public boolean isSanctionedCountry(String countryCode) {
        return SANCTIONED_COUNTRIES.contains(countryCode);
    }

    /**
     * Calculate country risk score (0.0 - 1.0)
     */
    public double calculateCountryRiskScore(String countryCode) {
        if (countryCode == null) return 0.5;

        if (SANCTIONED_COUNTRIES.contains(countryCode)) return 0.95; // Critical risk
        if (HIGH_RISK_COUNTRIES.contains(countryCode)) return 0.85;  // High risk
        if (ELEVATED_FRAUD_COUNTRIES.contains(countryCode)) return 0.6; // Moderate-high risk

        // Low-risk countries
        if (Set.of("US", "CA", "GB", "DE", "FR", "AU", "NZ", "JP", "SG").contains(countryCode)) {
            return 0.1; // Low risk
        }

        return 0.3; // Default moderate-low risk
    }

    /**
     * Detect impossible travel between two locations
     *
     * @param location1 First location
     * @param location2 Second location
     * @param timeDifferenceSeconds Time between transactions in seconds
     * @return true if travel is impossible (exceeds 900 km/h - speed of commercial aircraft)
     */
    public boolean isImpossibleTravel(GeoLocation location1, GeoLocation location2, long timeDifferenceSeconds) {
        if (location1 == null || location2 == null) return false;
        if (location1.getLatitude() == null || location2.getLatitude() == null) return false;

        // Calculate distance in kilometers
        double distanceKm = calculateDistance(
            location1.getLatitude(), location1.getLongitude(),
            location2.getLatitude(), location2.getLongitude()
        );

        // Calculate required speed (km/h)
        double timeDifferenceHours = timeDifferenceSeconds / 3600.0;
        if (timeDifferenceHours == 0) return distanceKm > 50; // Same second, but far apart

        double requiredSpeedKmh = distanceKm / timeDifferenceHours;

        // Commercial aircraft max ~900 km/h
        // Allow some margin for error in geolocation
        boolean impossible = requiredSpeedKmh > 1000;

        if (impossible) {
            log.warn("GEOLOCATION: IMPOSSIBLE TRAVEL DETECTED - Distance: {} km, Time: {} hours, Speed: {} km/h",
                String.format("%.2f", distanceKm),
                String.format("%.2f", timeDifferenceHours),
                String.format("%.2f", requiredSpeedKmh));
        }

        return impossible;
    }

    /**
     * Calculate distance between two points using Haversine formula
     *
     * @return Distance in kilometers
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
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
     * Check if IP is private/localhost
     */
    private boolean isPrivateIP(String ipAddress) {
        return ipAddress.startsWith("127.") ||
               ipAddress.startsWith("10.") ||
               ipAddress.startsWith("192.168.") ||
               ipAddress.startsWith("172.16.") ||
               ipAddress.equals("localhost") ||
               ipAddress.equals("::1");
    }

    /**
     * Check if IP is likely from a VPN
     */
    private boolean isLikelyVPN(CityResponse response) {
        // MaxMind provides some VPN detection through hosting provider flag
        // For more accurate VPN detection, integrate with dedicated VPN detection services
        return response.getTraits().isHostingProvider() &&
               !response.getTraits().isAnonymousProxy() &&
               !response.getTraits().isTorExitNode();
    }

    /**
     * Create fallback location when database unavailable
     */
    private GeoLocation createFallbackLocation(String ipAddress) {
        return GeoLocation.builder()
            .ipAddress(ipAddress)
            .countryCode("XX") // Unknown
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
     * Get risk assessment for IP address
     */
    public IPRiskAssessment assessIPRisk(String ipAddress) {
        GeoLocation location = geolocateIP(ipAddress);

        double riskScore = 0.0;

        // Factor 1: Country risk (40%)
        riskScore += location.getCountryRiskScore() * 0.4;

        // Factor 2: Proxy/VPN/Tor (30%)
        if (location.getIsTorExitNode()) {
            riskScore += 0.9 * 0.3; // Tor is very high risk
        } else if (location.getIsAnonymousProxy()) {
            riskScore += 0.7 * 0.3; // Anonymous proxy is high risk
        } else if (location.getIsVpn()) {
            riskScore += 0.5 * 0.3; // VPN is moderate risk
        } else {
            riskScore += 0.1 * 0.3; // Direct connection is low risk
        }

        // Factor 3: Hosting provider (20%)
        if (location.getIsHostingProvider()) {
            riskScore += 0.6 * 0.2; // Hosting providers can be risky
        } else {
            riskScore += 0.1 * 0.2;
        }

        // Factor 4: Sanctioned country (10%)
        if (location.getIsSanctionedCountry()) {
            riskScore += 0.9 * 0.1;
        } else {
            riskScore += 0.1 * 0.1;
        }

        return IPRiskAssessment.builder()
            .ipAddress(ipAddress)
            .location(location)
            .riskScore(Math.min(1.0, riskScore))
            .riskLevel(getRiskLevel(riskScore))
            .shouldBlock(location.getIsSanctionedCountry() || location.getIsTorExitNode())
            .requiresReview(riskScore > 0.7)
            .build();
    }

    private String getRiskLevel(double score) {
        if (score < 0.2) return "LOW";
        if (score < 0.4) return "MODERATE_LOW";
        if (score < 0.6) return "MODERATE";
        if (score < 0.8) return "MODERATE_HIGH";
        return "HIGH";
    }

    /**
     * IP Risk Assessment Result
     */
    @lombok.Data
    @lombok.Builder
    public static class IPRiskAssessment {
        private String ipAddress;
        private GeoLocation location;
        private Double riskScore;
        private String riskLevel;
        private Boolean shouldBlock;
        private Boolean requiresReview;
    }
}
