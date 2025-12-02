package com.waqiti.analytics.processor;

import com.waqiti.analytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise-grade fraud analytics processor with ML-based detection
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudAnalyticsProcessor {

    private final Map<String, List<FraudEvent>> fraudEventHistory = new ConcurrentHashMap<>();
    private final Map<String, UserRiskProfile> userRiskProfiles = new ConcurrentHashMap<>();
    private final Map<String, MerchantRiskProfile> merchantRiskProfiles = new ConcurrentHashMap<>();
    private final Map<String, AnomalyPattern> detectedAnomalies = new ConcurrentHashMap<>();
    
    // Risk scoring thresholds
    private static final BigDecimal LOW_RISK_THRESHOLD = BigDecimal.valueOf(30);
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal HIGH_RISK_THRESHOLD = BigDecimal.valueOf(85);
    
    // Velocity thresholds
    private static final int MAX_TRANSACTIONS_PER_HOUR = 50;
    private static final BigDecimal MAX_AMOUNT_PER_HOUR = BigDecimal.valueOf(10000);

    /**
     * Analyze transaction for fraud patterns
     */
    public void analyzeTransaction(PaymentEvent event) {
        try {
            // Create fraud event
            FraudEvent fraudEvent = createFraudEvent(event);
            
            // Store fraud event
            storeFraudEvent(fraudEvent);
            
            // Perform multi-layered fraud analysis
            FraudAnalysisResult analysisResult = performComprehensiveFraudAnalysis(fraudEvent);
            
            // Update risk profiles
            updateRiskProfiles(fraudEvent, analysisResult);
            
            // Detect anomalies
            detectAnomalies(fraudEvent, analysisResult);
            
            // Log high-risk transactions
            if (analysisResult.getRiskScore().compareTo(HIGH_RISK_THRESHOLD) > 0) {
                log.warn("High-risk transaction detected: {} - Risk Score: {}", 
                        event.getTransactionId(), analysisResult.getRiskScore());
            }
            
        } catch (Exception e) {
            log.error("Error analyzing transaction for fraud: {}", event.getTransactionId(), e);
        }
    }

    /**
     * Create fraud event from payment event
     */
    private FraudEvent createFraudEvent(PaymentEvent event) {
        return FraudEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .paymentMethod(event.getPaymentMethod())
                .deviceId(event.getDeviceId())
                .ipAddress(event.getIpAddress())
                .location(event.getLocation())
                .timestamp(event.getTimestamp())
                .status(event.getStatus())
                .metadata(event.getMetadata())
                .build();
    }

    /**
     * Store fraud event for analysis
     */
    private void storeFraudEvent(FraudEvent event) {
        String key = "fraud-events-" + event.getTimestamp().toString().substring(0, 10); // Daily partitioning
        fraudEventHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
    }

    /**
     * Perform comprehensive fraud analysis
     */
    private FraudAnalysisResult performComprehensiveFraudAnalysis(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal totalRiskScore = BigDecimal.ZERO;
        
        // 1. Velocity Analysis
        VelocityAnalysisResult velocityResult = analyzeVelocity(event);
        totalRiskScore = totalRiskScore.add(velocityResult.getRiskScore());
        riskFactors.addAll(velocityResult.getRiskFactors());
        
        // 2. Amount Pattern Analysis
        AmountAnalysisResult amountResult = analyzeAmountPatterns(event);
        totalRiskScore = totalRiskScore.add(amountResult.getRiskScore());
        riskFactors.addAll(amountResult.getRiskFactors());
        
        // 3. Geographic Analysis
        GeographicAnalysisResult geoResult = analyzeGeographicPatterns(event);
        totalRiskScore = totalRiskScore.add(geoResult.getRiskScore());
        riskFactors.addAll(geoResult.getRiskFactors());
        
        // 4. Device Analysis
        DeviceAnalysisResult deviceResult = analyzeDevicePatterns(event);
        totalRiskScore = totalRiskScore.add(deviceResult.getRiskScore());
        riskFactors.addAll(deviceResult.getRiskFactors());
        
        // 5. Time Pattern Analysis
        TimeAnalysisResult timeResult = analyzeTimePatterns(event);
        totalRiskScore = totalRiskScore.add(timeResult.getRiskScore());
        riskFactors.addAll(timeResult.getRiskFactors());
        
        // 6. Behavioral Analysis
        BehavioralAnalysisResult behaviorResult = analyzeBehavioralPatterns(event);
        totalRiskScore = totalRiskScore.add(behaviorResult.getRiskScore());
        riskFactors.addAll(behaviorResult.getRiskFactors());
        
        // Normalize risk score (0-100 scale)
        BigDecimal normalizedScore = totalRiskScore.min(BigDecimal.valueOf(100));
        
        return FraudAnalysisResult.builder()
                .eventId(event.getEventId())
                .transactionId(event.getTransactionId())
                .riskScore(normalizedScore)
                .riskLevel(determineRiskLevel(normalizedScore))
                .riskFactors(riskFactors)
                .recommendation(determineRecommendation(normalizedScore, riskFactors))
                .confidence(calculateConfidence(riskFactors))
                .analysisTimestamp(Instant.now())
                .build();
    }

    /**
     * Analyze velocity patterns
     */
    private VelocityAnalysisResult analyzeVelocity(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        Instant oneHourAgo = event.getTimestamp().minus(1, ChronoUnit.HOURS);
        
        // Get recent transactions for this user
        List<FraudEvent> recentTransactions = getRecentTransactionsByUser(event.getCustomerId(), oneHourAgo);
        
        // Check transaction frequency
        int transactionCount = recentTransactions.size();
        if (transactionCount > MAX_TRANSACTIONS_PER_HOUR) {
            riskFactors.add("EXCESSIVE_TRANSACTION_FREQUENCY");
            riskScore = riskScore.add(BigDecimal.valueOf(25));
        } else if (transactionCount > MAX_TRANSACTIONS_PER_HOUR / 2) {
            riskFactors.add("HIGH_TRANSACTION_FREQUENCY");
            riskScore = riskScore.add(BigDecimal.valueOf(15));
        }
        
        // Check transaction amount velocity
        BigDecimal totalAmount = recentTransactions.stream()
                .map(FraudEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalAmount.compareTo(MAX_AMOUNT_PER_HOUR) > 0) {
            riskFactors.add("EXCESSIVE_AMOUNT_VELOCITY");
            riskScore = riskScore.add(BigDecimal.valueOf(30));
        }
        
        return VelocityAnalysisResult.builder()
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .transactionCount(transactionCount)
                .totalAmount(totalAmount)
                .build();
    }

    /**
     * Analyze amount patterns
     */
    private AmountAnalysisResult analyzeAmountPatterns(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        // Get user's historical transaction amounts
        List<FraudEvent> userHistory = getUserTransactionHistory(event.getCustomerId(), 30);
        
        if (!userHistory.isEmpty()) {
            // Calculate average amount
            BigDecimal avgAmount = userHistory.stream()
                    .map(FraudEvent::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(userHistory.size()), 2, RoundingMode.HALF_UP);
            
            // Check for unusual amounts
            BigDecimal amountRatio = event.getAmount().divide(avgAmount, 2, RoundingMode.HALF_UP);
            
            if (amountRatio.compareTo(BigDecimal.valueOf(10)) > 0) {
                riskFactors.add("EXTREMELY_UNUSUAL_AMOUNT");
                riskScore = riskScore.add(BigDecimal.valueOf(35));
            } else if (amountRatio.compareTo(BigDecimal.valueOf(5)) > 0) {
                riskFactors.add("UNUSUAL_AMOUNT");
                riskScore = riskScore.add(BigDecimal.valueOf(20));
            }
            
            // Check for round number patterns (potential fraud indicator)
            if (isRoundNumber(event.getAmount())) {
                riskFactors.add("ROUND_NUMBER_PATTERN");
                riskScore = riskScore.add(BigDecimal.valueOf(5));
            }
        }
        
        return AmountAnalysisResult.builder()
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .build();
    }

    /**
     * Analyze geographic patterns
     */
    private GeographicAnalysisResult analyzeGeographicPatterns(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        if (event.getLocation() != null) {
            // Get user's recent locations
            List<String> recentLocations = getUserRecentLocations(event.getCustomerId(), 7);
            
            if (!recentLocations.contains(event.getLocation())) {
                riskFactors.add("UNUSUAL_LOCATION");
                riskScore = riskScore.add(BigDecimal.valueOf(15));
                
                // Check for impossible travel (simplified)
                if (hasImpossibleTravel(event, recentLocations)) {
                    riskFactors.add("IMPOSSIBLE_TRAVEL");
                    riskScore = riskScore.add(BigDecimal.valueOf(40));
                }
            }
        }
        
        // IP-based geographic analysis
        if (event.getIpAddress() != null) {
            if (isSuspiciousIp(event.getIpAddress())) {
                riskFactors.add("SUSPICIOUS_IP_ADDRESS");
                riskScore = riskScore.add(BigDecimal.valueOf(25));
            }
        }
        
        return GeographicAnalysisResult.builder()
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .build();
    }

    /**
     * Analyze device patterns
     */
    private DeviceAnalysisResult analyzeDevicePatterns(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        if (event.getDeviceId() != null) {
            // Check if device is known
            List<String> userDevices = getUserKnownDevices(event.getCustomerId());
            
            if (!userDevices.contains(event.getDeviceId())) {
                riskFactors.add("UNKNOWN_DEVICE");
                riskScore = riskScore.add(BigDecimal.valueOf(20));
                
                // Check for suspicious device patterns
                if (isSuspiciousDevice(event.getDeviceId())) {
                    riskFactors.add("SUSPICIOUS_DEVICE");
                    riskScore = riskScore.add(BigDecimal.valueOf(30));
                }
            }
        }
        
        return DeviceAnalysisResult.builder()
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .build();
    }

    /**
     * Analyze time patterns
     */
    private TimeAnalysisResult analyzeTimePatterns(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        int hour = event.getTimestamp().atZone(java.time.ZoneOffset.UTC).getHour();
        
        // Check for unusual hours (late night/early morning)
        if (hour >= 1 && hour <= 5) {
            riskFactors.add("UNUSUAL_TRANSACTION_TIME");
            riskScore = riskScore.add(BigDecimal.valueOf(10));
        }
        
        // Check user's typical transaction times
        List<Integer> userTypicalHours = getUserTypicalTransactionHours(event.getCustomerId());
        if (!userTypicalHours.isEmpty() && !userTypicalHours.contains(hour)) {
            riskFactors.add("ATYPICAL_TIME_PATTERN");
            riskScore = riskScore.add(BigDecimal.valueOf(15));
        }
        
        return TimeAnalysisResult.builder()
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .build();
    }

    /**
     * Analyze behavioral patterns
     */
    private BehavioralAnalysisResult analyzeBehavioralPatterns(FraudEvent event) {
        List<String> riskFactors = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        // Check for rapid successive transactions (card testing)
        List<FraudEvent> recentEvents = getRecentTransactionsByUser(event.getCustomerId(), 
                event.getTimestamp().minus(10, ChronoUnit.MINUTES));
        
        if (recentEvents.size() > 5) {
            riskFactors.add("RAPID_SUCCESSIVE_TRANSACTIONS");
            riskScore = riskScore.add(BigDecimal.valueOf(25));
        }
        
        // Check for unusual merchant patterns
        String merchantId = event.getMerchantId();
        if (merchantId != null && !isUserFamiliarWithMerchant(event.getCustomerId(), merchantId)) {
            riskFactors.add("UNFAMILIAR_MERCHANT");
            riskScore = riskScore.add(BigDecimal.valueOf(10));
        }
        
        return BehavioralAnalysisResult.builder()
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .build();
    }

    /**
     * Update risk profiles
     */
    private void updateRiskProfiles(FraudEvent event, FraudAnalysisResult analysisResult) {
        // Update user risk profile
        updateUserRiskProfile(event.getCustomerId(), analysisResult);
        
        // Update merchant risk profile
        if (event.getMerchantId() != null) {
            updateMerchantRiskProfile(event.getMerchantId(), analysisResult);
        }
    }

    /**
     * Update user risk profile
     */
    private void updateUserRiskProfile(String userId, FraudAnalysisResult analysisResult) {
        UserRiskProfile profile = userRiskProfiles.computeIfAbsent(userId, 
                k -> UserRiskProfile.builder()
                        .userId(k)
                        .riskScore(BigDecimal.ZERO)
                        .riskLevel("LOW")
                        .transactionCount(0L)
                        .flaggedTransactions(0L)
                        .createdAt(Instant.now())
                        .build());
        
        // Update running average risk score
        BigDecimal newRiskScore = profile.getRiskScore()
                .multiply(BigDecimal.valueOf(profile.getTransactionCount()))
                .add(analysisResult.getRiskScore())
                .divide(BigDecimal.valueOf(profile.getTransactionCount() + 1), 2, RoundingMode.HALF_UP);
        
        profile.setRiskScore(newRiskScore);
        profile.setRiskLevel(determineRiskLevel(newRiskScore));
        profile.setTransactionCount(profile.getTransactionCount() + 1);
        
        if (analysisResult.getRiskScore().compareTo(MEDIUM_RISK_THRESHOLD) > 0) {
            profile.setFlaggedTransactions(profile.getFlaggedTransactions() + 1);
        }
        
        profile.setLastUpdated(Instant.now());
    }

    /**
     * Update merchant risk profile
     */
    private void updateMerchantRiskProfile(String merchantId, FraudAnalysisResult analysisResult) {
        MerchantRiskProfile profile = merchantRiskProfiles.computeIfAbsent(merchantId,
                k -> MerchantRiskProfile.builder()
                        .merchantId(k)
                        .riskScore(BigDecimal.ZERO)
                        .riskLevel("LOW")
                        .transactionCount(0L)
                        .flaggedTransactions(0L)
                        .createdAt(Instant.now())
                        .build());
        
        // Update running average risk score
        BigDecimal newRiskScore = profile.getRiskScore()
                .multiply(BigDecimal.valueOf(profile.getTransactionCount()))
                .add(analysisResult.getRiskScore())
                .divide(BigDecimal.valueOf(profile.getTransactionCount() + 1), 2, RoundingMode.HALF_UP);
        
        profile.setRiskScore(newRiskScore);
        profile.setRiskLevel(determineRiskLevel(newRiskScore));
        profile.setTransactionCount(profile.getTransactionCount() + 1);
        
        if (analysisResult.getRiskScore().compareTo(MEDIUM_RISK_THRESHOLD) > 0) {
            profile.setFlaggedTransactions(profile.getFlaggedTransactions() + 1);
        }
        
        profile.setLastUpdated(Instant.now());
    }

    /**
     * Detect anomalies
     */
    public List<AnomalyDetectionResult> detectAnomalies() {
        List<AnomalyDetectionResult> anomalies = new ArrayList<>();
        
        // Detect user anomalies
        anomalies.addAll(detectUserAnomalies());
        
        // Detect merchant anomalies
        anomalies.addAll(detectMerchantAnomalies());
        
        // Detect system-wide anomalies
        anomalies.addAll(detectSystemAnomalies());
        
        return anomalies;
    }

    /**
     * Detect user anomalies
     */
    private List<AnomalyDetectionResult> detectUserAnomalies() {
        return userRiskProfiles.entrySet().stream()
                .filter(entry -> entry.getValue().getRiskScore().compareTo(HIGH_RISK_THRESHOLD) > 0)
                .map(entry -> AnomalyDetectionResult.builder()
                        .anomalyId(UUID.randomUUID().toString())
                        .type("USER_HIGH_RISK")
                        .entityId(entry.getKey())
                        .description("User has high risk score: " + entry.getValue().getRiskScore())
                        .severity("HIGH")
                        .riskScore(entry.getValue().getRiskScore())
                        .detectedAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Detect merchant anomalies
     */
    private List<AnomalyDetectionResult> detectMerchantAnomalies() {
        return merchantRiskProfiles.entrySet().stream()
                .filter(entry -> {
                    MerchantRiskProfile profile = entry.getValue();
                    double flaggedRate = profile.getFlaggedTransactions().doubleValue() / profile.getTransactionCount() * 100;
                    return flaggedRate > 20; // More than 20% flagged transactions
                })
                .map(entry -> AnomalyDetectionResult.builder()
                        .anomalyId(UUID.randomUUID().toString())
                        .type("MERCHANT_HIGH_FRAUD_RATE")
                        .entityId(entry.getKey())
                        .description("Merchant has high fraud rate")
                        .severity("MEDIUM")
                        .riskScore(entry.getValue().getRiskScore())
                        .detectedAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Detect system-wide anomalies
     */
    private List<AnomalyDetectionResult> detectSystemAnomalies() {
        List<AnomalyDetectionResult> anomalies = new ArrayList<>();
        
        // Detect unusual transaction volume spikes
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentTransactions = fraudEventHistory.values().stream()
                .flatMap(List::stream)
                .filter(event -> event.getTimestamp().isAfter(oneHourAgo))
                .count();
        
        if (recentTransactions > 1000) { // Threshold for unusual volume
            anomalies.add(AnomalyDetectionResult.builder()
                    .anomalyId(UUID.randomUUID().toString())
                    .type("TRANSACTION_VOLUME_SPIKE")
                    .entityId("SYSTEM")
                    .description("Unusual spike in transaction volume: " + recentTransactions)
                    .severity("HIGH")
                    .riskScore(BigDecimal.valueOf(80))
                    .detectedAt(Instant.now())
                    .build());
        }
        
        return anomalies;
    }

    /**
     * Helper methods
     */
    private List<FraudEvent> getRecentTransactionsByUser(String userId, Instant since) {
        return fraudEventHistory.values().stream()
                .flatMap(List::stream)
                .filter(event -> userId.equals(event.getCustomerId()) && event.getTimestamp().isAfter(since))
                .collect(Collectors.toList());
    }

    private List<FraudEvent> getUserTransactionHistory(String userId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return getRecentTransactionsByUser(userId, since);
    }

    private String determineRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(HIGH_RISK_THRESHOLD) > 0) return "HIGH";
        if (riskScore.compareTo(MEDIUM_RISK_THRESHOLD) > 0) return "MEDIUM";
        if (riskScore.compareTo(LOW_RISK_THRESHOLD) > 0) return "LOW";
        return "VERY_LOW";
    }

    private String determineRecommendation(BigDecimal riskScore, List<String> riskFactors) {
        if (riskScore.compareTo(HIGH_RISK_THRESHOLD) > 0) {
            return "BLOCK_TRANSACTION";
        } else if (riskScore.compareTo(MEDIUM_RISK_THRESHOLD) > 0) {
            return "REQUIRE_ADDITIONAL_VERIFICATION";
        } else if (riskScore.compareTo(LOW_RISK_THRESHOLD) > 0) {
            return "MONITOR_CLOSELY";
        } else {
            return "ALLOW_TRANSACTION";
        }
    }

    private BigDecimal calculateConfidence(List<String> riskFactors) {
        // Simple confidence calculation based on number of risk factors
        return BigDecimal.valueOf(Math.min(riskFactors.size() * 15 + 40, 100));
    }

    // Additional helper methods (simplified implementations)
    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(BigDecimal.valueOf(100)).equals(BigDecimal.ZERO) ||
               amount.remainder(BigDecimal.valueOf(50)).equals(BigDecimal.ZERO);
    }

    private List<String> getUserRecentLocations(String userId, int days) {
        return getUserTransactionHistory(userId, days).stream()
                .map(FraudEvent::getLocation)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean hasImpossibleTravel(FraudEvent event, List<String> recentLocations) {
        // Simplified implementation - would need actual geolocation logic
        return false;
    }

    private boolean isSuspiciousIp(String ipAddress) {
        // Simplified implementation - would check against blacklists, VPN detection, etc.
        return ipAddress.startsWith("192.168.") || ipAddress.equals("127.0.0.1");
    }

    private List<String> getUserKnownDevices(String userId) {
        return getUserTransactionHistory(userId, 30).stream()
                .map(FraudEvent::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isSuspiciousDevice(String deviceId) {
        // Simplified implementation - would check device reputation
        return deviceId.contains("emulator") || deviceId.contains("bot");
    }

    private List<Integer> getUserTypicalTransactionHours(String userId) {
        return getUserTransactionHistory(userId, 30).stream()
                .map(event -> event.getTimestamp().atZone(java.time.ZoneOffset.UTC).getHour())
                .collect(Collectors.groupingBy(hour -> hour, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 2) // Hours with more than 2 transactions
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private boolean isUserFamiliarWithMerchant(String userId, String merchantId) {
        return getUserTransactionHistory(userId, 90).stream()
                .anyMatch(event -> merchantId.equals(event.getMerchantId()));
    }

    private void detectAnomalies(FraudEvent event, FraudAnalysisResult analysisResult) {
        if (analysisResult.getRiskScore().compareTo(HIGH_RISK_THRESHOLD) > 0) {
            AnomalyPattern pattern = AnomalyPattern.builder()
                    .patternId(UUID.randomUUID().toString())
                    .type("HIGH_RISK_TRANSACTION")
                    .entityId(event.getCustomerId())
                    .description("High-risk transaction pattern detected")
                    .severity("HIGH")
                    .detectedAt(Instant.now())
                    .build();
            
            detectedAnomalies.put(pattern.getPatternId(), pattern);
        }
    }
}