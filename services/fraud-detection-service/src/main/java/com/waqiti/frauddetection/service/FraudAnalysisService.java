package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.*;
import com.waqiti.frauddetection.ml.FraudMLModel;
import com.waqiti.common.math.MoneyMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Fraud Analysis Service
 * 
 * CRITICAL IMPLEMENTATION: Core fraud detection engine
 * Replaces missing implementation identified in audit
 * 
 * Features:
 * - Real-time transaction analysis
 * - ML-based fraud scoring
 * - Rule-based fraud detection
 * - Behavioral analysis
 * - Device fingerprinting
 * - Velocity checking
 * - Pattern recognition
 * 
 * @author Waqiti Security Team
 * @version 2.0 - Production Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudAnalysisService {

    private final RiskScoringService riskScoringService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final VelocityCheckService velocityCheckService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final FraudMLModel fraudMLModel;
    private final FraudRulesEngine fraudRulesEngine;
    private final com.waqiti.frauddetection.repository.TransactionHistoryRepository transactionHistoryRepository;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    // Fraud detection thresholds
    private static final double HIGH_RISK_THRESHOLD = 0.75;
    private static final double MEDIUM_RISK_THRESHOLD = 0.50;
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal CRITICAL_VALUE_THRESHOLD = new BigDecimal("10000.00");

    /**
     * Comprehensive fraud risk assessment for transactions
     * 
     * CRITICAL: This is the main entry point for fraud detection
     * Combines multiple fraud detection techniques
     */
    @Transactional
    public FraudAssessmentResult assessTransactionFraud(FraudAssessmentRequest request) {
        log.info("Starting comprehensive fraud assessment for user: {}, amount: {}", 
            request.getUserId(), request.getAmount());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate input
            validateFraudAssessmentRequest(request);
            
            // Run fraud checks in parallel for performance
            CompletableFuture<Double> mlScoreFuture = CompletableFuture.supplyAsync(() -> 
                performMLFraudDetection(request)
            );
            
            CompletableFuture<List<FraudRule>> rulesFuture = CompletableFuture.supplyAsync(() ->
                performRuleBasedDetection(request)
            );
            
            CompletableFuture<BehavioralScore> behavioralFuture = CompletableFuture.supplyAsync(() ->
                performBehavioralAnalysis(request)
            );
            
            CompletableFuture<VelocityCheckResult> velocityFuture = CompletableFuture.supplyAsync(() ->
                performVelocityChecks(request)
            );
            
            CompletableFuture<DeviceFingerprintResult> deviceFuture = CompletableFuture.supplyAsync(() ->
                performDeviceFingerprintAnalysis(request)
            );
            
            // Wait for all checks to complete
            CompletableFuture.allOf(mlScoreFuture, rulesFuture, behavioralFuture, 
                velocityFuture, deviceFuture).join();
            
            // Collect results
            Double mlScore = mlScoreFuture.get();
            List<FraudRule> triggeredRules = rulesFuture.get();
            BehavioralScore behavioralScore = behavioralFuture.get();
            VelocityCheckResult velocityResult = velocityFuture.get();
            DeviceFingerprintResult deviceResult = deviceFuture.get();
            
            // Calculate final fraud score
            FraudScore finalScore = calculateFinalFraudScore(
                mlScore, triggeredRules, behavioralScore, velocityResult, deviceResult
            );
            
            // Determine if transaction should be blocked
            boolean blocked = shouldBlockTransaction(finalScore, request);
            
            // Generate mitigation actions
            List<String> mitigationActions = generateMitigationActions(
                finalScore, triggeredRules, blocked
            );
            
            // Build assessment result
            FraudAssessmentResult result = FraudAssessmentResult.builder()
                .assessmentId(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .transactionId(request.getTransactionId())
                .riskScore(finalScore.getOverallScore())
                .riskLevel(determineRiskLevel(finalScore.getOverallScore()))
                .blocked(blocked)
                .mlScore(mlScore)
                .ruleScore(finalScore.getRuleScore())
                .behavioralScore(behavioralScore.getScore())
                .velocityScore(velocityResult.getRiskScore())
                .deviceScore(deviceResult.getRiskScore())
                .triggeredRules(mapTriggeredRules(triggeredRules))
                .mitigationActions(mitigationActions)
                .requiresManualReview(requiresManualReview(finalScore))
                .confidence(finalScore.getConfidence())
                .assessmentTimestamp(LocalDateTime.now())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
            
            // Log assessment
            logFraudAssessment(result, request);
            
            // Trigger alerts if needed
            if (blocked || finalScore.getOverallScore() > HIGH_RISK_THRESHOLD) {
                triggerFraudAlert(result, request);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error during fraud assessment for user: {}", request.getUserId(), e);
            // SECURITY: On error, block transaction for manual review
            return createFailsafeFraudAssessment(request, e);
        }
    }

    /**
     * ML-based fraud detection
     */
    private Double performMLFraudDetection(FraudAssessmentRequest request) {
        try {
            log.debug("Performing ML fraud detection for user: {}", request.getUserId());
            
            // Prepare features for ML model
            Map<String, Object> features = prepareMLFeatures(request);
            
            // Get ML prediction
            double mlScore = fraudMLModel.predict(features);
            
            log.debug("ML fraud score for user {}: {}", request.getUserId(), mlScore);
            return mlScore;
            
        } catch (Exception e) {
            log.error("ML fraud detection failed for user: {}", request.getUserId(), e);
            // Return high risk score on ML failure
            return 0.80;
        }
    }

    /**
     * Rule-based fraud detection
     */
    private List<FraudRule> performRuleBasedDetection(FraudAssessmentRequest request) {
        log.debug("Performing rule-based fraud detection for user: {}", request.getUserId());
        
        List<FraudRule> triggeredRules = new ArrayList<>();
        
        // Rule 1: High-value transaction
        if (request.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            triggeredRules.add(new FraudRule("HIGH_VALUE_TRANSACTION", 0.3, 
                "Transaction amount exceeds high-value threshold"));
        }
        
        // Rule 2: Critical-value transaction
        if (request.getAmount().compareTo(CRITICAL_VALUE_THRESHOLD) > 0) {
            triggeredRules.add(new FraudRule("CRITICAL_VALUE_TRANSACTION", 0.5, 
                "Transaction amount exceeds critical threshold"));
        }
        
        // Rule 3: First transaction
        if (isFirstTransaction(request.getUserId())) {
            triggeredRules.add(new FraudRule("FIRST_TRANSACTION", 0.2, 
                "First transaction for user - higher risk"));
        }
        
        // Rule 4: Unusual hour
        if (isUnusualHour()) {
            triggeredRules.add(new FraudRule("UNUSUAL_HOUR", 0.15, 
                "Transaction during unusual hours"));
        }
        
        // Rule 5: Geographic anomaly
        if (hasGeographicAnomaly(request)) {
            triggeredRules.add(new FraudRule("GEOGRAPHIC_ANOMALY", 0.4, 
                "Transaction from unusual location"));
        }
        
        // Additional rules from fraud rules engine
        List<FraudRule> engineRules = fraudRulesEngine.evaluateRules(request);
        triggeredRules.addAll(engineRules);
        
        log.debug("Triggered {} fraud rules for user: {}", triggeredRules.size(), request.getUserId());
        return triggeredRules;
    }

    /**
     * Behavioral analysis
     */
    private BehavioralScore performBehavioralAnalysis(FraudAssessmentRequest request) {
        log.debug("Performing behavioral analysis for user: {}", request.getUserId());
        return behavioralAnalysisService.analyzeBehavior(request.getUserId(), request);
    }

    /**
     * Velocity checks
     */
    private VelocityCheckResult performVelocityChecks(FraudAssessmentRequest request) {
        log.debug("Performing velocity checks for user: {}", request.getUserId());
        return velocityCheckService.checkVelocity(request.getUserId(), request.getAmount());
    }

    /**
     * Device fingerprint analysis
     */
    private DeviceFingerprintResult performDeviceFingerprintAnalysis(FraudAssessmentRequest request) {
        log.debug("Performing device fingerprint analysis for user: {}", request.getUserId());
        return deviceFingerprintService.analyzeDeviceFingerprint(
            request.getUserId(), 
            request.getDeviceFingerprint()
        );
    }

    /**
     * Calculate final fraud score combining all detection methods
     */
    private FraudScore calculateFinalFraudScore(
            Double mlScore,
            List<FraudRule> triggeredRules,
            BehavioralScore behavioralScore,
            VelocityCheckResult velocityResult,
            DeviceFingerprintResult deviceResult) {
        
        // Weighted scoring
        double ML_WEIGHT = 0.40;
        double RULES_WEIGHT = 0.25;
        double BEHAVIORAL_WEIGHT = 0.15;
        double VELOCITY_WEIGHT = 0.10;
        double DEVICE_WEIGHT = 0.10;
        
        // Calculate rule score
        double ruleScore = triggeredRules.stream()
            .mapToDouble(FraudRule::getWeight)
            .sum();
        ruleScore = Math.min(ruleScore, 1.0); // Cap at 1.0
        
        // Calculate overall score
        double overallScore = (mlScore * ML_WEIGHT) +
                              (ruleScore * RULES_WEIGHT) +
                              (behavioralScore.getScore() * BEHAVIORAL_WEIGHT) +
                              (velocityResult.getRiskScore() * VELOCITY_WEIGHT) +
                              (deviceResult.getRiskScore() * DEVICE_WEIGHT);
        
        // Calculate confidence based on agreement between methods
        double confidence = calculateConfidence(mlScore, ruleScore, behavioralScore.getScore());
        
        return FraudScore.builder()
            .overallScore(overallScore)
            .mlScore(mlScore)
            .ruleScore(ruleScore)
            .behavioralScore(behavioralScore.getScore())
            .velocityScore(velocityResult.getRiskScore())
            .deviceScore(deviceResult.getRiskScore())
            .confidence(confidence)
            .build();
    }

    /**
     * Determine if transaction should be blocked
     */
    private boolean shouldBlockTransaction(FraudScore score, FraudAssessmentRequest request) {
        // Block if overall score is very high
        if (score.getOverallScore() > HIGH_RISK_THRESHOLD) {
            log.warn("FRAUD ALERT: Blocking transaction for user {} - High risk score: {}", 
                request.getUserId(), score.getOverallScore());
            return true;
        }
        
        // Block if ML model has high confidence in fraud
        if (score.getMlScore() > 0.85 && score.getConfidence() > 0.90) {
            log.warn("FRAUD ALERT: Blocking transaction for user {} - High ML confidence", 
                request.getUserId());
            return true;
        }
        
        // Block if multiple high-severity rules triggered
        // (This would be implemented based on triggered rules)
        
        return false;
    }

    /**
     * Determine if manual review is required
     */
    private boolean requiresManualReview(FraudScore score) {
        return score.getOverallScore() > MEDIUM_RISK_THRESHOLD || 
               score.getConfidence() < 0.70;
    }

    /**
     * Generate mitigation actions based on fraud score
     */
    private List<String> generateMitigationActions(
            FraudScore score, List<FraudRule> rules, boolean blocked) {
        
        List<String> actions = new ArrayList<>();
        
        if (blocked) {
            actions.add("TRANSACTION_BLOCKED");
            actions.add("MANUAL_REVIEW_REQUIRED");
        }
        
        if (score.getOverallScore() > MEDIUM_RISK_THRESHOLD) {
            actions.add("ENHANCED_VERIFICATION_REQUIRED");
            actions.add("2FA_REQUIRED");
        }
        
        if (score.getDeviceScore() > 0.70) {
            actions.add("DEVICE_VERIFICATION_REQUIRED");
        }
        
        if (score.getVelocityScore() > 0.70) {
            actions.add("VELOCITY_LIMIT_APPLIED");
            actions.add("COOLING_PERIOD_REQUIRED");
        }
        
        if (rules.stream().anyMatch(r -> "GEOGRAPHIC_ANOMALY".equals(r.getRuleId()))) {
            actions.add("LOCATION_VERIFICATION_REQUIRED");
        }
        
        return actions;
    }

    /**
     * Prepare ML features from request
     */
    private Map<String, Object> prepareMLFeatures(FraudAssessmentRequest request) {
        Map<String, Object> features = new HashMap<>();

        features.put("amount", (double) MoneyMath.toMLFeature(request.getAmount()));
        features.put("hour_of_day", LocalDateTime.now().getHour());
        features.put("day_of_week", LocalDateTime.now().getDayOfWeek().getValue());
        features.put("user_id", request.getUserId());
        features.put("merchant_id", request.getMerchantId());
        features.put("currency", request.getCurrency());
        features.put("transaction_type", request.getTransactionType());

        // Add more sophisticated features
        features.put("device_fingerprint", request.getDeviceFingerprint());
        features.put("ip_address", request.getIpAddress());
        features.put("user_agent", request.getUserAgent());

        return features;
    }

    /**
     * Calculate confidence level
     */
    private double calculateConfidence(double mlScore, double ruleScore, double behavioralScore) {
        // Agreement between different detection methods increases confidence
        double variance = Math.abs(mlScore - ruleScore) + 
                         Math.abs(mlScore - behavioralScore) +
                         Math.abs(ruleScore - behavioralScore);
        
        // Lower variance = higher confidence
        return Math.max(0.0, 1.0 - (variance / 3.0));
    }

    /**
     * Validate fraud assessment request
     */
    private void validateFraudAssessmentRequest(FraudAssessmentRequest request) {
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid transaction amount is required");
        }
    }

    /**
     * Create failsafe fraud assessment on error
     */
    private FraudAssessmentResult createFailsafeFraudAssessment(
            FraudAssessmentRequest request, Exception error) {
        
        return FraudAssessmentResult.builder()
            .assessmentId(UUID.randomUUID().toString())
            .userId(request.getUserId())
            .transactionId(request.getTransactionId())
            .riskScore(0.95) // HIGH RISK on error
            .riskLevel("CRITICAL")
            .blocked(true) // BLOCK on error (fail-secure)
            .triggeredRules(Collections.singletonList("SYSTEM_ERROR"))
            .mitigationActions(Arrays.asList("MANUAL_REVIEW_REQUIRED", "SYSTEM_ERROR_INVESTIGATION"))
            .requiresManualReview(true)
            .confidence(0.0)
            .assessmentTimestamp(LocalDateTime.now())
            .errorMessage("Fraud assessment system error: " + error.getMessage())
            .build();
    }

    /**
     * Determine risk level from score
     */
    private String determineRiskLevel(double score) {
        if (score >= 0.75) return "CRITICAL";
        if (score >= 0.50) return "HIGH";
        if (score >= 0.25) return "MEDIUM";
        return "LOW";
    }

    /**
     * Map triggered rules to simple format
     */
    private List<String> mapTriggeredRules(List<FraudRule> rules) {
        return rules.stream()
            .map(FraudRule::getRuleId)
            .toList();
    }

    /**
     * Helper methods
     */
    private boolean isFirstTransaction(String userId) {
        try {
            long count = transactionHistoryRepository.countByUserId(userId);
            return count == 0;
        } catch (Exception e) {
            log.error("Error checking transaction history for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private boolean isUnusualHour() {
        int hour = LocalDateTime.now().getHour();
        return hour < 6 || hour > 23;
    }

    private boolean hasGeographicAnomaly(FraudAssessmentRequest request) {
        try {
            String currentCountry = request.getCountryCode();
            String ipAddress = request.getIpAddress();
            
            if (currentCountry == null || ipAddress == null) return false;
            
            List<com.waqiti.frauddetection.entity.TransactionHistory> recentTransactions = 
                transactionHistoryRepository.findTop5ByUserIdOrderByTransactionDateDesc(request.getUserId());
            
            if (recentTransactions.isEmpty()) return false;
            
            for (com.waqiti.frauddetection.entity.TransactionHistory txn : recentTransactions) {
                if (txn.getCountryCode() != null && !txn.getCountryCode().equals(currentCountry)) {
                    java.time.Duration timeDiff = java.time.Duration.between(
                        txn.getTransactionDate(), 
                        LocalDateTime.now()
                    );
                    
                    if (timeDiff.toHours() < 2) {
                        log.warn("Geographic anomaly detected: user {} changed from {} to {} in {} hours",
                            request.getUserId(), txn.getCountryCode(), currentCountry, timeDiff.toHours());
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking geographic anomaly: {}", e.getMessage());
            return false;
        }
    }

    private void logFraudAssessment(FraudAssessmentResult result, FraudAssessmentRequest request) {
        log.info("Fraud assessment completed - User: {}, Score: {}, Level: {}, Blocked: {}, Time: {}ms",
            request.getUserId(), result.getRiskScore(), result.getRiskLevel(), 
            result.isBlocked(), result.getProcessingTimeMs());
    }

    private void triggerFraudAlert(FraudAssessmentResult result, FraudAssessmentRequest request) {
        log.error("FRAUD ALERT: High-risk transaction detected - User: {}, Score: {}, Amount: {}",
            request.getUserId(), result.getRiskScore(), request.getAmount());
        
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "HIGH_RISK_FRAUD");
            alert.put("severity", "CRITICAL");
            alert.put("userId", request.getUserId());
            alert.put("transactionId", request.getTransactionId());
            alert.put("amount", request.getAmount().toString());
            alert.put("riskScore", result.getRiskScore());
            alert.put("riskLevel", result.getRiskLevel());
            alert.put("triggeredRules", result.getTriggeredRules());
            alert.put("timestamp", System.currentTimeMillis());
            alert.put("ipAddress", request.getIpAddress());
            alert.put("countryCode", request.getCountryCode());
            
            kafkaTemplate.send("fraud-alerts", request.getUserId(), alert);
            
            log.info("Fraud alert sent to alerting system for transaction: {}", request.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to send fraud alert to alerting system: {}", e.getMessage());
        }
    }
}