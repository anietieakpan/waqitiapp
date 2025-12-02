package com.waqiti.payment.core.service;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.ml.FraudMLModelService;
import com.waqiti.common.fraud.ComprehensiveFraudBlacklistService;
import com.waqiti.common.fraud.FraudServiceHelper;
import com.waqiti.common.fraud.model.*;
import com.waqiti.common.velocity.VelocityCheckService;
import com.waqiti.common.geo.GeoLocationService;
import com.waqiti.common.device.DeviceFingerprintService;
import com.waqiti.common.behavior.BehavioralAnalysisService;
import com.waqiti.common.graph.NetworkAnalysisService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Industrial-strength fraud detection service with ML integration
 * 
 * Features:
 * - Real-time machine learning scoring
 * - Behavioral pattern analysis
 * - Device fingerprinting and tracking
 * - Velocity checking across multiple dimensions
 * - Geographic anomaly detection
 * - Network graph analysis for fraud rings
 * - Blacklist and watchlist screening
 * - Rule engine with dynamic rules
 * - Real-time risk scoring
 * - Adaptive learning from feedback
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentFraudDetectionService {
    
    // Service dependencies
    private final FraudMLModelService mlModelService;
    private final ComprehensiveFraudBlacklistService blacklistService;
    private final FraudServiceHelper fraudServiceHelper;
    private final VelocityCheckService velocityService;
    private final GeoLocationService geoLocationService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final NetworkAnalysisService networkAnalysisService;
    private final MeterRegistry meterRegistry;
    
    // Configuration
    @Value("${fraud.detection.ml.enabled:true}")
    private boolean mlEnabled;
    
    @Value("${fraud.detection.rules.enabled:true}")
    private boolean rulesEnabled;
    
    @Value("${fraud.detection.realtime.enabled:true}")
    private boolean realtimeEnabled;
    
    @Value("${fraud.detection.suspicious-amount:10000.00}")
    private BigDecimal SUSPICIOUS_AMOUNT_THRESHOLD;
    
    @Value("${fraud.detection.max-hourly-transactions:20}")
    private int MAX_TRANSACTIONS_PER_HOUR;
    
    @Value("${fraud.detection.max-hourly-volume:50000.00}")
    private BigDecimal MAX_HOURLY_VOLUME;
    
    @Value("${fraud.detection.high-risk-threshold:0.7}")
    private double HIGH_RISK_THRESHOLD;
    
    @Value("${fraud.detection.medium-risk-threshold:0.4}")
    private double MEDIUM_RISK_THRESHOLD;
    
    // Caching and tracking
    private final Map<String, UserRiskProfile> userRiskProfiles = new ConcurrentHashMap<>();
    private final Map<String, DeviceProfile> deviceProfiles = new ConcurrentHashMap<>();
    private final Map<String, FraudRule> activeRules = new ConcurrentHashMap<>();
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(10);
    
    /**
     * Performs comprehensive fraud analysis with parallel processing
     */
    public FraudAnalysisResult analyzePayment(PaymentRequest request) {
        log.info("Starting comprehensive fraud analysis for payment: {}", request.getPaymentId());
        
        try {
            // Start parallel analysis tasks
            List<CompletableFuture<FraudScore>> analysisTasks = new ArrayList<>();
            
            // ML Model Scoring
            if (mlEnabled) {
                analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                    performMLScoring(request), analysisExecutor));
            }
            
            // Rule-based Analysis
            if (rulesEnabled) {
                analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                    performRuleBasedAnalysis(request), analysisExecutor));
            }
            
            // Velocity Analysis
            analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                performVelocityAnalysis(request), analysisExecutor));
            
            // Behavioral Analysis
            analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                performBehavioralAnalysis(request), analysisExecutor));
            
            // Device Analysis
            analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                performDeviceAnalysis(request), analysisExecutor));
            
            // Geographic Analysis
            analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                performGeographicAnalysis(request), analysisExecutor));
            
            // Network Analysis
            analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                performNetworkAnalysis(request), analysisExecutor));
            
            // Blacklist Screening
            analysisTasks.add(CompletableFuture.supplyAsync(() -> 
                performBlacklistScreening(request), analysisExecutor));
            
            // Wait for all analyses to complete
            CompletableFuture<Void> allAnalyses = CompletableFuture.allOf(
                analysisTasks.toArray(new CompletableFuture[0])
            );
            
            allAnalyses.get(5, TimeUnit.SECONDS); // Timeout after 5 seconds
            
            // Collect and aggregate results
            List<FraudScore> scores = analysisTasks.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
            
            // Calculate composite risk score
            FraudAnalysisResult result = aggregateScores(request, scores);
            
            // Update profiles and learning
            updateProfiles(request, result);
            
            // Log metrics
            logFraudMetrics(result);
            
            log.info("Fraud analysis completed: paymentId={}, riskScore={}, decision={}", 
                request.getPaymentId(), result.getRiskScore(), result.getDecision());
            
            return result;
            
        } catch (TimeoutException e) {
            log.error("Fraud analysis timeout for payment: {}", request.getPaymentId());
            return createTimeoutResult(request);
        } catch (Exception e) {
            log.error("Fraud analysis failed: ", e);
            return createErrorResult(request, e);
        }
    }
    
    /**
     * ML-based fraud scoring
     */
    private FraudScore performMLScoring(PaymentRequest request) {
        try {
            // Prepare features for ML model
            Map<String, Object> features = extractMLFeatures(request);
            
            // Get ML prediction
            MLPrediction prediction = mlModelService.predict(features);
            
            return FraudScore.builder()
                .scoreType("ML_MODEL")
                .score(prediction.getFraudProbability())
                .confidence(prediction.getConfidence())
                .factors(prediction.getTopFactors())
                .weight(0.35) // ML gets 35% weight
                .build();
                
        } catch (Exception e) {
            log.error("ML scoring failed: ", e);
            return FraudScore.defaultScore("ML_MODEL");
        }
    }
    
    /**
     * Rule-based fraud analysis
     */
    private FraudScore performRuleBasedAnalysis(PaymentRequest request) {
        try {
            List<String> triggeredRules = new ArrayList<>();
            double ruleScore = 0.0;
            
            // Check amount rules
            if (request.getAmount().compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
                triggeredRules.add("HIGH_AMOUNT");
                ruleScore += 0.3;
            }
            
            // Check for round amounts (potential testing)
            if (isRoundAmount(request.getAmount())) {
                triggeredRules.add("ROUND_AMOUNT");
                ruleScore += 0.1;
            }
            
            // Check for rapid succession
            if (isRapidSuccession(request)) {
                triggeredRules.add("RAPID_SUCCESSION");
                ruleScore += 0.4;
            }
            
            // Check for new recipient
            if (isNewRecipient(request)) {
                triggeredRules.add("NEW_RECIPIENT");
                ruleScore += 0.2;
            }
            
            // Check time-based rules
            if (isUnusualTime(request)) {
                triggeredRules.add("UNUSUAL_TIME");
                ruleScore += 0.15;
            }
            
            // Check for pattern anomalies
            if (hasPatternAnomalies(request)) {
                triggeredRules.add("PATTERN_ANOMALY");
                ruleScore += 0.25;
            }
            
            return FraudScore.builder()
                .scoreType("RULE_ENGINE")
                .score(Math.min(ruleScore, 1.0))
                .confidence(0.9)
                .factors(triggeredRules)
                .weight(0.25) // Rules get 25% weight
                .build();
                
        } catch (Exception e) {
            log.error("Rule analysis failed: ", e);
            return FraudScore.defaultScore("RULE_ENGINE");
        }
    }
    
    private int analyzeAmount(PaymentRequest request, StringBuilder riskFactors) {
        int score = 0;
        BigDecimal amount = request.getAmount();
        
        // Large amount transactions are riskier
        if (amount.compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            score += 30;
            riskFactors.append("Large amount transaction; ");
        }
        
        // Round numbers might indicate testing
        if (amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0) {
            score += 5;
            riskFactors.append("Round amount; ");
        }
        
        return score;
    }
    
    /**
     * Velocity-based fraud analysis
     */
    private FraudScore performVelocityAnalysis(PaymentRequest request) {
        try {
            String userId = request.getFromUserId();
            List<String> velocityFlags = new ArrayList<>();
            double velocityScore = 0.0;
            
            // Check transaction count velocity
            VelocityCheck hourlyCheck = velocityService.checkHourlyVelocity(userId);
            if (hourlyCheck.getCount() > MAX_TRANSACTIONS_PER_HOUR) {
                velocityFlags.add("HOURLY_COUNT_EXCEEDED");
                velocityScore += 0.4;
            }
            
            // Check volume velocity
            if (hourlyCheck.getTotalAmount().compareTo(MAX_HOURLY_VOLUME) > 0) {
                velocityFlags.add("HOURLY_VOLUME_EXCEEDED");
                velocityScore += 0.35;
            }
            
            // Check daily velocity
            VelocityCheck dailyCheck = velocityService.checkDailyVelocity(userId);
            if (dailyCheck.getCount() > MAX_TRANSACTIONS_PER_HOUR * 10) {
                velocityFlags.add("DAILY_COUNT_EXCEEDED");
                velocityScore += 0.25;
            }
            
            // Check for velocity spikes
            if (hasVelocitySpike(userId)) {
                velocityFlags.add("VELOCITY_SPIKE");
                velocityScore += 0.3;
            }
            
            // Check recipient velocity (for P2P)
            if (request.getType() == PaymentType.P2P && request.getToUserId() != null) {
                VelocityCheck recipientCheck = velocityService.checkRecipientVelocity(
                    userId, request.getToUserId()
                );
                if (recipientCheck.getCount() > 5) {
                    velocityFlags.add("HIGH_RECIPIENT_FREQUENCY");
                    velocityScore += 0.2;
                }
            }
            
            return FraudScore.builder()
                .scoreType("VELOCITY")
                .score(Math.min(velocityScore, 1.0))
                .confidence(0.85)
                .factors(velocityFlags)
                .weight(0.15) // Velocity gets 15% weight
                .build();
                
        } catch (Exception e) {
            log.error("Velocity analysis failed: ", e);
            return FraudScore.defaultScore("VELOCITY");
        }
    }
    
    private int analyzePatterns(PaymentRequest request, StringBuilder riskFactors) {
        int score = 0;
        
        // Check for suspicious patterns in metadata
        if (request.getMetadata() != null) {
            // Multiple payments to same recipient in short time
            if (request.getType() == PaymentType.P2P && request.getToUserId() != null) {
                // In production, check recent payments to same user
                // This is a simplified check
            }
            
            // Check for suspicious descriptions or metadata
            Object description = request.getMetadata().get("description");
            if (description != null && containsSuspiciousKeywords(description.toString())) {
                score += 15;
                riskFactors.append("Suspicious description; ");
            }
        }
        
        return score;
    }
    
    private int analyzeGeographic(PaymentRequest request, StringBuilder riskFactors) {
        int score = 0;
        
        if (request.getMetadata() != null) {
            Object location = request.getMetadata().get("location");
            Object ipAddress = request.getMetadata().get("ipAddress");
            
            // Check for high-risk countries or unusual locations
            if (location != null) {
                String locationStr = location.toString().toLowerCase();
                if (isHighRiskLocation(locationStr)) {
                    score += 20;
                    riskFactors.append("High-risk location; ");
                }
            }
            
            // Check for VPN/proxy usage
            if (ipAddress != null && isProxyOrVpn(ipAddress.toString())) {
                score += 15;
                riskFactors.append("Proxy/VPN detected; ");
            }
        }
        
        return score;
    }
    
    private int analyzeDevice(PaymentRequest request, StringBuilder riskFactors) {
        int score = 0;
        
        if (request.getMetadata() != null) {
            Object deviceId = request.getMetadata().get("deviceId");
            Object userAgent = request.getMetadata().get("userAgent");
            
            // Check for new or suspicious devices
            if (deviceId != null && isNewDevice(request.getFromUserId(), deviceId.toString())) {
                score += 10;
                riskFactors.append("New device; ");
            }
            
            // Check for suspicious user agents
            if (userAgent != null && isSuspiciousUserAgent(userAgent.toString())) {
                score += 15;
                riskFactors.append("Suspicious user agent; ");
            }
        }
        
        return score;
    }
    
    private FraudRiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= HIGH_RISK_THRESHOLD) {
            return FraudRiskLevel.CRITICAL;
        } else if (riskScore >= 0.6) {
            return FraudRiskLevel.HIGH;
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            return FraudRiskLevel.MEDIUM;
        } else if (riskScore >= 0.2) {
            return FraudRiskLevel.LOW;
        } else {
            return FraudRiskLevel.MINIMAL;
        }
    }
    
    private FraudDecision determineDecision(double riskScore, List<String> factors) {
        // Check for immediate block conditions
        if (factors.contains("USER_BLACKLISTED") || 
            factors.contains("DEVICE_BLACKLISTED") ||
            factors.contains("FRAUD_RING_SUSPECTED")) {
            return FraudDecision.BLOCK;
        }
        
        // Score-based decision
        if (riskScore >= HIGH_RISK_THRESHOLD) {
            return FraudDecision.BLOCK;
        } else if (riskScore >= 0.5) {
            return FraudDecision.CHALLENGE; // Require additional verification
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            return FraudDecision.REVIEW; // Manual review required
        } else {
            return FraudDecision.ALLOW;
        }
    }
    
    private FraudRecommendation getRecommendation(FraudRiskLevel riskLevel) {
        switch (riskLevel) {
            case HIGH:
                return FraudRecommendation.BLOCK;
            case MEDIUM:
                return FraudRecommendation.REVIEW;
            case LOW:
                return FraudRecommendation.MONITOR;
            default:
                return FraudRecommendation.ALLOW;
        }
    }
    
    /**
     * Behavioral analysis
     */
    private FraudScore performBehavioralAnalysis(PaymentRequest request) {
        try {
            String userId = request.getFromUserId();
            List<String> behaviorFlags = new ArrayList<>();
            double behaviorScore = 0.0;
            
            // Get user's behavioral profile
            BehaviorProfile profile = behavioralAnalysisService.getUserProfile(userId);
            
            // Check for deviations from normal behavior
            if (isAmountDeviation(request.getAmount(), profile)) {
                behaviorFlags.add("AMOUNT_DEVIATION");
                behaviorScore += 0.3;
            }
            
            if (isTimeDeviation(LocalDateTime.now(), profile)) {
                behaviorFlags.add("TIME_PATTERN_DEVIATION");
                behaviorScore += 0.2;
            }
            
            if (isFrequencyDeviation(profile)) {
                behaviorFlags.add("FREQUENCY_DEVIATION");
                behaviorScore += 0.25;
            }
            
            // Check for suspicious patterns
            if (hasSuspiciousSequence(userId)) {
                behaviorFlags.add("SUSPICIOUS_SEQUENCE");
                behaviorScore += 0.4;
            }
            
            return FraudScore.builder()
                .scoreType("BEHAVIORAL")
                .score(Math.min(behaviorScore, 1.0))
                .confidence(0.75)
                .factors(behaviorFlags)
                .weight(0.10) // Behavioral gets 10% weight
                .build();
                
        } catch (Exception e) {
            log.error("Behavioral analysis failed: ", e);
            return FraudScore.defaultScore("BEHAVIORAL");
        }
    }
    
    /**
     * Device fingerprinting analysis
     */
    private FraudScore performDeviceAnalysis(PaymentRequest request) {
        try {
            List<String> deviceFlags = new ArrayList<>();
            double deviceScore = 0.0;
            
            String deviceId = extractDeviceId(request);
            if (deviceId == null) {
                deviceFlags.add("NO_DEVICE_ID");
                deviceScore += 0.3;
            } else {
                // Check device profile
                DeviceProfile deviceProfile = deviceProfiles.get(deviceId);
                
                if (deviceProfile == null) {
                    // New device
                    deviceFlags.add("NEW_DEVICE");
                    deviceScore += 0.25;
                    deviceProfiles.put(deviceId, createDeviceProfile(request));
                } else {
                    // Check for device anomalies
                    if (deviceProfile.isSuspicious()) {
                        deviceFlags.add("SUSPICIOUS_DEVICE");
                        deviceScore += 0.5;
                    }
                    
                    if (deviceProfile.hasMultipleUsers()) {
                        deviceFlags.add("SHARED_DEVICE");
                        deviceScore += 0.3;
                    }
                    
                    if (hasDeviceVelocityIssue(deviceProfile)) {
                        deviceFlags.add("DEVICE_VELOCITY_HIGH");
                        deviceScore += 0.35;
                    }
                }
                
                // Check for device spoofing
                DeviceSpoofingResult spoofCheck = deviceFingerprintService.checkSpoofing(
                    request.getMetadata()
                );
                if (spoofCheck.isSpoofed()) {
                    deviceFlags.add("DEVICE_SPOOFING_DETECTED");
                    deviceScore += 0.6;
                }
            }
            
            return FraudScore.builder()
                .scoreType("DEVICE")
                .score(Math.min(deviceScore, 1.0))
                .confidence(0.8)
                .factors(deviceFlags)
                .weight(0.10) // Device gets 10% weight
                .build();
                
        } catch (Exception e) {
            log.error("Device analysis failed: ", e);
            return FraudScore.defaultScore("DEVICE");
        }
    }
    
    /**
     * Geographic analysis
     */
    private FraudScore performGeographicAnalysis(PaymentRequest request) {
        try {
            List<String> geoFlags = new ArrayList<>();
            double geoScore = 0.0;
            
            String ipAddress = extractIpAddress(request);
            if (ipAddress != null) {
                GeoLocation location = geoLocationService.getLocation(ipAddress);
                
                // Check for high-risk countries
                if (location.isHighRiskCountry()) {
                    geoFlags.add("HIGH_RISK_COUNTRY");
                    geoScore += 0.4;
                }
                
                // Check for impossible travel
                if (hasImpossibleTravel(request.getFromUserId(), location)) {
                    geoFlags.add("IMPOSSIBLE_TRAVEL");
                    geoScore += 0.6;
                }
                
                // Check for VPN/Proxy
                if (location.isVpnOrProxy()) {
                    geoFlags.add("VPN_PROXY_DETECTED");
                    geoScore += 0.3;
                }
                
                // Check for TOR
                if (location.isTorExit()) {
                    geoFlags.add("TOR_EXIT_NODE");
                    geoScore += 0.5;
                }
                
                // Check for location mismatch
                if (hasLocationMismatch(request.getFromUserId(), location)) {
                    geoFlags.add("LOCATION_MISMATCH");
                    geoScore += 0.35;
                }
            } else {
                geoFlags.add("NO_LOCATION_DATA");
                geoScore += 0.2;
            }
            
            return FraudScore.builder()
                .scoreType("GEOGRAPHIC")
                .score(Math.min(geoScore, 1.0))
                .confidence(0.85)
                .factors(geoFlags)
                .weight(0.05) // Geographic gets 5% weight
                .build();
                
        } catch (Exception e) {
            log.error("Geographic analysis failed: ", e);
            return FraudScore.defaultScore("GEOGRAPHIC");
        }
    }
    
    /**
     * Network graph analysis for fraud rings
     */
    private FraudScore performNetworkAnalysis(PaymentRequest request) {
        try {
            List<String> networkFlags = new ArrayList<>();
            double networkScore = 0.0;
            
            // Analyze transaction network
            NetworkAnalysis analysis = networkAnalysisService.analyzeTransaction(
                request.getFromUserId(),
                request.getToUserId()
            );
            
            // Check for fraud ring indicators
            if (analysis.hasFraudRingIndicators()) {
                networkFlags.add("FRAUD_RING_SUSPECTED");
                networkScore += 0.7;
            }
            
            // Check for money mule patterns
            if (analysis.hasMoneyMulePattern()) {
                networkFlags.add("MONEY_MULE_PATTERN");
                networkScore += 0.6;
            }
            
            // Check for circular transactions
            if (analysis.hasCircularTransactions()) {
                networkFlags.add("CIRCULAR_TRANSACTIONS");
                networkScore += 0.5;
            }
            
            // Check network density
            if (analysis.getNetworkDensity() > 0.7) {
                networkFlags.add("HIGH_NETWORK_DENSITY");
                networkScore += 0.3;
            }
            
            return FraudScore.builder()
                .scoreType("NETWORK")
                .score(Math.min(networkScore, 1.0))
                .confidence(0.7)
                .factors(networkFlags)
                .weight(0.05) // Network gets 5% weight
                .build();
                
        } catch (Exception e) {
            log.error("Network analysis failed: ", e);
            return FraudScore.defaultScore("NETWORK");
        }
    }
    
    /**
     * Blacklist screening
     */
    private FraudScore performBlacklistScreening(PaymentRequest request) {
        try {
            List<String> blacklistFlags = new ArrayList<>();
            double blacklistScore = 0.0;
            
            // Check user blacklist
            BlacklistCheckResult userCheck = blacklistService.checkEntity(
                request.getFromUserId(), "USER"
            );
            
            if (userCheck.isBlacklisted()) {
                blacklistFlags.add("USER_BLACKLISTED");
                blacklistScore = 1.0; // Immediate high score
            } else if (userCheck.isWatchlisted()) {
                blacklistFlags.add("USER_WATCHLISTED");
                blacklistScore += 0.5;
            }
            
            // Check recipient blacklist for P2P
            if (request.getType() == PaymentType.P2P && request.getToUserId() != null) {
                BlacklistCheckResult recipientCheck = blacklistService.checkEntity(
                    request.getToUserId(), "USER"
                );
                
                if (recipientCheck.isBlacklisted()) {
                    blacklistFlags.add("RECIPIENT_BLACKLISTED");
                    blacklistScore = 1.0;
                } else if (recipientCheck.isWatchlisted()) {
                    blacklistFlags.add("RECIPIENT_WATCHLISTED");
                    blacklistScore += 0.4;
                }
            }
            
            // Check device blacklist
            String deviceId = extractDeviceId(request);
            if (deviceId != null) {
                BlacklistCheckResult deviceCheck = blacklistService.checkEntity(
                    deviceId, "DEVICE"
                );
                
                if (deviceCheck.isBlacklisted()) {
                    blacklistFlags.add("DEVICE_BLACKLISTED");
                    blacklistScore = 1.0;
                }
            }
            
            // Check IP blacklist
            String ipAddress = extractIpAddress(request);
            if (ipAddress != null) {
                BlacklistCheckResult ipCheck = blacklistService.checkEntity(
                    ipAddress, "IP"
                );
                
                if (ipCheck.isBlacklisted()) {
                    blacklistFlags.add("IP_BLACKLISTED");
                    blacklistScore = Math.max(blacklistScore, 0.8);
                }
            }
            
            return FraudScore.builder()
                .scoreType("BLACKLIST")
                .score(blacklistScore)
                .confidence(1.0) // Blacklist checks are definitive
                .factors(blacklistFlags)
                .weight(0.05) // Blacklist gets 5% weight but can override
                .build();
                
        } catch (Exception e) {
            log.error("Blacklist screening failed: ", e);
            return FraudScore.defaultScore("BLACKLIST");
        }
    }
    
    /**
     * Aggregates scores from all analyses
     */
    private FraudAnalysisResult aggregateScores(PaymentRequest request, List<FraudScore> scores) {
        // Calculate weighted average
        double totalScore = 0.0;
        double totalWeight = 0.0;
        List<String> allFactors = new ArrayList<>();
        Map<String, Double> scoreBreakdown = new HashMap<>();
        
        for (FraudScore score : scores) {
            // Check for immediate block conditions
            if (score.getScoreType().equals("BLACKLIST") && score.getScore() >= 1.0) {
                return createBlockedResult(request, score);
            }
            
            totalScore += score.getScore() * score.getWeight();
            totalWeight += score.getWeight();
            allFactors.addAll(score.getFactors());
            scoreBreakdown.put(score.getScoreType(), score.getScore());
        }
        
        // Normalize score
        double finalScore = totalWeight > 0 ? totalScore / totalWeight : 0.0;
        
        // Determine risk level and decision
        FraudRiskLevel riskLevel = determineRiskLevel(finalScore);
        FraudDecision decision = determineDecision(finalScore, allFactors);
        
        return FraudAnalysisResult.builder()
            .paymentId(request.getPaymentId())
            .analysisId(UUID.randomUUID().toString())
            .riskScore(finalScore)
            .riskLevel(riskLevel)
            .decision(decision)
            .riskFactors(allFactors)
            .scoreBreakdown(scoreBreakdown)
            .requiresManualReview(decision == FraudDecision.REVIEW)
            .requiresAdditionalVerification(finalScore > 0.3 && finalScore < 0.7)
            .analysisTimeMs(System.currentTimeMillis())
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Updates user and device profiles
     */
    @Async
    private void updateProfiles(PaymentRequest request, FraudAnalysisResult result) {
        try {
            // Update user risk profile
            String userId = request.getFromUserId();
            userRiskProfiles.compute(userId, (key, profile) -> {
                if (profile == null) {
                    profile = new UserRiskProfile(userId);
                }
                profile.addTransaction(request, result);
                return profile;
            });
            
            // Update device profile
            String deviceId = extractDeviceId(request);
            if (deviceId != null) {
                deviceProfiles.compute(deviceId, (key, profile) -> {
                    if (profile == null) {
                        profile = new DeviceProfile(deviceId);
                    }
                    profile.addTransaction(request, result);
                    return profile;
                });
            }
            
            // Send feedback to ML model for learning
            if (mlEnabled) {
                mlModelService.recordFeedback(request, result);
            }
            
        } catch (Exception e) {
            log.error("Failed to update profiles: ", e);
        }
    }
    
    private boolean containsSuspiciousKeywords(String description) {
        String[] suspiciousKeywords = {"test", "fake", "launder", "illegal", "hack"};
        String lowerDesc = description.toLowerCase();
        for (String keyword : suspiciousKeywords) {
            if (lowerDesc.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isHighRiskLocation(String location) {
        // Simplified check - in production would use proper geo-risk database
        return location.contains("unknown") || location.contains("tor");
    }
    
    private boolean isProxyOrVpn(String ipAddress) {
        // Simplified check - in production would use IP intelligence service
        return ipAddress.startsWith("10.") || ipAddress.startsWith("192.168.");
    }
    
    private boolean isNewDevice(String userId, String deviceId) {
        // Simplified check - in production would check device history
        return deviceId.contains("new") || deviceId.contains("unknown");
    }
    
    private boolean isSuspiciousUserAgent(String userAgent) {
        // Simplified check - in production would have comprehensive UA analysis
        return userAgent.toLowerCase().contains("bot") || userAgent.toLowerCase().contains("crawler");
    }
    
    // Helper methods
    
    private Map<String, Object> extractMLFeatures(PaymentRequest request) {
        Map<String, Object> features = new HashMap<>();
        
        // Transaction features
        features.put("amount", request.getAmount().doubleValue());
        features.put("payment_type", request.getType().toString());
        features.put("hour_of_day", LocalDateTime.now().getHour());
        features.put("day_of_week", LocalDateTime.now().getDayOfWeek().getValue());
        
        // User features
        UserRiskProfile userProfile = userRiskProfiles.get(request.getFromUserId());
        if (userProfile != null) {
            features.put("user_risk_score", userProfile.getCurrentRiskScore());
            features.put("user_transaction_count", userProfile.getTotalTransactions());
            features.put("user_avg_amount", userProfile.getAverageAmount());
        }
        
        // Device features
        String deviceId = extractDeviceId(request);
        if (deviceId != null) {
            DeviceProfile deviceProfile = deviceProfiles.get(deviceId);
            if (deviceProfile != null) {
                features.put("device_risk_score", deviceProfile.getRiskScore());
                features.put("device_user_count", deviceProfile.getUserCount());
            }
        }
        
        return features;
    }
    
    private String extractDeviceId(PaymentRequest request) {
        if (request.getMetadata() != null) {
            Object deviceId = request.getMetadata().get("deviceId");
            return deviceId != null ? deviceId.toString() : null;
        }
        return null;
    }
    
    private String extractIpAddress(PaymentRequest request) {
        if (request.getMetadata() != null) {
            Object ip = request.getMetadata().get("ipAddress");
            return ip != null ? ip.toString() : null;
        }
        return null;
    }
    
    private boolean isRoundAmount(BigDecimal amount) {
        return amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0 ||
               amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0;
    }
    
    private boolean isRapidSuccession(PaymentRequest request) {
        UserRiskProfile profile = userRiskProfiles.get(request.getFromUserId());
        if (profile != null && profile.getLastTransactionTime() != null) {
            long secondsSinceLastTransaction = ChronoUnit.SECONDS.between(
                profile.getLastTransactionTime(), LocalDateTime.now()
            );
            return secondsSinceLastTransaction < 30; // Less than 30 seconds
        }
        return false;
    }
    
    private boolean isNewRecipient(PaymentRequest request) {
        if (request.getType() != PaymentType.P2P || request.getToUserId() == null) {
            return false;
        }
        
        UserRiskProfile profile = userRiskProfiles.get(request.getFromUserId());
        return profile == null || !profile.hasTransactedWith(request.getToUserId());
    }
    
    private boolean isUnusualTime(PaymentRequest request) {
        int hour = LocalDateTime.now().getHour();
        // Unusual hours: 2 AM - 5 AM
        return hour >= 2 && hour <= 5;
    }
    
    private boolean hasPatternAnomalies(PaymentRequest request) {
        // Check for pattern anomalies in transaction
        UserRiskProfile profile = userRiskProfiles.get(request.getFromUserId());
        if (profile != null) {
            // Check if amount deviates significantly from average
            BigDecimal avgAmount = profile.getAverageAmount();
            if (avgAmount != null && avgAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal deviation = request.getAmount().subtract(avgAmount)
                    .abs().divide(avgAmount, 2, RoundingMode.HALF_UP);
                return deviation.compareTo(new BigDecimal("3")) > 0; // More than 300% deviation
            }
        }
        return false;
    }
    
    private boolean hasVelocitySpike(String userId) {
        UserRiskProfile profile = userRiskProfiles.get(userId);
        return profile != null && profile.hasVelocitySpike();
    }
    
    private boolean isAmountDeviation(BigDecimal amount, BehaviorProfile profile) {
        if (profile.getAverageAmount() != null) {
            BigDecimal deviation = amount.subtract(profile.getAverageAmount())
                .abs().divide(profile.getAverageAmount(), 2, RoundingMode.HALF_UP);
            return deviation.compareTo(new BigDecimal("2")) > 0; // More than 200% deviation
        }
        return false;
    }
    
    private boolean isTimeDeviation(LocalDateTime time, BehaviorProfile profile) {
        return !profile.isNormalTransactionTime(time.getHour());
    }
    
    private boolean isFrequencyDeviation(BehaviorProfile profile) {
        return profile.getCurrentFrequency() > profile.getNormalFrequency() * 2;
    }
    
    private boolean hasSuspiciousSequence(String userId) {
        UserRiskProfile profile = userRiskProfiles.get(userId);
        return profile != null && profile.hasSuspiciousSequence();
    }
    
    private boolean hasDeviceVelocityIssue(DeviceProfile profile) {
        return profile.getHourlyTransactionCount() > 20;
    }
    
    private boolean hasImpossibleTravel(String userId, GeoLocation currentLocation) {
        UserRiskProfile profile = userRiskProfiles.get(userId);
        if (profile != null && profile.getLastLocation() != null) {
            double distance = calculateDistance(
                profile.getLastLocation(), currentLocation
            );
            long minutesSinceLastTransaction = ChronoUnit.MINUTES.between(
                profile.getLastTransactionTime(), LocalDateTime.now()
            );
            
            // Check if travel speed is impossible (> 900 km/h)
            double speed = (distance / minutesSinceLastTransaction) * 60;
            return speed > 900;
        }
        return false;
    }
    
    private boolean hasLocationMismatch(String userId, GeoLocation location) {
        UserRiskProfile profile = userRiskProfiles.get(userId);
        return profile != null && 
               profile.getPrimaryCountry() != null && 
               !profile.getPrimaryCountry().equals(location.getCountry());
    }
    
    private double calculateDistance(GeoLocation loc1, GeoLocation loc2) {
        // Haversine formula for distance calculation
        double lat1 = Math.toRadians(loc1.getLatitude());
        double lat2 = Math.toRadians(loc2.getLatitude());
        double deltaLat = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double deltaLon = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return 6371 * c; // Earth's radius in kilometers
    }
    
    private DeviceProfile createDeviceProfile(PaymentRequest request) {
        return new DeviceProfile(extractDeviceId(request));
    }
    
    private FraudAnalysisResult createBlockedResult(PaymentRequest request, FraudScore blockingScore) {
        return FraudAnalysisResult.builder()
            .paymentId(request.getPaymentId())
            .analysisId(UUID.randomUUID().toString())
            .riskScore(1.0)
            .riskLevel(FraudRiskLevel.CRITICAL)
            .decision(FraudDecision.BLOCK)
            .riskFactors(blockingScore.getFactors())
            .blockReason("Blacklist match: " + String.join(", ", blockingScore.getFactors()))
            .requiresManualReview(false)
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    private FraudAnalysisResult createTimeoutResult(PaymentRequest request) {
        return FraudAnalysisResult.builder()
            .paymentId(request.getPaymentId())
            .analysisId(UUID.randomUUID().toString())
            .riskScore(0.5)
            .riskLevel(FraudRiskLevel.MEDIUM)
            .decision(FraudDecision.REVIEW)
            .riskFactors(List.of("ANALYSIS_TIMEOUT"))
            .requiresManualReview(true)
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    private FraudAnalysisResult createErrorResult(PaymentRequest request, Exception e) {
        return FraudAnalysisResult.builder()
            .paymentId(request.getPaymentId())
            .analysisId(UUID.randomUUID().toString())
            .riskScore(0.7)
            .riskLevel(FraudRiskLevel.HIGH)
            .decision(FraudDecision.REVIEW)
            .riskFactors(List.of("ANALYSIS_ERROR"))
            .errorMessage(e.getMessage())
            .requiresManualReview(true)
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    private void logFraudMetrics(FraudAnalysisResult result) {
        Counter.builder("fraud.analysis.completed")
            .tag("risk_level", result.getRiskLevel().toString())
            .tag("decision", result.getDecision().toString())
            .register(meterRegistry)
            .increment();
    }
}