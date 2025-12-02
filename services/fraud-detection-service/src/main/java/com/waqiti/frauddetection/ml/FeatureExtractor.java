package com.waqiti.frauddetection.ml;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PRODUCTION-READY FEATURE EXTRACTOR
 * 
 * Extracts and normalizes features from fraud check requests for ML model input.
 * 
 * Feature Categories:
 * 1. Transaction Features: amount, type, frequency
 * 2. Temporal Features: time of day, day of week, seasonality
 * 3. User Features: account age, transaction history, behavior patterns
 * 4. Device Features: device fingerprint, location, network
 * 5. Risk Features: velocity checks, blacklist matches, anomalies
 */
@Component
@Slf4j
public class FeatureExtractor {
    
    private static final int FEATURE_COUNT = 25; // Total number of features
    
    /**
     * Extract features for ML model input
     * Returns normalized feature array suitable for TensorFlow model
     */
    public float[][] extractFeatures(FraudCheckRequest request) {
        log.debug("Extracting features for transaction: {}", request.getTransactionId());
        
        float[] features = new float[FEATURE_COUNT];
        int index = 0;
        
        // 1. TRANSACTION FEATURES (0-4)
        features[index++] = normalizeAmount(request.getAmount().doubleValue());
        features[index++] = encodeTransactionType(request.getTransactionType());
        features[index++] = normalizeFrequency(request.getRecentTransactionCount());
        features[index++] = normalizeAccountAge(request.getAccountAge());
        features[index++] = normalizeRecipientCount(request.getRecentRecipientCount());
        
        // 2. TEMPORAL FEATURES (5-7)
        LocalDateTime now = LocalDateTime.now();
        features[index++] = normalizeHour(now.getHour());
        features[index++] = normalizeDayOfWeek(now.getDayOfWeek().getValue());
        features[index++] = normalizeMonth(now.getMonthValue());
        
        // 3. USER BEHAVIOR FEATURES (8-11)
        features[index++] = booleanToFloat(request.isNewDevice());
        features[index++] = booleanToFloat(request.isNewRecipient());
        features[index++] = normalizeFailedAttempts(request.getFailedLoginAttempts());
        features[index++] = encodeUserRiskLevel(request.getUserRiskLevel());
        
        // 4. DEVICE & LOCATION FEATURES (12-16)
        features[index++] = booleanToFloat(request.isVpnDetected());
        features[index++] = booleanToFloat(request.isProxyDetected());
        features[index++] = booleanToFloat(request.isTorDetected());
        features[index++] = booleanToFloat(!request.getSenderCountry().equals(request.getRecipientCountry()));
        features[index++] = encodeCountryRisk(request.getSenderCountry(), request.getRecipientCountry());
        
        // 5. VELOCITY & PATTERN FEATURES (17-20)
        features[index++] = normalizeVelocityScore(request.getVelocityScore());
        features[index++] = normalizeAmount(request.getDailyTransactionSum());
        features[index++] = normalizeFrequency(request.getHourlyTransactionCount());
        features[index++] = normalizeDiversityScore(request.getRecipientDiversityScore());
        
        // 6. ADVANCED RISK FEATURES (21-24)
        features[index++] = normalizeRiskScore(request.getDeviceRiskScore());
        features[index++] = normalizeRiskScore(request.getGeoRiskScore());
        features[index++] = normalizeRiskScore(request.getBehaviorRiskScore());
        features[index++] = booleanToFloat(request.isBlacklistMatch());
        
        log.debug("Extracted {} features for transaction {}", features.length, request.getTransactionId());
        
        return new float[][] { features };
    }
    
    /**
     * Extract features as a map for easier debugging and logging
     */
    public Map<String, Object> extractFeaturesAsMap(FraudCheckRequest request) {
        Map<String, Object> features = new HashMap<>();
        
        // Transaction features
        features.put("amount", request.getAmount().doubleValue());
        features.put("transaction_type", request.getTransactionType());
        features.put("recent_transaction_count", request.getRecentTransactionCount());
        features.put("account_age", request.getAccountAge());
        features.put("recent_recipient_count", request.getRecentRecipientCount());
        
        // Temporal features
        LocalDateTime now = LocalDateTime.now();
        features.put("hour_of_day", now.getHour());
        features.put("day_of_week", now.getDayOfWeek().getValue());
        features.put("month", now.getMonthValue());
        
        // User behavior features
        features.put("new_device", request.isNewDevice());
        features.put("new_recipient", request.isNewRecipient());
        features.put("failed_login_attempts", request.getFailedLoginAttempts());
        features.put("user_risk_level", request.getUserRiskLevel());
        
        // Device & location features
        features.put("vpn_detected", request.isVpnDetected());
        features.put("proxy_detected", request.isProxyDetected());
        features.put("tor_detected", request.isTorDetected());
        features.put("international", !request.getSenderCountry().equals(request.getRecipientCountry()));
        features.put("sender_country", request.getSenderCountry());
        features.put("recipient_country", request.getRecipientCountry());
        
        // Risk scores
        features.put("velocity_score", request.getVelocityScore());
        features.put("device_risk_score", request.getDeviceRiskScore());
        features.put("geo_risk_score", request.getGeoRiskScore());
        features.put("behavior_risk_score", request.getBehaviorRiskScore());
        features.put("blacklist_match", request.isBlacklistMatch());
        
        return features;
    }
    
