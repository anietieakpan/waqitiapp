package com.waqiti.analytics.ml;

import com.waqiti.analytics.domain.*;
import com.waqiti.analytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.*;
import smile.classification.GradientTreeBoost;
import smile.data.DataFrame;
import smile.data.formula.Formula;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionEngine {
    
    private final ModelRepository modelRepository;
    private final FeatureExtractor featureExtractor;
    private final RuleEngine ruleEngine;
    
    // ML Models
    private SavedModelBundle tensorflowModel;
    private RandomForest randomForestModel;
    private GradientTreeBoost gradientBoostModel;
    
    // Model ensemble weights
    private static final double TF_WEIGHT = 0.4;
    private static final double RF_WEIGHT = 0.3;
    private static final double GB_WEIGHT = 0.3;
    
    // Feature importance cache
    private final Map<String, Double> featureImportance = new ConcurrentHashMap<>();
    
    // Real-time pattern tracking
    private final Map<String, UserPattern> userPatterns = new ConcurrentHashMap<>();
    private final Map<String, MerchantRiskProfile> merchantProfiles = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        loadModels();
        initializeFeatureImportance();
        startModelUpdateScheduler();
    }
    
    public FraudScore scoreTransaction(TransactionEvent transaction) {
        try {
            // Extract features
            FeatureVector features = featureExtractor.extractFeatures(transaction);
            
            // Rule-based checks
            RuleCheckResult ruleResult = ruleEngine.checkFraudRules(transaction);
            if (ruleResult.isHighRisk()) {
                return FraudScore.builder()
                    .score(0.95)
                    .confidence(1.0)
                    .reasons(ruleResult.getViolations())
                    .modelScores(Map.of("rules", 0.95))
                    .requiresManualReview(true)
                    .build();
            }
            
            // ML model predictions
            Map<String, Double> modelScores = new HashMap<>();
            
            // TensorFlow deep learning model
            double tfScore = scoreTensorFlow(features);
            modelScores.put("tensorflow", tfScore);
            
            // Random Forest
            double rfScore = scoreRandomForest(features);
            modelScores.put("randomForest", rfScore);
            
            // Gradient Boosting
            double gbScore = scoreGradientBoost(features);
            modelScores.put("gradientBoost", gbScore);
            
            // Ensemble score
            double ensembleScore = (tfScore * TF_WEIGHT) + 
                                 (rfScore * RF_WEIGHT) + 
                                 (gbScore * GB_WEIGHT);
            
            // Behavioral analysis
            BehavioralScore behaviorScore = analyzeBehavior(transaction);
            
            // Network analysis
            NetworkScore networkScore = analyzeNetwork(transaction);
            
            // Final score combining all factors
            double finalScore = combinedScore(
                ensembleScore, 
                behaviorScore.getScore(), 
                networkScore.getScore(),
                ruleResult.getRiskScore()
            );
            
            // Generate explanations
            List<String> reasons = generateExplanations(
                features, 
                modelScores, 
                behaviorScore, 
                networkScore
            );
            
            return FraudScore.builder()
                .score(finalScore)
                .confidence(calculateConfidence(modelScores))
                .reasons(reasons)
                .modelScores(modelScores)
                .behavioralScore(behaviorScore.getScore())
                .networkScore(networkScore.getScore())
                .requiresManualReview(finalScore > 0.7)
                .recommendedAction(determineAction(finalScore))
                .build();
                
        } catch (Exception e) {
            log.error("Error scoring transaction: {}", transaction.getId(), e);
            // Return conservative score on error
            return FraudScore.builder()
                .score(0.5)
                .confidence(0.0)
                .reasons(List.of("Scoring error - manual review required"))
                .requiresManualReview(true)
                .build();
        }
    }
    
    private double scoreTensorFlow(FeatureVector features) {
        try (Session session = tensorflowModel.session()) {
            // Convert features to tensor
            float[][] featureArray = features.toArray();
            Tensor<Float> inputTensor = Tensor.create(featureArray, Float.class);
            
            // Run inference
            List<Tensor<?>> outputs = session.runner()
                .feed("input", inputTensor)
                .fetch("output")
                .run();
            
            // Extract score
            float[][] result = new float[1][1];
            outputs.get(0).copyTo(result);
            
            return result[0][0];
        } catch (Exception e) {
            log.error("TensorFlow scoring error", e);
            return 0.5; // Neutral score on error
        }
    }
    
    private double scoreRandomForest(FeatureVector features) {
        try {
            Instance instance = features.toWekaInstance();
            double[] distribution = randomForestModel.distributionForInstance(instance);
            return distribution[1]; // Probability of fraud class
        } catch (Exception e) {
            log.error("Random Forest scoring error", e);
            return 0.5;
        }
    }
    
    private double scoreGradientBoost(FeatureVector features) {
        try {
            double[] featureArray = features.toDoubleArray();
            int prediction = gradientBoostModel.predict(featureArray);
            double[] probability = new double[2];
            gradientBoostModel.predict(featureArray, probability);
            return probability[1]; // Probability of fraud class
        } catch (Exception e) {
            log.error("Gradient Boost scoring error", e);
            return 0.5;
        }
    }
    
    private BehavioralScore analyzeBehavior(TransactionEvent transaction) {
        String userId = transaction.getUserId();
        UserPattern pattern = userPatterns.computeIfAbsent(userId, k -> new UserPattern(userId));
        
        // Update pattern with new transaction
        pattern.addTransaction(transaction);
        
        // Calculate behavioral anomalies
        List<BehavioralAnomaly> anomalies = new ArrayList<>();
        
        // Velocity checks
        VelocityCheck velocityCheck = checkVelocity(pattern, transaction);
        if (velocityCheck.isAnomaly()) {
            anomalies.add(velocityCheck.toAnomaly());
        }
        
        // Amount anomaly
        if (isAmountAnomaly(pattern, transaction)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type("AMOUNT_ANOMALY")
                .severity(calculateAmountAnomalySeverity(pattern, transaction))
                .description("Transaction amount significantly deviates from user pattern")
                .build());
        }
        
        // Time pattern anomaly
        if (isTimeAnomaly(pattern, transaction)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type("TIME_ANOMALY")
                .severity(0.7)
                .description("Transaction at unusual time for user")
                .build());
        }
        
        // Location anomaly
        if (isLocationAnomaly(pattern, transaction)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type("LOCATION_ANOMALY")
                .severity(0.8)
                .description("Transaction from unusual location")
                .build());
        }
        
        // Calculate overall behavioral score
        double score = calculateBehavioralScore(anomalies);
        
        return BehavioralScore.builder()
            .score(score)
            .anomalies(anomalies)
            .userPattern(pattern)
            .build();
    }
    
    private NetworkScore analyzeNetwork(TransactionEvent transaction) {
        // Graph-based fraud detection
        NetworkAnalysis analysis = new NetworkAnalysis();
        
        // Check if part of fraud ring
        boolean fraudRing = checkFraudRing(transaction);
        if (fraudRing) {
            analysis.addFlag("FRAUD_RING_DETECTED", 0.9);
        }
        
        // Money mule detection
        if (isMoneyMulePattern(transaction)) {
            analysis.addFlag("MONEY_MULE_PATTERN", 0.8);
        }
        
        // Suspicious network patterns
        NetworkMetrics metrics = calculateNetworkMetrics(transaction);
        if (metrics.isSuspicious()) {
            analysis.addFlag("SUSPICIOUS_NETWORK", metrics.getSuspicionScore());
        }
        
        // Account takeover patterns
        if (hasAccountTakeoverSignals(transaction)) {
            analysis.addFlag("ACCOUNT_TAKEOVER_RISK", 0.85);
        }
        
        return NetworkScore.builder()
            .score(analysis.getOverallScore())
            .flags(analysis.getFlags())
            .metrics(metrics)
            .build();
    }
    
    private double combinedScore(double mlScore, double behaviorScore, 
                                double networkScore, double ruleScore) {
        // Weighted combination with non-linear adjustments
        double baseScore = mlScore * 0.4 + behaviorScore * 0.3 + 
                          networkScore * 0.2 + ruleScore * 0.1;
        
        // Apply sigmoid to smooth extreme values
        return 1.0 / (1.0 + Math.exp(-10 * (baseScore - 0.5)));
    }
    
    private List<String> generateExplanations(FeatureVector features, 
                                            Map<String, Double> modelScores,
                                            BehavioralScore behaviorScore,
                                            NetworkScore networkScore) {
        List<String> explanations = new ArrayList<>();
        
        // Top contributing features
        List<FeatureContribution> contributions = calculateFeatureContributions(features);
        for (FeatureContribution contrib : contributions.subList(0, Math.min(3, contributions.size()))) {
            explanations.add(String.format("%s: %.2f impact", 
                contrib.getFeatureName(), contrib.getContribution()));
        }
        
        // Behavioral anomalies
        for (BehavioralAnomaly anomaly : behaviorScore.getAnomalies()) {
            if (anomaly.getSeverity() > 0.6) {
                explanations.add(anomaly.getDescription());
            }
        }
        
        // Network flags
        for (Map.Entry<String, Double> flag : networkScore.getFlags().entrySet()) {
            if (flag.getValue() > 0.7) {
                explanations.add(formatNetworkFlag(flag.getKey()));
            }
        }
        
        return explanations;
    }
    
    private boolean checkFraudRing(TransactionEvent transaction) {
        // Complex graph analysis to detect coordinated fraud
        Set<String> connectedAccounts = getConnectedAccounts(transaction.getUserId());
        
        // Check for circular transfers
        boolean hasCircularPattern = detectCircularTransfers(connectedAccounts);
        
        // Check for rapid fund distribution
        boolean hasRapidDistribution = detectRapidDistribution(connectedAccounts);
        
        // Check for common attributes
        boolean hasCommonAttributes = checkCommonAttributes(connectedAccounts);
        
        return hasCircularPattern || hasRapidDistribution || hasCommonAttributes;
    }
    
    private boolean isMoneyMulePattern(TransactionEvent transaction) {
        // Detect money mule characteristics
        UserTransactionHistory history = getUserTransactionHistory(transaction.getUserId());
        
        // Check for: receive -> withdraw pattern
        boolean hasReceiveWithdrawPattern = history.hasPattern("RECEIVE_WITHDRAW", 24);
        
        // Check for multiple sources, single destination
        boolean hasMultiSourcePattern = history.getUniqueSourceCount(7) > 5 && 
                                      history.getUniqueDestinationCount(7) <= 2;
        
        // Check for layering behavior
        boolean hasLayeringBehavior = detectLayering(history);
        
        return hasReceiveWithdrawPattern || hasMultiSourcePattern || hasLayeringBehavior;
    }
    
    private class VelocityCheck {
        private final boolean anomaly;
        private final String type;
        private final double severity;
        private final String description;
        
        public boolean isAnomaly() { return anomaly; }
        
        public BehavioralAnomaly toAnomaly() {
            return BehavioralAnomaly.builder()
                .type(type)
                .severity(severity)
                .description(description)
                .build();
        }
    }
    
    private VelocityCheck checkVelocity(UserPattern pattern, TransactionEvent transaction) {
        // Check transaction velocity
        int recentCount = pattern.getTransactionCount(5, ChronoUnit.MINUTES);
        if (recentCount > 5) {
            return new VelocityCheck(true, "HIGH_VELOCITY", 0.8, 
                "Unusually high transaction frequency");
        }
        
        // Check amount velocity
        BigDecimal recentAmount = pattern.getTotalAmount(1, ChronoUnit.HOURS);
        BigDecimal dailyAverage = pattern.getDailyAverageAmount();
        if (recentAmount.compareTo(dailyAverage.multiply(BigDecimal.valueOf(5))) > 0) {
            return new VelocityCheck(true, "AMOUNT_VELOCITY", 0.9, 
                "Transaction amount velocity exceeds normal pattern");
        }
        
        return new VelocityCheck(false, null, 0, null);
    }
    
    private void updateModels() {
        // Periodic model retraining with new data
        try {
            log.info("Starting model update process");
            
            // Collect recent labeled data
            Dataset labeledData = collectRecentLabeledData();
            
            // Retrain models if sufficient new data
            if (labeledData.size() > 1000) {
                // Retrain ensemble models
                retrainTensorFlowModel(labeledData);
                retrainRandomForest(labeledData);
                retrainGradientBoost(labeledData);
                
                // Update feature importance
                updateFeatureImportance();
                
                // Validate model performance
                ModelPerformance performance = validateModels(labeledData);
                if (performance.isImproved()) {
                    deployNewModels();
                } else {
                    rollbackModels();
                }
            }
            
        } catch (Exception e) {
            log.error("Model update failed", e);
        }
    }
}