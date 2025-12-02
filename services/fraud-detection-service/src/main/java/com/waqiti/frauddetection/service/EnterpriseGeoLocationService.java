package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.LocationInfo;
import com.waqiti.frauddetection.dto.GeoRiskAssessment;
import com.waqiti.frauddetection.dto.TravelVelocity;
import com.waqiti.frauddetection.repository.GeoLocationRepository;
import com.waqiti.frauddetection.repository.CountryRiskRepository;
import com.waqiti.frauddetection.config.GeoLocationConfig;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.security.encryption.EncryptionService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise-grade geolocation service with comprehensive fraud detection
 * capabilities including IP analysis, travel velocity checks, and risk scoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnterpriseGeoLocationService {
    
    private final GeoLocationRepository geoLocationRepository;
    private final CountryRiskRepository countryRiskRepository;
    private final CacheService cacheService;
    private final EncryptionService encryptionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final WebClient.Builder webClientBuilder;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    
    @Value("${geo.service.ipapi.key}")
    private String ipApiKey;
    
    @Value("${geo.service.ipstack.key}")
    private String ipStackKey;
    
    @Value("${geo.service.ip2location.key}")
    private String ip2LocationKey;
    
    @Value("${geo.service.maxmind.key}")
    private String maxMindKey;
    
    @Value("${geo.service.cache.ttl:3600}")
    private int cacheTtlSeconds;
    
    @Value("${geo.risk.high-risk-threshold:0.7}")
    private double highRiskThreshold;
    
    @Value("${geo.risk.impossible-travel-speed:800}")
    private double impossibleTravelSpeedKmh;
    
    private WebClient ipApiClient;
    private WebClient ipStackClient;
    private WebClient ip2LocationClient;
    private WebClient maxMindClient;
    
    private CircuitBreaker ipApiCircuitBreaker;
    private CircuitBreaker ipStackCircuitBreaker;
    private Retry geoServiceRetry;
    
    // Country risk scores (0.0-1.0)
    private static final Map<String, Double> COUNTRY_RISK_SCORES = new ConcurrentHashMap<>();
    private static final Map<String, Double> POLITICAL_INSTABILITY_SCORES = new ConcurrentHashMap<>();
    private static final Map<String, Double> REGULATORY_RISK_SCORES = new ConcurrentHashMap<>();
    
    // High-risk countries for financial services
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "IR", "KP", "SY", "CU", "SD", "ZW", "BY", "MM", "VE", "YE",
        "AF", "IQ", "LY", "SO", "CF", "CD", "ER", "GN", "LR", "ML"
    );
    
    // OFAC sanctioned countries
    private static final Set<String> SANCTIONED_COUNTRIES = Set.of(
        "IR", "KP", "SY", "CU", "RU", "BY", "MM"
    );
    
    // Known datacenter IP ranges (CIDR notation)
    private static final List<String> DATACENTER_IP_RANGES = Arrays.asList(
        "13.0.0.0/8",      // Amazon AWS
        "34.64.0.0/10",    // Google Cloud
        "40.74.0.0/15",    // Microsoft Azure
        "104.16.0.0/12",   // Cloudflare
        "162.125.0.0/16",  // DigitalOcean
        "45.55.0.0/16",    // DigitalOcean
        "159.65.0.0/16",   // DigitalOcean
        "138.68.0.0/16",   // DigitalOcean
        "178.62.0.0/16",   // DigitalOcean
        "167.99.0.0/16"    // DigitalOcean
    );
    
    @PostConstruct
    public void init() {
        // Initialize WebClients with proper configuration
        this.ipApiClient = webClientBuilder
            .baseUrl("http://ip-api.com/json/")
            .defaultHeader(HttpHeaders.USER_AGENT, "Waqiti-Fraud-Detection/1.0")
            .build();
            
        this.ipStackClient = webClientBuilder
            .baseUrl("http://api.ipstack.com/")
            .defaultHeader(HttpHeaders.USER_AGENT, "Waqiti-Fraud-Detection/1.0")
            .build();
            
        this.ip2LocationClient = webClientBuilder
            .baseUrl("https://api.ip2location.com/v2/")
            .defaultHeader(HttpHeaders.USER_AGENT, "Waqiti-Fraud-Detection/1.0")
            .defaultHeader("Key", ip2LocationKey)
            .build();
            
        this.maxMindClient = webClientBuilder
            .baseUrl("https://geoip.maxmind.com/geoip/v2.1/")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + 
                Base64.getEncoder().encodeToString((maxMindKey + ":").getBytes()))
            .build();
        
        // Initialize circuit breakers
        this.ipApiCircuitBreaker = circuitBreakerRegistry.circuitBreaker("ipApiService");
        this.ipStackCircuitBreaker = circuitBreakerRegistry.circuitBreaker("ipStackService");
        this.geoServiceRetry = retryRegistry.retry("geoService");
        
        // Initialize risk scores
        initializeRiskScores();
    }
    
    private void initializeRiskScores() {
        // Country risk scores based on financial crime indices
        COUNTRY_RISK_SCORES.put("IR", 0.95); // Iran
        COUNTRY_RISK_SCORES.put("KP", 0.95); // North Korea
        COUNTRY_RISK_SCORES.put("SY", 0.90); // Syria
        COUNTRY_RISK_SCORES.put("CU", 0.85); // Cuba
        COUNTRY_RISK_SCORES.put("SD", 0.85); // Sudan
        COUNTRY_RISK_SCORES.put("ZW", 0.80); // Zimbabwe
        COUNTRY_RISK_SCORES.put("BY", 0.80); // Belarus
        COUNTRY_RISK_SCORES.put("MM", 0.85); // Myanmar
        COUNTRY_RISK_SCORES.put("VE", 0.75); // Venezuela
        COUNTRY_RISK_SCORES.put("YE", 0.85); // Yemen
        COUNTRY_RISK_SCORES.put("AF", 0.90); // Afghanistan
        COUNTRY_RISK_SCORES.put("NG", 0.65); // Nigeria
        COUNTRY_RISK_SCORES.put("PK", 0.60); // Pakistan
        COUNTRY_RISK_SCORES.put("BD", 0.55); // Bangladesh
        COUNTRY_RISK_SCORES.put("KE", 0.50); // Kenya
        COUNTRY_RISK_SCORES.put("UA", 0.60); // Ukraine
        COUNTRY_RISK_SCORES.put("RU", 0.70); // Russia
        COUNTRY_RISK_SCORES.put("CN", 0.45); // China
        COUNTRY_RISK_SCORES.put("IN", 0.40); // India
        COUNTRY_RISK_SCORES.put("BR", 0.35); // Brazil
        COUNTRY_RISK_SCORES.put("MX", 0.40); // Mexico
        COUNTRY_RISK_SCORES.put("US", 0.10); // United States
        COUNTRY_RISK_SCORES.put("GB", 0.10); // United Kingdom
        COUNTRY_RISK_SCORES.put("DE", 0.05); // Germany
        COUNTRY_RISK_SCORES.put("CH", 0.05); // Switzerland
        COUNTRY_RISK_SCORES.put("SG", 0.10); // Singapore
        COUNTRY_RISK_SCORES.put("JP", 0.05); // Japan
        
        // Political instability scores
        POLITICAL_INSTABILITY_SCORES.put("AF", 0.95);
        POLITICAL_INSTABILITY_SCORES.put("YE", 0.90);
        POLITICAL_INSTABILITY_SCORES.put("SY", 0.95);
        POLITICAL_INSTABILITY_SCORES.put("LY", 0.85);
        POLITICAL_INSTABILITY_SCORES.put("SO", 0.90);
        POLITICAL_INSTABILITY_SCORES.put("SD", 0.80);
        POLITICAL_INSTABILITY_SCORES.put("VE", 0.75);
        POLITICAL_INSTABILITY_SCORES.put("MM", 0.80);
        POLITICAL_INSTABILITY_SCORES.put("HT", 0.70);
        POLITICAL_INSTABILITY_SCORES.put("ML", 0.75);
        
        // Regulatory risk scores (AML/CFT compliance)
        REGULATORY_RISK_SCORES.put("IR", 0.95);
        REGULATORY_RISK_SCORES.put("KP", 0.95);
        REGULATORY_RISK_SCORES.put("MM", 0.85);
        REGULATORY_RISK_SCORES.put("SY", 0.90);
        REGULATORY_RISK_SCORES.put("YE", 0.85);
        REGULATORY_RISK_SCORES.put("AF", 0.90);
        REGULATORY_RISK_SCORES.put("PK", 0.70);
        REGULATORY_RISK_SCORES.put("TN", 0.65);
        REGULATORY_RISK_SCORES.put("MA", 0.60);
        REGULATORY_RISK_SCORES.put("JM", 0.65);
    }
    
    /**
     * Comprehensive geolocation lookup with multiple provider fallback
     */
    @Cacheable(value = "geoLocation", key = "#ipAddress", unless = "#result == null")
    public CompletableFuture<LocationInfo> lookupLocation(String ipAddress) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Try primary provider (MaxMind)
                LocationInfo location = queryMaxMindService(ipAddress);
                if (location != null) {
                    sample.stop(meterRegistry.timer("geo.lookup.duration", "provider", "maxmind"));
                    return enrichLocationInfo(location);
                }
                
                // Try secondary provider (IPStack)
                location = queryIpStackService(ipAddress);
                if (location != null) {
                    sample.stop(meterRegistry.timer("geo.lookup.duration", "provider", "ipstack"));
                    return enrichLocationInfo(location);
                }
                
                // Try tertiary provider (IP-API)
                location = queryIpApiService(ipAddress);
                if (location != null) {
                    sample.stop(meterRegistry.timer("geo.lookup.duration", "provider", "ipapi"));
                    return enrichLocationInfo(location);
                }
                
                // Try quaternary provider (IP2Location)
                location = queryIp2LocationService(ipAddress);
                if (location != null) {
                    sample.stop(meterRegistry.timer("geo.lookup.duration", "provider", "ip2location"));
                    return enrichLocationInfo(location);
                }
                
                log.warn("All geolocation providers failed for IP: {}", ipAddress);
                meterRegistry.counter("geo.lookup.failures").increment();
                
                return createUnknownLocation(ipAddress);
                
            } catch (Exception e) {
                log.error("Error during geolocation lookup for IP: {}", ipAddress, e);
                meterRegistry.counter("geo.lookup.errors").increment();
                return createUnknownLocation(ipAddress);
            }
        });
    }
    
    /**
     * Query MaxMind GeoIP2 service
     */
    private LocationInfo queryMaxMindService(String ipAddress) {
        try {
            return ipApiCircuitBreaker.executeSupplier(() -> 
                geoServiceRetry.executeSupplier(() -> {
                    Map<String, Object> response = maxMindClient
                        .get()
                        .uri("/city/" + ipAddress)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(5));
                    
                    if (response != null && !response.containsKey("error")) {
                        return parseMaxMindResponse(response, ipAddress);
                    }
                    return null;
                })
            );
        } catch (Exception e) {
            log.debug("MaxMind lookup failed for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }
    
    /**
     * Query IPStack service
     */
    private LocationInfo queryIpStackService(String ipAddress) {
        try {
            return ipStackCircuitBreaker.executeSupplier(() ->
                geoServiceRetry.executeSupplier(() -> {
                    Map<String, Object> response = ipStackClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                            .path(ipAddress)
                            .queryParam("access_key", ipStackKey)
                            .queryParam("security", "1")
                            .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(5));
                    
                    if (response != null && response.get("success") != Boolean.FALSE) {
                        return parseIpStackResponse(response, ipAddress);
                    }
                    return null;
                })
            );
        } catch (Exception e) {
            log.debug("IPStack lookup failed for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }
    
    /**
     * Query IP-API service
     */
    private LocationInfo queryIpApiService(String ipAddress) {
        try {
            Map<String, Object> response = ipApiClient
                .get()
                .uri(ipAddress + "?fields=status,country,countryCode,region,regionName,city," +
                     "zip,lat,lon,timezone,isp,org,as,proxy,hosting,query")
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(3));
            
            if (response != null && "success".equals(response.get("status"))) {
                return parseIpApiResponse(response, ipAddress);
            }
            return null;
        } catch (Exception e) {
            log.debug("IP-API lookup failed for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }
    
    /**
     * Query IP2Location service
     */
    private LocationInfo queryIp2LocationService(String ipAddress) {
        try {
            Map<String, Object> response = ip2LocationClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("ip", ipAddress)
                    .queryParam("key", ip2LocationKey)
                    .queryParam("package", "WS25")
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(3));
            
            if (response != null && !"INVALID_IP_ADDRESS".equals(response.get("response"))) {
                return parseIp2LocationResponse(response, ipAddress);
            }
            return null;
        } catch (Exception e) {
            log.debug("IP2Location lookup failed for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse MaxMind response
     */
    private LocationInfo parseMaxMindResponse(Map<String, Object> response, String ipAddress) {
        Map<String, Object> location = (Map<String, Object>) response.get("location");
        Map<String, Object> country = (Map<String, Object>) response.get("country");
        Map<String, Object> city = (Map<String, Object>) response.get("city");
        Map<String, Object> traits = (Map<String, Object>) response.get("traits");
        
        return LocationInfo.builder()
            .ipAddress(ipAddress)
            .country((String) ((Map) country.get("names")).get("en"))
            .countryCode((String) country.get("iso_code"))
            .city((String) ((Map) city.get("names")).get("en"))
            .latitude((Double) location.get("latitude"))
            .longitude((Double) location.get("longitude"))
            .timezone((String) location.get("time_zone"))
            .accuracyRadius((Integer) location.get("accuracy_radius"))
            .isp((String) traits.get("isp"))
            .organization((String) traits.get("organization"))
            .isVpn((Boolean) traits.get("is_anonymous_vpn"))
            .isProxy((Boolean) traits.get("is_anonymous_proxy"))
            .isTor((Boolean) traits.get("is_tor_exit_node"))
            .isHosting((Boolean) traits.get("is_hosting_provider"))
            .provider("MaxMind")
            .build();
    }
    
    /**
     * Parse IPStack response
     */
    private LocationInfo parseIpStackResponse(Map<String, Object> response, String ipAddress) {
        Map<String, Object> security = (Map<String, Object>) response.get("security");
        
        return LocationInfo.builder()
            .ipAddress(ipAddress)
            .country((String) response.get("country_name"))
            .countryCode((String) response.get("country_code"))
            .city((String) response.get("city"))
            .region((String) response.get("region_name"))
            .latitude((Double) response.get("latitude"))
            .longitude((Double) response.get("longitude"))
            .timezone((String) ((Map) response.get("time_zone")).get("id"))
            .isVpn(security != null ? (Boolean) security.get("is_vpn") : false)
            .isProxy(security != null ? (Boolean) security.get("is_proxy") : false)
            .isTor(security != null ? (Boolean) security.get("is_tor") : false)
            .provider("IPStack")
            .build();
    }
    
    /**
     * Parse IP-API response
     */
    private LocationInfo parseIpApiResponse(Map<String, Object> response, String ipAddress) {
        return LocationInfo.builder()
            .ipAddress(ipAddress)
            .country((String) response.get("country"))
            .countryCode((String) response.get("countryCode"))
            .city((String) response.get("city"))
            .region((String) response.get("regionName"))
            .latitude((Double) response.get("lat"))
            .longitude((Double) response.get("lon"))
            .timezone((String) response.get("timezone"))
            .isp((String) response.get("isp"))
            .organization((String) response.get("org"))
            .isVpn((Boolean) response.get("proxy"))
            .isProxy((Boolean) response.get("proxy"))
            .isHosting((Boolean) response.get("hosting"))
            .provider("IP-API")
            .build();
    }
    
    /**
     * Parse IP2Location response
     */
    private LocationInfo parseIp2LocationResponse(Map<String, Object> response, String ipAddress) {
        return LocationInfo.builder()
            .ipAddress(ipAddress)
            .country((String) response.get("country_name"))
            .countryCode((String) response.get("country_code"))
            .city((String) response.get("city_name"))
            .region((String) response.get("region_name"))
            .latitude(Double.parseDouble((String) response.get("latitude")))
            .longitude(Double.parseDouble((String) response.get("longitude")))
            .timezone((String) response.get("time_zone"))
            .isp((String) response.get("isp"))
            .isVpn("VPN".equals(response.get("usage_type")))
            .isProxy("PX".equals(response.get("proxy_type")))
            .provider("IP2Location")
            .build();
    }
    
    /**
     * Enrich location info with additional risk data
     */
    private LocationInfo enrichLocationInfo(LocationInfo location) {
        location.setRiskScore(calculateLocationRiskScore(location));
        location.setIsHighRisk(location.getRiskScore() > highRiskThreshold);
        location.setIsSanctioned(SANCTIONED_COUNTRIES.contains(location.getCountryCode()));
        location.setIsDatacenter(checkIfDatacenterIp(location.getIpAddress()));
        location.setLastUpdated(LocalDateTime.now());
        
        return location;
    }
    
    /**
     * Calculate comprehensive location risk score
     */
    public double calculateLocationRiskScore(LocationInfo location) {
        double baseScore = 0.0;
        List<Double> riskFactors = new ArrayList<>();
        
        // Country risk (40% weight)
        double countryRisk = getCountryRiskScore(location.getCountryCode());
        riskFactors.add(countryRisk * 0.4);
        
        // Political instability (15% weight)
        double politicalRisk = getPoliticalInstabilityScore(location.getCountryCode());
        riskFactors.add(politicalRisk * 0.15);
        
        // Regulatory risk (15% weight)
        double regulatoryRisk = getRegulatoryRiskScore(location.getCountryCode());
        riskFactors.add(regulatoryRisk * 0.15);
        
        // Anonymization risk (20% weight)
        double anonymizationRisk = 0.0;
        if (Boolean.TRUE.equals(location.getIsVpn())) anonymizationRisk += 0.3;
        if (Boolean.TRUE.equals(location.getIsProxy())) anonymizationRisk += 0.3;
        if (Boolean.TRUE.equals(location.getIsTor())) anonymizationRisk += 0.4;
        riskFactors.add(Math.min(anonymizationRisk, 1.0) * 0.2);
        
        // Datacenter risk (10% weight)
        if (Boolean.TRUE.equals(location.getIsDatacenter())) {
            riskFactors.add(0.1);
        }
        
        // Calculate weighted sum
        baseScore = riskFactors.stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        
        // Apply risk amplification for multiple factors
        double amplificationFactor = 1.0;
        long highRiskFactors = riskFactors.stream()
            .filter(risk -> risk > 0.5)
            .count();
        
        if (highRiskFactors >= 3) {
            amplificationFactor = 1.3;
        } else if (highRiskFactors >= 2) {
            amplificationFactor = 1.15;
        }
        
        return Math.min(baseScore * amplificationFactor, 1.0);
    }
    
    /**
     * Check if IP belongs to a datacenter
     */
    private boolean checkIfDatacenterIp(String ipAddress) {
        try {
            // Convert IP to long for range checking
            long ipLong = ipToLong(ipAddress);
            
            for (String cidr : DATACENTER_IP_RANGES) {
                if (isIpInCidr(ipLong, cidr)) {
                    return true;
                }
            }
            
            // Additional checks can be added here for ASN-based detection
            return false;
        } catch (Exception e) {
            log.error("Error checking datacenter IP: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check for impossible travel between locations
     */
    public boolean isImpossibleTravel(LocationInfo fromLocation, LocationInfo toLocation, 
                                     LocalDateTime fromTime, LocalDateTime toTime) {
        if (fromLocation == null || toLocation == null) {
            return false;
        }
        
        // Calculate distance between locations
        double distance = calculateDistance(
            fromLocation.getLatitude(), fromLocation.getLongitude(),
            toLocation.getLatitude(), toLocation.getLongitude()
        );
        
        // Calculate time difference in hours
        double hoursDiff = ChronoUnit.MINUTES.between(fromTime, toTime) / 60.0;
        
        if (hoursDiff <= 0) {
            return false; // Invalid time sequence
        }
        
        // Calculate required speed
        double requiredSpeed = distance / hoursDiff;
        
        // Check if speed exceeds reasonable travel speed
        // Commercial flight max speed ~900 km/h, adding buffer for connections
        return requiredSpeed > impossibleTravelSpeedKmh;
    }
    
    /**
     * Detect location hopping patterns
     */
    public boolean isLocationHopping(List<LocationInfo> locationHistory, LocationInfo currentLocation) {
        if (locationHistory == null || locationHistory.size() < 3) {
            return false;
        }
        
        // Check for rapid country changes
        Set<String> recentCountries = locationHistory.stream()
            .limit(5)
            .map(LocationInfo::getCountryCode)
            .collect(Collectors.toSet());
        
        // If more than 3 different countries in last 5 locations
        if (recentCountries.size() > 3) {
            return true;
        }
        
        // Check for alternating pattern (A->B->A->B)
        if (locationHistory.size() >= 4) {
            String current = currentLocation.getCountryCode();
            String prev1 = locationHistory.get(0).getCountryCode();
            String prev2 = locationHistory.get(1).getCountryCode();
            String prev3 = locationHistory.get(2).getCountryCode();
            
            if (current.equals(prev2) && prev1.equals(prev3) && !current.equals(prev1)) {
                return true; // Alternating pattern detected
            }
        }
        
        return false;
    }
    
    /**
     * Check for unusual timezone activity
     */
    public boolean isUnusualTimeZone(String userId, LocationInfo location) {
        try {
            // Get user's historical timezone patterns
            List<String> historicalTimezones = geoLocationRepository
                .findUserTimezoneHistory(userId, 30); // Last 30 days
            
            if (historicalTimezones.isEmpty()) {
                return false; // No history to compare
            }
            
            // Calculate timezone frequency
            Map<String, Long> timezoneFrequency = historicalTimezones.stream()
                .collect(Collectors.groupingBy(tz -> tz, Collectors.counting()));
            
            String currentTimezone = location.getTimezone();
            
            // If timezone never seen before
            if (!timezoneFrequency.containsKey(currentTimezone)) {
                // Check if it's significantly different from usual patterns
                int maxOffset = getMaxTimezoneOffset(timezoneFrequency.keySet());
                int currentOffset = getTimezoneOffset(currentTimezone);
                
                // If difference is more than 6 hours from usual patterns
                return Math.abs(currentOffset - maxOffset) > 6;
            }
            
            // If timezone is rare (less than 5% of activity)
            long totalActivity = timezoneFrequency.values().stream()
                .mapToLong(Long::longValue)
                .sum();
            
            double frequency = timezoneFrequency.get(currentTimezone) / (double) totalActivity;
            return frequency < 0.05;
            
        } catch (Exception e) {
            log.error("Error checking timezone patterns for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get country risk score
     */
    private double getCountryRiskScore(String countryCode) {
        return COUNTRY_RISK_SCORES.getOrDefault(countryCode, 0.3);
    }
    
    /**
     * Get political instability score
     */
    private double getPoliticalInstabilityScore(String countryCode) {
        return POLITICAL_INSTABILITY_SCORES.getOrDefault(countryCode, 0.1);
    }
    
    /**
     * Get regulatory risk score
     */
    private double getRegulatoryRiskScore(String countryCode) {
        return REGULATORY_RISK_SCORES.getOrDefault(countryCode, 0.1);
    }
    
    /**
     * Check if country is high risk
     */
    private boolean isHighRiskCountry(String countryCode) {
        return HIGH_RISK_COUNTRIES.contains(countryCode);
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Earth's radius in kilometers
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Convert IP address to long for range checking
     */
    private long ipToLong(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }
        
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(parts[i]);
        }
        return result;
    }
    
    /**
     * Check if IP is in CIDR range
     */
    private boolean isIpInCidr(long ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            long cidrIp = ipToLong(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            long mask = -1L << (32 - prefixLength);
            
            return (ip & mask) == (cidrIp & mask);
        } catch (Exception e) {
            log.error("Error checking CIDR range: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get timezone offset in hours
     */
    private int getTimezoneOffset(String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            return zoneId.getRules().getStandardOffset(java.time.Instant.now()).getTotalSeconds() / 3600;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Get maximum timezone offset from a set
     */
    private int getMaxTimezoneOffset(Set<String> timezones) {
        return timezones.stream()
            .mapToInt(this::getTimezoneOffset)
            .max()
            .orElse(0);
    }
    
    /**
     * Create unknown location object
     */
    private LocationInfo createUnknownLocation(String ipAddress) {
        return LocationInfo.builder()
            .ipAddress(ipAddress)
            .country("Unknown")
            .countryCode("XX")
            .city("Unknown")
            .riskScore(0.5) // Medium risk for unknown locations
            .isHighRisk(false)
            .provider("None")
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * Publish geolocation event to Kafka
     */
    @Async
    public void publishGeoLocationEvent(String eventType, LocationInfo location, String userId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("userId", userId);
            event.put("location", location);
            event.put("timestamp", LocalDateTime.now());
            event.put("riskScore", location.getRiskScore());
            
            kafkaTemplate.send("fraud-geolocation-events", userId, event);
            
            meterRegistry.counter("geo.events.published",
                "type", eventType,
                "risk", location.getIsHighRisk() ? "high" : "normal"
            ).increment();
            
        } catch (Exception e) {
            log.error("Failed to publish geolocation event: {}", e.getMessage());
        }
    }
}