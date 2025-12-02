package com.waqiti.ml.service;

import com.waqiti.ml.model.FraudDetectionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {
    
    private final FeatureExtractionService featureExtractionService;
    private final ModelInferenceService modelInferenceService;
    private final RiskScoringService riskScoringService;
    
    private static final String MODEL_VERSION = "v2.1.0";
    private static final double HIGH_RISK_THRESHOLD = 0.8;
    private static final double MEDIUM_RISK_THRESHOLD = 0.5;
    private static final double LOW_RISK_THRESHOLD = 0.2;
    
    public CompletableFuture<FraudDetectionModel> detectFraud(FraudDetectionModel input) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting fraud detection for transaction: {}", input.getTransactionId());
                
                // Extract features
                FraudDetectionModel enrichedModel = featureExtractionService.extractFeatures(input);
                
                // Run ML model inference
                double fraudProbability = modelInferenceService.predict(enrichedModel);
                enrichedModel.setFraudProbability(fraudProbability);
                
                // Calculate risk level and recommendation
                String riskLevel = calculateRiskLevel(fraudProbability);
                String recommendation = generateRecommendation(fraudProbability, enrichedModel);
                
                enrichedModel.setRiskLevel(riskLevel);
                enrichedModel.setRecommendation(recommendation);
                enrichedModel.setModelVersion(MODEL_VERSION);
                
                // Calculate feature importance
                Map<String, Object> featureImportance = calculateFeatureImportance(enrichedModel);
                enrichedModel.setFeatureImportance(featureImportance);
                
                log.info("Fraud detection completed for transaction: {} with risk level: {} and probability: {}", 
                    input.getTransactionId(), riskLevel, fraudProbability);
                
                return enrichedModel;
                
            } catch (Exception e) {
                log.error("Error in fraud detection for transaction: {}", input.getTransactionId(), e);
                // Return safe default
                input.setFraudProbability(0.5);
                input.setRiskLevel("MEDIUM");
                input.setRecommendation("REVIEW");
                input.setModelVersion(MODEL_VERSION);
                return input;
            }
        });
    }
    
    private String calculateRiskLevel(double fraudProbability) {
        if (fraudProbability >= HIGH_RISK_THRESHOLD) {
            return "HIGH";
        } else if (fraudProbability >= MEDIUM_RISK_THRESHOLD) {
            return "MEDIUM";
        } else if (fraudProbability >= LOW_RISK_THRESHOLD) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }
    
    private String generateRecommendation(double fraudProbability, FraudDetectionModel model) {
        if (fraudProbability >= HIGH_RISK_THRESHOLD) {
            return "BLOCK";
        } else if (fraudProbability >= MEDIUM_RISK_THRESHOLD) {
            if (model.getIsNewDevice() || model.getIsLocationChange() || model.getIsVpn()) {
                return "CHALLENGE";
            } else {
                return "REVIEW";
            }
        } else if (fraudProbability >= LOW_RISK_THRESHOLD) {
            return "MONITOR";
        } else {
            return "APPROVE";
        }
    }
    
    private Map<String, Object> calculateFeatureImportance(FraudDetectionModel model) {
        Map<String, Object> importance = new HashMap<>();
        
        // Calculate importance scores based on feature contribution
        double amountImportance = calculateAmountImportance(model);
        double locationImportance = calculateLocationImportance(model);
        double deviceImportance = calculateDeviceImportance(model);
        double behaviorImportance = calculateBehaviorImportance(model);
        double velocityImportance = calculateVelocityImportance(model);
        double timeImportance = calculateTimeImportance(model);
        
        importance.put("amount_risk", amountImportance);
        importance.put("location_risk", locationImportance);
        importance.put("device_risk", deviceImportance);
        importance.put("behavior_risk", behaviorImportance);
        importance.put("velocity_risk", velocityImportance);
        importance.put("time_risk", timeImportance);
        
        // Add specific high-impact features
        if (model.getIsNewDevice()) {
            importance.put("new_device_flag", 0.15);
        }
        if (model.getIsVpn() || model.getIsProxy() || model.getIsTor()) {
            importance.put("anonymization_risk", 0.20);
        }
        if (model.getIsOnWatchlist() || model.getIsPep() || model.getIsSanctioned()) {
            importance.put("external_risk_lists", 0.25);
        }
        
        return importance;
    }
    
    private double calculateAmountImportance(FraudDetectionModel model) {
        double importance = 0.0;
        
        if (model.getAmountDeviationFromAverage() != null) {
            BigDecimal deviation = model.getAmountDeviationFromAverage();
            if (deviation.compareTo(BigDecimal.valueOf(3.0)) > 0) {
                importance += 0.15; // High deviation from user's average
            }
        }
        
        if (model.getIsRoundAmount()) {
            importance += 0.05; // Round amounts are slightly suspicious
        }
        
        // Large amounts get higher scrutiny
        if (model.getAmount() != null && model.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            importance += 0.10;
        }
        
        return Math.min(importance, 0.30);
    }
    
    private double calculateLocationImportance(FraudDetectionModel model) {
        double importance = 0.0;
        
        if (model.getIsLocationChange()) {
            importance += 0.10;
        }
        
        if (model.getDistanceFromUsualLocation() != null && model.getDistanceFromUsualLocation() > 1000) {
            importance += 0.15; // Long distance from usual location
        }
        
        if (model.getIsInternationalTransaction()) {
            importance += 0.08;
        }
        
        return Math.min(importance, 0.25);
    }
    
    private double calculateDeviceImportance(FraudDetectionModel model) {
        double importance = 0.0;
        
        if (model.getIsNewDevice()) {
            importance += 0.12;
        }
        
        if (model.getIsVpn() || model.getIsProxy()) {
            importance += 0.15;
        }
        
        if (model.getIsTor()) {
            importance += 0.20; // Tor is high risk
        }
        
        return Math.min(importance, 0.30);
    }
    
    private double calculateBehaviorImportance(FraudDetectionModel model) {
        double importance = 0.0;
        
        if (model.getBehavioralScore() != null && model.getBehavioralScore() > 0.7) {
            importance += 0.12;
        }
        
        if (model.getIsTypingPatternAnomaly() || model.getIsClickPatternAnomaly()) {
            importance += 0.08;
        }
        
        if (model.getIsRushTransaction()) {
            importance += 0.05;
        }
        
        return Math.min(importance, 0.20);
    }
    
    private double calculateVelocityImportance(FraudDetectionModel model) {
        double importance = 0.0;
        
        if (model.getTransactionVelocity() != null && model.getTransactionVelocity() > 10) {
            importance += 0.15; // High transaction velocity
        }
        
        if (model.getTransactionCountLast24h() != null && model.getTransactionCountLast24h() > 20) {
            importance += 0.10;
        }
        
        if (model.getFailedAttemptsLast24h() != null && model.getFailedAttemptsLast24h() > 3) {
            importance += 0.12;
        }
        
        return Math.min(importance, 0.25);
    }
    
    private double calculateTimeImportance(FraudDetectionModel model) {
        double importance = 0.0;
        
        // Late night transactions (between 2 AM and 6 AM) are more suspicious
        if (model.getHourOfDay() != null && (model.getHourOfDay() >= 2 && model.getHourOfDay() <= 6)) {
            importance += 0.05;
        }
        
        // Weekend transactions might have different patterns
        if (model.getIsWeekend()) {
            importance += 0.02;
        }
        
        return Math.min(importance, 0.10);
    }
    
    public CompletableFuture<Map<String, Object>> explainDecision(String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> explanation = new HashMap<>();
            
            // This would typically fetch the stored model result and provide explanations
            explanation.put("model_version", MODEL_VERSION);
            explanation.put("explanation_type", "LIME"); // Local Interpretable Model-agnostic Explanations
            explanation.put("confidence_level", 0.85);
            
            // Add decision tree path or rule-based explanation
            explanation.put("decision_path", "High velocity → New device → VPN usage → BLOCK");
            
            return explanation;
        });
    }
    
    public CompletableFuture<Boolean> updateModelFeedback(String transactionId, boolean wasActuallyFraud) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Store feedback for model retraining
                log.info("Received fraud feedback for transaction {}: {}", transactionId, wasActuallyFraud);
                
                // This would typically:
                // 1. Store the feedback in a training dataset
                // 2. Update model performance metrics
                // 3. Trigger retraining if enough new samples are collected
                // 4. Update feature importance based on feedback
                
                return true;
            } catch (Exception e) {
                log.error("Error updating model feedback for transaction: {}", transactionId, e);
                return false;
            }
        });
    }
}