    // NORMALIZATION METHODS
    
    private float normalizeAmount(double amount) {
        // Log transform for amount normalization (handles large values better)
        return (float) (Math.log1p(amount) / 15.0); // Assumes max ~3M transaction
    }
    
    private float encodeTransactionType(String type) {
        // One-hot encoding for transaction types
        switch (type.toUpperCase()) {
            case "P2P": return 0.2f;
            case "MERCHANT": return 0.4f;
            case "ATM": return 0.6f;
            case "INTERNATIONAL": return 0.8f;
            case "CRYPTO": return 1.0f;
            default: return 0.0f;
        }
    }
    
    private float normalizeFrequency(int count) {
        // Sigmoid-like normalization for frequency counts
        return (float) (1.0 / (1.0 + Math.exp(-(count - 10.0) / 5.0)));
    }
    
    private float normalizeAccountAge(int days) {
        // Normalize account age (0-1, with 2 years as max)
        return Math.min(days / 730.0f, 1.0f);
    }
    
    private float normalizeRecipientCount(int count) {
        // Normalize recipient count (logarithmic scale)
        return (float) (Math.log1p(count) / 5.0);
    }
    
    private float normalizeHour(int hour) {
        // Cyclic encoding for hour (0-23 -> 0-1)
        return hour / 24.0f;
    }
    
    private float normalizeDayOfWeek(int day) {
        // Normalize day of week (1-7 -> 0-1)
        return (day - 1) / 6.0f;
    }
    
    private float normalizeMonth(int month) {
        // Normalize month (1-12 -> 0-1)
        return (month - 1) / 11.0f;
    }
    
    private float normalizeFailedAttempts(int attempts) {
        // Normalize failed attempts with cap at 20
        return Math.min(attempts / 20.0f, 1.0f);
    }
    
    private float encodeUserRiskLevel(String riskLevel) {
        // Encode user risk level as numeric value
        switch (riskLevel.toUpperCase()) {
            case "LOW": return 0.1f;
            case "MEDIUM": return 0.5f;
            case "HIGH": return 0.8f;
            case "CRITICAL": return 1.0f;
            default: return 0.3f; // Default medium-low
        }
    }
    
    private float encodeCountryRisk(String senderCountry, String recipientCountry) {
        // Simple country risk encoding (would be enhanced with real risk data)
        if (isHighRiskCountry(senderCountry) || isHighRiskCountry(recipientCountry)) {
            return 1.0f;
        }
        if (isMediumRiskCountry(senderCountry) || isMediumRiskCountry(recipientCountry)) {
            return 0.5f;
        }
        return 0.1f;
    }
    
    private float normalizeVelocityScore(double score) {
        // Normalize velocity score (0-100 -> 0-1)
        return (float) Math.min(score / 100.0, 1.0);
    }
    
    private float normalizeDiversityScore(double score) {
        // Normalize diversity score (0-10 -> 0-1)
        return (float) Math.min(score / 10.0, 1.0);
    }
    
    private float normalizeRiskScore(double score) {
        // Generic risk score normalization (0-100 -> 0-1)
        return (float) Math.min(score / 100.0, 1.0);
    }
    
    private float booleanToFloat(boolean value) {
        return value ? 1.0f : 0.0f;
    }
    
    // RISK ASSESSMENT HELPERS
    
    private boolean isHighRiskCountry(String country) {
        // Would be loaded from configuration or external service
        return country != null && 
               (country.equals("AF") || country.equals("IR") || 
                country.equals("KP") || country.equals("SY"));
    }
    
    private boolean isMediumRiskCountry(String country) {
        // Would be loaded from configuration or external service
        return country != null && 
               (country.equals("PK") || country.equals("BD") || 
                country.equals("NG") || country.equals("ID"));
    }
    
    /**
     * Validate extracted features for consistency
     */
    public boolean validateFeatures(float[][] features) {
        if (features == null || features.length != 1 || features[0].length != FEATURE_COUNT) {
            log.warn("Invalid feature array dimensions: expected [1][{}], got {}",
                    FEATURE_COUNT, features != null ? features.length + "x" + (features[0] != null ? features[0].length : "null") : "null");
            return false;
        }
        
        // Check for NaN or infinite values
        for (float feature : features[0]) {
            if (Float.isNaN(feature) || Float.isInfinite(feature)) {
                log.warn("Invalid feature value detected: {}", feature);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get feature importance weights for model interpretation
     */
    public Map<String, Double> getFeatureImportance() {
        Map<String, Double> importance = new HashMap<>();
        
        // Feature importance weights (would be learned from model training)
        importance.put("amount", 0.15);
        importance.put("transaction_type", 0.12);
        importance.put("velocity_score", 0.18);
        importance.put("device_risk_score", 0.14);
        importance.put("geo_risk_score", 0.11);
        importance.put("behavior_risk_score", 0.10);
        importance.put("new_device", 0.08);
        importance.put("international", 0.07);
        importance.put("blacklist_match", 0.05);
        
        return importance;
    }
}