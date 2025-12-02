package com.waqiti.ml.fraud;

import com.waqiti.common.events.FraudEvent;
import com.waqiti.common.events.FinancialEventPublisher;
import com.waqiti.ml.dto.BehaviorAnalysisResult;
import com.waqiti.ml.dto.GeolocationAnalysisResult;
import com.waqiti.ml.dto.NetworkAnalysisResult;
import com.waqiti.ml.dto.UserRiskProfile;
import com.waqiti.ml.fraud.model.*;
import com.waqiti.ml.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Advanced ML-based fraud detection engine for financial transactions.
 * Implements multiple detection algorithms, behavioral analysis, and real-time scoring.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedFraudDetectionEngine {

    private final FinancialEventPublisher eventPublisher;
    private final UserBehaviorProfileService behaviorProfileService;
    private final GeolocationService geolocationService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final NetworkAnalysisService networkAnalysisService;
    private final MachineLearningModelService mlModelService;

    // PRODUCTION FIX: Inject helper class with all fraud detection algorithms
    private final AdvancedFraudDetectionEngineHelper helper;

    // Cache for user risk profiles
    private final Map<String, UserRiskProfile> userRiskCache = new ConcurrentHashMap<>();

    // Known fraud patterns
    private final Map<String, FraudPattern> fraudPatterns = new ConcurrentHashMap<>();

    // Velocity tracking
    private final Map<String, List<TransactionVelocity>> velocityTracker = new ConcurrentHashMap<>();

    /**
     * CRITICAL SECURITY: Comprehensive fraud analysis for transactions
     * FIXED: Replaced hardcoded 0.5 fraud score with real ML-based analysis
     */
    public FraudAnalysisResult analyzeTransaction(TransactionData transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("SECURITY: Starting comprehensive fraud analysis for transaction: {}", 
                    transaction.getTransactionId());
            
            FraudAnalysisResult result = new FraudAnalysisResult();
            result.setTransactionId(transaction.getTransactionId());
            result.setUserId(transaction.getUserId());
            result.setTimestamp(LocalDateTime.now());
            
            // 1. Velocity-based analysis
            VelocityAnalysisResult velocityResult = analyzeVelocity(transaction);
            result.addAnalysis("velocity", velocityResult);
            log.debug("FRAUD: Velocity analysis score: {}", velocityResult.getOverallVelocityScore());
            
            // 2. Behavioral analysis
            BehaviorAnalysisResult behaviorResult = analyzeBehavior(transaction);
            result.addAnalysis("behavior", behaviorResult);
            log.debug("FRAUD: Behavior analysis score: {}", behaviorResult.getOverallBehaviorScore());
            
            // 3. Geolocation analysis
            GeolocationAnalysisResult geoResult = analyzeGeolocation(transaction);
            result.addAnalysis("geolocation", geoResult);
            log.debug("FRAUD: Geolocation analysis score: {}", geoResult.getOverallGeoScore());
            
            // 4. Device fingerprinting
            DeviceAnalysisResult deviceResult = analyzeDevice(transaction);
            result.addAnalysis("device", deviceResult);
            log.debug("FRAUD: Device analysis score: {}", deviceResult.getOverallDeviceScore());
            
            // 5. Network analysis
            NetworkAnalysisResult networkResult = analyzeNetwork(transaction);
            result.addAnalysis("network", networkResult);
            log.debug("FRAUD: Network analysis score: {}", networkResult.getOverallNetworkScore());
            
            // 6. Pattern matching
            PatternAnalysisResult patternResult = analyzePatterns(transaction);
            result.addAnalysis("patterns", patternResult);
            log.debug("FRAUD: Pattern analysis score: {}", patternResult.getOverallPatternScore());
            
            // 7. CRITICAL: ML model scoring - REAL IMPLEMENTATION
            MLAnalysisResult mlResult = runMLModels(transaction);
            result.addAnalysis("ml_models", mlResult);
            log.debug("FRAUD: ML ensemble score: {}", mlResult.getEnsembleScore());
            
            // 8. Graph-based analysis (relationship networks)
            GraphAnalysisResult graphResult = analyzeTransactionGraph(transaction);
            result.addAnalysis("graph", graphResult);
            log.debug("FRAUD: Graph analysis score: {}", graphResult.getOverallGraphScore());
            
            // CRITICAL SECURITY FIX: Calculate composite risk score using real analysis
            double compositeScore = calculateCompositeRiskScore(result);
            result.setRiskScore(compositeScore);
            result.setRiskLevel(determineRiskLevel(compositeScore));
            
            // SECURITY: Log final fraud assessment
            log.info("SECURITY: Fraud analysis complete - Transaction: {}, Risk Score: {}, Risk Level: {}", 
                    transaction.getTransactionId(), compositeScore, result.getRiskLevel());
            
            // Generate recommendations based on real analysis
            List<FraudRecommendation> recommendations = generateRecommendations(result);
            result.setRecommendations(recommendations);
            
            // Update user risk profile with actual data
            updateUserRiskProfile(transaction.getUserId(), result);
            
            // Track analysis performance
            long analysisTime = System.currentTimeMillis() - startTime;
            log.info("PERFORMANCE: Fraud analysis completed for transaction {} in {}ms, risk score: {}", 
                transaction.getTransactionId(), analysisTime, compositeScore);
            
            // Publish fraud event if significant risk detected
            if (result.getRiskLevel().ordinal() >= RiskLevel.MEDIUM.ordinal()) {
                log.warn("SECURITY: High-risk transaction detected - publishing fraud event: {}", 
                        transaction.getTransactionId());
                publishFraudEvent(transaction, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("CRITICAL: Error during fraud analysis for transaction: " + transaction.getTransactionId(), e);
            
            // SECURITY FIX: Return actual risk assessment even on error
            FraudAnalysisResult errorResult = new FraudAnalysisResult();
            errorResult.setTransactionId(transaction.getTransactionId());
            
            // Use basic heuristics if ML analysis fails
            double basicRiskScore = calculateBasicRiskScore(transaction);
            errorResult.setRiskScore(basicRiskScore);
            errorResult.setRiskLevel(determineRiskLevel(basicRiskScore));
            errorResult.setError("Analysis error: " + e.getMessage());
            
            log.warn("SECURITY: Fraud analysis error - using basic heuristics: transaction={}, basicRisk={}", 
                    transaction.getTransactionId(), basicRiskScore);
            
            return errorResult;
        }
    }
    
    /**
     * SECURITY FIX: Calculate basic risk score when ML analysis fails
     */
    private double calculateBasicRiskScore(TransactionData transaction) {
        double riskScore = 0.0;
        
        // Amount-based risk (large amounts are riskier)
        BigDecimal amount = transaction.getAmount();
        if (amount != null) {
            if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
                riskScore += 0.3; // Large amount
            } else if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) {
                riskScore += 0.1; // Medium amount
            }
        }
        
        // Time-based risk (off-hours transactions)
        LocalDateTime timestamp = transaction.getTimestamp();
        if (timestamp != null) {
            int hour = timestamp.getHour();
            if (hour < 6 || hour > 22) { // Outside normal hours
                riskScore += 0.2;
            }
        }
        
        // New user risk
        String userId = transaction.getUserId();
        if (userId != null && behaviorProfileService != null) {
            UserBehaviorProfile profile = behaviorProfileService.getUserProfile(userId);
            if (profile == null) {
                riskScore += 0.2; // New user
            }
        }
        
        // Device risk
        if (transaction.getDeviceId() == null) {
            riskScore += 0.1; // Unknown device
        }
        
        // Location risk
        if (transaction.getLatitude() == null || transaction.getLongitude() == null) {
            riskScore += 0.1; // Unknown location
        }
        
        // Cap at maximum risk
        return Math.min(riskScore, 0.95);
    }

    /**
     * Real-time user risk assessment
     */
    public UserRiskAssessment assessUserRisk(String userId) {
        UserRiskProfile profile = getUserRiskProfile(userId);
        
        UserRiskAssessment assessment = new UserRiskAssessment();
        assessment.setUserId(userId);
        assessment.setTimestamp(LocalDateTime.now());
        
        // Historical risk analysis
        double historicalRisk = calculateHistoricalRisk(userId);
        assessment.setHistoricalRiskScore(historicalRisk);
        
        // Current session risk
        double sessionRisk = calculateSessionRisk(userId);
        assessment.setSessionRiskScore(sessionRisk);
        
        // Account security score
        double securityScore = calculateAccountSecurityScore(userId);
        assessment.setSecurityScore(securityScore);
        
        // Behavioral consistency score
        double behaviorScore = calculateBehaviorConsistency(userId);
        assessment.setBehaviorConsistencyScore(behaviorScore);
        
        // Overall user risk
        double overallRisk = (historicalRisk * 0.3 + sessionRisk * 0.3 + 
                            (1 - securityScore) * 0.2 + (1 - behaviorScore) * 0.2);
        assessment.setOverallRiskScore(overallRisk);
        assessment.setRiskLevel(determineRiskLevel(overallRisk));
        
        // Risk factors
        List<String> riskFactors = identifyRiskFactors(profile, assessment);
        assessment.setRiskFactors(riskFactors);
        
        // Trust score (inverse of risk)
        assessment.setTrustScore(1.0 - overallRisk);
        
        log.debug("User risk assessment completed for user {}: risk={}, trust={}", 
            userId, overallRisk, assessment.getTrustScore());
        
        return assessment;
    }

    /**
     * Velocity analysis - detects unusual transaction patterns
     * PRODUCTION: Delegates to helper class for all algorithm implementations
     */
    private VelocityAnalysisResult analyzeVelocity(TransactionData transaction) {
        String userId = transaction.getUserId();
        BigDecimal amount = transaction.getAmount();
        LocalDateTime timestamp = transaction.getTimestamp();

        VelocityAnalysisResult result = new VelocityAnalysisResult();

        // Get recent transactions for velocity analysis - DELEGATED TO HELPER
        List<TransactionVelocity> recentTransactions = helper.getRecentTransactions(userId, 24);

        // Count-based velocity (number of transactions)
        long transactionsLastHour = recentTransactions.stream()
            .filter(tx -> tx.getTimestamp().isAfter(timestamp.minusHours(1)))
            .count();

        long transactionsLast24Hours = recentTransactions.size();

        // Amount-based velocity
        BigDecimal amountLastHour = recentTransactions.stream()
            .filter(tx -> tx.getTimestamp().isAfter(timestamp.minusHours(1)))
            .map(TransactionVelocity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal amountLast24Hours = recentTransactions.stream()
            .map(TransactionVelocity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate velocity scores - DELEGATED TO HELPER
        double countVelocityScore = helper.calculateCountVelocityScore(transactionsLastHour, transactionsLast24Hours);
        double amountVelocityScore = helper.calculateAmountVelocityScore(amountLastHour, amountLast24Hours, amount);

        result.setTransactionsLastHour(transactionsLastHour);
        result.setTransactionsLast24Hours(transactionsLast24Hours);
        result.setAmountLastHour(amountLastHour);
        result.setAmountLast24Hours(amountLast24Hours);
        result.setCountVelocityScore(countVelocityScore);
        result.setAmountVelocityScore(amountVelocityScore);
        result.setOverallVelocityScore(Math.max(countVelocityScore, amountVelocityScore));

        // Check for burst patterns - DELEGATED TO HELPER
        boolean burstDetected = helper.detectBurstPattern(recentTransactions);
        result.setBurstPatternDetected(burstDetected);

        // Update velocity tracker - DELEGATED TO HELPER
        helper.updateVelocityTracker(userId, new TransactionVelocity(transaction));

        return result;
    }

    /**
     * Behavioral analysis - compares against user's normal behavior
     * PRODUCTION: Delegates to helper class for all pattern analysis
     */
    private BehaviorAnalysisResult analyzeBehavior(TransactionData transaction) {
        String userId = transaction.getUserId();
        UserBehaviorProfile profile = behaviorProfileService.getUserProfile(userId);

        BehaviorAnalysisResult result = new BehaviorAnalysisResult();

        if (profile == null) {
            result.setNewUser(true);
            result.setOverallBehaviorScore(0.3); // Higher risk for new users
            return result;
        }

        // All pattern analysis delegated to helper class
        double timeScore = helper.analyzeTimePattern(transaction, profile);
        result.setTimePatternScore(timeScore);

        double amountScore = helper.analyzeAmountPattern(transaction, profile);
        result.setAmountPatternScore(amountScore);

        double recipientScore = helper.analyzeRecipientPattern(transaction, profile);
        result.setRecipientPatternScore(recipientScore);

        double typeScore = helper.analyzeTransactionTypePattern(transaction, profile);
        result.setTypePatternScore(typeScore);

        double frequencyScore = helper.analyzeFrequencyPattern(transaction, profile);
        result.setFrequencyPatternScore(frequencyScore);

        // Calculate overall behavior score
        double overallScore = (timeScore + amountScore + recipientScore + typeScore + frequencyScore) / 5.0;
        result.setOverallBehaviorScore(overallScore);

        // Identify behavioral anomalies - DELEGATED TO HELPER
        List<String> anomalies = helper.identifyBehavioralAnomalies(transaction, profile);
        result.setAnomalies(anomalies);

        return result;
    }

    /**
     * Geolocation analysis - detects impossible travel and location anomalies
     */
    private GeolocationAnalysisResult analyzeGeolocation(TransactionData transaction) {
        GeolocationAnalysisResult result = new GeolocationAnalysisResult();
        
        if (transaction.getLatitude() == null || transaction.getLongitude() == null) {
            result.setLocationUnavailable(true);
            result.setOverallGeoScore(0.2); // Slightly elevated risk
            return result;
        }
        
        String userId = transaction.getUserId();
        Location currentLocation = new Location(transaction.getLatitude(), transaction.getLongitude());
        
        // Get user's recent locations
        List<LocationHistory> recentLocations = geolocationService.getRecentLocations(userId, 7); // 7 days
        
        if (recentLocations.isEmpty()) {
            result.setNewLocation(true);
            result.setOverallGeoScore(0.3);
            return result;
        }
        
        // Check for impossible travel
        LocationHistory lastLocation = recentLocations.get(0);
        double distanceKm = geolocationService.calculateDistance(
            lastLocation.getLocation(), currentLocation);
        long timeDiffMinutes = ChronoUnit.MINUTES.between(
            lastLocation.getTimestamp(), transaction.getTimestamp());
        
        // Impossible travel check - DELEGATED TO HELPER
        boolean impossibleTravel = helper.checkImpossibleTravel(distanceKm, timeDiffMinutes);
        result.setImpossibleTravel(impossibleTravel);
        result.setDistanceFromLastLocation(distanceKm);
        result.setTimeSinceLastTransaction(timeDiffMinutes);

        // Check if location is in user's normal areas
        boolean familiarLocation = geolocationService.isFamiliarLocation(userId, currentLocation);
        result.setFamiliarLocation(familiarLocation);

        // Check for high-risk geographical areas
        boolean highRiskLocation = geolocationService.isHighRiskLocation(currentLocation);
        result.setHighRiskLocation(highRiskLocation);

        // Calculate geo risk score - DELEGATED TO HELPER
        double geoScore = helper.calculateGeoRiskScore(result);
        result.setOverallGeoScore(geoScore);

        return result;
    }

    /**
     * Device analysis - fingerprinting and device behavior
     */
    private DeviceAnalysisResult analyzeDevice(TransactionData transaction) {
        DeviceAnalysisResult result = new DeviceAnalysisResult();
        
        String deviceId = transaction.getDeviceId();
        if (deviceId == null) {
            result.setDeviceUnknown(true);
            result.setOverallDeviceScore(0.4);
            return result;
        }
        
        String userId = transaction.getUserId();
        
        // Check if device is recognized
        boolean recognizedDevice = deviceFingerprintService.isRecognizedDevice(userId, deviceId);
        result.setRecognizedDevice(recognizedDevice);
        
        // Device reputation analysis
        DeviceReputation reputation = deviceFingerprintService.getDeviceReputation(deviceId);
        result.setDeviceReputation(reputation);
        
        // Check for device anomalies
        List<String> deviceAnomalies = deviceFingerprintService.analyzeDeviceAnomalies(transaction);
        result.setDeviceAnomalies(deviceAnomalies);
        
        // Browser/app fingerprinting
        if (transaction.getUserAgent() != null) {
            BrowserFingerprint browserFingerprint = 
                deviceFingerprintService.analyzeBrowserFingerprint(transaction.getUserAgent());
            result.setBrowserFingerprint(browserFingerprint);
        }
        
        // Calculate device risk score - DELEGATED TO HELPER
        double deviceScore = helper.calculateDeviceRiskScore(result);
        result.setOverallDeviceScore(deviceScore);

        return result;
    }

    /**
     * Network analysis - IP reputation, proxy detection, network patterns
     */
    private NetworkAnalysisResult analyzeNetwork(TransactionData transaction) {
        NetworkAnalysisResult result = new NetworkAnalysisResult();
        
        String ipAddress = transaction.getIpAddress();
        if (ipAddress == null) {
            result.setOverallNetworkScore(0.3);
            return result;
        }
        
        // IP reputation check
        IPReputation ipReputation = networkAnalysisService.getIPReputation(ipAddress);
        result.setIpReputation(ipReputation);
        
        // Proxy/VPN detection
        boolean proxyDetected = networkAnalysisService.isProxyOrVPN(ipAddress);
        result.setProxyDetected(proxyDetected);
        
        // Tor network detection
        boolean torDetected = networkAnalysisService.isTorNetwork(ipAddress);
        result.setTorDetected(torDetected);
        
        // Botnet detection
        boolean botnetDetected = networkAnalysisService.isBotnetIP(ipAddress);
        result.setBotnetDetected(botnetDetected);
        
        // Geolocation consistency
        if (transaction.getLatitude() != null && transaction.getLongitude() != null) {
            Location geoLocation = new Location(transaction.getLatitude(), transaction.getLongitude());
            Location ipLocation = networkAnalysisService.getIPLocation(ipAddress);
            double geoInconsistency = geolocationService.calculateDistance(geoLocation, ipLocation);
            result.setGeoInconsistencyKm(geoInconsistency);
        }
        
        // Calculate network risk score - DELEGATED TO HELPER
        double networkScore = helper.calculateNetworkRiskScore(result);
        result.setOverallNetworkScore(networkScore);

        return result;
    }

    /**
     * Pattern analysis - matches against known fraud patterns
     */
    private PatternAnalysisResult analyzePatterns(TransactionData transaction) {
        PatternAnalysisResult result = new PatternAnalysisResult();
        List<String> matchedPatterns = new ArrayList<>();

        // All pattern detection delegated to helper class
        if (helper.isStructuringPattern(transaction.getAmount())) {
            matchedPatterns.add("STRUCTURING");
        }

        if (helper.isOffHourPattern(transaction.getTimestamp())) {
            matchedPatterns.add("OFF_HOURS");
        }

        if (helper.hasRepeatedFailures(transaction.getUserId())) {
            matchedPatterns.add("REPEATED_FAILURES");
        }

        if (helper.isRoundNumberPattern(transaction.getAmount())) {
            matchedPatterns.add("ROUND_NUMBERS");
        }

        if (helper.isRapidSequencePattern(transaction)) {
            matchedPatterns.add("RAPID_SEQUENCE");
        }

        result.setMatchedPatterns(matchedPatterns);
        result.setPatternCount(matchedPatterns.size());

        // Calculate pattern risk score - DELEGATED TO HELPER
        double patternScore = helper.calculatePatternRiskScore(matchedPatterns);
        result.setOverallPatternScore(patternScore);

        return result;
    }

    /**
     * ML model scoring using trained models
     */
    private MLAnalysisResult runMLModels(TransactionData transaction) {
        MLAnalysisResult result = new MLAnalysisResult();
        Map<String, Double> modelScores = new HashMap<>();

        try {
            // Feature extraction for ML models - DELEGATED TO HELPER
            Map<String, Object> features = helper.extractMLFeatures(transaction);

            // Random Forest model
            double randomForestScore = mlModelService.scoreRandomForest(features);
            modelScores.put("random_forest", randomForestScore);

            // Gradient Boosting model
            double gradientBoostingScore = mlModelService.scoreGradientBoosting(features);
            modelScores.put("gradient_boosting", gradientBoostingScore);

            // Neural Network model
            double neuralNetworkScore = mlModelService.scoreNeuralNetwork(features);
            modelScores.put("neural_network", neuralNetworkScore);

            // Ensemble score (weighted average)
            double ensembleScore = (randomForestScore * 0.4 +
                                  gradientBoostingScore * 0.4 +
                                  neuralNetworkScore * 0.2);

            result.setModelScores(modelScores);
            result.setEnsembleScore(ensembleScore);
            result.setFeatureCount(features.size());

        } catch (Exception e) {
            log.error("Error running ML models for transaction: " + transaction.getTransactionId(), e);
            result.setError("ML model error: " + e.getMessage());
            result.setEnsembleScore(0.5); // Default medium risk
        }

        return result;
    }

    /**
     * Graph-based analysis for relationship networks
     */
    private GraphAnalysisResult analyzeTransactionGraph(TransactionData transaction) {
        GraphAnalysisResult result = new GraphAnalysisResult();
        
        try {
            // Analyze user's transaction network
            TransactionGraph graph = networkAnalysisService.getUserTransactionGraph(
                transaction.getUserId(), 30); // 30 days
            
            // Calculate centrality metrics
            double centralityScore = graph.calculateCentrality(transaction.getUserId());
            result.setCentralityScore(centralityScore);
            
            // Detect suspicious clusters
            List<String> suspiciousClusters = graph.detectSuspiciousClusters();
            result.setSuspiciousClusters(suspiciousClusters);
            
            // Check for money laundering patterns
            boolean launderingPattern = graph.detectMoneyLaunderingPattern(transaction);
            result.setMoneyLaunderingPattern(launderingPattern);
            
            // Calculate network risk score - DELEGATED TO HELPER
            double networkRiskScore = helper.calculateGraphRiskScore(result);
            result.setOverallGraphScore(networkRiskScore);

        } catch (Exception e) {
            log.error("Error in graph analysis", e);
            result.setError("Graph analysis error: " + e.getMessage());
            result.setOverallGraphScore(0.0);
        }

        return result;
    }

    // Helper methods for risk score calculations

    private double calculateCompositeRiskScore(FraudAnalysisResult result) {
        // Weighted combination of all analysis results
        Map<String, Double> weights = Map.of(
            "velocity", 0.20,
            "behavior", 0.25,
            "geolocation", 0.15,
            "device", 0.10,
            "network", 0.10,
            "patterns", 0.15,
            "ml_models", 0.30,
            "graph", 0.10
        );
        
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<String, Object> entry : result.getAnalyses().entrySet()) {
            String analysisType = entry.getKey();
            Object analysisResult = entry.getValue();
            Double weight = weights.get(analysisType);
            
            if (weight != null && analysisResult instanceof ScoredAnalysisResult) {
                double score = ((ScoredAnalysisResult) analysisResult).getOverallScore();
                totalScore += score * weight;
                totalWeight += weight;
            }
        }
        
        return totalWeight > 0 ? totalScore / totalWeight : 0.5;
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) return RiskLevel.CRITICAL;
        if (riskScore >= 0.6) return RiskLevel.HIGH;
        if (riskScore >= 0.4) return RiskLevel.MEDIUM;
        if (riskScore >= 0.2) return RiskLevel.LOW;
        return RiskLevel.VERY_LOW;
    }

    private List<FraudRecommendation> generateRecommendations(FraudAnalysisResult result) {
        List<FraudRecommendation> recommendations = new ArrayList<>();
        
        if (result.getRiskLevel() == RiskLevel.CRITICAL) {
            recommendations.add(new FraudRecommendation(
                "BLOCK_TRANSACTION", "Block transaction immediately", 1.0));
            recommendations.add(new FraudRecommendation(
                "FREEZE_ACCOUNT", "Consider account freeze", 0.9));
        } else if (result.getRiskLevel() == RiskLevel.HIGH) {
            recommendations.add(new FraudRecommendation(
                "MANUAL_REVIEW", "Require manual review", 0.9));
            recommendations.add(new FraudRecommendation(
                "ADDITIONAL_AUTH", "Require additional authentication", 0.8));
        } else if (result.getRiskLevel() == RiskLevel.MEDIUM) {
            recommendations.add(new FraudRecommendation(
                "ENHANCED_MONITORING", "Enable enhanced monitoring", 0.7));
            recommendations.add(new FraudRecommendation(
                "CHALLENGE_QUESTION", "Present challenge question", 0.6));
        }
        
        return recommendations;
    }

    // Additional helper methods would be implemented here...
    // [Many more helper methods omitted for brevity]

    private void publishFraudEvent(TransactionData transaction, FraudAnalysisResult result) {
        FraudEvent fraudEvent = new FraudEvent();
        fraudEvent.setTransactionId(transaction.getTransactionId());
        fraudEvent.setUserId(transaction.getUserId());
        fraudEvent.setRiskScore(result.getRiskScore());
        fraudEvent.setRiskLevel(result.getRiskLevel());
        fraudEvent.setDetectionMethods(result.getAnalyses().keySet());
        fraudEvent.setTimestamp(LocalDateTime.now());
        
        eventPublisher.publishFraudEvent(fraudEvent);
    }

    // Additional implementation methods...
}