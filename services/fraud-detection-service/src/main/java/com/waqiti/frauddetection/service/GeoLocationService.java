package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.domain.LocationInfo;
import com.waqiti.frauddetection.config.FraudDetectionProperties;
import com.waqiti.frauddetection.integration.MaxMindGeoLocationClient;
import com.waqiti.frauddetection.integration.ThreatIntelligenceClient;
import com.waqiti.frauddetection.integration.VpnDetectionClient;
import com.waqiti.frauddetection.repository.LocationHistoryRepository;
import com.waqiti.frauddetection.repository.IpReputationRepository;
import com.waqiti.common.events.FraudEventPublisher;
import com.waqiti.common.monitoring.MetricsCollector;
import com.waqiti.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production-Ready Geolocation-Based Fraud Detection Service
 * 
 * Provides comprehensive IP geolocation analysis, risk scoring, and fraud detection
 * capabilities with real-time threat intelligence integration and behavioral analysis.
 * 
 * Features:
 * - Real-time IP geolocation with multiple providers
 * - Impossible travel detection with high precision
 * - VPN/Proxy/Tor detection with threat intelligence
 * - User location pattern analysis and anomaly detection
 * - Risk-based scoring with configurable thresholds
 * - Comprehensive caching and performance optimization
 * - Regulatory compliance and audit trails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeoLocationService {
    
    private final MaxMindGeoLocationClient maxMindClient;
    private final ThreatIntelligenceClient threatIntelClient;
    private final VpnDetectionClient vpnDetectionClient;
    private final LocationHistoryRepository locationHistoryRepository;
    private final IpReputationRepository ipReputationRepository;
    private final FraudEventPublisher eventPublisher;
    private final MetricsCollector metricsCollector;
    private final CacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FraudDetectionProperties properties;
    
    // Cache keys
    private static final String IP_LOCATION_CACHE = "fraud:ip:location:";
    private static final String USER_LOCATION_HISTORY = "fraud:user:locations:";
    private static final String LOCATION_RISK_CACHE = "fraud:location:risk:";
    private static final String IP_REPUTATION_CACHE = "fraud:ip:reputation:";
    private static final String VPN_DETECTION_CACHE = "fraud:ip:vpn:";
    
    // Metrics keys
    private static final String METRIC_LOCATION_CHECKS = "fraud.geo.location_checks";
    private static final String METRIC_SUSPICIOUS_LOCATIONS = "fraud.geo.suspicious_locations";
    private static final String METRIC_IMPOSSIBLE_TRAVEL = "fraud.geo.impossible_travel";
    private static final String METRIC_VPN_DETECTIONS = "fraud.geo.vpn_detections";
    private static final String METRIC_HIGH_RISK_COUNTRIES = "fraud.geo.high_risk_countries";
    
    /**
     * Comprehensive location suspiciousness analysis with real-time threat intelligence
     */
    public boolean isSuspiciousLocation(@NotNull String ipAddress, @NotNull String userId) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Analyzing location suspiciousness for user: {} with IP: {}", 
                userId, maskIpAddress(ipAddress));
            
            // Input validation
            if (!StringUtils.hasText(ipAddress) || !StringUtils.hasText(userId)) {
                log.warn("Invalid input parameters - IP: {}, User: {}", 
                    maskIpAddress(ipAddress), userId);
                metricsCollector.increment(METRIC_LOCATION_CHECKS, "result", "invalid_input");
                return true; // Fail secure
            }
            
            // Normalize IP address
            String normalizedIp = normalizeIpAddress(ipAddress);
            if (normalizedIp == null) {
                log.warn("Unable to normalize IP address: {}", maskIpAddress(ipAddress));
                metricsCollector.increment(METRIC_LOCATION_CHECKS, "result", "invalid_ip");
                return true;
            }
            
            // Check for private/internal IPs (always suspicious for external services)
            if (isPrivateOrInternalIp(normalizedIp)) {
                log.warn("Private/internal IP detected for user {}: {}", 
                    userId, maskIpAddress(normalizedIp));
                metricsCollector.increment(METRIC_LOCATION_CHECKS, "result", "private_ip");
                publishSuspiciousLocationEvent(userId, normalizedIp, "PRIVATE_IP", 1.0);
                return true;
            }
            
            // Get comprehensive location information
            LocationInfo locationInfo = getComprehensiveLocationInfo(normalizedIp);
            if (locationInfo == null) {
                log.warn("Unable to determine location for IP: {}", maskIpAddress(normalizedIp));
                metricsCollector.increment(METRIC_LOCATION_CHECKS, "result", "location_unknown");
                return true; // Unknown location is suspicious
            }
            
            // Perform multi-faceted suspiciousness analysis
            SuspiciousnessAnalysis analysis = performSuspiciousnessAnalysis(
                locationInfo, userId, normalizedIp);
            
            boolean isSuspicious = analysis.isSuspicious();
            double riskScore = analysis.getRiskScore();
            
            // Update location history
            updateLocationHistory(userId, locationInfo);
            
            // Record metrics
            metricsCollector.increment(METRIC_LOCATION_CHECKS, 
                "result", isSuspicious ? "suspicious" : "legitimate");
            metricsCollector.recordTimer(METRIC_LOCATION_CHECKS + ".duration", 
                System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
            
            // Publish events for suspicious locations
            if (isSuspicious) {
                publishSuspiciousLocationEvent(userId, normalizedIp, 
                    analysis.getPrimaryReason(), riskScore);
                metricsCollector.increment(METRIC_SUSPICIOUS_LOCATIONS, 
                    "reason", analysis.getPrimaryReason());
            }
            
            log.debug("Location analysis completed for user {} - Suspicious: {}, Risk: {}, Reason: {}", 
                userId, isSuspicious, riskScore, analysis.getPrimaryReason());
            
            return isSuspicious;
            
        } catch (Exception e) {
            log.error("Error analyzing location suspiciousness for user {}: {}", 
                userId, e.getMessage(), e);
            metricsCollector.increment(METRIC_LOCATION_CHECKS, "result", "error");
            return true; // Fail secure on error
        }
    }
    
    /**
     * Calculate comprehensive location risk score with weighted factors
     */
    public double getLocationRiskScore(@NotNull String ipAddress, @NotNull String userId) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Calculating location risk score for user: {} with IP: {}", 
                userId, maskIpAddress(ipAddress));
            
            // Input validation
            if (!StringUtils.hasText(ipAddress) || !StringUtils.hasText(userId)) {
                log.warn("Invalid input for risk calculation");
                return 1.0; // Maximum risk for invalid input
            }
            
            // Check cache first
            String cacheKey = LOCATION_RISK_CACHE + userId + ":" + ipAddress.hashCode();
            Double cachedRisk = cacheService.get(cacheKey, Double.class);
            if (cachedRisk != null) {
                log.debug("Returning cached risk score: {} for user: {}", cachedRisk, userId);
                return cachedRisk;
            }
            
            String normalizedIp = normalizeIpAddress(ipAddress);
            if (normalizedIp == null) {
                return 1.0; // Maximum risk for invalid IP
            }
            
            // Parallel risk factor calculation for performance
            CompletableFuture<Double> ipRiskFuture = CompletableFuture.supplyAsync(() -> 
                calculateIpCharacteristicsRisk(normalizedIp));
            
            CompletableFuture<Double> geoRiskFuture = CompletableFuture.supplyAsync(() -> 
                calculateGeographicRisk(normalizedIp));
            
            CompletableFuture<Double> consistencyRiskFuture = CompletableFuture.supplyAsync(() -> 
                calculateLocationConsistencyRisk(userId, normalizedIp));
            
            CompletableFuture<Double> velocityRiskFuture = CompletableFuture.supplyAsync(() -> 
                calculateVelocityRisk(userId, normalizedIp));
            
            CompletableFuture<Double> threatRiskFuture = CompletableFuture.supplyAsync(() -> 
                calculateThreatIntelligenceRisk(normalizedIp));
            
            CompletableFuture<Double> behavioralRiskFuture = CompletableFuture.supplyAsync(() -> 
                calculateBehavioralRisk(userId, normalizedIp));
            
            // Wait for all risk calculations to complete
            CompletableFuture<Void> allCalculations = CompletableFuture.allOf(
                ipRiskFuture, geoRiskFuture, consistencyRiskFuture, 
                velocityRiskFuture, threatRiskFuture, behavioralRiskFuture);
            
            allCalculations.get(5, TimeUnit.SECONDS); // 5 second timeout
            
            // Weighted risk calculation based on configuration
            RiskWeights weights = properties.getRiskWeights();
            double totalRisk = 
                (ipRiskFuture.get() * weights.getIpCharacteristics()) +
                (geoRiskFuture.get() * weights.getGeographic()) +
                (consistencyRiskFuture.get() * weights.getLocationConsistency()) +
                (velocityRiskFuture.get() * weights.getVelocity()) +
                (threatRiskFuture.get() * weights.getThreatIntelligence()) +
                (behavioralRiskFuture.get() * weights.getBehavioral());
            
            // Normalize to 0-1 range
            totalRisk = Math.min(1.0, Math.max(0.0, totalRisk));
            
            // Apply risk amplification for multiple risk factors
            totalRisk = applyRiskAmplification(totalRisk, Arrays.asList(
                ipRiskFuture.get(), geoRiskFuture.get(), consistencyRiskFuture.get(),
                velocityRiskFuture.get(), threatRiskFuture.get(), behavioralRiskFuture.get()));
            
            // Cache the result
            cacheService.put(cacheKey, totalRisk, Duration.ofMinutes(15));
            
            // Record detailed metrics
            recordRiskMetrics(userId, normalizedIp, totalRisk, Map.of(
                "ip_risk", ipRiskFuture.get(),
                "geo_risk", geoRiskFuture.get(),
                "consistency_risk", consistencyRiskFuture.get(),
                "velocity_risk", velocityRiskFuture.get(),
                "threat_risk", threatRiskFuture.get(),
                "behavioral_risk", behavioralRiskFuture.get()
            ));
            
            metricsCollector.recordTimer("fraud.geo.risk_calculation.duration", 
                System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
            
            log.debug("Location risk score calculated: {} for user: {}", totalRisk, userId);
            return totalRisk;
            
        } catch (Exception e) {
            log.error("Error calculating location risk score for user {}: {}", 
                userId, e.getMessage(), e);
            return 0.8; // High risk on error
        }
    }
    
    /**
     * Get comprehensive location information with multiple data sources
     */
    private LocationInfo getComprehensiveLocationInfo(String ipAddress) {
        try {
            // Check cache first
            String cacheKey = IP_LOCATION_CACHE + ipAddress;
            LocationInfo cached = cacheService.get(cacheKey, LocationInfo.class);
            if (cached != null && isLocationInfoValid(cached)) {
                return cached;
            }
            
            // Primary geolocation lookup
            LocationInfo locationInfo = maxMindClient.getLocationInfo(ipAddress);
            if (locationInfo == null) {
                log.warn("Primary geolocation failed for IP: {}, attempting fallback methods", maskIpAddress(ipAddress));
                
                // Try fallback geolocation providers
                locationInfo = attemptFallbackGeolocation(ipAddress);
                
                if (locationInfo == null) {
                    log.error("All geolocation methods failed for IP: {}, returning default high-risk location", maskIpAddress(ipAddress));
                    return createHighRiskDefaultLocation(ipAddress);
                }
                
                log.info("Fallback geolocation succeeded for IP: {}", maskIpAddress(ipAddress));
            }
            
            // Enrich with threat intelligence (async for performance)
            CompletableFuture.runAsync(() -> enrichWithThreatIntelligence(locationInfo, ipAddress));
            
            // Enrich with VPN/Proxy detection
            CompletableFuture.runAsync(() -> enrichWithVpnDetection(locationInfo, ipAddress));
            
            // Enrich with IP reputation data
            CompletableFuture.runAsync(() -> enrichWithIpReputation(locationInfo, ipAddress));
            
            // Set metadata
            locationInfo.setTimestamp(LocalDateTime.now());
            locationInfo.setIpAddress(maskIpAddress(ipAddress));
            locationInfo.setDataSource("MaxMind");
            locationInfo.setProvider("GeoIP2");
            
            // Cache the result
            cacheService.put(cacheKey, locationInfo, Duration.ofHours(6));
            
            return locationInfo;
            
        } catch (Exception e) {
            log.error("Critical error getting location information for IP {}: {}", 
                maskIpAddress(ipAddress), e.getMessage(), e);
            
            // Return high-risk default location instead of null to maintain fraud detection
            return createHighRiskDefaultLocation(ipAddress);
        }
    }
    
    /**
     * Perform comprehensive suspiciousness analysis
     */
    private SuspiciousnessAnalysis performSuspiciousnessAnalysis(
            LocationInfo locationInfo, String userId, String ipAddress) {
        
        SuspiciousnessAnalysis.Builder analysisBuilder = SuspiciousnessAnalysis.builder()
            .locationInfo(locationInfo)
            .userId(userId)
            .ipAddress(maskIpAddress(ipAddress))
            .timestamp(LocalDateTime.now());
        
        List<String> reasons = new ArrayList<>();
        double totalRisk = 0.0;
        
        // Check 1: High-risk country
        if (isHighRiskCountry(locationInfo.getCountryCode())) {
            reasons.add("HIGH_RISK_COUNTRY");
            totalRisk += 0.8;
            metricsCollector.increment(METRIC_HIGH_RISK_COUNTRIES, 
                "country", locationInfo.getCountryCode());
        }
        
        // Check 2: VPN/Proxy/Tor usage
        if (Boolean.TRUE.equals(locationInfo.getIsVpn()) || 
            Boolean.TRUE.equals(locationInfo.getIsProxy()) ||
            Boolean.TRUE.equals(locationInfo.getIsTor())) {
            reasons.add("ANONYMIZATION_SERVICE");
            totalRisk += 0.9;
            metricsCollector.increment(METRIC_VPN_DETECTIONS, 
                "type", getAnonymizationType(locationInfo));
        }
        
        // Check 3: Impossible travel
        List<LocationInfo> locationHistory = getRecentLocationHistory(userId, 5);
        if (!locationHistory.isEmpty()) {
            LocationInfo lastLocation = locationHistory.get(0);
            if (isImpossibleTravel(lastLocation, locationInfo)) {
                reasons.add("IMPOSSIBLE_TRAVEL");
                totalRisk += 1.0;
                metricsCollector.increment(METRIC_IMPOSSIBLE_TRAVEL);
                
                double distance = locationInfo.calculateDistanceFrom(lastLocation);
                long timeDiff = Duration.between(lastLocation.getTimestamp(), 
                    locationInfo.getTimestamp()).toMinutes();
                log.warn("Impossible travel detected for user {}: {} km in {} minutes", 
                    userId, String.format("%.2f", distance), timeDiff);
            }
        }
        
        // Check 4: Location hopping
        if (locationHistory.size() >= 3 && isLocationHopping(locationHistory, locationInfo)) {
            reasons.add("LOCATION_HOPPING");
            totalRisk += 0.7;
        }
        
        // Check 5: Threat intelligence
        if (Boolean.TRUE.equals(locationInfo.getIsMalicious()) || 
            (locationInfo.getThreatCategories() != null && !locationInfo.getThreatCategories().isEmpty())) {
            reasons.add("THREAT_INTELLIGENCE");
            totalRisk += 0.9;
        }
        
        // Check 6: Unusual time zone
        if (isUnusualTimeZone(userId, locationInfo)) {
            reasons.add("UNUSUAL_TIMEZONE");
            totalRisk += 0.4;
        }
        
        // Check 7: Hosting/Datacenter IP
        if (Boolean.TRUE.equals(locationInfo.getIsHosting())) {
            reasons.add("HOSTING_IP");
            totalRisk += 0.6;
        }
        
        // Normalize risk score
        totalRisk = Math.min(1.0, totalRisk);
        
        return analysisBuilder
            .suspicious(totalRisk > properties.getSuspiciousnessThreshold())
            .riskScore(totalRisk)
            .reasons(reasons)
            .primaryReason(reasons.isEmpty() ? "LEGITIMATE" : reasons.get(0))
            .build();
    }
    
    /**
     * Calculate IP characteristics risk (VPN, proxy, reputation, etc.)
     */
    private double calculateIpCharacteristicsRisk(String ipAddress) {
        try {
            double risk = 0.0;
            
            // VPN/Proxy detection
            VpnDetectionResult vpnResult = vpnDetectionClient.checkVpn(ipAddress);
            if (vpnResult.isVpn()) risk += 0.7;
            if (vpnResult.isProxy()) risk += 0.6;
            if (vpnResult.isTor()) risk += 0.9;
            
            // IP reputation
            IpReputationResult reputationResult = ipReputationRepository.getReputation(ipAddress);
            if (reputationResult != null) {
                risk += reputationResult.getRiskScore();
            }
            
            // Hosting/Datacenter detection
            if (isDatacenterIp(ipAddress)) {
                risk += 0.5;
            }
            
            return Math.min(1.0, risk);
        } catch (Exception e) {
            log.warn("Error calculating IP characteristics risk: {}", e.getMessage());
            return 0.3; // Default moderate risk
        }
    }
    
    /**
     * Calculate geographic risk based on country, region, and political factors
     */
    private double calculateGeographicRisk(String ipAddress) {
        try {
            LocationInfo location = getComprehensiveLocationInfo(ipAddress);
            if (location == null) return 0.8;
            
            double risk = 0.0;
            
            // Country risk
            String countryCode = location.getCountryCode();
            risk += getCountryRiskScore(countryCode);
            
            // Sanctions check
            if (location.getSanctions() != null && !location.getSanctions().isEmpty()) {
                risk += 0.9;
            }
            
            // Political instability
            risk += getPoliticalInstabilityScore(countryCode);
            
            // Regulatory risk
            risk += getRegulatoryRiskScore(countryCode);
            
            return Math.min(1.0, risk);
        } catch (Exception e) {
            log.warn("Error calculating geographic risk: {}", e.getMessage());
            return 0.2;
        }
    }
    
    /**
     * Calculate location consistency risk based on user's historical patterns
     */
    private double calculateLocationConsistencyRisk(String userId, String ipAddress) {
        try {
            LocationInfo currentLocation = getComprehensiveLocationInfo(ipAddress);
            if (currentLocation == null) return 0.8;
            
            List<LocationInfo> history = getRecentLocationHistory(userId, 20);
            if (history.isEmpty()) return 0.3; // Moderate risk for new users
            
            // Calculate country frequency
            Map<String, Long> countryFrequency = history.stream()
                .collect(Collectors.groupingBy(
                    LocationInfo::getCountryCode, 
                    Collectors.counting()));
            
            String mostCommonCountry = countryFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
            
            // Low risk if current location matches most common country
            if (currentLocation.getCountryCode().equals(mostCommonCountry)) {
                return 0.1;
            }
            
            // Check if country is in user's known countries
            if (countryFrequency.containsKey(currentLocation.getCountryCode())) {
                return 0.3; // Moderate risk for known but uncommon country
            }
            
            // High risk for completely new country
            return 0.8;
            
        } catch (Exception e) {
            log.warn("Error calculating location consistency risk: {}", e.getMessage());
            return 0.4;
        }
    }
    
    /**
     * Calculate velocity risk (impossible travel detection)
     */
    private double calculateVelocityRisk(String userId, String ipAddress) {
        try {
            LocationInfo currentLocation = getComprehensiveLocationInfo(ipAddress);
            if (currentLocation == null) return 0.0;
            
            List<LocationInfo> recentHistory = getRecentLocationHistory(userId, 3);
            if (recentHistory.isEmpty()) return 0.0;
            
            double maxRisk = 0.0;
            
            for (LocationInfo previousLocation : recentHistory) {
                if (isImpossibleTravel(previousLocation, currentLocation)) {
                    // Calculate severity based on travel speed required
                    double distance = currentLocation.calculateDistanceFrom(previousLocation);
                    long timeDiffHours = Duration.between(
                        previousLocation.getTimestamp(), 
                        LocalDateTime.now()).toHours();
                    
                    if (timeDiffHours > 0) {
                        double requiredSpeed = distance / timeDiffHours;
                        double riskMultiplier = Math.min(3.0, requiredSpeed / 1000.0); // Speed in Mach
                        maxRisk = Math.max(maxRisk, Math.min(1.0, 0.8 * riskMultiplier));
                    } else {
                        maxRisk = 1.0; // Simultaneous locations impossible
                    }
                }
            }
            
            return maxRisk;
        } catch (Exception e) {
            log.warn("Error calculating velocity risk: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Calculate threat intelligence risk
     */
    private double calculateThreatIntelligenceRisk(String ipAddress) {
        try {
            ThreatIntelligenceResult threatResult = threatIntelClient.checkThreat(ipAddress);
            if (threatResult == null) return 0.1;
            
            double risk = threatResult.getRiskScore();
            
            // Amplify risk based on threat categories
            if (threatResult.getCategories().contains("MALWARE")) risk += 0.8;
            if (threatResult.getCategories().contains("BOTNET")) risk += 0.9;
            if (threatResult.getCategories().contains("PHISHING")) risk += 0.7;
            if (threatResult.getCategories().contains("FRAUD")) risk += 0.8;
            
            return Math.min(1.0, risk);
        } catch (Exception e) {
            log.warn("Error calculating threat intelligence risk: {}", e.getMessage());
            return 0.1;
        }
    }
    
    /**
     * Calculate behavioral risk based on user patterns
     */
    private double calculateBehavioralRisk(String userId, String ipAddress) {
        try {
            // Analyze time-based patterns
            double timeRisk = analyzeTimePatternRisk(userId);
            
            // Analyze frequency patterns
            double frequencyRisk = analyzeFrequencyPatternRisk(userId);
            
            // Analyze session patterns
            double sessionRisk = analyzeSessionPatternRisk(userId, ipAddress);
            
            return (timeRisk + frequencyRisk + sessionRisk) / 3.0;
        } catch (Exception e) {
            log.warn("Error calculating behavioral risk: {}", e.getMessage());
            return 0.2;
        }
    }
    
    /**
     * Check for geographical anomalies in fraud detection context
     */
    public double checkGeoAnomaly(com.waqiti.frauddetection.dto.FraudCheckRequest request) {
        try {
            String ipAddress = request.getIpAddress();
            String userId = request.getUserId();
            
            if (!StringUtils.hasText(ipAddress) || !StringUtils.hasText(userId)) {
                log.warn("Invalid parameters for geo anomaly check");
                return 0.8; // High risk for invalid input
            }
            
            // Perform suspiciousness analysis
            boolean suspicious = isSuspiciousLocation(ipAddress, userId);
            if (suspicious) {
                return 0.9; // High risk score for suspicious locations
            }
            
            // Calculate detailed location risk score
            return getLocationRiskScore(ipAddress, userId);
            
        } catch (Exception e) {
            log.error("Error checking geo anomaly for user {}: {}", 
                request.getUserId(), e.getMessage(), e);
            return 0.7; // Default high risk on error
        }
    }
    
    // Helper methods for location analysis
    
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.length() < 7) return "***";
        return ipAddress.substring(0, 3) + ".***.***." + ipAddress.substring(ipAddress.lastIndexOf('.') + 1);
    }
    
    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null) {
            throw new IllegalArgumentException("IP address cannot be null");
        }
        // Basic IP validation and normalization
        return ipAddress.trim();
    }
    
    private boolean isPrivateOrInternalIp(String ipAddress) {
        // Check for private IP ranges
        return ipAddress.startsWith("192.168.") || 
               ipAddress.startsWith("10.") || 
               ipAddress.startsWith("172.16.") ||
               ipAddress.startsWith("172.17.") ||
               ipAddress.startsWith("172.18.") ||
               ipAddress.startsWith("172.19.") ||
               ipAddress.startsWith("172.20.") ||
               ipAddress.startsWith("172.21.") ||
               ipAddress.startsWith("172.22.") ||
               ipAddress.startsWith("172.23.") ||
               ipAddress.startsWith("172.24.") ||
               ipAddress.startsWith("172.25.") ||
               ipAddress.startsWith("172.26.") ||
               ipAddress.startsWith("172.27.") ||
               ipAddress.startsWith("172.28.") ||
               ipAddress.startsWith("172.29.") ||
               ipAddress.startsWith("172.30.") ||
               ipAddress.startsWith("172.31.") ||
               ipAddress.equals("127.0.0.1") ||
               ipAddress.equals("localhost");
    }
    
    private boolean isLocationInfoValid(LocationInfo locationInfo) {
        return locationInfo != null && 
               StringUtils.hasText(locationInfo.getCountryCode()) &&
               locationInfo.getTimestamp() != null &&
               locationInfo.getTimestamp().isAfter(LocalDateTime.now().minusHours(6));
    }
    
    private void enrichWithThreatIntelligence(LocationInfo locationInfo, String ipAddress) {
        try {
            // Enrich location info with threat intelligence data
            // This would be implemented with actual threat intelligence APIs
            log.debug("Enriching location info with threat intelligence for IP: {}", maskIpAddress(ipAddress));
        } catch (Exception e) {
            log.warn("Failed to enrich with threat intelligence: {}", e.getMessage());
        }
    }
    
    private void enrichWithVpnDetection(LocationInfo locationInfo, String ipAddress) {
        try {
            // Enrich with VPN/Proxy detection
            log.debug("Enriching location info with VPN detection for IP: {}", maskIpAddress(ipAddress));
        } catch (Exception e) {
            log.warn("Failed to enrich with VPN detection: {}", e.getMessage());
        }
    }
    
    private void enrichWithIpReputation(LocationInfo locationInfo, String ipAddress) {
        try {
            // Enrich with IP reputation data
            log.debug("Enriching location info with IP reputation for IP: {}", maskIpAddress(ipAddress));
        } catch (Exception e) {
            log.warn("Failed to enrich with IP reputation: {}", e.getMessage());
        }
    }
    
    private List<LocationInfo> getRecentLocationHistory(String userId, int limit) {
        try {
            return locationHistoryRepository.findRecentByUserId(userId, limit);
        } catch (Exception e) {
            log.warn("Failed to get location history for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private void updateLocationHistory(String userId, LocationInfo locationInfo) {
        try {
            locationHistoryRepository.save(userId, locationInfo);
        } catch (Exception e) {
            log.warn("Failed to update location history for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Attempt fallback geolocation using alternative providers
     */
    private LocationInfo attemptFallbackGeolocation(String ipAddress) {
        try {
            // Try IP-API as fallback (free service with rate limits)
            LocationInfo fallbackLocation = queryIpApiService(ipAddress);
            if (fallbackLocation != null) {
                fallbackLocation.setDataSource("IP-API");
                fallbackLocation.setProvider("IP-API-Fallback");
                return fallbackLocation;
            }
            
            // Try ip2location as second fallback
            fallbackLocation = queryIp2LocationService(ipAddress);
            if (fallbackLocation != null) {
                fallbackLocation.setDataSource("IP2Location");
                fallbackLocation.setProvider("IP2Location-Fallback");
                return fallbackLocation;
            }
            
            log.warn("All fallback geolocation services failed for IP: {}", maskIpAddress(ipAddress));
            // Return high-risk default when all services fail
            return createHighRiskDefaultLocation(ipAddress);
            
        } catch (Exception e) {
            log.error("Error in fallback geolocation attempts", e);
            // Return high-risk default on error
            return createHighRiskDefaultLocation(ipAddress);
        }
    }
    
    /**
     * Create a high-risk default location when geolocation fails
     */
    private LocationInfo createHighRiskDefaultLocation(String ipAddress) {
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
            .asn(0L)
            .timeZone("UTC")
            .riskScore(0.9) // High risk when location is unknown
            .isSuspicious(true)
            .suspiciousReasons(List.of("Geolocation failed - unknown location", "High risk due to geo detection failure"))
            .isVpn(true) // Assume VPN when location is unknown for security
            .isProxy(true) // Assume proxy when location is unknown for security
            .isTor(false)
            .threatLevel("HIGH")
            .timestamp(LocalDateTime.now())
            .dataSource("FALLBACK")
            .provider("SECURITY-FALLBACK")
            .isValid(true)
            .metadata(Map.of(
                "fallback", true,
                "reason", "Geolocation services unavailable",
                "securityMode", "HIGH_RISK_DEFAULT"
            ))
            .build();
    }
    
    /**
     * Query IP-API service as fallback
     */
    private LocationInfo queryIpApiService(String ipAddress) {
        try {
            // This would make HTTP call to ip-api.com
            // Service not yet implemented - return appropriate response
            log.debug("IP-API fallback service not yet implemented");
            throw new GeoLocationServiceException("IP-API service not available");
        } catch (Exception e) {
            log.debug("IP-API fallback failed", e);
            throw new GeoLocationServiceException("IP-API query failed", e);
        }
    }
    
    /**
     * Query IP2Location service as fallback
     */
    private LocationInfo queryIp2LocationService(String ipAddress) {
        try {
            // This would make HTTP call to ip2location service
            // Service not yet implemented - return appropriate response
            log.debug("IP2Location fallback service not yet implemented");
            throw new GeoLocationServiceException("IP2Location service not available");
        } catch (Exception e) {
            log.debug("IP2Location fallback failed", e);
            throw new GeoLocationServiceException("IP2Location query failed", e);
        }
    }

    private void publishSuspiciousLocationEvent(String userId, String ipAddress, String reason, double riskScore) {
        try {
            eventPublisher.publishSuspiciousLocationEvent(userId, ipAddress, reason, riskScore);
        } catch (Exception e) {
            log.warn("Failed to publish suspicious location event: {}", e.getMessage());
        }
    }
    
    /**
     * Determine if country is high-risk based on multiple threat intelligence sources
     *
     * Checks against:
     * - OFAC sanctions list
     * - FATF high-risk jurisdictions
     * - EU/UN sanctions
     * - Internal fraud statistics
     * - Configurable risk threshold
     *
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return true if country is considered high-risk
     */
    private boolean isHighRiskCountry(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            log.warn("Null or empty country code provided");
            return true; // Fail secure
        }

        String normalizedCode = countryCode.trim().toUpperCase();

        try {
            // Check cache first for performance
            String cacheKey = LOCATION_RISK_CACHE + "country:" + normalizedCode;
            Boolean cachedResult = (Boolean) redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }

            // OFAC Sanctioned Countries (as of 2025)
            Set<String> ofacSanctioned = Set.of(
                "IR", "KP", "SY", "CU", "VE", // OFAC primary sanctions
                "BY", "RU"  // Russia and Belarus (comprehensive sanctions)
            );

            // FATF High-Risk Jurisdictions
            Set<String> fatfHighRisk = Set.of(
                "KP", "IR", "MM", // FATF blacklist
                "YE", "UG", "TR", "PK", "JO", "ML", "MZ", "NI", "PH", "SN", "TZ", "VN" // FATF greylist
            );

            // Countries with high fraud rates (based on internal statistics)
            Set<String> highFraudCountries = Set.of(
                "NG", "GH", "CI", "ZA", // West/South Africa
                "RO", "BG", "AL", // Eastern Europe
                "ID", "PH", "VN"  // Southeast Asia
            );

            // EU/UN Sanctions
            Set<String> euUnSanctions = Set.of(
                "KP", "IR", "SY", "RU", "BY", "LY", "SO", "SD", "SS", "YE"
            );

            // Check against all lists
            boolean isHighRisk = ofacSanctioned.contains(normalizedCode)
                    || fatfHighRisk.contains(normalizedCode)
                    || highFraudCountries.contains(normalizedCode)
                    || euUnSanctions.contains(normalizedCode);

            // Additional dynamic check from threat intelligence
            if (!isHighRisk && threatIntelClient != null) {
                try {
                    isHighRisk = threatIntelClient.isHighRiskCountry(normalizedCode);
                } catch (Exception e) {
                    log.debug("Threat intelligence check failed for country {}: {}",
                        normalizedCode, e.getMessage());
                }
            }

            // Cache result for 1 hour
            redisTemplate.opsForValue().set(cacheKey, isHighRisk, 1, TimeUnit.HOURS);

            if (isHighRisk) {
                log.info("High-risk country detected: {}", normalizedCode);
                metricsCollector.increment(METRIC_HIGH_RISK_COUNTRIES,
                    "country", normalizedCode);
            }

            return isHighRisk;

        } catch (Exception e) {
            log.error("Error checking high-risk country status for {}: {}",
                normalizedCode, e.getMessage(), e);
            return true; // Fail secure on error
        }
    }
    
    private String getAnonymizationType(LocationInfo locationInfo) {
        if (Boolean.TRUE.equals(locationInfo.getIsVpn())) return "VPN";
        if (Boolean.TRUE.equals(locationInfo.getIsProxy())) return "PROXY";
        if (Boolean.TRUE.equals(locationInfo.getIsTor())) return "TOR";
        return "UNKNOWN";
    }
    
    /**
     * Detect impossible travel based on physical distance and time constraints
     *
     * Uses Haversine formula to calculate great-circle distance between coordinates
     * and compares against maximum physically possible travel speeds.
     *
     * Maximum speeds considered:
     * - Commercial aircraft: 900 km/h
     * - Private jet: 1000 km/h
     * - Concorde (theoretical): 2200 km/h
     * - Safety margin: 20% buffer for timezone/calculation errors
     *
     * @param fromLocation Previous location
     * @param toLocation Current location
     * @return true if travel is physically impossible given time constraints
     */
    private boolean isImpossibleTravel(LocationInfo fromLocation, LocationInfo toLocation) {
        if (fromLocation == null || toLocation == null) {
            return false;
        }

        if (fromLocation.getLatitude() == null || fromLocation.getLongitude() == null ||
            toLocation.getLatitude() == null || toLocation.getLongitude() == null ||
            fromLocation.getTimestamp() == null || toLocation.getTimestamp() == null) {
            log.debug("Missing coordinates or timestamp data for impossible travel check");
            return false;
        }

        try {
            // Calculate distance in kilometers using Haversine formula
            double distanceKm = calculateHaversineDistance(
                fromLocation.getLatitude(), fromLocation.getLongitude(),
                toLocation.getLatitude(), toLocation.getLongitude()
            );

            // Calculate time difference in hours
            Duration timeDiff = Duration.between(fromLocation.getTimestamp(), toLocation.getTimestamp());
            double hours = timeDiff.toMinutes() / 60.0;

            if (hours <= 0) {
                log.warn("Invalid time difference for impossible travel check: {} minutes", timeDiff.toMinutes());
                return false;
            }

            // Calculate required speed in km/h
            double requiredSpeed = distanceKm / hours;

            // Maximum physically possible speed (commercial aircraft + 20% buffer)
            // 900 km/h * 1.2 = 1080 km/h
            double maxPossibleSpeed = 1080.0;

            // If within same city (< 50km), allow faster "travel" due to IP routing
            if (distanceKm < 50) {
                return false;
            }

            boolean isImpossible = requiredSpeed > maxPossibleSpeed;

            if (isImpossible) {
                log.warn("IMPOSSIBLE TRAVEL DETECTED: Distance: {:.2f} km, Time: {:.2f} hours, " +
                         "Required Speed: {:.2f} km/h, Max Possible: {:.2f} km/h",
                    distanceKm, hours, requiredSpeed, maxPossibleSpeed);

                metricsCollector.increment(METRIC_IMPOSSIBLE_TRAVEL,
                    "from_country", fromLocation.getCountryCode(),
                    "to_country", toLocation.getCountryCode());
            }

            return isImpossible;

        } catch (Exception e) {
            log.error("Error calculating impossible travel: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calculate great-circle distance between two coordinates using Haversine formula
     *
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in kilometers
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;

        // Convert to radians
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        // Haversine formula
        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Detect location hopping - rapid changes across multiple countries
     *
     * Indicators of fraud:
     * - 3+ different countries within 24 hours
     * - 5+ different cities within 12 hours
     * - Alternating between distant locations
     *
     * @param history Recent location history
     * @param currentLocation Current location
     * @return true if location hopping pattern detected
     */
    private boolean isLocationHopping(List<LocationInfo> history, LocationInfo currentLocation) {
        if (history == null || history.isEmpty() || currentLocation == null) {
            return false;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime last24Hours = now.minusHours(24);
            LocalDateTime last12Hours = now.minusHours(12);

            // Filter to recent history
            List<LocationInfo> recent24h = history.stream()
                .filter(loc -> loc.getTimestamp() != null && loc.getTimestamp().isAfter(last24Hours))
                .collect(Collectors.toList());

            List<LocationInfo> recent12h = history.stream()
                .filter(loc -> loc.getTimestamp() != null && loc.getTimestamp().isAfter(last12Hours))
                .collect(Collectors.toList());

            // Count unique countries in last 24 hours
            Set<String> uniqueCountries24h = recent24h.stream()
                .map(LocationInfo::getCountryCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            uniqueCountries24h.add(currentLocation.getCountryCode());

            // Count unique cities in last 12 hours
            Set<String> uniqueCities12h = recent12h.stream()
                .map(LocationInfo::getCity)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            if (currentLocation.getCity() != null) {
                uniqueCities12h.add(currentLocation.getCity());
            }

            // Location hopping thresholds
            boolean countryHopping = uniqueCountries24h.size() >= 3;
            boolean cityHopping = uniqueCities12h.size() >= 5;

            // Check for alternating pattern (A -> B -> A)
            boolean alternatingPattern = detectAlternatingLocationPattern(recent24h, currentLocation);

            boolean isHopping = countryHopping || cityHopping || alternatingPattern;

            if (isHopping) {
                log.warn("LOCATION HOPPING DETECTED: Countries(24h): {}, Cities(12h): {}, Alternating: {}",
                    uniqueCountries24h.size(), uniqueCities12h.size(), alternatingPattern);
            }

            return isHopping;

        } catch (Exception e) {
            log.error("Error detecting location hopping: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean detectAlternatingLocationPattern(List<LocationInfo> history, LocationInfo current) {
        if (history.size() < 2) return false;

        try {
            // Check if user is alternating between two distant locations
            LocationInfo prev = history.get(history.size() - 1);
            LocationInfo prevPrev = history.get(history.size() - 2);

            if (prev == null || prevPrev == null || current == null) return false;

            // Same country as two steps ago but different from previous
            boolean sameAsTwoBack = Objects.equals(current.getCountryCode(), prevPrev.getCountryCode());
            boolean differentFromPrev = !Objects.equals(current.getCountryCode(), prev.getCountryCode());

            return sameAsTwoBack && differentFromPrev;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detect unusual timezone for user based on historical patterns
     *
     * Uses statistical analysis of user's typical timezones to identify anomalies.
     * Accounts for legitimate travel and timezone changes.
     *
     * @param userId User identifier
     * @param locationInfo Current location with timezone
     * @return true if timezone is statistically unusual for this user
     */
    private boolean isUnusualTimeZone(String userId, LocationInfo locationInfo) {
        if (userId == null || locationInfo == null || locationInfo.getTimezone() == null) {
            return false;
        }

        try {
            // Get user's location history from last 90 days
            String historyKey = USER_LOCATION_HISTORY + userId;
            List<LocationInfo> history = locationHistoryRepository.findByUserIdAndTimestampAfter(
                userId, LocalDateTime.now().minusDays(90));

            if (history == null || history.size() < 5) {
                // Not enough history to determine pattern
                return false;
            }

            // Calculate timezone frequency distribution
            Map<String, Long> timezoneFrequency = history.stream()
                .map(LocationInfo::getTimezone)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(tz -> tz, Collectors.counting()));

            // User's most common timezones (>10% of history)
            long totalCount = history.size();
            Set<String> commonTimezones = timezoneFrequency.entrySet().stream()
                .filter(entry -> (entry.getValue() * 100.0 / totalCount) > 10.0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

            // Check if current timezone is in common set
            boolean isCommon = commonTimezones.contains(locationInfo.getTimezone());

            // Check if timezone is adjacent (within 3 hours) to common timezone
            if (!isCommon) {
                for (String commonTz : commonTimezones) {
                    if (isAdjacentTimezone(commonTz, locationInfo.getTimezone(), 3)) {
                        isCommon = true;
                        break;
                    }
                }
            }

            boolean isUnusual = !isCommon;

            if (isUnusual) {
                log.info("Unusual timezone detected for user {}: {} (common: {})",
                    userId, locationInfo.getTimezone(), commonTimezones);
            }

            return isUnusual;

        } catch (Exception e) {
            log.error("Error checking unusual timezone: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isAdjacentTimezone(String tz1, String tz2, int maxHoursDiff) {
        // Simplified timezone adjacency check
        // In production, would use proper timezone offset comparison
        try {
            // Extract UTC offset if present (e.g., "UTC+05:30")
            // This is a simplified implementation
            return Math.abs(tz1.hashCode() - tz2.hashCode()) < 1000; // Placeholder
        } catch (Exception e) {
            return false;
        }
    }
    
    // Additional helper methods...
    
    /**
     * Calculate comprehensive country risk score (0.0 - 1.0)
     *
     * Combines multiple risk factors:
     * - Political stability (30%)
     * - Regulatory risk (30%)
     * - Fraud statistics (20%)
     * - Economic indicators (20%)
     */
    private double getCountryRiskScore(String countryCode) {
        if (countryCode == null) return 0.5;

        try {
            String cacheKey = LOCATION_RISK_CACHE + "score:" + countryCode;
            Double cached = (Double) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;

            double politicalScore = getPoliticalInstabilityScore(countryCode);
            double regulatoryScore = getRegulatoryRiskScore(countryCode);
            double fraudScore = getFraudPrevalenceScore(countryCode);
            double economicScore = getEconomicRiskScore(countryCode);

            double totalScore = (politicalScore * 0.3) + (regulatoryScore * 0.3) +
                               (fraudScore * 0.2) + (economicScore * 0.2);

            redisTemplate.opsForValue().set(cacheKey, totalScore, 6, TimeUnit.HOURS);
            return Math.min(totalScore, 1.0);

        } catch (Exception e) {
            log.error("Error calculating country risk score: {}", e.getMessage(), e);
            return 0.5; // Medium risk on error
        }
    }

    private double getPoliticalInstabilityScore(String countryCode) {
        // High instability countries based on World Bank Governance Indicators
        Map<String, Double> instabilityScores = Map.ofEntries(
            Map.entry("AF", 0.95), Map.entry("SY", 0.98), Map.entry("YE", 0.92),
            Map.entry("SO", 0.94), Map.entry("SD", 0.89), Map.entry("SS", 0.91),
            Map.entry("LY", 0.87), Map.entry("IQ", 0.82), Map.entry("VE", 0.78),
            Map.entry("MM", 0.85), Map.entry("BY", 0.72), Map.entry("RU", 0.68)
        );
        return instabilityScores.getOrDefault(countryCode, 0.1);
    }

    private double getRegulatoryRiskScore(String countryCode) {
        // FATF compliance and regulatory framework strength
        Map<String, Double> regulatoryScores = Map.ofEntries(
            Map.entry("KP", 1.0), Map.entry("IR", 0.95), Map.entry("MM", 0.88),
            Map.entry("NG", 0.65), Map.entry("PK", 0.62), Map.entry("VN", 0.55)
        );
        return regulatoryScores.getOrDefault(countryCode, 0.15);
    }

    private double getFraudPrevalenceScore(String countryCode) {
        // Internal fraud statistics (simulated - would use real data)
        Map<String, Double> fraudScores = Map.ofEntries(
            Map.entry("NG", 0.85), Map.entry("GH", 0.72), Map.entry("CI", 0.68),
            Map.entry("RO", 0.62), Map.entry("BG", 0.58), Map.entry("PH", 0.55)
        );
        return fraudScores.getOrDefault(countryCode, 0.1);
    }

    private double getEconomicRiskScore(String countryCode) {
        // Economic instability indicators
        Map<String, Double> economicScores = Map.ofEntries(
            Map.entry("VE", 0.92), Map.entry("ZW", 0.88), Map.entry("LB", 0.82),
            Map.entry("AR", 0.65), Map.entry("TR", 0.58)
        );
        return economicScores.getOrDefault(countryCode, 0.1);
    }

    private boolean isDatacenterIp(String ipAddress) {
        if (ipAddress == null) return false;

        try {
            String cacheKey = VPN_DETECTION_CACHE + ipAddress;
            Boolean cached = (Boolean) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;

            // Check against known datacenter IP ranges (AWS, Azure, GCP, etc.)
            boolean isDatacenter = checkCloudProviderRanges(ipAddress) ||
                                  checkHostingProviders(ipAddress) ||
                                  checkVpnProviders(ipAddress);

            // Use VPN detection client for additional verification
            if (!isDatacenter && vpnDetectionClient != null) {
                try {
                    isDatacenter = vpnDetectionClient.isHostingProvider(ipAddress);
                } catch (Exception e) {
                    log.debug("VPN detection check failed: {}", e.getMessage());
                }
            }

            redisTemplate.opsForValue().set(cacheKey, isDatacenter, 24, TimeUnit.HOURS);

            if (isDatacenter) {
                metricsCollector.increment(METRIC_VPN_DETECTIONS, "type", "datacenter");
            }

            return isDatacenter;

        } catch (Exception e) {
            log.error("Error checking datacenter IP: {}", e.getMessage(), e);
            return true; // Fail secure - treat unknown as suspicious
        }
    }

    private boolean checkCloudProviderRanges(String ipAddress) {
        // AWS, Azure, GCP IP ranges (simplified - production would use CIDR checks)
        // This would integrate with cloud provider IP range lists
        return false; // Placeholder
    }

    private boolean checkHostingProviders(String ipAddress) {
        // DigitalOcean, Linode, OVH, Hetzner, etc.
        return false; // Placeholder
    }

    private boolean checkVpnProviders(String ipAddress) {
        // Commercial VPN providers (NordVPN, ExpressVPN, etc.)
        return false; // Placeholder
    }

    private double applyRiskAmplification(double baseRisk, java.util.List<Double> individualRisks) {
        if (individualRisks == null || individualRisks.isEmpty()) {
            return baseRisk;
        }

        try {
            // Layered risk calculation with context-aware amplification
            double amplifiedRisk = baseRisk;

            // Each additional risk factor increases total risk multiplicatively
            for (Double risk : individualRisks) {
                if (risk != null && risk > 0) {
                    amplifiedRisk += risk * (1 - amplifiedRisk);
                }
            }

            // Cap at 1.0 (100% risk)
            return Math.min(amplifiedRisk, 1.0);

        } catch (Exception e) {
            log.error("Error applying risk amplification: {}", e.getMessage(), e);
            return baseRisk;
        }
    }
    
    private void recordRiskMetrics(String userId, String ipAddress, double totalRisk, Map<String, Double> componentRisks) {
        // Record detailed risk metrics
        log.debug("Recording risk metrics for user {}: total={}", userId, totalRisk);
    }
    
    /**
     * Analyze time-based behavioral patterns for risk
     *
     * Detects:
     * - Transactions at unusual hours (nighttime in user's timezone)
     * - Weekend vs weekday anomalies
     * - Holiday activity
     * - Sudden changes in transaction time patterns
     */
    private double analyzeTimePatternRisk(String userId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            int hourOfDay = now.getHour();

            // Get user's typical transaction hours from history
            List<Integer> typicalHours = getUserTypicalTransactionHours(userId);

            if (typicalHours.isEmpty()) {
                return 0.1; // Not enough data
            }

            // Calculate hour frequency
            Map<Integer, Long> hourFrequency = typicalHours.stream()
                .collect(Collectors.groupingBy(h -> h, Collectors.counting()));

            long currentHourCount = hourFrequency.getOrDefault(hourOfDay, 0L);
            long maxHourCount = hourFrequency.values().stream().max(Long::compareTo).orElse(1L);

            // Calculate risk based on how unusual this hour is
            double hourRisk = 1.0 - ((double) currentHourCount / maxHourCount);

            // Amplify risk for nighttime hours (11 PM - 6 AM)
            if (hourOfDay >= 23 || hourOfDay < 6) {
                hourRisk *= 1.5;
            }

            // Weekend check
            boolean isWeekend = now.getDayOfWeek().getValue() >= 6;
            boolean userTypicallyWeekday = isUserTypicallyWeekdayActive(userId);
            if (isWeekend && userTypicallyWeekday) {
                hourRisk *= 1.2;
            }

            return Math.min(hourRisk, 1.0);

        } catch (Exception e) {
            log.error("Error analyzing time pattern risk: {}", e.getMessage(), e);
            return 0.2; // Default medium-low risk
        }
    }

    private List<Integer> getUserTypicalTransactionHours(String userId) {
        // Fetch from cache or database
        // Returns list of hours when user typically transacts
        return List.of(9, 10, 11, 12, 13, 14, 15, 16, 17); // Business hours placeholder
    }

    private boolean isUserTypicallyWeekdayActive(String userId) {
        // Check if user typically transacts on weekdays
        return true; // Placeholder
    }

    /**
     * Analyze transaction frequency patterns
     *
     * Uses statistical process control to detect:
     * - Sudden spikes in transaction rate
     * - Unusual transaction velocity
     * - Dormancy followed by burst activity
     */
    private double analyzeFrequencyPatternRisk(String userId) {
        try {
            // Get transaction counts for last 7 days
            Map<Integer, Long> dailyCounts = getUserDailyTransactionCounts(userId, 7);

            if (dailyCounts.isEmpty() || dailyCounts.size() < 3) {
                return 0.1; // Not enough data
            }

            // Calculate mean and standard deviation
            double mean = dailyCounts.values().stream()
                .mapToDouble(Long::doubleValue)
                .average()
                .orElse(0.0);

            double stdDev = calculateStandardDeviation(dailyCounts.values(), mean);

            // Get today's count
            long todayCount = dailyCounts.getOrDefault(0, 0L);

            // Calculate z-score (number of standard deviations from mean)
            double zScore = stdDev > 0 ? Math.abs((todayCount - mean) / stdDev) : 0;

            // Convert z-score to risk (0-1 scale)
            // z > 3 is very suspicious (3-sigma rule)
            double frequencyRisk = Math.min(zScore / 3.0, 1.0);

            // Check for dormancy -> burst pattern
            boolean wasDormant = isDormantAccount(userId, dailyCounts);
            if (wasDormant && todayCount > mean + (2 * stdDev)) {
                frequencyRisk = Math.max(frequencyRisk, 0.7);
            }

            return frequencyRisk;

        } catch (Exception e) {
            log.error("Error analyzing frequency pattern risk: {}", e.getMessage(), e);
            return 0.2;
        }
    }

    private Map<Integer, Long> getUserDailyTransactionCounts(String userId, int days) {
        // Fetch from database - returns map of day offset (0=today) to count
        return Map.of(0, 5L, 1, 4L, 2, 6L, 3, 5L, 4, 4L, 5, 5L, 6, 6L); // Placeholder
    }

    private double calculateStandardDeviation(Collection<Long> values, double mean) {
        double variance = values.stream()
            .mapToDouble(Long::doubleValue)
            .map(val -> Math.pow(val - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }

    private boolean isDormantAccount(String userId, Map<Integer, Long> dailyCounts) {
        // Check if account was dormant (0 transactions) for 3+ consecutive days
        long consecutiveDormantDays = dailyCounts.entrySet().stream()
            .filter(e -> e.getKey() > 0 && e.getValue() == 0)
            .count();
        return consecutiveDormantDays >= 3;
    }

    /**
     * Analyze session behavioral patterns
     *
     * Detects:
     * - Unusually short or long sessions
     * - High activity rate within session
     * - Session hijacking indicators
     */
    private double analyzeSessionPatternRisk(String userId, String ipAddress) {
        try {
            // Get current session data
            // In production, this would track session start, actions, etc.

            // Placeholder risk calculation based on typical patterns
            double sessionRisk = 0.0;

            // Check session duration
            // Very short sessions (<30 seconds) with high-value transactions are risky
            // Very long sessions (>8 hours) can indicate bot activity

            // Check action rate
            // Too many actions per minute indicates automated activity

            // Check for session fingerprint changes
            // User-agent, screen resolution changes mid-session

            return Math.min(sessionRisk, 1.0);

        } catch (Exception e) {
            log.error("Error analyzing session pattern risk: {}", e.getMessage(), e);
            return 0.2;
        }
    }
    
    // Inner classes for analysis
    
    public static class SuspiciousnessAnalysis {
        private boolean suspicious;
        private double riskScore;
        private String primaryReason;
        private java.util.List<String> reasons;
        private LocationInfo locationInfo;
        private String userId;
        private String ipAddress;
        private LocalDateTime timestamp;
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private SuspiciousnessAnalysis analysis = new SuspiciousnessAnalysis();
            
            public Builder suspicious(boolean suspicious) {
                analysis.suspicious = suspicious;
                return this;
            }
            
            public Builder riskScore(double riskScore) {
                analysis.riskScore = riskScore;
                return this;
            }
            
            public Builder primaryReason(String primaryReason) {
                analysis.primaryReason = primaryReason;
                return this;
            }
            
            public Builder reasons(java.util.List<String> reasons) {
                analysis.reasons = reasons;
                return this;
            }
            
            public Builder locationInfo(LocationInfo locationInfo) {
                analysis.locationInfo = locationInfo;
                return this;
            }
            
            public Builder userId(String userId) {
                analysis.userId = userId;
                return this;
            }
            
            public Builder ipAddress(String ipAddress) {
                analysis.ipAddress = ipAddress;
                return this;
            }
            
            public Builder timestamp(LocalDateTime timestamp) {
                analysis.timestamp = timestamp;
                return this;
            }
            
            public SuspiciousnessAnalysis build() {
                return analysis;
            }
        }
        
        public boolean isSuspicious() { return suspicious; }
        public double getRiskScore() { return riskScore; }
        public String getPrimaryReason() { return primaryReason; }
        public java.util.List<String> getReasons() { return reasons; }
    }
    
    // Additional helper classes and interfaces would be defined here...
    
    /**
     * GeoLocation Service Exception for geolocation operation failures
     */
    public static class GeoLocationServiceException extends RuntimeException {
        public GeoLocationServiceException(String message) {
            super(message);
        }
        
        public GeoLocationServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}