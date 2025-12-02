package com.waqiti.frauddetection.integration;

import com.waqiti.frauddetection.domain.LocationInfo;
import com.waqiti.frauddetection.config.FraudDetectionProperties;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Production MaxMind GeoIP2 Integration Client
 * 
 * Integrates with MaxMind GeoIP2 databases for precise IP geolocation.
 * Supports both GeoLite2 (free) and GeoIP2 (commercial) databases.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaxMindGeoLocationClient {
    
    private final FraudDetectionProperties properties;
    private DatabaseReader cityReader;
    private DatabaseReader asnReader;
    private DatabaseReader countryReader;
    
    @PostConstruct
    public void initialize() {
        try {
            // Initialize MaxMind database readers
            String databasePath = properties.getMaxmind().getDatabasePath();
            
            // City database for detailed location
            File cityDatabase = new File(databasePath + "/GeoLite2-City.mmdb");
            if (cityDatabase.exists()) {
                cityReader = new DatabaseReader.Builder(cityDatabase).build();
                log.info("MaxMind City database loaded: {}", cityDatabase.getAbsolutePath());
            } else {
                log.warn("MaxMind City database not found: {}", cityDatabase.getAbsolutePath());
            }
            
            // ASN database for ISP/organization info
            File asnDatabase = new File(databasePath + "/GeoLite2-ASN.mmdb");
            if (asnDatabase.exists()) {
                asnReader = new DatabaseReader.Builder(asnDatabase).build();
                log.info("MaxMind ASN database loaded: {}", asnDatabase.getAbsolutePath());
            }
            
            // Country database as fallback
            File countryDatabase = new File(databasePath + "/GeoLite2-Country.mmdb");
            if (countryDatabase.exists()) {
                countryReader = new DatabaseReader.Builder(countryDatabase).build();
                log.info("MaxMind Country database loaded: {}", countryDatabase.getAbsolutePath());
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize MaxMind databases: {}", e.getMessage(), e);
            throw new RuntimeException("MaxMind database initialization failed", e);
        }
    }
    
    /**
     * Get comprehensive location information for IP address
     */
    public LocationInfo getLocationInfo(String ipAddress) {
        try {
            if (!StringUtils.hasText(ipAddress)) {
                log.warn("Empty IP address provided to MaxMind client");
                throw new IllegalArgumentException("IP address cannot be null or empty for geolocation lookup");
            }
            
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            LocationInfo.LocationInfoBuilder builder = LocationInfo.builder();
            
            // Get city information (most detailed)
            if (cityReader != null) {
                try {
                    CityResponse cityResponse = cityReader.city(inetAddress);
                    populateFromCityResponse(builder, cityResponse);
                } catch (Exception e) {
                    log.debug("City lookup failed for IP {}, trying country lookup: {}", ipAddress, e.getMessage());
                    
                    // Fallback to country lookup
                    if (countryReader != null) {
                        com.maxmind.geoip2.model.CountryResponse countryResponse = countryReader.country(inetAddress);
                        populateFromCountryResponse(builder, countryResponse);
                    }
                }
            }
            
            // Get ASN information
            if (asnReader != null) {
                try {
                    com.maxmind.geoip2.model.AsnResponse asnResponse = asnReader.asn(inetAddress);
                    populateFromAsnResponse(builder, asnResponse);
                } catch (Exception e) {
                    log.debug("ASN lookup failed for IP {}: {}", ipAddress, e.getMessage());
                }
            }
            
            // Set metadata
            LocationInfo locationInfo = builder
                .timestamp(LocalDateTime.now())
                .ipAddress(maskIpAddress(ipAddress))
                .dataSource("MaxMind")
                .provider("GeoIP2")
                .confidenceLevel(0.8) // MaxMind typically has high confidence
                .build();
            
            // Validate and enrich the location info
            validateAndEnrichLocationInfo(locationInfo);
            
            log.debug("Successfully retrieved location info for IP: {} -> {}", 
                maskIpAddress(ipAddress), locationInfo.getLocationString());
            
            return locationInfo;
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation errors
            throw e;
        } catch (Exception e) {
            log.error("Error getting location info from MaxMind for IP {}: {}", 
                maskIpAddress(ipAddress), e.getMessage(), e);
            
            // Return a fallback location instead of null to maintain fraud detection
            return createFallbackLocationInfo(ipAddress, e.getMessage());
        }
    }
    
    /**
     * Populate LocationInfo from MaxMind City response
     */
    private void populateFromCityResponse(LocationInfo.LocationInfoBuilder builder, CityResponse response) {
        // Country information
        Country country = response.getCountry();
        if (country != null) {
            builder.countryCode(country.getIsoCode());
            builder.countryName(country.getName());
            builder.isEuCountry(country.isInEuropeanUnion());
        }
        
        // Region/State information
        Subdivision subdivision = response.getMostSpecificSubdivision();
        if (subdivision != null) {
            builder.regionCode(subdivision.getIsoCode());
            builder.regionName(subdivision.getName());
        }
        
        // City information
        City city = response.getCity();
        if (city != null) {
            builder.city(city.getName());
        }
        
        // Postal code
        Postal postal = response.getPostal();
        if (postal != null) {
            builder.postalCode(postal.getCode());
        }
        
        // Location coordinates
        Location location = response.getLocation();
        if (location != null) {
            if (location.getLatitude() != null) {
                builder.latitude(location.getLatitude());
            }
            if (location.getLongitude() != null) {
                builder.longitude(location.getLongitude());
            }
            if (location.getAccuracyRadius() != null) {
                builder.accuracyRadius(location.getAccuracyRadius());
            }
        }
    }
    
    /**
     * Populate LocationInfo from MaxMind Country response (fallback)
     */
    private void populateFromCountryResponse(LocationInfo.LocationInfoBuilder builder, 
                                           com.maxmind.geoip2.model.CountryResponse response) {
        Country country = response.getCountry();
        if (country != null) {
            builder.countryCode(country.getIsoCode());
            builder.countryName(country.getName());
            builder.isEuCountry(country.isInEuropeanUnion());
            builder.confidenceLevel(0.6); // Lower confidence for country-only
        }
    }
    
    /**
     * Populate ASN and ISP information
     */
    private void populateFromAsnResponse(LocationInfo.LocationInfoBuilder builder, 
                                       com.maxmind.geoip2.model.AsnResponse response) {
        if (response.getAutonomousSystemNumber() != null) {
            builder.asn(response.getAutonomousSystemNumber().toString());
        }
        
        if (StringUtils.hasText(response.getAutonomousSystemOrganization())) {
            builder.asnOrganization(response.getAutonomousSystemOrganization());
            builder.isp(response.getAutonomousSystemOrganization());
            builder.organization(response.getAutonomousSystemOrganization());
            
            // Detect hosting/datacenter based on organization name
            String orgName = response.getAutonomousSystemOrganization().toLowerCase();
            boolean isHosting = orgName.contains("hosting") || 
                               orgName.contains("datacenter") || 
                               orgName.contains("server") ||
                               orgName.contains("cloud") ||
                               orgName.contains("amazon") ||
                               orgName.contains("google") ||
                               orgName.contains("microsoft");
            builder.isHosting(isHosting);
        }
    }
    
    /**
     * Validate and enrich location information with additional context
     */
    private void validateAndEnrichLocationInfo(LocationInfo locationInfo) {
        // Set high-risk country flag
        if (locationInfo.getCountryCode() != null) {
            boolean isHighRisk = properties.getHighRiskCountries()
                .contains(locationInfo.getCountryCode().toLowerCase());
            locationInfo.setIsHighRiskCountry(isHighRisk);
        }
        
        // Set regulatory zone
        if (Boolean.TRUE.equals(locationInfo.getIsEuCountry())) {
            locationInfo.setRegulatoryZone("EU");
        } else if ("US".equals(locationInfo.getCountryCode())) {
            locationInfo.setRegulatoryZone("US");
        } else {
            locationInfo.setRegulatoryZone("OTHER");
        }
        
        // Validate coordinates
        if (locationInfo.getLatitude() != null && locationInfo.getLongitude() != null) {
            if (Math.abs(locationInfo.getLatitude()) > 90 || Math.abs(locationInfo.getLongitude()) > 180) {
                log.warn("Invalid coordinates detected: lat={}, lon={}", 
                    locationInfo.getLatitude(), locationInfo.getLongitude());
                locationInfo.setLatitude(null);
                locationInfo.setLongitude(null);
                locationInfo.setConfidenceLevel(locationInfo.getConfidenceLevel() * 0.5);
            }
        }
        
        // Set quality score based on available data
        int qualityScore = 0;
        if (StringUtils.hasText(locationInfo.getCountryCode())) qualityScore += 20;
        if (StringUtils.hasText(locationInfo.getCity())) qualityScore += 30;
        if (locationInfo.getLatitude() != null) qualityScore += 25;
        if (StringUtils.hasText(locationInfo.getAsn())) qualityScore += 15;
        if (locationInfo.getAccuracyRadius() != null && locationInfo.getAccuracyRadius() < 50) qualityScore += 10;
        
        locationInfo.setQualityScore(qualityScore);
    }
    
    /**
     * Mask IP address for privacy in logs
     */
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.length() < 8) {
            return "***";
        }
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.**";
        }
        return ipAddress.substring(0, Math.min(8, ipAddress.length())) + "***";
    }
    
    /**
     * Check if MaxMind databases are properly loaded
     */
    public boolean isReady() {
        return cityReader != null || countryReader != null;
    }
    
    /**
     * Get database information for monitoring
     */
    public List<String> getDatabaseInfo() {
        List<String> info = new java.util.ArrayList<>();
        
        if (cityReader != null) {
            info.add("City database loaded");
        }
        if (asnReader != null) {
            info.add("ASN database loaded");
        }
        if (countryReader != null) {
            info.add("Country database loaded");
        }
        
        if (info.isEmpty()) {
            info.add("No databases loaded");
        }
        
        return info;
    }
    
    /**
     * Create fallback location info when MaxMind lookup fails
     */
    private LocationInfo createFallbackLocationInfo(String ipAddress, String errorReason) {
        return LocationInfo.builder()
            .ipAddress(maskIpAddress(ipAddress))
            .country("UNKNOWN")
            .countryCode("XX")
            .city("UNKNOWN")
            .region("UNKNOWN")
            .latitude(0.0)
            .longitude(0.0)
            .accuracy(0)
            .organization("UNKNOWN")
            .asn("0")
            .asnOrganization("UNKNOWN")
            .isp("UNKNOWN")
            .timeZone("UTC")
            .postalCode("00000")
            .metroCode(0)
            .connectionType("UNKNOWN")
            .userType("UNKNOWN")
            .isAnonymousProxy(true) // Assume proxy for security
            .isSatelliteProvider(false)
            .isLegitimateProxy(false)
            .isHosting(true) // Assume hosting for security
            .isTorExitNode(false)
            .isHighRiskCountry(true) // Assume high risk when unknown
            .isEuCountry(false)
            .accuracyRadius(50000) // Very inaccurate
            .isValid(true)
            .dataSource("MAXMIND_FALLBACK")
            .provider("MaxMind-Error-Fallback")
            .confidence(0.1) // Very low confidence
            .regulatoryZone("UNKNOWN")
            .metadata(Map.of(
                "fallback", true,
                "error", errorReason,
                "maxmind_failure", true,
                "security_mode", "HIGH_RISK_FALLBACK"
            ))
            .build();
    }
}