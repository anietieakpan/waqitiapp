package com.waqiti.ml.service;

import com.waqiti.ml.model.FraudDetectionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelInferenceService {
    
    // Model weights - these would typically be loaded from a trained ML model
    private static final Map<String, Double> FEATURE_WEIGHTS = initializeWeights();
    private static final double BIAS = -2.5; // Intercept term
    
    public double predict(FraudDetectionModel model) {
        try {
            log.debug("Running model inference for transaction: {}", model.getTransactionId());
            
            // Calculate weighted sum of features
            double logit = BIAS;
            
            // Amount-based features
            logit += calculateAmountFeatures(model);
            
            // Temporal features
            logit += calculateTemporalFeatures(model);
            
            // User profile features
            logit += calculateUserProfileFeatures(model);
            
            // Device and location features
            logit += calculateDeviceLocationFeatures(model);
            
            // Transaction pattern features
            logit += calculateTransactionPatternFeatures(model);
            
            // Behavioral features
            logit += calculateBehavioralFeatures(model);
            
            // Historical risk features
            logit += calculateHistoricalRiskFeatures(model);
            
            // External data features
            logit += calculateExternalDataFeatures(model);
            
            // Apply sigmoid function to get probability
            double probability = sigmoid(logit);
            
            log.debug("Model inference completed for transaction: {} with probability: {}", 
                model.getTransactionId(), probability);
            
            return Math.max(0.0, Math.min(1.0, probability)); // Ensure between 0 and 1
            
        } catch (Exception e) {
            log.error("Error in model inference for transaction: {}", model.getTransactionId(), e);
            return 0.5; // Return neutral probability on error
        }
    }
    
    private double calculateAmountFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // Large amounts are more risky
        if (model.getAmount() != null) {
            double amountLog = Math.log(model.getAmount().doubleValue() + 1);
            score += amountLog * FEATURE_WEIGHTS.get("amount_log");
        }
        
        // Deviation from user's average
        if (model.getAmountDeviationFromAverage() != null) {
            double deviation = model.getAmountDeviationFromAverage().doubleValue();
            score += Math.min(deviation, 10.0) * FEATURE_WEIGHTS.get("amount_deviation");
        }
        
        // Round amounts
        if (model.getIsRoundAmount() != null && model.getIsRoundAmount()) {
            score += FEATURE_WEIGHTS.get("round_amount");
        }
        
        return score;
    }
    
    private double calculateTemporalFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // Hour of day - late night transactions are riskier
        if (model.getHourOfDay() != null) {
            int hour = model.getHourOfDay();
            if (hour >= 2 && hour <= 6) { // Late night hours
                score += FEATURE_WEIGHTS.get("late_night");
            } else if (hour >= 22 || hour <= 2) { // Very late/early hours
                score += FEATURE_WEIGHTS.get("very_late");
            }
        }
        
        // Weekend transactions
        if (model.getIsWeekend() != null && model.getIsWeekend()) {
            score += FEATURE_WEIGHTS.get("weekend");
        }
        
        // Holiday transactions
        if (model.getIsHoliday() != null && model.getIsHoliday()) {
            score += FEATURE_WEIGHTS.get("holiday");
        }
        
        return score;
    }
    
    private double calculateUserProfileFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // New users are riskier
        if (model.getIsFirstTimeUser() != null && model.getIsFirstTimeUser()) {
            score += FEATURE_WEIGHTS.get("first_time_user");
        }
        
        // Very new accounts (less than 7 days)
        if (model.getDaysSinceRegistration() != null && model.getDaysSinceRegistration() < 7) {
            score += FEATURE_WEIGHTS.get("very_new_account");
        }
        
        // Account age factor
        if (model.getDaysSinceRegistration() != null) {
            double ageScore = Math.max(0, 30 - model.getDaysSinceRegistration()) / 30.0;
            score += ageScore * FEATURE_WEIGHTS.get("account_age_factor");
        }
        
        return score;
    }
    
    private double calculateDeviceLocationFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // New device
        if (model.getIsNewDevice() != null && model.getIsNewDevice()) {
            score += FEATURE_WEIGHTS.get("new_device");
        }
        
        // VPN usage
        if (model.getIsVpn() != null && model.getIsVpn()) {
            score += FEATURE_WEIGHTS.get("vpn_usage");
        }
        
        // Proxy usage
        if (model.getIsProxy() != null && model.getIsProxy()) {
            score += FEATURE_WEIGHTS.get("proxy_usage");
        }
        
        // Tor usage (highest risk)
        if (model.getIsTor() != null && model.getIsTor()) {
            score += FEATURE_WEIGHTS.get("tor_usage");
        }
        
        // Location change
        if (model.getIsLocationChange() != null && model.getIsLocationChange()) {
            score += FEATURE_WEIGHTS.get("location_change");
        }
        
        // Distance from usual location
        if (model.getDistanceFromUsualLocation() != null) {
            double distance = model.getDistanceFromUsualLocation();
            double distanceScore = Math.min(distance / 1000.0, 5.0); // Normalize to max 5
            score += distanceScore * FEATURE_WEIGHTS.get("distance_factor");
        }
        
        // International transaction
        if (model.getIsInternationalTransaction() != null && model.getIsInternationalTransaction()) {
            score += FEATURE_WEIGHTS.get("international_transaction");
        }
        
        return score;
    }
    
    private double calculateTransactionPatternFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // High transaction velocity
        if (model.getTransactionVelocity() != null) {
            double velocity = model.getTransactionVelocity();
            if (velocity > 10) {
                score += Math.min(velocity / 10.0, 3.0) * FEATURE_WEIGHTS.get("high_velocity");
            }
        }
        
        // Many transactions in last 24h
        if (model.getTransactionCountLast24h() != null) {
            int count24h = model.getTransactionCountLast24h();
            if (count24h > 15) {
                score += Math.min(count24h / 15.0, 2.0) * FEATURE_WEIGHTS.get("high_frequency_24h");
            }
        }
        
        // Rush transaction
        if (model.getIsRushTransaction() != null && model.getIsRushTransaction()) {
            score += FEATURE_WEIGHTS.get("rush_transaction");
        }
        
        return score;
    }
    
    private double calculateBehavioralFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // High behavioral risk score
        if (model.getBehavioralScore() != null) {
            score += model.getBehavioralScore() * FEATURE_WEIGHTS.get("behavioral_score");
        }
        
        // Typing pattern anomaly
        if (model.getIsTypingPatternAnomaly() != null && model.getIsTypingPatternAnomaly()) {
            score += FEATURE_WEIGHTS.get("typing_anomaly");
        }
        
        // Click pattern anomaly
        if (model.getIsClickPatternAnomaly() != null && model.getIsClickPatternAnomaly()) {
            score += FEATURE_WEIGHTS.get("click_anomaly");
        }
        
        // Very short session (might indicate automation)
        if (model.getSessionDuration() != null && model.getSessionDuration() < 5) {
            score += FEATURE_WEIGHTS.get("short_session");
        }
        
        return score;
    }
    
    private double calculateHistoricalRiskFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // Recent failed attempts
        if (model.getFailedAttemptsLast24h() != null) {
            int failedAttempts = model.getFailedAttemptsLast24h();
            if (failedAttempts > 0) {
                score += Math.min(failedAttempts, 5) * FEATURE_WEIGHTS.get("failed_attempts");
            }
        }
        
        // Recent chargebacks
        if (model.getHasRecentChargebacks() != null && model.getHasRecentChargebacks()) {
            score += FEATURE_WEIGHTS.get("recent_chargebacks");
        }
        
        // Recent disputes
        if (model.getHasRecentDisputes() != null && model.getHasRecentDisputes()) {
            score += FEATURE_WEIGHTS.get("recent_disputes");
        }
        
        // Historical fraud rate
        if (model.getHistoricalFraudRate() != null) {
            score += model.getHistoricalFraudRate() * FEATURE_WEIGHTS.get("historical_fraud_rate");
        }
        
        return score;
    }
    
    private double calculateExternalDataFeatures(FraudDetectionModel model) {
        double score = 0.0;
        
        // Watchlist
        if (model.getIsOnWatchlist() != null && model.getIsOnWatchlist()) {
            score += FEATURE_WEIGHTS.get("on_watchlist");
        }
        
        // Politically Exposed Person
        if (model.getIsPep() != null && model.getIsPep()) {
            score += FEATURE_WEIGHTS.get("is_pep");
        }
        
        // Sanctions list
        if (model.getIsSanctioned() != null && model.getIsSanctioned()) {
            score += FEATURE_WEIGHTS.get("is_sanctioned");
        }
        
        // External risk score
        if (model.getExternalRiskScore() != null) {
            double extScore = model.getExternalRiskScore() / 100.0; // Normalize to 0-1
            score += extScore * FEATURE_WEIGHTS.get("external_risk_score");
        }
        
        return score;
    }
    
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    private static Map<String, Double> initializeWeights() {
        // These weights would typically be learned from training data
        Map<String, Double> weights = new HashMap<>();
        
        // Amount features
        weights.put("amount_log", 0.15);
        weights.put("amount_deviation", 0.25);
        weights.put("round_amount", 0.08);
        
        // Temporal features
        weights.put("late_night", 0.12);
        weights.put("very_late", 0.18);
        weights.put("weekend", 0.05);
        weights.put("holiday", 0.03);
        
        // User profile features
        weights.put("first_time_user", 0.35);
        weights.put("very_new_account", 0.28);
        weights.put("account_age_factor", 0.20);
        
        // Device and location features
        weights.put("new_device", 0.30);
        weights.put("vpn_usage", 0.45);
        weights.put("proxy_usage", 0.35);
        weights.put("tor_usage", 0.80);
        weights.put("location_change", 0.25);
        weights.put("distance_factor", 0.15);
        weights.put("international_transaction", 0.20);
        
        // Transaction pattern features
        weights.put("high_velocity", 0.40);
        weights.put("high_frequency_24h", 0.30);
        weights.put("rush_transaction", 0.15);
        
        // Behavioral features
        weights.put("behavioral_score", 0.35);
        weights.put("typing_anomaly", 0.20);
        weights.put("click_anomaly", 0.18);
        weights.put("short_session", 0.25);
        
        // Historical risk features
        weights.put("failed_attempts", 0.30);
        weights.put("recent_chargebacks", 0.60);
        weights.put("recent_disputes", 0.40);
        weights.put("historical_fraud_rate", 0.70);
        
        // External data features
        weights.put("on_watchlist", 0.85);
        weights.put("is_pep", 0.30);
        weights.put("is_sanctioned", 0.95);
        weights.put("external_risk_score", 0.50);
        
        return weights;
    }
}