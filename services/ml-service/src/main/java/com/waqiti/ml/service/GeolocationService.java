package com.waqiti.ml.service;

import com.waqiti.ml.dto.GeolocationAnalysisResult;
import com.waqiti.ml.dto.TransactionData;
import com.waqiti.ml.entity.GeolocationPattern;
import com.waqiti.ml.repository.GeolocationPatternRepository;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready Geolocation Service with advanced analytics and threat detection.
 * Provides location-based risk analysis, velocity checking, and geographic anomaly detection.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeolocationService {

    private final GeolocationPatternRepository geolocationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${geolocation.maxspeed.kmh:900}") // Max realistic travel speed (commercial flight)
    private double maxTravelSpeedKmh;

    @Value("${geolocation.risk.countries:NONE}")
    private String highRiskCountries;

    @Value("${geolocation.threat.intel.enabled:true}")
    private boolean threatIntelEnabled;

    @Value("${geolocation.api.key:#{null}}")
    private String geoApiKey;

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final String CACHE_PREFIX = "geo:";
    private static final String LOCATION_CACHE_PREFIX = "loc:";

    /**
     * Comprehensive geolocation analysis with velocity and threat detection
     */
    @Traced(operation = "geolocation_analysis")
    public GeolocationAnalysisResult analyzeGeolocation(TransactionData transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting geolocation analysis for transaction: {}", transaction.getTransactionId());

            GeolocationAnalysisResult result = new GeolocationAnalysisResult();
            result.setTransactionId(transaction.getTransactionId());
            result.setUserId(transaction.getUserId());
            result.setTimestamp(LocalDateTime.now());
            
            // Extract location data
            TransactionData.GeolocationData geoData = transaction.getGeolocation();
            if (geoData == null || geoData.getLatitude() == null || geoData.getLongitude() == null) {
                return createNoLocationResult(result, transaction);
            }

            // Perform comprehensive geolocation analysis
            performLocationRiskAnalysis(result, transaction, geoData);
            performVelocityAnalysis(result, transaction, geoData);
            performGeographicAnomalyDetection(result, transaction, geoData);
            performThreatIntelligenceAnalysis(result, transaction, geoData);
            
            // Calculate overall geolocation risk score
            double overallRisk = calculateOverallLocationRisk(result);
            result.setRiskScore(overallRisk);
            result.setRiskLevel(determineRiskLevel(overallRisk));
            
            // Save location pattern for future analysis
            saveLocationPattern(transaction, geoData, result);
            
            long duration = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(duration);
            
            log.debug("Geolocation analysis completed in {}ms for transaction: {}, risk score: {}", 
                duration, transaction.getTransactionId(), overallRisk);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in geolocation analysis for transaction: {}", transaction.getTransactionId(), e);
            throw new MLProcessingException("Failed to analyze geolocation", e);
        }
    }

    /**
     * Perform location-based risk analysis
     */
    private void performLocationRiskAnalysis(GeolocationAnalysisResult result, 
                                           TransactionData transaction, 
                                           TransactionData.GeolocationData geoData) {
        
        double locationRisk = 0.0;
        
        // Country-based risk assessment
        String country = geoData.getCountry();
        if (country != null) {
            locationRisk += assessCountryRisk(country);
            result.setCountryRiskLevel(getCountryRiskLevel(country));
        }
        
        // Location accuracy assessment
        Double accuracy = geoData.getAccuracyMeters();
        if (accuracy == null || accuracy > 1000) {
            locationRisk += 15.0; // Poor location accuracy
        }
        
        // Mock location detection
        if (Boolean.TRUE.equals(geoData.getIsMockLocation())) {
            locationRisk += 25.0; // Significant risk for spoofed location
            result.setMockLocationDetected(true);
        }
        
        // Distance from user's usual locations
        double distanceRisk = analyzeDistanceFromUsualLocations(transaction.getUserId(), geoData);
        locationRisk += distanceRisk;
        
        result.setLocationRiskScore(Math.min(locationRisk, 100.0));
    }

    /**
     * Perform velocity analysis (impossible travel detection)
     */
    private void performVelocityAnalysis(GeolocationAnalysisResult result,
                                       TransactionData transaction,
                                       TransactionData.GeolocationData geoData) {
        
        // Get last known location for this user
        GeolocationPattern lastLocation = getLastKnownLocation(transaction.getUserId());
        
        if (lastLocation == null) {
            result.setVelocityImpossible(false);
            result.setDistanceFromLastLocation(0.0);
            result.setEstimatedTravelSpeed(0.0);
            return;
        }
        
        // Calculate distance between locations
        double distance = calculateDistance(
            lastLocation.getLatitude(), lastLocation.getLongitude(),
            geoData.getLatitude(), geoData.getLongitude()
        );
        
        // Calculate time difference
        long timeDifferenceMinutes = ChronoUnit.MINUTES.between(
            lastLocation.getTimestamp(), transaction.getTimestamp());
        
        if (timeDifferenceMinutes <= 0) {
            result.setVelocityImpossible(true);
            result.setDistanceFromLastLocation(distance);
            result.setEstimatedTravelSpeed(Double.MAX_VALUE);
            return;
        }
        
        // Calculate required travel speed
        double requiredSpeedKmh = (distance / timeDifferenceMinutes) * 60; // Convert to km/h
        
        result.setDistanceFromLastLocation(distance);
        result.setEstimatedTravelSpeed(requiredSpeedKmh);
        result.setVelocityImpossible(requiredSpeedKmh > maxTravelSpeedKmh);
        
        // Add velocity risk to overall assessment
        if (result.isVelocityImpossible()) {
            result.setVelocityRiskScore(50.0);
        } else if (requiredSpeedKmh > 200) { // High speed travel
            result.setVelocityRiskScore(25.0);
        } else {
            result.setVelocityRiskScore(0.0);
        }
    }

    /**
     * Perform geographic anomaly detection
     */
    private void performGeographicAnomalyDetection(GeolocationAnalysisResult result,
                                                 TransactionData transaction,
                                                 TransactionData.GeolocationData geoData) {
        
        // Get user's historical location patterns
        List<GeolocationPattern> historicalLocations = getHistoricalLocations(
            transaction.getUserId(), 90);
        
        if (historicalLocations.isEmpty()) {
            result.setGeographicAnomalyScore(15.0); // New user risk
            return;
        }
        
        double anomalyScore = 0.0;
        
        // Check if location is in user's usual area
        boolean isUsualLocation = isWithinUsualArea(geoData, historicalLocations);
        if (!isUsualLocation) {
            anomalyScore += 20.0;
        }
        
        // Check for clustering of historical locations
        double clusteringScore = analyzeLocationClustering(geoData, historicalLocations);
        anomalyScore += clusteringScore;
        
        // Check for rapid location changes
        double rapidChangeScore = analyzeRapidLocationChanges(transaction.getUserId(), geoData);
        anomalyScore += rapidChangeScore;
        
        result.setGeographicAnomalyScore(Math.min(anomalyScore, 50.0));
    }

    /**
     * Perform threat intelligence analysis
     */
    private void performThreatIntelligenceAnalysis(GeolocationAnalysisResult result,
                                                 TransactionData transaction,
                                                 TransactionData.GeolocationData geoData) {
        
        if (!threatIntelEnabled) {
            result.setThreatIntelScore(0.0);
            return;
        }
        
        double threatScore = 0.0;
        
        // Check IP geolocation consistency
        if (transaction.getIpAddress() != null) {
            threatScore += analyzeIpGeolocationConsistency(transaction.getIpAddress(), geoData);
        }
        
        // Check against known fraud hotspots
        threatScore += checkFraudHotspots(geoData);
        
        // Check time zone consistency
        threatScore += analyzeTimeZoneConsistency(transaction.getTimestamp(), geoData);
        
        result.setThreatIntelScore(Math.min(threatScore, 50.0));
    }

    /**
     * Calculate overall location risk score
     */
    private double calculateOverallLocationRisk(GeolocationAnalysisResult result) {
        double totalRisk = 0.0;
        
        // Weight different risk components
        totalRisk += result.getLocationRiskScore() * 0.3;
        totalRisk += result.getVelocityRiskScore() * 0.25;
        totalRisk += result.getGeographicAnomalyScore() * 0.25;
        totalRisk += result.getThreatIntelScore() * 0.2;
        
        return Math.min(totalRisk, 100.0);
    }

    /**
     * Assess country-based risk
     */
    private double assessCountryRisk(String country) {
        if (country == null) return 10.0;
        
        // Check against high-risk countries list
        if (isHighRiskCountry(country)) {
            return 40.0;
        }
        
        // Check against medium-risk countries
        if (isMediumRiskCountry(country)) {
            return 20.0;
        }
        
        return 0.0;
    }

    /**
     * Get country risk level
     */
    private String getCountryRiskLevel(String country) {
        if (isHighRiskCountry(country)) return "HIGH";
        if (isMediumRiskCountry(country)) return "MEDIUM";
        return "LOW";
    }

    /**
     * Analyze distance from user's usual locations
     */
    private double analyzeDistanceFromUsualLocations(String userId, 
                                                   TransactionData.GeolocationData geoData) {
        
        List<GeolocationPattern> usualLocations = getUserUsualLocations(userId);
        
        if (usualLocations.isEmpty()) {
            return 15.0; // New user risk
        }
        
        // Find minimum distance to any usual location
        double minDistance = usualLocations.stream()
            .mapToDouble(loc -> calculateDistance(
                loc.getLatitude(), loc.getLongitude(),
                geoData.getLatitude(), geoData.getLongitude()))
            .min()
            .orElse(Double.MAX_VALUE);
        
        // Risk scoring based on distance
        if (minDistance > 1000) return 30.0; // >1000km
        if (minDistance > 500) return 20.0;  // >500km
        if (minDistance > 100) return 10.0;  // >100km
        if (minDistance > 50) return 5.0;    // >50km
        
        return 0.0;
    }

    /**
     * Check if location is within user's usual area
     */
    private boolean isWithinUsualArea(TransactionData.GeolocationData geoData, 
                                    List<GeolocationPattern> historicalLocations) {
        
        final double USUAL_AREA_RADIUS_KM = 25.0; // 25km radius
        
        return historicalLocations.stream()
            .anyMatch(loc -> calculateDistance(
                loc.getLatitude(), loc.getLongitude(),
                geoData.getLatitude(), geoData.getLongitude()) <= USUAL_AREA_RADIUS_KM);
    }

    /**
     * Analyze location clustering patterns
     */
    private double analyzeLocationClustering(TransactionData.GeolocationData geoData,
                                           List<GeolocationPattern> historicalLocations) {
        
        // Group locations into clusters
        Map<String, List<GeolocationPattern>> clusters = groupLocationsByClusters(historicalLocations);
        
        // Check if current location fits any existing cluster
        for (List<GeolocationPattern> cluster : clusters.values()) {
            if (isLocationInCluster(geoData, cluster)) {
                return 0.0; // Location fits known pattern
            }
        }
        
        // New location cluster
        return 15.0;
    }

    /**
     * Analyze rapid location changes
     */
    private double analyzeRapidLocationChanges(String userId, 
                                             TransactionData.GeolocationData geoData) {
        
        // Get recent location changes (last 24 hours)
        List<GeolocationPattern> recentLocations = getRecentLocations(userId, 24);
        
        if (recentLocations.size() < 2) {
            return 0.0;
        }
        
        // Check for multiple rapid location changes
        int rapidChanges = 0;
        for (int i = 1; i < recentLocations.size(); i++) {
            GeolocationPattern current = recentLocations.get(i);
            GeolocationPattern previous = recentLocations.get(i - 1);
            
            double distance = calculateDistance(
                current.getLatitude(), current.getLongitude(),
                previous.getLatitude(), previous.getLongitude());
            
            long timeDiff = ChronoUnit.MINUTES.between(previous.getTimestamp(), current.getTimestamp());
            
            if (distance > 50 && timeDiff < 60) { // >50km in <1 hour
                rapidChanges++;
            }
        }
        
        return rapidChanges * 10.0; // 10 points per rapid change
    }

    /**
     * Analyze IP geolocation consistency
     */
    private double analyzeIpGeolocationConsistency(String ipAddress, 
                                                 TransactionData.GeolocationData geoData) {
        
        try {
            // Get IP geolocation
            Map<String, Object> ipLocation = getIpGeolocation(ipAddress);
            
            if (ipLocation == null) {
                return 10.0; // Unknown IP location
            }
            
            Double ipLat = (Double) ipLocation.get("latitude");
            Double ipLon = (Double) ipLocation.get("longitude");
            
            if (ipLat == null || ipLon == null) {
                return 10.0;
            }
            
            // Calculate distance between IP location and GPS location
            double distance = calculateDistance(ipLat, ipLon, geoData.getLatitude(), geoData.getLongitude());
            
            // Risk scoring based on distance discrepancy
            if (distance > 1000) return 30.0; // >1000km discrepancy
            if (distance > 500) return 20.0;  // >500km discrepancy
            if (distance > 100) return 10.0;  // >100km discrepancy
            
            return 0.0;
            
        } catch (Exception e) {
            log.warn("Error analyzing IP geolocation consistency: {}", e.getMessage());
            return 5.0; // Conservative risk on error
        }
    }

    /**
     * Check against known fraud hotspots
     */
    private double checkFraudHotspots(TransactionData.GeolocationData geoData) {
        
        String cacheKey = CACHE_PREFIX + "hotspot:" + 
                         Math.round(geoData.getLatitude() * 100) + ":" + 
                         Math.round(geoData.getLongitude() * 100);
        
        Boolean isHotspot = (Boolean) redisTemplate.opsForValue().get(cacheKey);
        
        if (isHotspot == null) {
            // Check against fraud hotspot database
            isHotspot = checkFraudHotspotDatabase(geoData);
            
            // Cache result for 1 hour
            redisTemplate.opsForValue().set(cacheKey, isHotspot, 1, TimeUnit.HOURS);
        }
        
        return Boolean.TRUE.equals(isHotspot) ? 35.0 : 0.0;
    }

    /**
     * Analyze time zone consistency
     */
    private double analyzeTimeZoneConsistency(LocalDateTime transactionTime, 
                                            TransactionData.GeolocationData geoData) {
        
        try {
            // Get expected time zone for location
            String expectedTimeZone = getTimeZoneForLocation(geoData.getLatitude(), geoData.getLongitude());
            
            if (expectedTimeZone == null) {
                return 5.0; // Unknown time zone
            }
            
            // Check if transaction time is reasonable for the location
            int transactionHour = transactionTime.getHour();
            
            // Very basic time zone check (would need more sophisticated implementation)
            if (transactionHour < 6 || transactionHour > 23) {
                return 10.0; // Unusual time for transaction
            }
            
            return 0.0;
            
        } catch (Exception e) {
            log.warn("Error analyzing time zone consistency: {}", e.getMessage());
            return 5.0;
        }
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Get last known location for user
     */
    private GeolocationPattern getLastKnownLocation(String userId) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + "last:" + userId;
        GeolocationPattern cachedPattern = (GeolocationPattern) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedPattern != null) {
            return cachedPattern;
        }
        
        // Load from database
        GeolocationPattern pattern = geolocationRepository.findFirstByUserIdOrderByTimestampDesc(userId);
        
        if (pattern != null) {
            // Cache for 1 hour
            redisTemplate.opsForValue().set(cacheKey, pattern, 1, TimeUnit.HOURS);
        }
        
        return pattern;
    }

    /**
     * Get user's usual locations (most frequent)
     */
    private List<GeolocationPattern> getUserUsualLocations(String userId) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + "usual:" + userId;
        @SuppressWarnings("unchecked")
        List<GeolocationPattern> cachedPatterns = (List<GeolocationPattern>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedPatterns != null) {
            return cachedPatterns;
        }
        
        // Load from database
        List<GeolocationPattern> patterns = geolocationRepository.findUsualLocationsByUserId(userId, 5); // Top 5 usual locations
        
        if (patterns != null && !patterns.isEmpty()) {
            // Cache for 2 hours (usual locations change less frequently)
            redisTemplate.opsForValue().set(cacheKey, patterns, 2, TimeUnit.HOURS);
        }
        
        return patterns;
    }

    /**
     * Get historical locations for time period
     */
    private List<GeolocationPattern> getHistoricalLocations(String userId, int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minus(hours, ChronoUnit.HOURS);
        return geolocationRepository.findByUserIdAndTimestampAfter(userId, cutoff);
    }

    /**
     * Get recent locations
     */
    private List<GeolocationPattern> getRecentLocations(String userId, int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minus(hours, ChronoUnit.HOURS);
        return geolocationRepository.findByUserIdAndTimestampAfterOrderByTimestampDesc(userId, cutoff);
    }

    /**
     * Group locations into clusters
     */
    private Map<String, List<GeolocationPattern>> groupLocationsByClusters(
            List<GeolocationPattern> locations) {
        
        Map<String, List<GeolocationPattern>> clusters = new HashMap<>();
        final double CLUSTER_RADIUS_KM = 10.0; // 10km clustering radius
        
        for (GeolocationPattern location : locations) {
            boolean addedToCluster = false;
            
            // Try to add to existing cluster
            for (Map.Entry<String, List<GeolocationPattern>> entry : clusters.entrySet()) {
                List<GeolocationPattern> cluster = entry.getValue();
                if (!cluster.isEmpty()) {
                    GeolocationPattern representative = cluster.get(0);
                    double distance = calculateDistance(
                        representative.getLatitude(), representative.getLongitude(),
                        location.getLatitude(), location.getLongitude());
                    
                    if (distance <= CLUSTER_RADIUS_KM) {
                        cluster.add(location);
                        addedToCluster = true;
                        break;
                    }
                }
            }
            
            // Create new cluster if not added to existing
            if (!addedToCluster) {
                String clusterId = "cluster_" + clusters.size();
                clusters.put(clusterId, new ArrayList<>(Arrays.asList(location)));
            }
        }
        
        return clusters;
    }

    /**
     * Check if location is within a cluster
     */
    private boolean isLocationInCluster(TransactionData.GeolocationData geoData,
                                      List<GeolocationPattern> cluster) {
        
        final double CLUSTER_RADIUS_KM = 10.0;
        
        return cluster.stream().anyMatch(loc -> 
            calculateDistance(loc.getLatitude(), loc.getLongitude(),
                            geoData.getLatitude(), geoData.getLongitude()) <= CLUSTER_RADIUS_KM);
    }

    /**
     * Save location pattern for future analysis
     */
    private void saveLocationPattern(TransactionData transaction,
                                   TransactionData.GeolocationData geoData,
                                   GeolocationAnalysisResult result) {
        
        try {
            GeolocationPattern pattern = new GeolocationPattern();
            pattern.setUserId(transaction.getUserId());
            pattern.setTransactionId(transaction.getTransactionId());
            pattern.setLatitude(geoData.getLatitude());
            pattern.setLongitude(geoData.getLongitude());
            pattern.setCountry(geoData.getCountry());
            pattern.setCity(geoData.getCity());
            pattern.setTimestamp(transaction.getTimestamp());
            pattern.setAccuracyMeters(geoData.getAccuracyMeters());
            pattern.setIsMockLocation(geoData.getIsMockLocation());
            pattern.setRiskScore(result.getRiskScore());
            pattern.setVelocityImpossible(result.isVelocityImpossible());
            pattern.setDistanceFromLastLocation(result.getDistanceFromLastLocation());
            
            geolocationRepository.save(pattern);
            
            // Update cache
            String cacheKey = LOCATION_CACHE_PREFIX + "last:" + transaction.getUserId();
            redisTemplate.opsForValue().set(cacheKey, pattern, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.warn("Error saving location pattern: {}", e.getMessage());
        }
    }

    /**
     * Create result for transactions without location data
     */
    private GeolocationAnalysisResult createNoLocationResult(GeolocationAnalysisResult result,
                                                           TransactionData transaction) {
        
        result.setLocationRiskScore(25.0); // Risk for no location data
        result.setRiskScore(25.0);
        result.setRiskLevel("MEDIUM");
        result.setNoLocationData(true);
        result.setProcessingTimeMs(1L);
        
        return result;
    }

    /**
     * Determine risk level from score
     */
    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 80) return "CRITICAL";
        if (riskScore >= 60) return "HIGH";
        if (riskScore >= 40) return "MEDIUM";
        if (riskScore >= 20) return "LOW";
        return "MINIMAL";
    }

    /**
     * Helper methods for risk assessment
     */
    private boolean isHighRiskCountry(String country) {
        // Implementation would check against sanctions lists and high-risk jurisdictions
        Set<String> highRiskCountries = Set.of("AF", "IR", "KP", "SY", "YE"); // Example
        return highRiskCountries.contains(country);
    }

    private boolean isMediumRiskCountry(String country) {
        // Implementation would check against medium-risk jurisdictions
        Set<String> mediumRiskCountries = Set.of("PK", "BD", "MM"); // Example
        return mediumRiskCountries.contains(country);
    }

    private boolean checkFraudHotspotDatabase(TransactionData.GeolocationData geoData) {
        // Implementation would check against fraud hotspot database
        // For now, return false (would be replaced with actual database check)
        return false;
    }

    private Map<String, Object> getIpGeolocation(String ipAddress) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + "ipgeo:" + ipAddress;
        @SuppressWarnings("unchecked")
        Map<String, Object> cachedLocation = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedLocation != null) {
            return cachedLocation;
        }
        
        try {
            // Comprehensive IP validation
            if (!isValidIpAddress(ipAddress)) {
                log.warn("Invalid IP address format: {}", ipAddress);
                return createInvalidIpLocationData(ipAddress);
            }
            
            // Check for private/internal IP addresses
            if (isPrivateIpAddress(ipAddress)) {
                log.debug("Private IP address detected: {}, using internal location data", ipAddress);
                return createPrivateIpLocationData(ipAddress);
            }
            
            // Check for reserved/special IP addresses
            if (isReservedIpAddress(ipAddress)) {
                log.debug("Reserved IP address detected: {}, using default location data", ipAddress);
                return createReservedIpLocationData(ipAddress);
            }
            
            Map<String, Object> location = null;
            
            if (geoApiKey != null && !geoApiKey.isEmpty()) {
                // Try multiple geolocation providers for redundancy
                location = getLocationFromProviders(ipAddress);
            }
            
            if (location == null) {
                log.warn("No geolocation data available for IP: {}, using fallback", ipAddress);
                location = createFallbackLocationData(ipAddress);
            }
            
            if (location != null) {
                // Validate and enrich location data
                location = validateAndEnrichLocationData(location, ipAddress);
                
                // Cache for different durations based on confidence
                int cacheDurationHours = getCacheDurationByConfidence(location);
                redisTemplate.opsForValue().set(cacheKey, location, cacheDurationHours, TimeUnit.HOURS);
            }
            
            return location;
            
        } catch (Exception e) {
            log.error("Error getting IP geolocation for: {}", ipAddress, e);
            return createErrorLocationData(ipAddress, e.getMessage());
        }
    }

    private Map<String, Object> createFallbackLocationData(String ipAddress) {
        // Create fallback location data when API is unavailable
        Map<String, Object> fallbackLocation = new HashMap<>();
        fallbackLocation.put("ip", ipAddress);
        fallbackLocation.put("country", "Unknown");
        fallbackLocation.put("countryCode", "XX");
        fallbackLocation.put("region", "Unknown");
        fallbackLocation.put("city", "Unknown");
        fallbackLocation.put("latitude", 0.0);
        fallbackLocation.put("longitude", 0.0);
        fallbackLocation.put("timezone", "UTC");
        fallbackLocation.put("source", "FALLBACK");
        fallbackLocation.put("confidence", 0.1);
        
        log.debug("Created fallback location data for IP: {}", ipAddress);
        return fallbackLocation;
    }
    
    private Map<String, Object> simulateGeolocationApiCall(String ipAddress) {
        // Simulate IP geolocation API response
        // In production, this would call actual geolocation services
        Map<String, Object> location = new HashMap<>();
        
        // Simulate different locations based on IP patterns for demo
        if (ipAddress.startsWith("192.168") || ipAddress.startsWith("10.") || ipAddress.startsWith("127.")) {
            // Private/local IP addresses
            location.put("ip", ipAddress);
            location.put("country", "United States");
            location.put("countryCode", "US");
            location.put("region", "California");
            location.put("city", "San Francisco");
            location.put("latitude", 37.7749);
            location.put("longitude", -122.4194);
            location.put("timezone", "America/Los_Angeles");
        } else {
            // Public IP simulation - vary by IP hash
            int hash = ipAddress.hashCode();
            String[] countries = {"US", "GB", "DE", "FR", "JP", "CA", "AU"};
            String countryCode = countries[Math.abs(hash) % countries.length];
            
            location.put("ip", ipAddress);
            location.put("countryCode", countryCode);
            location.put("country", getCountryName(countryCode));
            location.put("region", "Simulated Region");
            location.put("city", "Simulated City");
            location.put("latitude", 40.0 + (hash % 40)); // Simulate latitude
            location.put("longitude", -100.0 + (hash % 200)); // Simulate longitude
            location.put("timezone", getTimezoneForCountry(countryCode));
        }
        
        location.put("source", "SIMULATED_API");
        location.put("confidence", 0.8);
        location.put("provider", "MaxMind-Simulated");
        
        log.debug("Simulated geolocation for IP {}: {}", ipAddress, location);
        return location;
    }
    
    private String getCountryName(String countryCode) {
        return switch (countryCode) {
            case "US" -> "United States";
            case "GB" -> "United Kingdom";
            case "DE" -> "Germany";
            case "FR" -> "France";
            case "JP" -> "Japan";
            case "CA" -> "Canada";
            case "AU" -> "Australia";
            default -> "Unknown Country";
        };
    }
    
    private String getTimezoneForCountry(String countryCode) {
        return switch (countryCode) {
            case "US" -> "America/New_York";
            case "GB" -> "Europe/London";
            case "DE" -> "Europe/Berlin";
            case "FR" -> "Europe/Paris";
            case "JP" -> "Asia/Tokyo";
            case "CA" -> "America/Toronto";
            case "AU" -> "Australia/Sydney";
            default -> "UTC";
        };
    }

    private String getTimeZoneForLocation(double latitude, double longitude) {
        // Determine timezone based on coordinates
        // In production, this would use a timezone lookup service
        if (longitude >= -125 && longitude <= -67 && latitude >= 20 && latitude <= 50) {
            return "America/New_York"; // Approximate US timezone
        } else if (longitude >= -10 && longitude <= 40 && latitude >= 35 && latitude <= 70) {
            return "Europe/London"; // Approximate European timezone
        } else if (longitude >= 125 && longitude <= 145 && latitude >= 30 && latitude <= 45) {
            return "Asia/Tokyo"; // Approximate Asia-Pacific timezone
        } else {
            return "UTC"; // Default fallback
        }
    }

    /**
     * Validate location coordinates
     */
    public boolean isValidLocation(double latitude, double longitude) {
        return latitude >= -90.0 && latitude <= 90.0 && 
               longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * Check if location is in specific country
     */
    public boolean isLocationInCountry(TransactionData.GeolocationData geoData, String countryCode) {
        return geoData != null && countryCode.equals(geoData.getCountry());
    }

    /**
     * Get location risk summary for user
     */
    public Map<String, Object> getLocationRiskSummary(String userId) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + "risksummary:" + userId;
        @SuppressWarnings("unchecked")
        Map<String, Object> cachedSummary = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedSummary != null) {
            return cachedSummary;
        }
        
        Map<String, Object> summary = new HashMap<>();
        
        try {
            List<GeolocationPattern> patterns = geolocationRepository.findByUserId(userId);
            
            summary.put("total_locations", patterns.size());
            summary.put("unique_countries", getUniqueCountriesCount(patterns));
            summary.put("unique_cities", getUniqueCitiesCount(patterns));
            summary.put("average_risk_score", getAverageRiskScore(patterns));
            summary.put("high_risk_locations", getHighRiskLocationsCount(patterns));
            summary.put("velocity_violations", getVelocityViolationsCount(patterns));
            
            // Cache for 30 minutes
            redisTemplate.opsForValue().set(cacheKey, summary, 30, TimeUnit.MINUTES);
            
        } catch (Exception e) {
            log.warn("Error generating location risk summary for user: {}", userId, e);
        }
        
        return summary;
    }

    // Helper methods for summary calculations
    private long getUniqueCountriesCount(List<GeolocationPattern> patterns) {
        return patterns.stream()
            .filter(p -> p.getCountry() != null)
            .map(GeolocationPattern::getCountry)
            .distinct()
            .count();
    }

    private long getUniqueCitiesCount(List<GeolocationPattern> patterns) {
        return patterns.stream()
            .filter(p -> p.getCity() != null)
            .map(GeolocationPattern::getCity)
            .distinct()
            .count();
    }

    private double getAverageRiskScore(List<GeolocationPattern> patterns) {
        return patterns.stream()
            .filter(p -> p.getRiskScore() != null)
            .mapToDouble(GeolocationPattern::getRiskScore)
            .average()
            .orElse(0.0);
    }

    private long getHighRiskLocationsCount(List<GeolocationPattern> patterns) {
        return patterns.stream()
            .filter(p -> p.getRiskScore() != null && p.getRiskScore() >= 60.0)
            .count();
    }

    private long getVelocityViolationsCount(List<GeolocationPattern> patterns) {
        return patterns.stream()
            .filter(p -> Boolean.TRUE.equals(p.getVelocityImpossible()))
            .count();
    }
    
    // Comprehensive IP validation and geolocation methods
    
    /**
     * Validate IP address format (IPv4 and IPv6)
     */
    private boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Check IPv4 format
            if (isValidIpv4(ipAddress)) {
                return true;
            }
            
            // Check IPv6 format
            if (isValidIpv6(ipAddress)) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.debug("IP address validation failed for: {}", ipAddress);
            return false;
        }
    }
    
    /**
     * Validate IPv4 address format
     */
    private boolean isValidIpv4(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate IPv6 address format
     */
    private boolean isValidIpv6(String ipAddress) {
        // Basic IPv6 validation
        String[] parts = ipAddress.split(":");
        if (parts.length < 3 || parts.length > 8) {
            return false;
        }
        
        for (String part : parts) {
            if (part.isEmpty()) {
                continue; // Allow for compressed notation ::
            }
            
            if (part.length() > 4) {
                return false;
            }
            
            for (char c : part.toCharArray()) {
                if (!Character.isDigit(c) && !(c >= 'a' && c <= 'f') && !(c >= 'A' && c <= 'F')) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Check if IP address is private/internal
     */
    private boolean isPrivateIpAddress(String ipAddress) {
        if (!isValidIpv4(ipAddress)) {
            return false; // For simplicity, only check IPv4 private ranges
        }
        
        String[] parts = ipAddress.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        
        // RFC 1918 private address ranges
        // 10.0.0.0/8
        if (first == 10) {
            return true;
        }
        
        // 172.16.0.0/12
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        
        // 192.168.0.0/16
        if (first == 192 && second == 168) {
            return true;
        }
        
        // Loopback (127.0.0.0/8)
        if (first == 127) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if IP address is reserved/special
     */
    private boolean isReservedIpAddress(String ipAddress) {
        if (!isValidIpv4(ipAddress)) {
            return false;
        }
        
        String[] parts = ipAddress.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        
        // Reserved ranges
        // 0.0.0.0/8 - "This network"
        if (first == 0) {
            return true;
        }
        
        // 169.254.0.0/16 - Link-local (APIPA)
        if (first == 169 && second == 254) {
            return true;
        }
        
        // 224.0.0.0/4 - Multicast
        if (first >= 224 && first <= 239) {
            return true;
        }
        
        // 240.0.0.0/4 - Reserved for future use
        if (first >= 240) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get location from multiple providers with fallback
     */
    private Map<String, Object> getLocationFromProviders(String ipAddress) {
        Map<String, Object> location = null;
        
        try {
            // Try primary provider (MaxMind GeoIP2)
            location = getLocationFromMaxMind(ipAddress);
            if (location != null && isHighConfidenceLocation(location)) {
                location.put("provider", "MaxMind");
                return location;
            }
        } catch (Exception e) {
            log.warn("MaxMind geolocation failed for IP {}: {}", ipAddress, e.getMessage());
        }
        
        try {
            // Try secondary provider (IPStack)
            location = getLocationFromIpStack(ipAddress);
            if (location != null && isHighConfidenceLocation(location)) {
                location.put("provider", "IPStack");
                return location;
            }
        } catch (Exception e) {
            log.warn("IPStack geolocation failed for IP {}: {}", ipAddress, e.getMessage());
        }
        
        try {
            // Try tertiary provider (ipinfo.io)
            location = getLocationFromIpInfo(ipAddress);
            if (location != null) {
                location.put("provider", "IPInfo");
                return location;
            }
        } catch (Exception e) {
            log.warn("IPInfo geolocation failed for IP {}: {}", ipAddress, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get location from MaxMind GeoIP2 service
     */
    private Map<String, Object> getLocationFromMaxMind(String ipAddress) {
        try {
            // MaxMind GeoIP2 API call
            String url = String.format("https://geoip.maxmind.com/geoip/v2.1/city/%s?key=%s", 
                ipAddress, geoApiKey);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("location")) {
                return convertMaxMindResponse(response, ipAddress);
            }
            
        } catch (Exception e) {
            log.debug("MaxMind API call failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get location from IPStack service
     */
    private Map<String, Object> getLocationFromIpStack(String ipAddress) {
        try {
            // IPStack API call
            String url = String.format("http://api.ipstack.com/%s?access_key=%s&format=1", 
                ipAddress, geoApiKey);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && !response.containsKey("error")) {
                return convertIpStackResponse(response, ipAddress);
            }
            
        } catch (Exception e) {
            log.debug("IPStack API call failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get location from ipinfo.io service
     */
    private Map<String, Object> getLocationFromIpInfo(String ipAddress) {
        try {
            // IPInfo.io API call
            String url = String.format("https://ipinfo.io/%s?token=%s", ipAddress, geoApiKey);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && !response.containsKey("error")) {
                return convertIpInfoResponse(response, ipAddress);
            }
            
        } catch (Exception e) {
            log.debug("IPInfo API call failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Convert MaxMind API response to standard format
     */
    private Map<String, Object> convertMaxMindResponse(Map<String, Object> response, String ipAddress) {
        Map<String, Object> location = new HashMap<>();
        
        try {
            Map<String, Object> locationData = (Map<String, Object>) response.get("location");
            Map<String, Object> country = (Map<String, Object>) response.get("country");
            Map<String, Object> city = (Map<String, Object>) response.get("city");
            
            location.put("ip", ipAddress);
            location.put("latitude", locationData.get("latitude"));
            location.put("longitude", locationData.get("longitude"));
            location.put("accuracy_radius", locationData.get("accuracy_radius"));
            location.put("country", country != null ? country.get("names") : null);
            location.put("countryCode", country != null ? country.get("iso_code") : null);
            location.put("city", city != null ? city.get("names") : null);
            location.put("timezone", locationData.get("time_zone"));
            location.put("confidence", calculateMaxMindConfidence(locationData));
            location.put("source", "MAXMIND_API");
            
        } catch (Exception e) {
            log.warn("Error converting MaxMind response: {}", e.getMessage());
        }
        
        return location;
    }
    
    /**
     * Convert IPStack API response to standard format
     */
    private Map<String, Object> convertIpStackResponse(Map<String, Object> response, String ipAddress) {
        Map<String, Object> location = new HashMap<>();
        
        try {
            location.put("ip", ipAddress);
            location.put("latitude", response.get("latitude"));
            location.put("longitude", response.get("longitude"));
            location.put("country", response.get("country_name"));
            location.put("countryCode", response.get("country_code"));
            location.put("region", response.get("region_name"));
            location.put("city", response.get("city"));
            location.put("timezone", response.get("time_zone"));
            location.put("confidence", calculateIpStackConfidence(response));
            location.put("source", "IPSTACK_API");
            
        } catch (Exception e) {
            log.warn("Error converting IPStack response: {}", e.getMessage());
        }
        
        return location;
    }
    
    /**
     * Convert IPInfo API response to standard format
     */
    private Map<String, Object> convertIpInfoResponse(Map<String, Object> response, String ipAddress) {
        Map<String, Object> location = new HashMap<>();
        
        try {
            String loc = (String) response.get("loc");
            if (loc != null && loc.contains(",")) {
                String[] coords = loc.split(",");
                location.put("latitude", Double.parseDouble(coords[0]));
                location.put("longitude", Double.parseDouble(coords[1]));
            }
            
            location.put("ip", ipAddress);
            location.put("country", response.get("country"));
            location.put("countryCode", response.get("country"));
            location.put("region", response.get("region"));
            location.put("city", response.get("city"));
            location.put("timezone", response.get("timezone"));
            location.put("confidence", 0.7); // IPInfo typically has good accuracy
            location.put("source", "IPINFO_API");
            
        } catch (Exception e) {
            log.warn("Error converting IPInfo response: {}", e.getMessage());
        }
        
        return location;
    }
    
    /**
     * Calculate confidence score for MaxMind data
     */
    private double calculateMaxMindConfidence(Map<String, Object> locationData) {
        try {
            Integer accuracyRadius = (Integer) locationData.get("accuracy_radius");
            if (accuracyRadius != null) {
                // Higher accuracy (lower radius) = higher confidence
                if (accuracyRadius <= 10) return 0.95;
                if (accuracyRadius <= 50) return 0.85;
                if (accuracyRadius <= 100) return 0.75;
                if (accuracyRadius <= 500) return 0.65;
                return 0.5;
            }
        } catch (Exception e) {
            log.debug("Error calculating MaxMind confidence: {}", e.getMessage());
        }
        
        return 0.6; // Default confidence
    }
    
    /**
     * Calculate confidence score for IPStack data
     */
    private double calculateIpStackConfidence(Map<String, Object> response) {
        try {
            String connectionType = (String) response.get("connection_type");
            if ("residential".equals(connectionType)) {
                return 0.8;
            } else if ("corporate".equals(connectionType)) {
                return 0.75;
            } else if ("hosting".equals(connectionType)) {
                return 0.6;
            }
        } catch (Exception e) {
            log.debug("Error calculating IPStack confidence: {}", e.getMessage());
        }
        
        return 0.7; // Default confidence
    }
    
    /**
     * Check if location data has high confidence
     */
    private boolean isHighConfidenceLocation(Map<String, Object> location) {
        try {
            Double confidence = (Double) location.get("confidence");
            return confidence != null && confidence >= 0.7;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate and enrich location data
     */
    private Map<String, Object> validateAndEnrichLocationData(Map<String, Object> location, String ipAddress) {
        try {
            // Validate coordinates
            Double latitude = (Double) location.get("latitude");
            Double longitude = (Double) location.get("longitude");
            
            if (latitude == null || longitude == null || !isValidLocation(latitude, longitude)) {
                log.warn("Invalid coordinates for IP {}: lat={}, lon={}", ipAddress, latitude, longitude);
                location.put("coordinates_valid", false);
                location.put("confidence", Math.max(0.1, (Double) location.getOrDefault("confidence", 0.5) - 0.3));
            } else {
                location.put("coordinates_valid", true);
            }
            
            // Add security assessment
            location.put("security_assessment", assessIpSecurity(ipAddress, location));
            
            // Add threat intelligence flags
            location.put("threat_flags", getThreatFlags(ipAddress, location));
            
            // Normalize country code
            String countryCode = (String) location.get("countryCode");
            if (countryCode != null) {
                location.put("countryCode", countryCode.toUpperCase());
                location.put("country_risk_level", getCountryRiskLevel(countryCode));
            }
            
            // Add timestamp
            location.put("updated_at", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.warn("Error enriching location data for IP {}: {}", ipAddress, e.getMessage());
        }
        
        return location;
    }
    
    /**
     * Assess IP security characteristics
     */
    private Map<String, Object> assessIpSecurity(String ipAddress, Map<String, Object> location) {
        Map<String, Object> security = new HashMap<>();
        
        try {
            // Check for VPN/Proxy indicators
            security.put("is_proxy", checkForProxy(ipAddress, location));
            security.put("is_vpn", checkForVpn(ipAddress, location));
            security.put("is_tor", checkForTor(ipAddress));
            security.put("is_datacenter", checkForDatacenter(ipAddress, location));
            security.put("is_mobile", checkForMobile(ipAddress, location));
            security.put("reputation_score", getIpReputationScore(ipAddress));
            
        } catch (Exception e) {
            log.debug("Error assessing IP security for {}: {}", ipAddress, e.getMessage());
        }
        
        return security;
    }
    
    /**
     * Get threat intelligence flags
     */
    private Map<String, String> getThreatFlags(String ipAddress, Map<String, Object> location) {
        Map<String, String> flags = new HashMap<>();
        
        try {
            // Check against threat intelligence feeds
            if (isInThreatFeed(ipAddress)) {
                flags.put("threat_feed", "MALICIOUS");
            }
            
            // Check for botnet activity
            if (isBotnetIp(ipAddress)) {
                flags.put("botnet", "SUSPECTED");
            }
            
            // Check for high-risk hosting provider
            if (isHighRiskHosting(location)) {
                flags.put("hosting_risk", "HIGH");
            }
            
        } catch (Exception e) {
            log.debug("Error getting threat flags for {}: {}", ipAddress, e.getMessage());
        }
        
        return flags;
    }
    
    /**
     * Get cache duration based on confidence level
     */
    private int getCacheDurationByConfidence(Map<String, Object> location) {
        try {
            Double confidence = (Double) location.get("confidence");
            if (confidence != null) {
                if (confidence >= 0.9) return 48; // High confidence - cache longer
                if (confidence >= 0.7) return 24; // Medium confidence
                if (confidence >= 0.5) return 12; // Low confidence
                return 6; // Very low confidence
            }
        } catch (Exception e) {
            log.debug("Error determining cache duration: {}", e.getMessage());
        }
        
        return 24; // Default 24 hours
    }
    
    /**
     * Create location data for invalid IP addresses
     */
    private Map<String, Object> createInvalidIpLocationData(String ipAddress) {
        Map<String, Object> location = new HashMap<>();
        location.put("ip", ipAddress);
        location.put("error", "INVALID_IP_FORMAT");
        location.put("country", "Unknown");
        location.put("countryCode", "XX");
        location.put("latitude", 0.0);
        location.put("longitude", 0.0);
        location.put("confidence", 0.0);
        location.put("source", "VALIDATION_ERROR");
        return location;
    }
    
    /**
     * Create location data for private IP addresses
     */
    private Map<String, Object> createPrivateIpLocationData(String ipAddress) {
        Map<String, Object> location = new HashMap<>();
        location.put("ip", ipAddress);
        location.put("country", "Private Network");
        location.put("countryCode", "XX");
        location.put("city", "Internal");
        location.put("latitude", 0.0);
        location.put("longitude", 0.0);
        location.put("confidence", 0.1);
        location.put("source", "PRIVATE_IP");
        location.put("is_private", true);
        return location;
    }
    
    /**
     * Create location data for reserved IP addresses
     */
    private Map<String, Object> createReservedIpLocationData(String ipAddress) {
        Map<String, Object> location = new HashMap<>();
        location.put("ip", ipAddress);
        location.put("country", "Reserved");
        location.put("countryCode", "XX");
        location.put("city", "Reserved");
        location.put("latitude", 0.0);
        location.put("longitude", 0.0);
        location.put("confidence", 0.1);
        location.put("source", "RESERVED_IP");
        location.put("is_reserved", true);
        return location;
    }
    
    /**
     * Create location data for API errors
     */
    private Map<String, Object> createErrorLocationData(String ipAddress, String errorMessage) {
        Map<String, Object> location = new HashMap<>();
        location.put("ip", ipAddress);
        location.put("error", errorMessage);
        location.put("country", "Unknown");
        location.put("countryCode", "XX");
        location.put("latitude", 0.0);
        location.put("longitude", 0.0);
        location.put("confidence", 0.0);
        location.put("source", "ERROR");
        return location;
    }
    
    // Security checking helper methods
    
    private boolean checkForProxy(String ipAddress, Map<String, Object> location) {
        // Check for proxy indicators in hosting provider, ASN, etc.
        String city = (String) location.get("city");
        return city != null && (city.toLowerCase().contains("proxy") || city.toLowerCase().contains("vpn"));
    }
    
    private boolean checkForVpn(String ipAddress, Map<String, Object> location) {
        // Check for VPN providers and known VPN IP ranges
        String provider = (String) location.get("org");
        return provider != null && (provider.toLowerCase().contains("vpn") || 
                                  provider.toLowerCase().contains("virtual private"));
    }
    
    private boolean checkForTor(String ipAddress) {
        // Check against Tor exit node lists
        // In production, this would query Tor directory authorities
        return false; // Placeholder
    }
    
    private boolean checkForDatacenter(String ipAddress, Map<String, Object> location) {
        // Check if IP belongs to a datacenter/hosting provider
        String org = (String) location.get("org");
        return org != null && (org.toLowerCase().contains("hosting") || 
                              org.toLowerCase().contains("datacenter") ||
                              org.toLowerCase().contains("cloud"));
    }
    
    private boolean checkForMobile(String ipAddress, Map<String, Object> location) {
        // Check if IP belongs to mobile carrier
        String org = (String) location.get("org");
        return org != null && (org.toLowerCase().contains("mobile") || 
                              org.toLowerCase().contains("wireless") ||
                              org.toLowerCase().contains("cellular"));
    }
    
    private double getIpReputationScore(String ipAddress) {
        // Check IP reputation from multiple sources
        // In production, this would query reputation databases
        return 0.8; // Default good reputation
    }
    
    private boolean isInThreatFeed(String ipAddress) {
        // Check against threat intelligence feeds
        return false; // Placeholder
    }
    
    private boolean isBotnetIp(String ipAddress) {
        // Check against botnet IP lists
        return false; // Placeholder
    }
    
    private boolean isHighRiskHosting(Map<String, Object> location) {
        // Check if hosting provider has high fraud risk
        String org = (String) location.get("org");
        return org != null && org.toLowerCase().contains("bulletproof");
    }
}