package com.waqiti.common.fraud.analytics;

import com.waqiti.common.fraud.transaction.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced transaction analysis service for fraud detection.
 * Analyzes transaction patterns, user behavior, and risk indicators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionAnalysisService {
    
    private final TransactionPatternDetector patternDetector;
    private final VelocityAnalyzer velocityAnalyzer;
    private final AnomalyDetector anomalyDetector;
    private final TransactionRepository transactionRepository;
    
    /**
     * Perform comprehensive transaction analysis
     */
    public TransactionAnalysisResult analyzeTransaction(TransactionEvent transaction) {
        log.debug("Analyzing transaction: {}", transaction.getTransactionId());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Get historical context
            List<TransactionEvent> userHistory = transactionRepository
                    .getUserTransactionHistory(transaction.getUserId(), 90); // Last 90 days
            
            // Analyze various aspects
            VelocityAnalysis velocityAnalysis = velocityAnalyzer.analyze(transaction, userHistory);
            PatternAnalysis patternAnalysis = patternDetector.detectPatterns(transaction, userHistory);
            AnomalyAnalysis anomalyAnalysis = anomalyDetector.detectAnomalies(transaction, userHistory);
            RiskAnalysis riskAnalysis = calculateRiskScore(transaction, velocityAnalysis, patternAnalysis, anomalyAnalysis);
            
            // Generate insights and recommendations
            List<String> insights = generateInsights(velocityAnalysis, patternAnalysis, anomalyAnalysis);
            List<String> recommendations = generateRecommendations(riskAnalysis, insights);
            
            long analysisTime = System.currentTimeMillis() - startTime;
            
            TransactionAnalysisResult result = TransactionAnalysisResult.builder()
                    .transactionId(transaction.getTransactionId())
                    .userId(transaction.getUserId())
                    .velocityAnalysis(velocityAnalysis)
                    .patternAnalysis(patternAnalysis)
                    .anomalyAnalysis(anomalyAnalysis)
                    .riskAnalysis(riskAnalysis)
                    .insights(insights)
                    .recommendations(recommendations)
                    .analysisTimestamp(LocalDateTime.now())
                    .processingTimeMs(analysisTime)
                    .build();
            
            log.debug("Transaction analysis completed for {} in {}ms", 
                transaction.getTransactionId(), analysisTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error analyzing transaction: {}", transaction.getTransactionId(), e);
            return createErrorResult(transaction.getTransactionId(), e.getMessage());
        }
    }
    
    /**
     * Analyze user transaction behavior
     */
    public UserBehaviorAnalysis analyzeUserBehavior(String userId, int historyDays) {
        log.debug("Analyzing user behavior for user: {} over {} days", userId, historyDays);
        
        try {
            List<TransactionEvent> transactions = transactionRepository
                    .getUserTransactionHistory(userId, historyDays);
            
            if (transactions.isEmpty()) {
                return UserBehaviorAnalysis.noData(userId);
            }
            
            // Calculate behavior metrics
            BehaviorMetrics metrics = calculateBehaviorMetrics(transactions);
            BehaviorPatterns patterns = identifyBehaviorPatterns(transactions);
            RiskProfile riskProfile = createUserRiskProfile(transactions, metrics, patterns);
            
            return UserBehaviorAnalysis.builder()
                    .userId(userId)
                    .analysisWindow(historyDays)
                    .transactionCount(transactions.size())
                    .behaviorMetrics(metrics)
                    .behaviorPatterns(patterns)
                    .riskProfile(riskProfile)
                    .analysisTimestamp(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("Error analyzing user behavior for user: {}", userId, e);
            return UserBehaviorAnalysis.error(userId, e.getMessage());
        }
    }
    
    /**
     * Analyze merchant transaction patterns
     */
    public MerchantAnalysis analyzeMerchant(String merchantId, int historyDays) {
        log.debug("Analyzing merchant patterns for merchant: {} over {} days", merchantId, historyDays);
        
        try {
            List<TransactionEvent> transactions = transactionRepository
                    .getMerchantTransactionHistory(merchantId, historyDays);
            
            if (transactions.isEmpty()) {
                return MerchantAnalysis.noData(merchantId);
            }
            
            MerchantMetrics metrics = calculateMerchantMetrics(transactions);
            MerchantRiskIndicators riskIndicators = assessMerchantRisk(transactions, metrics);
            
            return MerchantAnalysis.builder()
                    .merchantId(merchantId)
                    .analysisWindow(historyDays)
                    .transactionCount(transactions.size())
                    .metrics(metrics)
                    .riskIndicators(riskIndicators)
                    .analysisTimestamp(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("Error analyzing merchant: {}", merchantId, e);
            return MerchantAnalysis.error(merchantId, e.getMessage());
        }
    }
    
    /**
     * Calculate comprehensive risk score
     */
    private RiskAnalysis calculateRiskScore(TransactionEvent transaction, 
                                          VelocityAnalysis velocity,
                                          PatternAnalysis pattern,
                                          AnomalyAnalysis anomaly) {
        
        // Weight the different risk components
        double velocityWeight = 0.3;
        double patternWeight = 0.3;
        double anomalyWeight = 0.25;
        double baseWeight = 0.15;
        
        // Base transaction risk
        double baseRisk = calculateBaseTransactionRisk(transaction);
        
        // Weighted risk score
        double overallRisk = (velocity.getRiskScore() * velocityWeight) +
                           (pattern.getRiskScore() * patternWeight) +
                           (anomaly.getRiskScore() * anomalyWeight) +
                           (baseRisk * baseWeight);
        
        // Confidence calculation
        double confidence = calculateConfidence(velocity, pattern, anomaly);
        
        // Risk level determination
        RiskLevel riskLevel = determineRiskLevel(overallRisk);
        
        return RiskAnalysis.builder()
                .overallRiskScore(overallRisk)
                .velocityRisk(velocity.getRiskScore())
                .patternRisk(pattern.getRiskScore())
                .anomalyRisk(anomaly.getRiskScore())
                .baseRisk(baseRisk)
                .riskLevel(riskLevel)
                .confidence(confidence)
                .riskFactors(collectRiskFactors(velocity, pattern, anomaly))
                .build();
    }
    
    /**
     * Calculate base transaction risk
     */
    private double calculateBaseTransactionRisk(TransactionEvent transaction) {
        double risk = 0.0;
        
        // Amount-based risk
        if (transaction.isHighValue()) {
            risk += 0.3;
        }
        
        // Cross-border risk
        if (transaction.isCrossBorder()) {
            risk += 0.2;
        }
        
        // Channel risk
        if (transaction.getChannel() == TransactionEvent.TransactionChannel.ONLINE) {
            risk += 0.1;
        }
        
        // Payment method risk
        if (transaction.getPaymentMethod() == TransactionEvent.PaymentMethod.DIGITAL_WALLET) {
            risk += 0.1;
        }
        
        // Card not present risk
        if (!transaction.isCardPresent()) {
            risk += 0.15;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate behavior metrics
     */
    private BehaviorMetrics calculateBehaviorMetrics(List<TransactionEvent> transactions) {
        if (transactions.isEmpty()) {
            return BehaviorMetrics.empty();
        }
        
        // Calculate various metrics
        double avgAmount = transactions.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount().doubleValue() : 0.0)
                .average()
                .orElse(0.0);

        double stdDevAmount = calculateStandardDeviation(
                transactions.stream()
                    .mapToDouble(t -> t.getAmount() != null ? t.getAmount().doubleValue() : 0.0)
                    .toArray());
        
        long uniqueMerchants = transactions.stream()
                .map(TransactionEvent::getMerchantId)
                .distinct()
                .count();
        
        Map<TransactionEvent.TransactionChannel, Long> channelDistribution = transactions.stream()
                .collect(Collectors.groupingBy(TransactionEvent::getChannel, Collectors.counting()));
        
        Map<String, Long> timeDistribution = calculateTimeDistribution(transactions);
        
        return BehaviorMetrics.builder()
                .averageAmount(avgAmount)
                .amountVariability(stdDevAmount)
                .transactionFrequency(calculateFrequency(transactions))
                .uniqueMerchantCount((int) uniqueMerchants)
                .channelDistribution(channelDistribution)
                .timePatterns(timeDistribution)
                .build();
    }
    
    /**
     * Identify behavior patterns
     */
    private BehaviorPatterns identifyBehaviorPatterns(List<TransactionEvent> transactions) {
        List<String> patterns = new ArrayList<>();
        
        // Regular patterns
        if (hasRegularTiming(transactions)) {
            patterns.add("regular_timing");
        }
        
        if (hasConsistentAmounts(transactions)) {
            patterns.add("consistent_amounts");
        }
        
        if (hasPreferredMerchants(transactions)) {
            patterns.add("merchant_loyalty");
        }
        
        // Unusual patterns
        if (hasBurstActivity(transactions)) {
            patterns.add("burst_activity");
        }
        
        if (hasUnusualTiming(transactions)) {
            patterns.add("unusual_timing");
        }
        
        return BehaviorPatterns.builder()
                .identifiedPatterns(patterns)
                .patternStrength(calculatePatternStrength(patterns))
                .lastPatternDate(getLastPatternDate(transactions))
                .build();
    }
    
    /**
     * Generate insights from analysis
     */
    private List<String> generateInsights(VelocityAnalysis velocity, 
                                        PatternAnalysis pattern, 
                                        AnomalyAnalysis anomaly) {
        List<String> insights = new ArrayList<>();
        
        if (velocity.isHighVelocity()) {
            insights.add("High transaction velocity detected");
        }
        
        if (pattern.hasUnusualPatterns()) {
            insights.add("Unusual transaction patterns identified");
        }
        
        if (anomaly.hasSignificantAnomalies()) {
            insights.add("Significant behavioral anomalies detected");
        }
        
        return insights;
    }
    
    /**
     * Generate recommendations based on analysis
     */
    private List<String> generateRecommendations(RiskAnalysis risk, List<String> insights) {
        List<String> recommendations = new ArrayList<>();
        
        if (risk.getRiskLevel() == RiskLevel.HIGH || risk.getRiskLevel() == RiskLevel.CRITICAL) {
            recommendations.add("Manual review required");
            recommendations.add("Consider additional authentication");
        }
        
        if (risk.getVelocityRisk() > 0.7) {
            recommendations.add("Monitor transaction velocity");
        }
        
        if (risk.getAnomalyRisk() > 0.6) {
            recommendations.add("Investigate behavioral anomalies");
        }
        
        return recommendations;
    }
    
    // Utility methods
    
    private double calculateStandardDeviation(double[] values) {
        if (values.length == 0) return 0.0;
        
        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    private double calculateFrequency(List<TransactionEvent> transactions) {
        if (transactions.size() < 2) return 0.0;
        
        LocalDateTime first = transactions.get(0).getTimestamp();
        LocalDateTime last = transactions.get(transactions.size() - 1).getTimestamp();
        long days = java.time.Duration.between(first, last).toDays();
        
        return days > 0 ? (double) transactions.size() / days : transactions.size();
    }
    
    private Map<String, Long> calculateTimeDistribution(List<TransactionEvent> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(
                    t -> String.valueOf(t.getTimestamp().getHour()),
                    Collectors.counting()
                ));
    }
    
    private double calculateConfidence(VelocityAnalysis velocity, PatternAnalysis pattern, AnomalyAnalysis anomaly) {
        // Confidence based on data quality and consistency
        double velocityConfidence = velocity.getConfidence();
        double patternConfidence = pattern.getConfidence();
        double anomalyConfidence = anomaly.getConfidence();
        
        return (velocityConfidence + patternConfidence + anomalyConfidence) / 3.0;
    }
    
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) return RiskLevel.CRITICAL;
        if (riskScore >= 0.6) return RiskLevel.HIGH;
        if (riskScore >= 0.4) return RiskLevel.MEDIUM;
        if (riskScore >= 0.2) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    private List<String> collectRiskFactors(VelocityAnalysis velocity, PatternAnalysis pattern, AnomalyAnalysis anomaly) {
        List<String> factors = new ArrayList<>();
        factors.addAll(velocity.getRiskFactors());
        factors.addAll(pattern.getRiskFactors());
        factors.addAll(anomaly.getAnomalies());
        return factors.stream().distinct().collect(Collectors.toList());
    }
    
    private boolean hasRegularTiming(List<TransactionEvent> transactions) {
        // Implementation for detecting regular timing patterns
        return false; // Simplified
    }
    
    private boolean hasConsistentAmounts(List<TransactionEvent> transactions) {
        // Implementation for detecting consistent amount patterns
        return false; // Simplified
    }
    
    private boolean hasPreferredMerchants(List<TransactionEvent> transactions) {
        // Implementation for detecting merchant preferences
        return false; // Simplified
    }
    
    private boolean hasBurstActivity(List<TransactionEvent> transactions) {
        // Implementation for detecting burst activity
        return false; // Simplified
    }
    
    private boolean hasUnusualTiming(List<TransactionEvent> transactions) {
        // Implementation for detecting unusual timing
        return false; // Simplified
    }
    
    private double calculatePatternStrength(List<String> patterns) {
        return patterns.size() * 0.2; // Simplified calculation
    }
    
    private LocalDateTime getLastPatternDate(List<TransactionEvent> transactions) {
        return transactions.stream()
                .map(TransactionEvent::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    private RiskProfile createUserRiskProfile(List<TransactionEvent> transactions, 
                                            BehaviorMetrics metrics, 
                                            BehaviorPatterns patterns) {
        // Simplified implementation
        return RiskProfile.builder().build();
    }
    
    private MerchantMetrics calculateMerchantMetrics(List<TransactionEvent> transactions) {
        // Simplified implementation
        return MerchantMetrics.builder().build();
    }
    
    private MerchantRiskIndicators assessMerchantRisk(List<TransactionEvent> transactions, 
                                                    MerchantMetrics metrics) {
        // Simplified implementation
        return MerchantRiskIndicators.builder().build();
    }
    
    private TransactionAnalysisResult createErrorResult(String transactionId, String error) {
        return TransactionAnalysisResult.builder()
                .transactionId(transactionId)
                .error(error)
                .analysisTimestamp(LocalDateTime.now())
                .build();
    }
    
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
}