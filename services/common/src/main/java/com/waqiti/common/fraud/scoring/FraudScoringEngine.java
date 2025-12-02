package com.waqiti.common.fraud.scoring;

import com.waqiti.common.fraud.FraudContext;
import com.waqiti.common.fraud.model.*;
import com.waqiti.common.fraud.scoring.MLModelResult;
import com.waqiti.common.fraud.scoring.RiskFactorResult;
import com.waqiti.common.fraud.dto.FraudScoreBreakdown;
import com.waqiti.common.fraud.dto.AmountRiskLevel;
import com.waqiti.common.fraud.dto.GeographicRisk;
import com.waqiti.common.fraud.dto.DeviceRisk;
import com.waqiti.common.fraud.ml.MachineLearningModelService;
import com.waqiti.common.fraud.rules.RiskFactorCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Advanced fraud scoring engine that combines multiple techniques:
 * - Machine learning model predictions
 * - Rule-based risk factor calculation
 * - Statistical anomaly detection
 * - Behavioral pattern analysis
 * - Historical fraud pattern matching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudScoringEngine {
    
    private final MachineLearningModelService mlModelService;
    private final RiskFactorCalculator riskFactorCalculator;
    
    // Scoring weights for different components
    private static final double ML_MODEL_WEIGHT = 0.4;
    private static final double RULE_BASED_WEIGHT = 0.3;
    private static final double ANOMALY_WEIGHT = 0.2;
    private static final double PATTERN_WEIGHT = 0.1;
    
    /**
     * Calculate comprehensive fraud score for a transaction context
     */
    @Cacheable(value = "fraudScores", key = "#context.transactionId", unless = "#result.score > 0.7")
    public FraudScore calculateFraudScore(FraudContext context) {
        try {
            log.debug("Calculating fraud score for transaction: {}", context.getTransactionId());
            
            // 1. ML Model Score
            MLModelResult mlResult = mlModelService.predictFraudProbability(context);
            double mlScore = mlResult.getFraudProbability();
            double mlConfidence = mlResult.getConfidence();
            
            // 2. Rule-based Risk Factors
            RiskFactorResult riskFactors = riskFactorCalculator.calculateRiskFactors(context);
            double ruleScore = calculateRuleBasedScore(riskFactors);
            
            // 3. Statistical Anomaly Score
            double anomalyScore = calculateAnomalyScore(context);
            
            // 4. Pattern Matching Score
            double patternScore = calculatePatternScore(context);
            
            // 5. Combine all scores with weights
            double finalScore = (mlScore * ML_MODEL_WEIGHT) +
                              (ruleScore * RULE_BASED_WEIGHT) +
                              (anomalyScore * ANOMALY_WEIGHT) +
                              (patternScore * PATTERN_WEIGHT);
            
            // Ensure score is between 0 and 1
            finalScore = Math.max(0.0, Math.min(1.0, finalScore));
            
            // Calculate overall confidence
            double overallConfidence = calculateOverallConfidence(
                mlConfidence, riskFactors.getConfidence(), anomalyScore, patternScore);
            
            // Create detailed scoring breakdown
            FraudScoreBreakdown breakdown = FraudScoreBreakdown.builder()
                .mlModelScore(mlScore)
                .mlModelConfidence(mlConfidence)
                .ruleBasedScore(ruleScore)
                .anomalyScore(anomalyScore)
                .patternScore(patternScore)
                .finalScore(finalScore)
                .scoringComponents(createScoringComponents(mlResult, riskFactors, anomalyScore, patternScore))
                .build();
            
            FraudScore fraudScore = FraudScore.builder()
                .score(BigDecimal.valueOf(finalScore).setScale(4, RoundingMode.HALF_UP).doubleValue())
                .overallScore(finalScore)
                .confidence(BigDecimal.valueOf(overallConfidence).setScale(4, RoundingMode.HALF_UP).doubleValue())
                .confidenceLevel(overallConfidence)
                .breakdown(breakdown)
                .calculatedAt(LocalDateTime.now())
                .scoringVersion(String.valueOf(mlModelService.getCurrentModelVersion()))
                .mlScore(mlScore)
                .build();
            
            log.debug("Fraud score calculated: {} (confidence: {}) for transaction: {}", 
                fraudScore.getScore(), fraudScore.getConfidence(), context.getTransactionId());
            
            return fraudScore;
            
        } catch (Exception e) {
            log.error("Error calculating fraud score for transaction: {}", context.getTransactionId(), e);
            
            // Return conservative score on error
            return FraudScore.builder()
                .score(0.5) // Medium risk as fallback
                .overallScore(0.5)
                .confidence(0.0) // Low confidence due to error
                .confidenceLevel(0.0)
                .breakdown(null)
                .calculatedAt(LocalDateTime.now())
                .scoringVersion("error-fallback")
                .build();
        }
    }
    
    /**
     * Calculate rule-based score from risk factors
     */
    private double calculateRuleBasedScore(RiskFactorResult riskFactors) {
        double score = 0.0;
        
        // Account age risk (newer accounts are riskier)
        if (riskFactors.getAccountAge() < 30) {
            score += 0.2;
        } else if (riskFactors.getAccountAge() < 90) {
            score += 0.1;
        }
        
        // Transaction amount risk
        if (riskFactors.getAmountRiskLevel() == AmountRiskLevel.HIGH) {
            score += 0.3;
        } else if (riskFactors.getAmountRiskLevel() == AmountRiskLevel.MEDIUM) {
            score += 0.15;
        }
        
        // Velocity risk
        if (riskFactors.getVelocityScore() > 0.7) {
            score += 0.25;
        } else if (riskFactors.getVelocityScore() > 0.4) {
            score += 0.1;
        }
        
        // Geographic risk
        if (riskFactors.getGeographicRisk() == GeographicRisk.HIGH) {
            score += 0.2;
        } else if (riskFactors.getGeographicRisk() == GeographicRisk.MEDIUM) {
            score += 0.1;
        }
        
        // Device risk
        if (riskFactors.getDeviceRisk() == DeviceRisk.HIGH) {
            score += 0.15;
        } else if (riskFactors.getDeviceRisk() == DeviceRisk.MEDIUM) {
            score += 0.05;
        }
        
        // Time-based risk
        if (riskFactors.getTimeRisk() == RiskFactorResult.TimeRisk.HIGH) {
            score += 0.1;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculate statistical anomaly score
     */
    private double calculateAnomalyScore(FraudContext context) {
        double anomalyScore = 0.0;
        com.waqiti.common.fraud.profiling.UserRiskProfile profile = context.getUserRiskProfileDomain();
        
        // Amount anomaly
        if (profile.getTypicalTransactionAmount() != null) {
            double amountRatio = context.getAmount()
                .divide(profile.getTypicalTransactionAmount(), 4, RoundingMode.HALF_UP)
                .doubleValue();
            
            if (amountRatio > 10 || amountRatio < 0.1) {
                anomalyScore += 0.4;
            } else if (amountRatio > 5 || amountRatio < 0.2) {
                anomalyScore += 0.2;
            }
        }
        
        // Time anomaly
        int transactionHour = context.getTimestamp().getHour();
        if (profile.getTypicalActiveHours() != null && 
            !profile.getTypicalActiveHours().contains(transactionHour)) {
            anomalyScore += 0.3;
        }
        
        // Frequency anomaly
        long todayTransactionCount = context.getRecentTransactions().stream()
            .mapToLong(t -> ChronoUnit.DAYS.between(t.getTimestamp(), context.getTimestamp()) == 0 ? 1 : 0)
            .sum();

        // Default to 5 transactions per day if not available
        long typicalDaily = (profile.getTransactionalData() != null &&
                            profile.getTransactionalData().getAverageDailyTransactions() != null)
                            ? profile.getTransactionalData().getAverageDailyTransactions().longValue()
                            : 5L;

        if (todayTransactionCount > typicalDaily * 3) {
            anomalyScore += 0.3;
        }
        
        return Math.min(1.0, anomalyScore);
    }
    
    /**
     * Calculate pattern-based score
     */
    private double calculatePatternScore(FraudContext context) {
        double patternScore = 0.0;
        
        // Rapid fire transactions
        long last5MinuteTransactions = context.getRecentTransactions().stream()
            .mapToLong(t -> ChronoUnit.MINUTES.between(t.getTimestamp(), context.getTimestamp()) <= 5 ? 1 : 0)
            .sum();
        
        if (last5MinuteTransactions > 5) {
            patternScore += 0.4;
        }
        
        // Round number bias (fraudsters often use round numbers)
        if (isRoundNumber(context.getAmount())) {
            patternScore += 0.1;
        }
        
        // Weekend/holiday patterns
        if (isWeekendOrHoliday(context.getTimestamp())) {
            patternScore += 0.1;
        }
        
        // Merchant risk
        if (context.getMerchantId() != null && isHighRiskMerchant(context.getMerchantId())) {
            patternScore += 0.3;
        }
        
        // Cross-border transaction
        if (isCrossBorderTransaction(context)) {
            patternScore += 0.1;
        }
        
        return Math.min(1.0, patternScore);
    }
    
    /**
     * Calculate overall confidence score
     */
    private double calculateOverallConfidence(
            double mlConfidence, 
            double riskFactorConfidence, 
            double anomalyScore, 
            double patternScore) {
        
        // Higher anomaly and pattern scores increase confidence
        double dataQualityScore = (anomalyScore + patternScore) / 2.0;
        
        // Combine ML confidence, risk factor confidence, and data quality
        return (mlConfidence * 0.5) + (riskFactorConfidence * 0.3) + (dataQualityScore * 0.2);
    }
    
    /**
     * Create detailed scoring components for transparency
     */
    private List<com.waqiti.common.fraud.dto.ScoringComponent> createScoringComponents(
            MLModelResult mlResult,
            RiskFactorResult riskFactors,
            double anomalyScore,
            double patternScore) {

        List<com.waqiti.common.fraud.dto.ScoringComponent> components = new ArrayList<>();

        // ML Model component
        components.add(com.waqiti.common.fraud.dto.ScoringComponent.builder()
            .name("Machine Learning Model")
            .score(mlResult.getFraudProbability())
            .weight(ML_MODEL_WEIGHT)
            .confidence(mlResult.getConfidence())
            .description("Deep learning model prediction based on historical fraud patterns")
            .features(mlResult.getTopFeatures())
            .build());
        
        // Rule-based component
        components.add(com.waqiti.common.fraud.dto.ScoringComponent.builder()
            .name("Rule-based Risk Factors")
            .score(calculateRuleBasedScore(riskFactors))
            .weight(RULE_BASED_WEIGHT)
            .confidence(riskFactors.getConfidence())
            .description("Risk assessment based on predefined business rules")
            .factors(riskFactors.getRiskFactors().stream()
                .map(rf -> rf.getFactorName() + ": " + rf.getRiskLevel())
                .collect(java.util.stream.Collectors.<String>toList()))
            .build());
        
        // Anomaly detection component
        components.add(com.waqiti.common.fraud.dto.ScoringComponent.builder()
            .name("Statistical Anomaly Detection")
            .score(anomalyScore)
            .weight(ANOMALY_WEIGHT)
            .confidence(anomalyScore > 0.5 ? 0.8 : 0.6)
            .description("Detection of unusual patterns compared to user's historical behavior")
            .build());
        
        // Pattern matching component
        components.add(com.waqiti.common.fraud.dto.ScoringComponent.builder()
            .name("Fraud Pattern Matching")
            .score(patternScore)
            .weight(PATTERN_WEIGHT)
            .confidence(patternScore > 0.3 ? 0.7 : 0.5)
            .description("Matching against known fraud patterns and attack vectors")
            .build());
        
        return components;
    }
    
    /**
     * Batch score multiple transactions for efficiency
     */
    public List<FraudScore> batchCalculateFraudScores(List<FraudContext> contexts) {
        log.info("Batch calculating fraud scores for {} transactions", contexts.size());
        
        return contexts.parallelStream()
            .map(this::calculateFraudScore)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Update model with feedback
     */
    public void updateModelWithFeedback(String transactionId, boolean isFraud, double actualLoss) {
        try {
            mlModelService.updateModelWithFeedback(transactionId, isFraud, actualLoss);
            log.debug("Model updated with feedback for transaction: {}", transactionId);
        } catch (Exception e) {
            log.error("Error updating model with feedback for transaction: {}", transactionId, e);
        }
    }
    
    // Helper methods
    
    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(BigDecimal.valueOf(10)).compareTo(BigDecimal.ZERO) == 0 ||
               amount.remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0;
    }
    
    private boolean isWeekendOrHoliday(LocalDateTime timestamp) {
        int dayOfWeek = timestamp.getDayOfWeek().getValue();
        return dayOfWeek == 6 || dayOfWeek == 7; // Saturday or Sunday
    }
    
    private boolean isHighRiskMerchant(String merchantId) {
        // In production, this would check against a high-risk merchant database
        return merchantId.startsWith("HIGH_RISK_");
    }
    
    private boolean isCrossBorderTransaction(FraudContext context) {
        // Simple logic - in production would use proper geographic analysis
        return context.getLocation() != null && 
               !context.getLocation().getCountryCode().equals("US");
    }
}