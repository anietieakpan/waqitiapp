package com.waqiti.user.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.dto.security.LocationData;
import com.waqiti.user.dto.security.LocationValidationResult;
import com.waqiti.user.dto.security.LocationAnalysisResult;
import com.waqiti.user.dto.security.UserLocationEntry;
import com.waqiti.user.dto.security.TravelValidationResult;
import com.waqiti.user.dto.security.LocationPatternAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Location Validation Service
 * 
 * Provides geolocation-based security validation:
 * - IP geolocation tracking
 * - Suspicious location detection
 * - Travel pattern analysis
 * - Country/region restriction enforcement
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationValidationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String USER_LOCATION_PREFIX = "user:location:";
    private static final String IP_LOCATION_CACHE_PREFIX = "ip:location:";
    private static final int LOCATION_CACHE_HOURS = 24;
    private static final double SUSPICIOUS_DISTANCE_KM = 1000; // 1000km
    private static final int SUSPICIOUS_TIME_MINUTES = 60; // 1 hour
    
    // Restricted countries (example list)
    private static final Set<String> RESTRICTED_COUNTRIES = Set.of(
        "IR", "KP", "SY" // ISO country codes
    );

    /**
     * Validates IP location against user's previous locations
     */
    public LocationValidationResult validateIpLocation(String previousIp, String currentIp) {
        try {
            if (previousIp == null || currentIp == null) {
                return LocationValidationResult.builder()
                    .valid(true)
                    .reason("Insufficient location data")
                    .build();
            }
            
            // Skip validation if IPs are the same
            if (previousIp.equals(currentIp)) {
                return LocationValidationResult.builder()
                    .valid(true)
                    .reason("Same IP address")
                    .build();
            }
            
            // Get location data for both IPs
            LocationData previousLocation = getIpLocation(previousIp);
            LocationData currentLocation = getIpLocation(currentIp);
            
            if (previousLocation == null || currentLocation == null) {
                return LocationValidationResult.builder()
                    .valid(true)
                    .reason("Unable to determine location")
                    .suspicious(false)
                    .build();
            }
            
            // Check for restricted countries
            if (RESTRICTED_COUNTRIES.contains(currentLocation.getCountryCode())) {
                return LocationValidationResult.builder()
                    .valid(false)
                    .reason("Access from restricted country: " + currentLocation.getCountryCode())
                    .suspicious(true)
                    .restrictedCountry(true)
                    .build();
            }
            
            // Calculate distance and time-based risk
            double distance = calculateDistance(previousLocation, currentLocation);
            boolean isSuspicious = distance > SUSPICIOUS_DISTANCE_KM;
            
            return LocationValidationResult.builder()
                .valid(true)
                .reason("Location validation completed")
                .suspicious(isSuspicious)
                .distance(distance)
                .previousLocation(previousLocation)
                .currentLocation(currentLocation)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to validate IP location", e);
            return LocationValidationResult.builder()
                .valid(true) // Fail open for availability
                .reason("Location validation error")
                .build();
        }
    }

    /**
     * Analyzes user location patterns for anomalies
     */
    public LocationAnalysisResult analyzeUserLocationPattern(UUID userId, String currentIp) {
        try {
            String userLocationKey = USER_LOCATION_PREFIX + userId.toString();
            
            // Get current location
            LocationData currentLocation = getIpLocation(currentIp);
            if (currentLocation == null) {
                return LocationAnalysisResult.builder()
                    .anomalyDetected(false)
                    .reason("Unable to determine current location")
                    .build();
            }
            
            // Get user's location history
            List<UserLocationEntry> locationHistory = getUserLocationHistory(userId);
            
            // Store current location
            storeUserLocation(userId, currentLocation, currentIp);
            
            if (locationHistory.isEmpty()) {
                return LocationAnalysisResult.builder()
                    .anomalyDetected(false)
                    .reason("First location entry for user")
                    .currentLocation(currentLocation)
                    .build();
            }
            
            // Analyze patterns
            LocationPatternAnalysis analysis = analyzeLocationPatterns(locationHistory, currentLocation);
            
            return LocationAnalysisResult.builder()
                .anomalyDetected(analysis.isAnomalous())
                .reason(analysis.getReason())
                .currentLocation(currentLocation)
                .locationHistory(locationHistory)
                .riskScore(analysis.getRiskScore())
                .patterns(analysis.getPatterns())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to analyze user location pattern for user {}", userId, e);
            return LocationAnalysisResult.builder()
                .anomalyDetected(false)
                .reason("Location analysis error")
                .build();
        }
    }

    /**
     * Gets geographical location data for an IP address
     */
    public LocationData getIpLocation(String ipAddress) {
        try {
            // Check cache first
            String cacheKey = IP_LOCATION_CACHE_PREFIX + ipAddress;
            LocationData cached = (LocationData) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
            
            // Call geolocation API (using a mock implementation here)
            LocationData location = queryGeolocationApi(ipAddress);
            
            if (location != null) {
                // Cache the result
                redisTemplate.opsForValue().set(cacheKey, location, LOCATION_CACHE_HOURS, TimeUnit.HOURS);
            }
            
            return location;
            
        } catch (Exception e) {
            log.error("Failed to get IP location for {}", ipAddress, e);
            return null;
        }
    }

    /**
     * Checks if an IP address is from a high-risk location
     */
    public boolean isHighRiskLocation(String ipAddress) {
        try {
            LocationData location = getIpLocation(ipAddress);
            if (location == null) {
                return false;
            }
            
            // Check restricted countries
            if (RESTRICTED_COUNTRIES.contains(location.getCountryCode())) {
                return true;
            }
            
            // Check other risk factors
            return isHighRiskCountry(location.getCountryCode()) ||
                   isKnownVpnOrProxy(location) ||
                   isHighFraudLocation(location);
            
        } catch (Exception e) {
            log.error("Failed to check high-risk location for IP {}", ipAddress, e);
            return false;
        }
    }

    /**
     * Validates impossible travel scenarios
     */
    public TravelValidationResult validateTravel(UserLocationEntry previousLocation, 
                                                LocationData currentLocation, 
                                                LocalDateTime currentTime) {
        try {
            if (previousLocation == null || currentLocation == null) {
                return TravelValidationResult.possible("Insufficient location data");
            }
            
            double distance = calculateDistance(previousLocation.getLocation(), currentLocation);
            long timeDifferenceMinutes = java.time.Duration.between(
                previousLocation.getTimestamp(), currentTime).toMinutes();
            
            // Calculate maximum possible speed (km/h)
            double maxPossibleSpeed = (distance / timeDifferenceMinutes) * 60;
            
            // Commercial aircraft maximum speed is roughly 900-1000 km/h
            // We'll use 1200 km/h as upper limit to account for time zones and delays
            boolean isPossible = maxPossibleSpeed <= 1200;
            
            if (!isPossible) {
                return TravelValidationResult.impossible(
                    String.format("Impossible travel: %.2f km in %d minutes (%.2f km/h)", 
                                distance, timeDifferenceMinutes, maxPossibleSpeed),
                    distance, timeDifferenceMinutes, maxPossibleSpeed);
            }
            
            // Check for suspicious travel (very fast but possible)
            boolean isSuspicious = maxPossibleSpeed > 500; // Faster than typical ground transport
            
            String reason = isSuspicious ? "Very fast travel detected" : "Travel is feasible";
            return TravelValidationResult.possible(reason);
            
        } catch (Exception e) {
            log.error("Failed to validate travel", e);
            return TravelValidationResult.possible("Travel validation error");
        }
    }

    // Private helper methods

    private LocationData queryGeolocationApi(String ipAddress) {
        try {
            // Mock implementation - in production, integrate with services like:
            // - MaxMind GeoIP2
            // - IP2Location
            // - ipapi.co
            // - ipgeolocation.io
            
            // For demo purposes, return mock data based on IP patterns
            if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
                // Private IP - assume local
                return LocationData.builder()
                    .ipAddress(ipAddress)
                    .countryCode("US")
                    .countryName("United States")
                    .city("Local Network")
                    .latitude(37.7749)
                    .longitude(-122.4194)
                    .timezone("America/Los_Angeles")
                    .vpnDetected(false)
                    .proxyDetected(false)
                    .build();
            }
            
            // For demo - return different locations based on IP hash
            int hash = Math.abs(ipAddress.hashCode());
            String[] countries = {"US", "CA", "GB", "DE", "FR", "JP", "AU"};
            String[] cities = {"New York", "Toronto", "London", "Berlin", "Paris", "Tokyo", "Sydney"};
            double[] latitudes = {40.7128, 43.6532, 51.5074, 52.5200, 48.8566, 35.6762, -33.8688};
            double[] longitudes = {-74.0060, -79.3832, -0.1278, 13.4050, 2.3522, 139.6503, 151.2093};
            
            int index = hash % countries.length;
            
            return LocationData.builder()
                .ipAddress(ipAddress)
                .countryCode(countries[index])
                .countryName(countries[index])
                .city(cities[index])
                .latitude(latitudes[index])
                .longitude(longitudes[index])
                .timezone("UTC")
                .vpnDetected(false)
                .proxyDetected(false)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to query geolocation API for IP {}", ipAddress, e);
            return null;
        }
    }

    private List<UserLocationEntry> getUserLocationHistory(UUID userId) {
        try {
            String userLocationKey = USER_LOCATION_PREFIX + userId.toString();
            List<Object> historyObjects = redisTemplate.opsForList().range(userLocationKey, 0, 50); // Last 50 locations
            
            if (historyObjects == null) {
                return Collections.emptyList();
            }
            
            List<UserLocationEntry> history = new ArrayList<>();
            for (Object obj : historyObjects) {
                if (obj instanceof UserLocationEntry) {
                    history.add((UserLocationEntry) obj);
                }
            }
            
            return history;
            
        } catch (Exception e) {
            log.error("Failed to get user location history for {}", userId, e);
            return Collections.emptyList();
        }
    }

    private void storeUserLocation(UUID userId, LocationData location, String ipAddress) {
        try {
            String userLocationKey = USER_LOCATION_PREFIX + userId.toString();
            
            UserLocationEntry entry = UserLocationEntry.builder()
                .locationData(location)
                .ipAddress(ipAddress)
                .timestamp(LocalDateTime.now())
                .entryId(UUID.randomUUID().toString())
                .userId(userId.toString())
                .entrySource("API_CALL")
                .detectionMethod("IP_GEOLOCATION")
                .validated(true)
                .build();
            
            // Add to beginning of list and keep only last 100 entries
            redisTemplate.opsForList().leftPush(userLocationKey, entry);
            redisTemplate.opsForList().trim(userLocationKey, 0, 99);
            redisTemplate.expire(userLocationKey, 90, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Failed to store user location for {}", userId, e);
        }
    }

    private LocationPatternAnalysis analyzeLocationPatterns(List<UserLocationEntry> history, LocationData currentLocation) {
        try {
            boolean isAnomalous = false;
            String reason = "Normal location pattern";
            double riskScore = 0.0;
            List<String> patterns = new ArrayList<>();
            
            // Check for impossible travel
            if (!history.isEmpty()) {
                UserLocationEntry lastLocation = history.get(0);
                TravelValidationResult travelResult = validateTravel(lastLocation, currentLocation, LocalDateTime.now());
                
                if (!travelResult.isPossible()) {
                    isAnomalous = true;
                    reason = "Impossible travel detected";
                    riskScore += 0.8;
                    patterns.add("IMPOSSIBLE_TRAVEL");
                } else if (travelResult.isSuspicious()) {
                    riskScore += 0.3;
                    patterns.add("FAST_TRAVEL");
                }
            }
            
            // Check for new country
            boolean isNewCountry = history.stream()
                .noneMatch(entry -> entry.getLocation().getCountryCode().equals(currentLocation.getCountryCode()));
            
            if (isNewCountry) {
                riskScore += 0.2;
                patterns.add("NEW_COUNTRY");
            }
            
            // Check for VPN/Proxy
            if (currentLocation.isVpn() || currentLocation.isProxy()) {
                riskScore += 0.4;
                patterns.add("VPN_OR_PROXY");
            }
            
            // Check location frequency (multiple recent logins from same location)
            long recentSameLocationCount = history.stream()
                .filter(entry -> entry.getTimestamp().isAfter(LocalDateTime.now().minusHours(24)))
                .filter(entry -> entry.getLocation().getCountryCode().equals(currentLocation.getCountryCode()))
                .count();
            
            if (recentSameLocationCount > 10) {
                patterns.add("HIGH_FREQUENCY_SAME_LOCATION");
            }
            
            return LocationPatternAnalysis.builder()
                .analysisId(UUID.randomUUID().toString())
                .analysisDate(LocalDateTime.now())
                .riskAssessment(LocationPatternAnalysis.RiskAssessment.builder()
                    .overallRiskScore(Math.min(riskScore, 1.0))
                    .riskLevel(isAnomalous ? "HIGH" : "LOW")
                    .riskFactors(patterns)
                    .assessedAt(LocalDateTime.now())
                    .build())
                .anomalies(LocationPatternAnalysis.PatternAnomalies.builder()
                    .anomalyCount(isAnomalous ? 1 : 0)
                    .anomalyRate(isAnomalous ? 1.0 : 0.0)
                    .build())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to analyze location patterns", e);
            return LocationPatternAnalysis.builder()
                .analysisId(UUID.randomUUID().toString())
                .analysisDate(LocalDateTime.now())
                .riskAssessment(LocationPatternAnalysis.RiskAssessment.builder()
                    .overallRiskScore(0.0)
                    .riskLevel("LOW")
                    .riskFactors(Collections.emptyList())
                    .assessedAt(LocalDateTime.now())
                    .build())
                .build();
        }
    }

    private double calculateDistance(LocationData loc1, LocationData loc2) {
        // Haversine formula for calculating distance between two points on Earth
        double lat1Rad = Math.toRadians(loc1.getLatitude());
        double lat2Rad = Math.toRadians(loc2.getLatitude());
        double deltaLatRad = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double deltaLonRad = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        
        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return 6371 * c; // Earth's radius in kilometers
    }

    private boolean isHighRiskCountry(String countryCode) {
        // List of countries with higher fraud risk (example)
        Set<String> highRiskCountries = Set.of("NG", "PK", "BD", "ID", "PH");
        return highRiskCountries.contains(countryCode);
    }

    private boolean isKnownVpnOrProxy(LocationData location) {
        return location.isVpn() || location.isProxy();
    }

    private boolean isHighFraudLocation(LocationData location) {
        // Additional fraud location checks could be implemented here
        return false;
    }

    // Data classes (simplified - would use Lombok @Data/@Builder in practice)
    // LocationData, UserLocationEntry, LocationValidationResult, etc.
}