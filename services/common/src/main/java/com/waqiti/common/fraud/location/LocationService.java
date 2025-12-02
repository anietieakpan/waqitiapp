package com.waqiti.common.fraud.location;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise location and geo-analytics service for fraud detection.
 * Provides location-based risk assessment, impossible travel detection,
 * geofencing, and location pattern analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocationService {
    
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_NORMAL_SPEED_KM_H = 900.0; // Commercial flight speed
    private static final double HIGH_RISK_DISTANCE_KM = 5000.0;
    
    private final Map<String, UserLocationProfile> locationProfiles = new ConcurrentHashMap<>();
    private final Map<String, GeofenceRule> geofenceRules = new ConcurrentHashMap<>();
    
    /**
     * Analyze location for fraud risk
     */
    public CompletableFuture<LocationAnalysisResult> analyzeLocation(LocationData locationData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UserLocationProfile profile = getOrCreateProfile(locationData.getUserId());
                
                // Perform various location checks
                ImpossibleTravelCheck travelCheck = checkImpossibleTravel(locationData, profile);
                GeofenceCheck geofenceCheck = checkGeofences(locationData);
                LocationPatternCheck patternCheck = analyzeLocationPattern(locationData, profile);
                CountryRiskCheck countryCheck = assessCountryRisk(locationData);
                VpnProxyCheck vpnCheck = detectVpnOrProxy(locationData);
                
                // Update profile with new location
                updateLocationProfile(profile, locationData);
                
                // Calculate overall risk score
                double riskScore = calculateLocationRiskScore(
                    travelCheck, geofenceCheck, patternCheck, countryCheck, vpnCheck
                );
                
                return LocationAnalysisResult.builder()
                    .locationData(locationData)
                    .riskScore(riskScore)
                    .riskLevel(determineRiskLevel(riskScore))
                    .impossibleTravelCheck(travelCheck)
                    .geofenceCheck(geofenceCheck)
                    .patternCheck(patternCheck)
                    .countryRiskCheck(countryCheck)
                    .vpnProxyCheck(vpnCheck)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            } catch (Exception e) {
                log.error("Error analyzing location: {}", e.getMessage(), e);
                return LocationAnalysisResult.builder()
                    .locationData(locationData)
                    .riskScore(0.5)
                    .riskLevel(RiskLevel.MEDIUM)
                    .error(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }
    
    /**
     * Check for impossible travel between locations
     */
    public ImpossibleTravelCheck checkImpossibleTravel(LocationData current, UserLocationProfile profile) {
        if (profile.getLastKnownLocation() == null) {
            return ImpossibleTravelCheck.builder()
                .isPossible(true)
                .confidence(0.5)
                .build();
        }
        
        LocationData previous = profile.getLastKnownLocation();
        double distance = calculateDistance(
            previous.getLatitude(), previous.getLongitude(),
            current.getLatitude(), current.getLongitude()
        );
        
        long timeDiffMinutes = java.time.Duration.between(
            previous.getTimestamp(), current.getTimestamp()
        ).toMinutes();
        
        if (timeDiffMinutes <= 0) {
            return ImpossibleTravelCheck.builder()
                .isPossible(false)
                .confidence(1.0)
                .reason("Invalid timestamp sequence")
                .build();
        }
        
        double speedKmH = (distance / timeDiffMinutes) * 60;
        boolean isPossible = speedKmH <= MAX_NORMAL_SPEED_KM_H;
        
        return ImpossibleTravelCheck.builder()
            .isPossible(isPossible)
            .distance(distance)
            .timeDifferenceMinutes(timeDiffMinutes)
            .calculatedSpeed(speedKmH)
            .confidence(isPossible ? 1.0 : Math.min(speedKmH / MAX_NORMAL_SPEED_KM_H / 2, 1.0))
            .reason(isPossible ? "Travel speed within normal limits" : 
                    String.format("Impossible travel detected: %.2f km/h", speedKmH))
            .build();
    }
    
    /**
     * Check geofence violations
     */
    public GeofenceCheck checkGeofences(LocationData location) {
        List<GeofenceViolation> violations = new ArrayList<>();
        
        for (GeofenceRule rule : geofenceRules.values()) {
            if (rule.appliesTo(location.getUserId())) {
                boolean isInside = isInsideGeofence(location, rule);
                
                if (rule.getType() == GeofenceType.WHITELIST && !isInside) {
                    violations.add(GeofenceViolation.builder()
                        .ruleName(rule.getName())
                        .type(GeofenceType.WHITELIST)
                        .severity(rule.getSeverity())
                        .message("Location outside whitelist geofence")
                        .build());
                } else if (rule.getType() == GeofenceType.BLACKLIST && isInside) {
                    violations.add(GeofenceViolation.builder()
                        .ruleName(rule.getName())
                        .type(GeofenceType.BLACKLIST)
                        .severity(rule.getSeverity())
                        .message("Location inside blacklist geofence")
                        .build());
                }
            }
        }
        
        return GeofenceCheck.builder()
            .hasViolations(!violations.isEmpty())
            .violations(violations)
            .riskScore(calculateGeofenceRiskScore(violations))
            .build();
    }
    
    /**
     * Analyze location patterns for anomalies
     */
    public LocationPatternCheck analyzeLocationPattern(LocationData location, UserLocationProfile profile) {
        List<String> anomalies = new ArrayList<>();
        double anomalyScore = 0.0;
        
        // Check if location is in user's common locations
        boolean isKnownLocation = profile.getCommonLocations().stream()
            .anyMatch(loc -> calculateDistance(
                loc.getLatitude(), loc.getLongitude(),
                location.getLatitude(), location.getLongitude()
            ) < 10.0); // Within 10km
        
        if (!isKnownLocation && profile.getCommonLocations().size() > 3) {
            anomalies.add("New location not in user's common locations");
            anomalyScore += 0.3;
        }
        
        // Check time-based patterns
        if (profile.getTimeBasedPatterns() != null) {
            String timePattern = getTimePattern(location.getTimestamp());
            if (!profile.getTimeBasedPatterns().containsKey(timePattern)) {
                anomalies.add("Unusual time pattern for location");
                anomalyScore += 0.2;
            }
        }
        
        // Check location consistency
        if (profile.getLocationHistory().size() > 10) {
            double avgDistance = calculateAverageDistance(profile.getLocationHistory());
            double currentDistance = calculateDistance(
                profile.getCenterPoint().getLatitude(),
                profile.getCenterPoint().getLongitude(),
                location.getLatitude(),
                location.getLongitude()
            );
            
            if (currentDistance > avgDistance * 3) {
                anomalies.add("Location significantly outside normal range");
                anomalyScore += 0.4;
            }
        }
        
        return LocationPatternCheck.builder()
            .hasAnomalies(!anomalies.isEmpty())
            .anomalies(anomalies)
            .anomalyScore(Math.min(anomalyScore, 1.0))
            .isKnownLocation(isKnownLocation)
            .confidence(profile.getLocationHistory().size() > 10 ? 0.9 : 0.5)
            .build();
    }
    
    /**
     * Assess country-based risk
     */
    public CountryRiskCheck assessCountryRisk(LocationData location) {
        String country = location.getCountryCode();
        
        // High-risk countries (simplified example)
        Set<String> highRiskCountries = Set.of("XX", "YY", "ZZ");
        Set<String> mediumRiskCountries = Set.of("AA", "BB", "CC");
        
        RiskLevel riskLevel;
        double riskScore;
        
        if (highRiskCountries.contains(country)) {
            riskLevel = RiskLevel.HIGH;
            riskScore = 0.8;
        } else if (mediumRiskCountries.contains(country)) {
            riskLevel = RiskLevel.MEDIUM;
            riskScore = 0.5;
        } else {
            riskLevel = RiskLevel.LOW;
            riskScore = 0.2;
        }
        
        return CountryRiskCheck.builder()
            .countryCode(country)
            .riskLevel(riskLevel)
            .riskScore(riskScore)
            .isSanctioned(checkSanctions(country))
            .hasTradeRestrictions(checkTradeRestrictions(country))
            .build();
    }
    
    /**
     * Detect VPN or proxy usage
     */
    public VpnProxyCheck detectVpnOrProxy(LocationData location) {
        // Simplified VPN/proxy detection
        boolean isVpn = checkVpnIndicators(location);
        boolean isProxy = checkProxyIndicators(location);
        boolean isTor = checkTorIndicators(location);
        
        double riskScore = 0.0;
        if (isVpn) riskScore += 0.4;
        if (isProxy) riskScore += 0.5;
        if (isTor) riskScore += 0.7;
        
        return VpnProxyCheck.builder()
            .isVpn(isVpn)
            .isProxy(isProxy)
            .isTor(isTor)
            .confidence(0.7)
            .riskScore(Math.min(riskScore, 1.0))
            .ipAddress(location.getIpAddress())
            .build();
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Update user location profile
     */
    private void updateLocationProfile(UserLocationProfile profile, LocationData location) {
        profile.setLastKnownLocation(location);
        profile.getLocationHistory().add(location);
        
        // Keep only last 100 locations
        if (profile.getLocationHistory().size() > 100) {
            profile.setLocationHistory(
                profile.getLocationHistory().stream()
                    .skip(profile.getLocationHistory().size() - 100)
                    .collect(Collectors.toList())
            );
        }
        
        // Update common locations
        updateCommonLocations(profile);
        
        // Update center point
        updateCenterPoint(profile);
        
        profile.setLastUpdated(LocalDateTime.now());
    }
    
    /**
     * Calculate overall location risk score
     */
    private double calculateLocationRiskScore(
            ImpossibleTravelCheck travel,
            GeofenceCheck geofence,
            LocationPatternCheck pattern,
            CountryRiskCheck country,
            VpnProxyCheck vpn) {
        
        double score = 0.0;
        double weight = 0.0;
        
        if (!travel.isPossible()) {
            score += 0.9 * 0.3;
            weight += 0.3;
        }
        
        if (geofence.hasViolations()) {
            score += geofence.getRiskScore() * 0.25;
            weight += 0.25;
        }
        
        if (pattern.hasAnomalies()) {
            score += pattern.getAnomalyScore() * 0.2;
            weight += 0.2;
        }
        
        score += country.getRiskScore() * 0.15;
        weight += 0.15;
        
        score += vpn.getRiskScore() * 0.1;
        weight += 0.1;
        
        return weight > 0 ? score / weight : 0.0;
    }
    
    /**
     * Determine risk level based on score
     */
    private RiskLevel determineRiskLevel(double score) {
        if (score >= 0.8) return RiskLevel.CRITICAL;
        if (score >= 0.6) return RiskLevel.HIGH;
        if (score >= 0.4) return RiskLevel.MEDIUM;
        if (score >= 0.2) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    /**
     * Get or create user location profile
     */
    private UserLocationProfile getOrCreateProfile(String userId) {
        return locationProfiles.computeIfAbsent(userId, k -> 
            UserLocationProfile.builder()
                .userId(k)
                .locationHistory(new ArrayList<>())
                .commonLocations(new ArrayList<>())
                .timeBasedPatterns(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build()
        );
    }
    
    /**
     * Check if location is inside geofence
     */
    private boolean isInsideGeofence(LocationData location, GeofenceRule rule) {
        if (rule.getShape() == GeofenceShape.CIRCLE) {
            double distance = calculateDistance(
                rule.getCenterLatitude(), rule.getCenterLongitude(),
                location.getLatitude(), location.getLongitude()
            );
            return distance <= rule.getRadiusKm();
        } else if (rule.getShape() == GeofenceShape.POLYGON) {
            return isPointInPolygon(location, rule.getPolygonPoints());
        }
        return false;
    }
    
    /**
     * Check if point is inside polygon
     */
    private boolean isPointInPolygon(LocationData point, List<GeoPoint> polygon) {
        int intersections = 0;
        for (int i = 0; i < polygon.size(); i++) {
            GeoPoint p1 = polygon.get(i);
            GeoPoint p2 = polygon.get((i + 1) % polygon.size());
            
            if (rayIntersectsSegment(point, p1, p2)) {
                intersections++;
            }
        }
        return intersections % 2 == 1;
    }
    
    /**
     * Ray casting algorithm for point in polygon
     */
    private boolean rayIntersectsSegment(LocationData point, GeoPoint p1, GeoPoint p2) {
        double px = point.getLongitude();
        double py = point.getLatitude();
        double p1x = p1.getLongitude();
        double p1y = p1.getLatitude();
        double p2x = p2.getLongitude();
        double p2y = p2.getLatitude();
        
        if (p1y == p2y) return false;
        if (py < Math.min(p1y, p2y)) return false;
        if (py >= Math.max(p1y, p2y)) return false;
        
        double x = (py - p1y) * (p2x - p1x) / (p2y - p1y) + p1x;
        return x < px;
    }
    
    /**
     * Calculate geofence risk score
     */
    private double calculateGeofenceRiskScore(List<GeofenceViolation> violations) {
        if (violations.isEmpty()) return 0.0;
        
        double maxSeverity = violations.stream()
            .mapToDouble(v -> v.getSeverity().getScore())
            .max()
            .orElse(0.0);
        
        return Math.min(maxSeverity * violations.size() * 0.2, 1.0);
    }
    
    /**
     * Get time pattern string
     */
    private String getTimePattern(LocalDateTime time) {
        int hour = time.getHour();
        if (hour >= 0 && hour < 6) return "NIGHT";
        if (hour >= 6 && hour < 12) return "MORNING";
        if (hour >= 12 && hour < 18) return "AFTERNOON";
        return "EVENING";
    }
    
    /**
     * Calculate average distance from location history
     */
    private double calculateAverageDistance(List<LocationData> history) {
        if (history.size() < 2) return 0.0;
        
        double totalDistance = 0.0;
        for (int i = 1; i < history.size(); i++) {
            LocationData prev = history.get(i - 1);
            LocationData curr = history.get(i);
            totalDistance += calculateDistance(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
        }
        
        return totalDistance / (history.size() - 1);
    }
    
    /**
     * Update common locations
     */
    private void updateCommonLocations(UserLocationProfile profile) {
        // Cluster locations to find common places
        // Simplified implementation
        Map<String, Integer> locationCounts = new HashMap<>();
        
        for (LocationData loc : profile.getLocationHistory()) {
            String key = String.format("%.2f,%.2f", loc.getLatitude(), loc.getLongitude());
            locationCounts.merge(key, 1, Integer::sum);
        }
        
        profile.setCommonLocations(
            locationCounts.entrySet().stream()
                .filter(e -> e.getValue() > 3)
                .map(e -> {
                    String[] parts = e.getKey().split(",");
                    return LocationData.builder()
                        .latitude(Double.parseDouble(parts[0]))
                        .longitude(Double.parseDouble(parts[1]))
                        .build();
                })
                .limit(10)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Update center point
     */
    private void updateCenterPoint(UserLocationProfile profile) {
        if (profile.getLocationHistory().isEmpty()) return;
        
        double avgLat = profile.getLocationHistory().stream()
            .mapToDouble(LocationData::getLatitude)
            .average()
            .orElse(0.0);
        
        double avgLon = profile.getLocationHistory().stream()
            .mapToDouble(LocationData::getLongitude)
            .average()
            .orElse(0.0);
        
        profile.setCenterPoint(GeoPoint.builder()
            .latitude(avgLat)
            .longitude(avgLon)
            .build());
    }
    
    /**
     * Check sanctions
     */
    private boolean checkSanctions(String countryCode) {
        // Simplified sanctions check
        Set<String> sanctionedCountries = Set.of("IR", "KP", "SY");
        return sanctionedCountries.contains(countryCode);
    }
    
    /**
     * Check trade restrictions
     */
    private boolean checkTradeRestrictions(String countryCode) {
        // Simplified trade restrictions check
        Set<String> restrictedCountries = Set.of("CU", "IR", "KP", "SY", "RU");
        return restrictedCountries.contains(countryCode);
    }
    
    /**
     * Check VPN indicators
     */
    private boolean checkVpnIndicators(LocationData location) {
        // Simplified VPN detection
        return location.getIpAddress() != null && 
               (location.getIpAddress().contains("vpn") || 
                location.getConnectionType() == ConnectionType.VPN);
    }
    
    /**
     * Check proxy indicators
     */
    private boolean checkProxyIndicators(LocationData location) {
        // Simplified proxy detection
        return location.getConnectionType() == ConnectionType.PROXY;
    }
    
    /**
     * Check Tor indicators
     */
    private boolean checkTorIndicators(LocationData location) {
        // Simplified Tor detection
        return location.getConnectionType() == ConnectionType.TOR;
    }
    
    /**
     * Add geofence rule
     */
    public void addGeofenceRule(GeofenceRule rule) {
        geofenceRules.put(rule.getId(), rule);
        log.info("Added geofence rule: {}", rule.getName());
    }
    
    /**
     * Remove geofence rule
     */
    public void removeGeofenceRule(String ruleId) {
        geofenceRules.remove(ruleId);
        log.info("Removed geofence rule: {}", ruleId);
    }
    
    /**
     * Get user location profile
     */
    public UserLocationProfile getUserProfile(String userId) {
        return locationProfiles.get(userId);
    }
    
    public enum RiskLevel {
        MINIMAL(0.0),
        LOW(0.2),
        MEDIUM(0.4),
        HIGH(0.6),
        CRITICAL(0.8);
        
        private final double threshold;
        
        RiskLevel(double threshold) {
            this.threshold = threshold;
        }
        
        public double getThreshold() {
            return threshold;
        }
    }
    
    public enum GeofenceType {
        WHITELIST,
        BLACKLIST
    }
    
    public enum GeofenceShape {
        CIRCLE,
        POLYGON
    }
    
    public enum GeofenceSeverity {
        LOW(0.3),
        MEDIUM(0.5),
        HIGH(0.7),
        CRITICAL(1.0);
        
        private final double score;
        
        GeofenceSeverity(double score) {
            this.score = score;
        }
        
        public double getScore() {
            return score;
        }
    }
    
    public enum ConnectionType {
        DIRECT,
        VPN,
        PROXY,
        TOR,
        UNKNOWN
    }
}