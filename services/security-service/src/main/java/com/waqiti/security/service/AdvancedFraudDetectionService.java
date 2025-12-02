package com.waqiti.security.service;

import com.waqiti.security.domain.FraudDetectionResult;
import com.waqiti.security.domain.FraudDetectionResult.FraudIndicator;
import com.waqiti.security.domain.FraudDetectionResult.FraudRiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced Fraud Detection Service
 * 
 * Comprehensive fraud detection using machine learning, behavioral analysis,
 * and rule-based detection systems.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedFraudDetectionService {

    private final VelocityCheckService velocityCheckService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final GeolocationService geolocationService;
    private final MachineLearningService machineLearningService;

    @Value("${security-service.fraud-detection.risk-thresholds.high:75}")
    private int highRiskThreshold;

    @Value("${security-service.fraud-detection.risk-thresholds.critical:90}")
    private int criticalRiskThreshold;

    /**
     * Comprehensive fraud detection analysis
     */
    public FraudDetectionResult analyzeFraud(FraudDetectionRequest request) {
        long startTime = System.currentTimeMillis();
        UUID analysisId = UUID.randomUUID();

        log.debug("Starting fraud analysis for transaction: {}", request.getTransactionId());

        try {
            // Run parallel analysis
            CompletableFuture<List<FraudIndicator>> velocityFuture = 
                CompletableFuture.supplyAsync(() -> analyzeVelocity(request));
            
            CompletableFuture<List<FraudIndicator>> behavioralFuture = 
                CompletableFuture.supplyAsync(() -> analyzeBehavior(request));
            
            CompletableFuture<List<FraudIndicator>> deviceFuture = 
                CompletableFuture.supplyAsync(() -> analyzeDevice(request));
            
            CompletableFuture<List<FraudIndicator>> geoFuture = 
                CompletableFuture.supplyAsync(() -> analyzeGeolocation(request));
            
            CompletableFuture<List<FraudIndicator>> patternFuture = 
                CompletableFuture.supplyAsync(() -> analyzePatterns(request));
            
            CompletableFuture<BigDecimal> mlFuture = 
                CompletableFuture.supplyAsync(() -> analyzeMachineLearning(request));

            // Collect all results
            List<FraudIndicator> allIndicators = new ArrayList<>();
            allIndicators.addAll(velocityFuture.join());
            allIndicators.addAll(behavioralFuture.join());
            allIndicators.addAll(deviceFuture.join());
            allIndicators.addAll(geoFuture.join());
            allIndicators.addAll(patternFuture.join());

            BigDecimal mlScore = mlFuture.join();

            // Calculate composite risk score
            BigDecimal riskScore = calculateCompositeRiskScore(allIndicators, mlScore);
            FraudRiskLevel riskLevel = FraudRiskLevel.fromScore(riskScore);

            // Determine decision
            String decision = determineDecision(riskScore, allIndicators);
            boolean requiresReview = shouldRequireManualReview(riskLevel, allIndicators);

            long processingTime = System.currentTimeMillis() - startTime;

            FraudDetectionResult result = FraudDetectionResult.builder()
                .analysisId(analysisId)
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .decision(decision)
                .indicators(allIndicators)
                .features(extractFeatures(request))
                .scores(extractScores(allIndicators, mlScore))
                .analysisTimestamp(LocalDateTime.now())
                .modelVersion("v2.1.0")
                .processingTimeMs(processingTime)
                .requiresManualReview(requiresReview)
                .reviewReason(determineReviewReason(riskLevel, allIndicators))
                .recommendations(generateRecommendations(riskLevel, allIndicators))
                .build();

            log.info("Fraud analysis completed for transaction: {} - Risk: {} ({})", 
                request.getTransactionId(), riskLevel, riskScore);

            return result;

        } catch (Exception e) {
            log.error("Error during fraud analysis for transaction: {}", request.getTransactionId(), e);
            
            // Return safe default - require review
            return FraudDetectionResult.builder()
                .analysisId(analysisId)
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .riskLevel(FraudRiskLevel.HIGH)
                .riskScore(BigDecimal.valueOf(75))
                .decision("REVIEW")
                .indicators(Collections.emptyList())
                .analysisTimestamp(LocalDateTime.now())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .requiresManualReview(true)
                .reviewReason("Analysis error - manual review required")
                .build();
        }
    }

    /**
     * Analyze velocity patterns
     */
    private List<FraudIndicator> analyzeVelocity(FraudDetectionRequest request) {
        List<FraudIndicator> indicators = new ArrayList<>();

        // Transaction count velocity
        VelocityCheckResult countResult = velocityCheckService.checkTransactionCount(
            request.getUserId(), request.getTimestamp());
        
        if (countResult.isExceeded()) {
            indicators.add(FraudIndicator.builder()
                .type("VELOCITY")
                .name("HIGH_TRANSACTION_COUNT")
                .description("Transaction count exceeds velocity limits")
                .severity(BigDecimal.valueOf(0.8))
                .confidence(BigDecimal.valueOf(0.9))
                .details(Map.of(
                    "count", countResult.getCurrentCount(),
                    "limit", countResult.getLimit(),
                    "timeframe", countResult.getTimeframe()
                ))
                .isBlocking(countResult.getCurrentCount() > countResult.getLimit() * 2)
                .build());
        }

        // Amount velocity
        VelocityCheckResult amountResult = velocityCheckService.checkTransactionAmount(
            request.getUserId(), request.getAmount(), request.getTimestamp());
        
        if (amountResult.isExceeded()) {
            indicators.add(FraudIndicator.builder()
                .type("VELOCITY")
                .name("HIGH_TRANSACTION_AMOUNT")
                .description("Transaction amount exceeds velocity limits")
                .severity(BigDecimal.valueOf(0.7))
                .confidence(BigDecimal.valueOf(0.9))
                .details(Map.of(
                    "amount", amountResult.getCurrentAmount(),
                    "limit", amountResult.getAmountLimit(),
                    "timeframe", amountResult.getTimeframe()
                ))
                .isBlocking(amountResult.getCurrentAmount().compareTo(
                    amountResult.getAmountLimit().multiply(BigDecimal.valueOf(1.5))) > 0)
                .build());
        }

        return indicators;
    }

    /**
     * Analyze behavioral patterns
     */
    private List<FraudIndicator> analyzeBehavior(FraudDetectionRequest request) {
        List<FraudIndicator> indicators = new ArrayList<>();

        BehavioralProfile profile = behavioralAnalysisService.getUserProfile(request.getUserId());
        if (profile == null) {
            return indicators; // New user - no baseline yet
        }

        // Unusual transaction time
        if (behavioralAnalysisService.isUnusualTime(profile, request.getTimestamp())) {
            indicators.add(FraudIndicator.builder()
                .type("BEHAVIORAL")
                .name("UNUSUAL_TRANSACTION_TIME")
                .description("Transaction at unusual time for user")
                .severity(BigDecimal.valueOf(0.4))
                .confidence(BigDecimal.valueOf(0.7))
                .details(Map.of(
                    "transactionTime", request.getTimestamp(),
                    "typicalTimeRange", profile.getTypicalTimeRange()
                ))
                .isBlocking(false)
                .build());
        }

        // Unusual amount pattern
        if (behavioralAnalysisService.isUnusualAmount(profile, request.getAmount())) {
            BigDecimal severity = calculateAmountAnomalySeverity(profile, request.getAmount());
            indicators.add(FraudIndicator.builder()
                .type("BEHAVIORAL")
                .name("UNUSUAL_TRANSACTION_AMOUNT")
                .description("Transaction amount deviates from user pattern")
                .severity(severity)
                .confidence(BigDecimal.valueOf(0.8))
                .details(Map.of(
                    "transactionAmount", request.getAmount(),
                    "typicalRange", profile.getTypicalAmountRange(),
                    "deviationScore", severity
                ))
                .isBlocking(severity.compareTo(BigDecimal.valueOf(0.9)) > 0)
                .build());
        }

        return indicators;
    }

    /**
     * Analyze device fingerprint
     */
    private List<FraudIndicator> analyzeDevice(FraudDetectionRequest request) {
        List<FraudIndicator> indicators = new ArrayList<>();

        if (request.getDeviceFingerprint() == null) {
            indicators.add(FraudIndicator.builder()
                .type("DEVICE")
                .name("MISSING_DEVICE_FINGERPRINT")
                .description("Device fingerprint not provided")
                .severity(BigDecimal.valueOf(0.6))
                .confidence(BigDecimal.valueOf(1.0))
                .details(Map.of("reason", "No device fingerprint in request"))
                .isBlocking(false)
                .build());
            return indicators;
        }

        DeviceAnalysisResult deviceResult = deviceFingerprintService.analyzeDevice(
            request.getUserId(), request.getDeviceFingerprint());

        if (!deviceResult.isKnownDevice()) {
            indicators.add(FraudIndicator.builder()
                .type("DEVICE")
                .name("UNKNOWN_DEVICE")
                .description("Transaction from unrecognized device")
                .severity(BigDecimal.valueOf(0.7))
                .confidence(BigDecimal.valueOf(0.9))
                .details(Map.of(
                    "deviceId", deviceResult.getDeviceId(),
                    "firstSeen", deviceResult.getFirstSeen()
                ))
                .isBlocking(false)
                .build());
        }

        if (deviceResult.isHighRiskDevice()) {
            indicators.add(FraudIndicator.builder()
                .type("DEVICE")
                .name("HIGH_RISK_DEVICE")
                .description("Device flagged as high risk")
                .severity(BigDecimal.valueOf(0.9))
                .confidence(BigDecimal.valueOf(0.8))
                .details(Map.of(
                    "riskFactors", deviceResult.getRiskFactors(),
                    "riskScore", deviceResult.getRiskScore()
                ))
                .isBlocking(true)
                .build());
        }

        return indicators;
    }

    /**
     * Analyze geolocation
     */
    private List<FraudIndicator> analyzeGeolocation(FraudDetectionRequest request) {
        List<FraudIndicator> indicators = new ArrayList<>();

        if (request.getIpAddress() == null) {
            return indicators;
        }

        GeolocationResult geoResult = geolocationService.analyzeLocation(
            request.getUserId(), request.getIpAddress());

        if (geoResult.isHighRiskCountry()) {
            indicators.add(FraudIndicator.builder()
                .type("GEOLOCATION")
                .name("HIGH_RISK_COUNTRY")
                .description("Transaction from high-risk country")
                .severity(BigDecimal.valueOf(0.8))
                .confidence(BigDecimal.valueOf(0.9))
                .details(Map.of(
                    "country", geoResult.getCountryCode(),
                    "city", geoResult.getCity(),
                    "riskLevel", geoResult.getCountryRiskLevel()
                ))
                .isBlocking(true)
                .build());
        }

        if (geoResult.isUnusualLocation()) {
            indicators.add(FraudIndicator.builder()
                .type("GEOLOCATION")
                .name("UNUSUAL_LOCATION")
                .description("Transaction from unusual location for user")
                .severity(BigDecimal.valueOf(0.6))
                .confidence(BigDecimal.valueOf(0.7))
                .details(Map.of(
                    "currentLocation", geoResult.getCurrentLocation(),
                    "typicalLocations", geoResult.getTypicalLocations(),
                    "distanceKm", geoResult.getDistanceFromTypical()
                ))
                .isBlocking(false)
                .build());
        }

        if (geoResult.isImpossibleTravel()) {
            indicators.add(FraudIndicator.builder()
                .type("GEOLOCATION")
                .name("IMPOSSIBLE_TRAVEL")
                .description("Impossible travel detected between locations")
                .severity(BigDecimal.valueOf(1.0))
                .confidence(BigDecimal.valueOf(0.95))
                .details(Map.of(
                    "previousLocation", geoResult.getPreviousLocation(),
                    "currentLocation", geoResult.getCurrentLocation(),
                    "timeDifference", geoResult.getTimeDifference(),
                    "requiredSpeed", geoResult.getRequiredSpeedKmh()
                ))
                .isBlocking(true)
                .build());
        }

        return indicators;
    }

    /**
     * Analyze transaction patterns
     */
    private List<FraudIndicator> analyzePatterns(FraudDetectionRequest request) {
        List<FraudIndicator> indicators = new ArrayList<>();

        // Round amount detection
        if (isRoundAmount(request.getAmount())) {
            indicators.add(FraudIndicator.builder()
                .type("PATTERN")
                .name("ROUND_AMOUNT")
                .description("Transaction amount is a round number")
                .severity(BigDecimal.valueOf(0.3))
                .confidence(BigDecimal.valueOf(0.8))
                .details(Map.of("amount", request.getAmount()))
                .isBlocking(false)
                .build());
        }

        // Split transaction detection
        if (isPotentialSplitTransaction(request)) {
            indicators.add(FraudIndicator.builder()
                .type("PATTERN")
                .name("POTENTIAL_STRUCTURING")
                .description("Potential transaction structuring detected")
                .severity(BigDecimal.valueOf(0.8))
                .confidence(BigDecimal.valueOf(0.7))
                .details(Map.of(
                    "reason", "Multiple transactions just below reporting threshold",
                    "amount", request.getAmount()
                ))
                .isBlocking(false)
                .build());
        }

        return indicators;
    }

    /**
     * Machine learning based analysis
     */
    private BigDecimal analyzeMachineLearning(FraudDetectionRequest request) {
        try {
            return machineLearningService.predictFraudScore(request);
        } catch (Exception e) {
            log.warn("ML fraud prediction failed for transaction: {}", request.getTransactionId(), e);
            return BigDecimal.valueOf(50); // Neutral score if ML fails
        }
    }

    // Helper methods

    private BigDecimal calculateCompositeRiskScore(List<FraudIndicator> indicators, BigDecimal mlScore) {
        if (indicators.isEmpty()) {
            return mlScore;
        }

        // Weighted scoring
        BigDecimal indicatorScore = indicators.stream()
            .map(indicator -> indicator.getSeverity().multiply(indicator.getConfidence()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(indicators.size()), 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        // Combine indicator score with ML score (70% indicators, 30% ML)
        return indicatorScore.multiply(BigDecimal.valueOf(0.7))
            .add(mlScore.multiply(BigDecimal.valueOf(0.3)))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String determineDecision(BigDecimal riskScore, List<FraudIndicator> indicators) {
        // Check for blocking indicators
        if (indicators.stream().anyMatch(FraudIndicator::isBlocking)) {
            return "DECLINE";
        }

        if (riskScore.compareTo(BigDecimal.valueOf(criticalRiskThreshold)) >= 0) {
            return "DECLINE";
        } else if (riskScore.compareTo(BigDecimal.valueOf(highRiskThreshold)) >= 0) {
            return "REVIEW";
        } else {
            return "APPROVE";
        }
    }

    private boolean shouldRequireManualReview(FraudRiskLevel riskLevel, List<FraudIndicator> indicators) {
        return riskLevel == FraudRiskLevel.HIGH || 
               riskLevel == FraudRiskLevel.VERY_HIGH ||
               indicators.stream().anyMatch(i -> i.getSeverity().compareTo(BigDecimal.valueOf(0.8)) > 0);
    }

    private String determineReviewReason(FraudRiskLevel riskLevel, List<FraudIndicator> indicators) {
        if (riskLevel == FraudRiskLevel.CRITICAL) {
            return "Critical risk level detected";
        }
        
        Optional<FraudIndicator> highSeverity = indicators.stream()
            .filter(i -> i.getSeverity().compareTo(BigDecimal.valueOf(0.8)) > 0)
            .findFirst();
            
        if (highSeverity.isPresent()) {
            return "High severity indicator: " + highSeverity.get().getName();
        }
        
        return riskLevel == FraudRiskLevel.HIGH ? "High risk transaction" : null;
    }

    private List<String> generateRecommendations(FraudRiskLevel riskLevel, List<FraudIndicator> indicators) {
        List<String> recommendations = new ArrayList<>();
        
        if (riskLevel == FraudRiskLevel.CRITICAL) {
            recommendations.add("Block transaction immediately");
            recommendations.add("Freeze account pending investigation");
            recommendations.add("Contact user via verified communication channel");
        } else if (riskLevel == FraudRiskLevel.HIGH || riskLevel == FraudRiskLevel.VERY_HIGH) {
            recommendations.add("Require additional authentication");
            recommendations.add("Manual review by fraud team");
            recommendations.add("Monitor account for suspicious activity");
        }
        
        // Specific recommendations based on indicators
        indicators.forEach(indicator -> {
            switch (indicator.getName()) {
                case "UNKNOWN_DEVICE":
                    recommendations.add("Require device verification");
                    break;
                case "UNUSUAL_LOCATION":
                    recommendations.add("Send location verification alert");
                    break;
                case "HIGH_TRANSACTION_COUNT":
                    recommendations.add("Implement temporary velocity limits");
                    break;
            }
        });
        
        return recommendations.stream().distinct().toList();
    }

    private Map<String, Object> extractFeatures(FraudDetectionRequest request) {
        Map<String, Object> features = new HashMap<>();
        features.put("amount", request.getAmount());
        features.put("timestamp", request.getTimestamp());
        features.put("userId", request.getUserId());
        features.put("transactionType", request.getTransactionType());
        features.put("hasDeviceFingerprint", request.getDeviceFingerprint() != null);
        features.put("hasIpAddress", request.getIpAddress() != null);
        return features;
    }

    private Map<String, BigDecimal> extractScores(List<FraudIndicator> indicators, BigDecimal mlScore) {
        Map<String, BigDecimal> scores = new HashMap<>();
        scores.put("machineLearning", mlScore);
        
        indicators.stream()
            .collect(Collectors.groupingBy(FraudIndicator::getType))
            .forEach((type, typeIndicators) -> {
                BigDecimal avgScore = typeIndicators.stream()
                    .map(i -> i.getSeverity().multiply(BigDecimal.valueOf(100)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(typeIndicators.size()), 2, RoundingMode.HALF_UP);
                scores.put(type.toLowerCase(), avgScore);
            });
            
        return scores;
    }

    private BigDecimal calculateAmountAnomalySeverity(BehavioralProfile profile, BigDecimal amount) {
        BigDecimal typical = profile.getTypicalAmount();
        BigDecimal deviation = amount.subtract(typical).abs().divide(typical, 2, RoundingMode.HALF_UP);
        return deviation.min(BigDecimal.ONE); // Cap at 1.0
    }

    private boolean isRoundAmount(BigDecimal amount) {
        return amount.remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0 ||
               amount.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isPotentialSplitTransaction(FraudDetectionRequest request) {
        BigDecimal reportingThreshold = BigDecimal.valueOf(10000);
        return request.getAmount().compareTo(reportingThreshold.multiply(BigDecimal.valueOf(0.9))) > 0 &&
               request.getAmount().compareTo(reportingThreshold) < 0;
    }

    // DTOs and supporting classes would be defined here or in separate files
    
    @lombok.Data
    @lombok.Builder
    public static class FraudDetectionRequest {
        private UUID transactionId;
        private UUID userId;
        private BigDecimal amount;
        private String transactionType;
        private LocalDateTime timestamp;
        private String deviceFingerprint;
        private String ipAddress;
        private Map<String, Object> metadata;
    }

    // Mock implementations for services - would be separate classes
    @lombok.Data
    public static class VelocityCheckResult {
        private boolean exceeded;
        private int currentCount;
        private int limit;
        private String timeframe;
        private BigDecimal currentAmount;
        private BigDecimal amountLimit;
    }

    @lombok.Data
    public static class BehavioralProfile {
        private String typicalTimeRange;
        private String typicalAmountRange;
        private BigDecimal typicalAmount;
    }

    @lombok.Data
    public static class DeviceAnalysisResult {
        private boolean knownDevice;
        private boolean highRiskDevice;
        private String deviceId;
        private LocalDateTime firstSeen;
        private List<String> riskFactors;
        private BigDecimal riskScore;
    }

    @lombok.Data
    public static class GeolocationResult {
        private boolean highRiskCountry;
        private boolean unusualLocation;
        private boolean impossibleTravel;
        private String countryCode;
        private String city;
        private String countryRiskLevel;
        private String currentLocation;
        private List<String> typicalLocations;
        private double distanceFromTypical;
        private String previousLocation;
        private long timeDifference;
        private double requiredSpeedKmh;
    }

    // Mock service implementations
    @Service
    public static class VelocityCheckService {
        public VelocityCheckResult checkTransactionCount(UUID userId, LocalDateTime timestamp) {
            // Implementation would check Redis for velocity counts
            return new VelocityCheckResult();
        }
        
        public VelocityCheckResult checkTransactionAmount(UUID userId, BigDecimal amount, LocalDateTime timestamp) {
            // Implementation would check Redis for velocity amounts
            return new VelocityCheckResult();
        }
    }

    @Service
    public static class BehavioralAnalysisService {
        public BehavioralProfile getUserProfile(UUID userId) {
            // Implementation would retrieve from cache/database
            return new BehavioralProfile();
        }
        
        public boolean isUnusualTime(BehavioralProfile profile, LocalDateTime timestamp) {
            // Implementation would analyze time patterns
            return false;
        }
        
        public boolean isUnusualAmount(BehavioralProfile profile, BigDecimal amount) {
            // Implementation would analyze amount patterns
            return false;
        }
    }

    @Service
    public static class DeviceFingerprintService {
        public DeviceAnalysisResult analyzeDevice(UUID userId, String deviceFingerprint) {
            // Implementation would analyze device patterns
            return new DeviceAnalysisResult();
        }
    }

    @Service
    public static class GeolocationService {
        public GeolocationResult analyzeLocation(UUID userId, String ipAddress) {
            // Implementation would use MaxMind GeoIP
            return new GeolocationResult();
        }
    }

    @Service
    public static class MachineLearningService {
        public BigDecimal predictFraudScore(FraudDetectionRequest request) {
            // Implementation would use ML model
            return BigDecimal.valueOf(30);
        }
    }
}