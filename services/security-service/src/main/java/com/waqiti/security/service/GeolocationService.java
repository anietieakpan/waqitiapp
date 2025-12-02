package com.waqiti.security.service;

import com.waqiti.security.model.GeoLocationData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Geolocation Service
 * Provides IP-based geolocation lookup with caching
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeolocationService {

    private final RestTemplate restTemplate;

    // Using ip-api.com free service (no auth required, 45 req/min limit)
    private static final String GEO_API_URL = "http://ip-api.com/json/";

    /**
     * Lookup geolocation data for IP address
     * Results are cached to reduce API calls
     */
    @Cacheable(value = "geolocation", key = "#ipAddress")
    public GeoLocationData lookupLocation(String ipAddress) {
        try {
            if (ipAddress == null || ipAddress.isEmpty()) {
                return createUnknownLocation("No IP address provided");
            }

            // Skip localhost and private IPs
            if (isPrivateOrLocalIP(ipAddress)) {
                return createUnknownLocation("Private or local IP");
            }

            log.debug("Looking up geolocation for IP: {}", ipAddress);

            // Call geolocation API
            String url = GEO_API_URL + ipAddress + "?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                log.warn("Empty response from geolocation API for IP: {}", ipAddress);
                return createUnknownLocation("Empty API response");
            }

            String status = (String) response.get("status");
            if (!"success".equals(status)) {
                String message = (String) response.get("message");
                log.warn("Geolocation API returned error for IP {}: {}", ipAddress, message);
                return createUnknownLocation(message != null ? message : "API error");
            }

            // Parse response
            return GeoLocationData.builder()
                .ipAddress(ipAddress)
                .country((String) response.get("country"))
                .countryCode((String) response.get("countryCode"))
                .region((String) response.get("regionName"))
                .regionCode((String) response.get("region"))
                .city((String) response.get("city"))
                .zipCode((String) response.get("zip"))
                .latitude(parseDouble(response.get("lat")))
                .longitude(parseDouble(response.get("lon")))
                .timezone((String) response.get("timezone"))
                .isp((String) response.get("isp"))
                .organization((String) response.get("org"))
                .asn((String) response.get("as"))
                .success(true)
                .errorMessage(null)
                .build();

        } catch (Exception e) {
            log.error("Error looking up geolocation for IP {}: {}", ipAddress, e.getMessage(), e);
            return createUnknownLocation("Lookup error: " + e.getMessage());
        }
    }

    /**
     * Check if IP is private or local
     */
    private boolean isPrivateOrLocalIP(String ipAddress) {
        if (ipAddress == null) return true;

        // Localhost
        if (ipAddress.equals("127.0.0.1") || ipAddress.equals("::1") || ipAddress.equals("localhost")) {
            return true;
        }

        // Private IPv4 ranges
        String[] octets = ipAddress.split("\\.");
        if (octets.length == 4) {
            try {
                int first = Integer.parseInt(octets[0]);
                int second = Integer.parseInt(octets[1]);

                // 10.0.0.0/8
                if (first == 10) return true;

                // 172.16.0.0/12
                if (first == 172 && second >= 16 && second <= 31) return true;

                // 192.168.0.0/16
                if (first == 192 && second == 168) return true;

                // Link-local 169.254.0.0/16
                if (first == 169 && second == 254) return true;

            } catch (NumberFormatException e) {
                // Invalid IP format
                return true;
            }
        }

        // Private IPv6
        if (ipAddress.startsWith("fc00:") || ipAddress.startsWith("fd00:") ||
            ipAddress.startsWith("fe80:")) {
            return true;
        }

        return false;
    }

    /**
     * Create unknown location object
     */
    private GeoLocationData createUnknownLocation(String errorMessage) {
        return GeoLocationData.builder()
            .ipAddress(null)
            .country("Unknown")
            .countryCode("XX")
            .region("Unknown")
            .regionCode(null)
            .city("Unknown")
            .zipCode(null)
            .latitude(null)
            .longitude(null)
            .timezone(null)
            .isp(null)
            .organization(null)
            .asn(null)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Parse double from object
     */
    private Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Calculate distance between two geographic points (Haversine formula)
     * Returns distance in meters
     */
    public double calculateDistance(
        double lat1, double lon1,
        double lat2, double lon2
    ) {
        final int EARTH_RADIUS = 6371000; // meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Check if location is high-risk based on historical data
     */
    public boolean isHighRiskLocation(String countryCode) {
        // List of high-risk country codes based on common fraud patterns
        // This should be configurable and updated based on threat intelligence
        Set<String> highRiskCountries = Set.of(
            "XX", // Unknown
            // Add specific country codes based on your risk assessment
            // This is a placeholder - actual risk assessment should be data-driven
        );

        return highRiskCountries.contains(countryCode);
    }

    /**
     * Calculate risk score for location (0-100)
     */
    public int calculateLocationRiskScore(GeoLocationData location) {
        if (location == null || !location.isSuccess()) {
            return 50; // Medium risk for unknown locations
        }

        int riskScore = 0;

        // Unknown location
        if ("Unknown".equals(location.getCountry())) {
            riskScore += 30;
        }

        // High-risk country
        if (isHighRiskLocation(location.getCountryCode())) {
            riskScore += 40;
        }

        // VPN/Proxy indicators (ISP contains certain keywords)
        if (location.getIsp() != null) {
            String ispLower = location.getIsp().toLowerCase();
            if (ispLower.contains("vpn") || ispLower.contains("proxy") ||
                ispLower.contains("hosting") || ispLower.contains("datacenter")) {
                riskScore += 20;
            }
        }

        // Tor exit nodes (organization contains "tor")
        if (location.getOrganization() != null &&
            location.getOrganization().toLowerCase().contains("tor")) {
            riskScore += 50;
        }

        return Math.min(riskScore, 100);
    }
}
