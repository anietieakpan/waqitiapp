package com.waqiti.security.service;

import com.waqiti.security.model.*;
import com.waqiti.security.repository.AuthenticationHistoryRepository;
import com.waqiti.security.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Behavioral Authentication Service
 * Analyzes user behavior patterns for anomaly detection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehavioralAuthService {

    private final AuthenticationHistoryRepository historyRepository;
    private final FailedEventRepository failedEventRepository;
    private final MLModelService mlModelService;

    private static final Duration RECENT_WINDOW = Duration.ofDays(30);
    private static final Duration SHORT_WINDOW = Duration.ofHours(24);
    private static final int MIN_HISTORY_FOR_ANALYSIS = 5;
    private static final double ANOMALY_THRESHOLD = 0.7;

    /**
     * Analyze user behavior for authentication event
     */
    public AnomalyDetectionResult analyzeBehavior(AuthenticationEvent event) {
        try {
            log.debug("Analyzing behavior for user: {}", event.getUserId());

            // Get user's historical authentication data
            Instant recentCutoff = Instant.now().minus(RECENT_WINDOW);
            List<AuthenticationHistory> recentHistory = historyRepository.findRecentByUserId(
                event.getUserId(),
                recentCutoff
            );

            // Not enough history for analysis
            if (recentHistory.size() < MIN_HISTORY_FOR_ANALYSIS) {
                log.debug("Insufficient history for user {}: {} events", event.getUserId(), recentHistory.size());
                return AnomalyDetectionResult.builder()
                    .overallAnomalous(false)
                    .overallConfidence(0.5)
                    .overallRiskScore(10)
                    .anomalies(new ArrayList<>())
                    .build();
            }

            List<DetectedAnomaly> anomalies = new ArrayList<>();

            // Analyze different behavioral aspects
            anomalies.addAll(analyzeLocationBehavior(event, recentHistory));
            anomalies.addAll(analyzeTimingBehavior(event, recentHistory));
            anomalies.addAll(analyzeDeviceBehavior(event, recentHistory));
            anomalies.addAll(analyzeIPBehavior(event, recentHistory));
            anomalies.addAll(analyzeFailurePatterns(event));

            // Use ML model for comprehensive analysis
            MLAnomalyResult mlResult = mlModelService.detectAnomalies(event, recentHistory);
            if (mlResult.isAnomalous()) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("ML_DETECTED_ANOMALY")
                    .severity(mlResult.getSeverity())
                    .confidence(mlResult.getConfidence())
                    .description("ML model detected behavioral anomaly")
                    .evidence(Map.of(
                        "model_name", mlResult.getModelName(),
                        "model_version", mlResult.getModelVersion(),
                        "anomaly_score", mlResult.getAnomalyScore(),
                        "contributing_features", mlResult.getContributingFeatures()
                    ))
                    .riskScore(calculateRiskScore(mlResult.getAnomalyScore()))
                    .detectedAt(Instant.now())
                    .detectionMethod("ML_BEHAVIORAL_ANALYSIS")
                    .modelVersion(mlResult.getModelVersion())
                    .build());
            }

            // Calculate overall risk
            boolean isAnomalous = !anomalies.isEmpty();
            double avgConfidence = anomalies.stream()
                .mapToDouble(DetectedAnomaly::getConfidence)
                .average()
                .orElse(0.0);

            int totalRiskScore = anomalies.stream()
                .mapToInt(DetectedAnomaly::getRiskScore)
                .sum();

            return AnomalyDetectionResult.builder()
                .overallAnomalous(isAnomalous)
                .overallConfidence(avgConfidence)
                .overallRiskScore(Math.min(totalRiskScore, 100))
                .anomalies(anomalies)
                .build();

        } catch (Exception e) {
            log.error("Error analyzing behavior for user {}: {}", event.getUserId(), e.getMessage(), e);
            return AnomalyDetectionResult.builder()
                .overallAnomalous(false)
                .overallConfidence(0.0)
                .overallRiskScore(0)
                .anomalies(new ArrayList<>())
                .build();
        }
    }

    /**
     * Analyze location behavior patterns
     */
    private List<DetectedAnomaly> analyzeLocationBehavior(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();

        if (event.getCountry() == null) {
            return anomalies;
        }

        // Get unique countries from history
        Set<String> historicalCountries = history.stream()
            .map(AuthenticationHistory::getCountry)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Check for new country
        if (!historicalCountries.isEmpty() && !historicalCountries.contains(event.getCountry())) {
            double confidence = calculateLocationConfidence(event, history);

            if (confidence > ANOMALY_THRESHOLD) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("NEW_LOCATION")
                    .severity(determineSeverity(confidence))
                    .confidence(confidence)
                    .description("Authentication from new country: " + event.getCountry())
                    .evidence(Map.of(
                        "current_country", event.getCountry(),
                        "historical_countries", historicalCountries,
                        "current_city", event.getCity() != null ? event.getCity() : "unknown"
                    ))
                    .riskScore(calculateRiskScore(confidence))
                    .detectedAt(Instant.now())
                    .detectionMethod("LOCATION_ANALYSIS")
                    .modelVersion("1.0")
                    .build());
            }
        }

        // Check for impossible travel
        anomalies.addAll(detectImpossibleTravel(event, history));

        return anomalies;
    }

    /**
     * Detect impossible travel (too far too fast)
     */
    private List<DetectedAnomaly> detectImpossibleTravel(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();

        if (event.getLatitude() == null || event.getLongitude() == null) {
            return anomalies;
        }

        // Get most recent authentication with location
        Optional<AuthenticationHistory> lastAuth = history.stream()
            .filter(h -> h.getLatitude() != null && h.getLongitude() != null)
            .max(Comparator.comparing(AuthenticationHistory::getAuthenticatedAt));

        if (lastAuth.isPresent()) {
            AuthenticationHistory last = lastAuth.get();

            double distance = calculateDistance(
                last.getLatitude(), last.getLongitude(),
                event.getLatitude(), event.getLongitude()
            );

            long timeDiffSeconds = Duration.between(
                last.getAuthenticatedAt(),
                event.getTimestamp()
            ).getSeconds();

            // Calculate required speed (km/h)
            double requiredSpeed = (distance / 1000.0) / (timeDiffSeconds / 3600.0);

            // Flag if speed > 1000 km/h (roughly speed of commercial jet)
            if (requiredSpeed > 1000) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("IMPOSSIBLE_TRAVEL")
                    .severity(AnomalySeverity.HIGH)
                    .confidence(0.95)
                    .description(String.format(
                        "Impossible travel detected: %.0f km in %.1f hours (%.0f km/h)",
                        distance / 1000, timeDiffSeconds / 3600.0, requiredSpeed
                    ))
                    .evidence(Map.of(
                        "distance_km", distance / 1000,
                        "time_diff_hours", timeDiffSeconds / 3600.0,
                        "required_speed_kmh", requiredSpeed,
                        "from_location", last.getCountry() + "/" + last.getCity(),
                        "to_location", event.getCountry() + "/" + event.getCity()
                    ))
                    .riskScore(80)
                    .detectedAt(Instant.now())
                    .detectionMethod("IMPOSSIBLE_TRAVEL_DETECTION")
                    .modelVersion("1.0")
                    .build());
            }
        }

        return anomalies;
    }

    /**
     * Analyze timing behavior patterns
     */
    private List<DetectedAnomaly> analyzeTimingBehavior(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();

        // Analyze time-of-day patterns
        Map<Integer, Long> hourFrequency = history.stream()
            .collect(Collectors.groupingBy(
                h -> ZonedDateTime.ofInstant(h.getAuthenticatedAt(), ZoneId.systemDefault()).getHour(),
                Collectors.counting()
            ));

        int currentHour = ZonedDateTime.ofInstant(event.getTimestamp(), ZoneId.systemDefault()).getHour();
        long currentHourCount = hourFrequency.getOrDefault(currentHour, 0L);
        long totalAuths = history.size();

        // If this hour has < 5% of historical activity, flag it
        if (totalAuths > 20 && currentHourCount < (totalAuths * 0.05)) {
            double confidence = 1.0 - (currentHourCount / (double) totalAuths);

            if (confidence > ANOMALY_THRESHOLD) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("UNUSUAL_TIME")
                    .severity(AnomalySeverity.MEDIUM)
                    .confidence(confidence)
                    .description("Authentication at unusual hour: " + currentHour)
                    .evidence(Map.of(
                        "current_hour", currentHour,
                        "historical_frequency", currentHourCount,
                        "total_auths", totalAuths
                    ))
                    .riskScore(calculateRiskScore(confidence * 0.7))
                    .detectedAt(Instant.now())
                    .detectionMethod("TIMING_ANALYSIS")
                    .modelVersion("1.0")
                    .build());
            }
        }

        return anomalies;
    }

    /**
     * Analyze device behavior patterns
     */
    private List<DetectedAnomaly> analyzeDeviceBehavior(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();

        if (event.getDeviceId() == null) {
            return anomalies;
        }

        // Get unique devices
        Set<String> historicalDevices = history.stream()
            .map(AuthenticationHistory::getDeviceId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Check for new device
        if (!historicalDevices.isEmpty() && !historicalDevices.contains(event.getDeviceId())) {
            double confidence = 0.8;

            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("NEW_DEVICE")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(confidence)
                .description("Authentication from new device")
                .evidence(Map.of(
                    "device_id", event.getDeviceId(),
                    "historical_device_count", historicalDevices.size(),
                    "browser", event.getBrowserName() != null ? event.getBrowserName() : "unknown",
                    "os", event.getOsName() != null ? event.getOsName() : "unknown"
                ))
                .riskScore(calculateRiskScore(confidence * 0.6))
                .detectedAt(Instant.now())
                .detectionMethod("DEVICE_ANALYSIS")
                .modelVersion("1.0")
                .build());
        }

        return anomalies;
    }

    /**
     * Analyze IP address behavior patterns
     */
    private List<DetectedAnomaly> analyzeIPBehavior(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();

        if (event.getIpAddress() == null) {
            return anomalies;
        }

        // Get unique IPs
        Set<String> historicalIPs = history.stream()
            .map(AuthenticationHistory::getIpAddress)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Check for new IP
        if (!historicalIPs.isEmpty() && !historicalIPs.contains(event.getIpAddress())) {
            double confidence = 0.6; // Lower confidence as IPs change frequently

            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("NEW_IP")
                .severity(AnomalySeverity.LOW)
                .confidence(confidence)
                .description("Authentication from new IP address")
                .evidence(Map.of(
                    "ip_address", event.getIpAddress(),
                    "historical_ip_count", historicalIPs.size()
                ))
                .riskScore(calculateRiskScore(confidence * 0.4))
                .detectedAt(Instant.now())
                .detectionMethod("IP_ANALYSIS")
                .modelVersion("1.0")
                .build());
        }

        return anomalies;
    }

    /**
     * Analyze failure patterns
     */
    private List<DetectedAnomaly> analyzeFailurePatterns(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();

        Instant recentCutoff = Instant.now().minus(SHORT_WINDOW);

        // Check for recent failures by user
        long recentFailures = failedEventRepository.countRecentByUserId(
            event.getUserId(),
            recentCutoff
        );

        if (recentFailures >= 5) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("MULTIPLE_RECENT_FAILURES")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(0.85)
                .description("Multiple recent failed authentication attempts")
                .evidence(Map.of(
                    "failure_count", recentFailures,
                    "time_window_hours", SHORT_WINDOW.toHours()
                ))
                .riskScore(calculateRiskScore(0.85 * 0.7))
                .detectedAt(Instant.now())
                .detectionMethod("FAILURE_PATTERN_ANALYSIS")
                .modelVersion("1.0")
                .build());
        }

        return anomalies;
    }

    /**
     * Calculate location confidence score
     */
    private double calculateLocationConfidence(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        // Base confidence for new country
        double confidence = 0.75;

        // Increase if city is also new
        Set<String> historicalCities = history.stream()
            .map(AuthenticationHistory::getCity)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (!historicalCities.isEmpty() && !historicalCities.contains(event.getCity())) {
            confidence += 0.1;
        }

        // Increase if no recent activity in that region
        long recentInCountry = history.stream()
            .filter(h -> event.getCountry().equals(h.getCountry()))
            .filter(h -> h.getAuthenticatedAt().isAfter(Instant.now().minus(Duration.ofDays(90))))
            .count();

        if (recentInCountry == 0) {
            confidence += 0.1;
        }

        return Math.min(confidence, 1.0);
    }

    /**
     * Calculate distance between two coordinates (Haversine formula)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // distance in meters
    }

    /**
     * Determine severity based on confidence
     */
    private AnomalySeverity determineSeverity(double confidence) {
        if (confidence >= 0.9) return AnomalySeverity.HIGH;
        if (confidence >= 0.75) return AnomalySeverity.MEDIUM;
        return AnomalySeverity.LOW;
    }

    /**
     * Calculate risk score from confidence (0-100)
     */
    private int calculateRiskScore(double confidence) {
        return (int) Math.round(confidence * 100);
    }
}
