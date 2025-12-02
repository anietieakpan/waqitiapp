package com.waqiti.common.fraud.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced feature engineering service for fraud detection machine learning models.
 * Transforms raw transaction data into meaningful features for ML models.
 */
@Service
public class FeatureEngineering {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FeatureEngineering.class);
    
    // Feature scaling and normalization parameters
    private static final Map<String, Double> FEATURE_MIN_VALUES = new ConcurrentHashMap<>();
    private static final Map<String, Double> FEATURE_MAX_VALUES = new ConcurrentHashMap<>();
    private static final Map<String, Double> FEATURE_MEANS = new ConcurrentHashMap<>();
    private static final Map<String, Double> FEATURE_STD_DEVS = new ConcurrentHashMap<>();
    
    // Time-based feature constants
    private static final int[] WINDOW_SIZES = {1, 7, 30, 90}; // Days
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Primary feature engineering pipeline
     */
    public Map<String, Double> engineerFeatures(Map<String, Object> rawFeatures) {
        if (rawFeatures == null || rawFeatures.isEmpty()) {
            log.warn("Empty or null raw features provided");
            return new HashMap<>();
        }
        
        Map<String, Double> engineeredFeatures = new HashMap<>();
        
        try {
            // Basic feature extraction and conversion
            extractBasicFeatures(rawFeatures, engineeredFeatures);
            
            // Amount-based features
            engineerAmountFeatures(rawFeatures, engineeredFeatures);
            
            // Time-based features
            engineerTemporalFeatures(rawFeatures, engineeredFeatures);
            
            // Location-based features
            engineerLocationFeatures(rawFeatures, engineeredFeatures);
            
            // User behavior features
            engineerBehaviorFeatures(rawFeatures, engineeredFeatures);
            
            // Transaction pattern features
            engineerPatternFeatures(rawFeatures, engineeredFeatures);
            
            // Device and channel features
            engineerDeviceFeatures(rawFeatures, engineeredFeatures);
            
            // Risk aggregation features
            engineerRiskFeatures(rawFeatures, engineeredFeatures);
            
            // Normalize features
            normalizeFeatures(engineeredFeatures);
            
            log.debug("Engineered {} features from {} raw inputs", 
                engineeredFeatures.size(), rawFeatures.size());
            
            return engineeredFeatures;
            
        } catch (Exception e) {
            log.error("Error in feature engineering", e);
            return createDefaultFeatures();
        }
    }
    
    /**
     * Extract basic numeric and categorical features
     */
    private void extractBasicFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        // Transaction amount
        engineered.put("amount", getNumericValue(raw, "amount", 0.0));
        
        // Account balance
        engineered.put("account_balance", getNumericValue(raw, "accountBalance", 0.0));
        
        // User age (in days)
        engineered.put("user_age_days", getNumericValue(raw, "userAgeDays", 0.0));
        
        // Transaction count for user
        engineered.put("user_transaction_count", getNumericValue(raw, "userTransactionCount", 0.0));
        
        // Days since last transaction
        engineered.put("days_since_last_transaction", getNumericValue(raw, "daysSinceLastTransaction", 0.0));
        
        // Merchant category encoding
        engineered.put("merchant_category_code", encodeCategorical(raw, "merchantCategory"));
        
        // Payment method encoding
        engineered.put("payment_method_code", encodeCategorical(raw, "paymentMethod"));
        
        // Channel encoding (online, mobile, ATM, etc.)
        engineered.put("channel_code", encodeCategorical(raw, "channel"));
        
        // Currency encoding
        engineered.put("currency_code", encodeCategorical(raw, "currency"));
    }
    
    /**
     * Engineer amount-based features
     */
    private void engineerAmountFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        double amount = getNumericValue(raw, "amount", 0.0);
        double accountBalance = getNumericValue(raw, "accountBalance", 0.0);
        double avgTransactionAmount = getNumericValue(raw, "avgTransactionAmount", 0.0);
        
        // Amount ratios
        if (accountBalance > 0) {
            engineered.put("amount_to_balance_ratio", amount / accountBalance);
        }
        
        if (avgTransactionAmount > 0) {
            engineered.put("amount_to_avg_ratio", amount / avgTransactionAmount);
        }
        
        // Amount categories
        engineered.put("is_micro_transaction", amount < 10.0 ? 1.0 : 0.0);
        engineered.put("is_small_transaction", amount < 100.0 ? 1.0 : 0.0);
        engineered.put("is_medium_transaction", (amount >= 100.0 && amount < 1000.0) ? 1.0 : 0.0);
        engineered.put("is_large_transaction", amount >= 1000.0 ? 1.0 : 0.0);
        engineered.put("is_very_large_transaction", amount >= 10000.0 ? 1.0 : 0.0);
        
        // Round number patterns
        engineered.put("is_round_amount", (amount % 10.0 == 0) ? 1.0 : 0.0);
        engineered.put("is_very_round_amount", (amount % 100.0 == 0) ? 1.0 : 0.0);
        
        // Logarithmic scaling for amount
        engineered.put("log_amount", Math.log(Math.max(amount, 1.0)));
    }
    
    /**
     * Engineer temporal features
     */
    private void engineerTemporalFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        LocalDateTime timestamp = getTimestamp(raw, "timestamp");
        
        if (timestamp != null) {
            // Hour of day (0-23)
            engineered.put("hour_of_day", (double) timestamp.getHour());
            
            // Day of week (1-7, Monday=1)
            engineered.put("day_of_week", (double) timestamp.getDayOfWeek().getValue());
            
            // Day of month (1-31)
            engineered.put("day_of_month", (double) timestamp.getDayOfMonth());
            
            // Month of year (1-12)
            engineered.put("month_of_year", (double) timestamp.getMonthValue());
            
            // Weekend indicator
            int dayOfWeek = timestamp.getDayOfWeek().getValue();
            engineered.put("is_weekend", (dayOfWeek >= 6) ? 1.0 : 0.0);
            
            // Business hours indicator (9 AM - 5 PM)
            int hour = timestamp.getHour();
            engineered.put("is_business_hours", (hour >= 9 && hour <= 17) ? 1.0 : 0.0);
            
            // Late night indicator (11 PM - 5 AM)
            engineered.put("is_late_night", (hour >= 23 || hour <= 5) ? 1.0 : 0.0);
            
            // Holiday proximity (simplified)
            engineered.put("is_near_month_end", (timestamp.getDayOfMonth() >= 28) ? 1.0 : 0.0);
            
            // Seasonal patterns
            int month = timestamp.getMonthValue();
            engineered.put("is_holiday_season", (month == 12 || month == 1) ? 1.0 : 0.0);
        }
    }
    
    /**
     * Engineer location-based features
     */
    private void engineerLocationFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        // Country/region codes
        engineered.put("country_code", encodeCategorical(raw, "country"));
        engineered.put("region_code", encodeCategorical(raw, "region"));
        
        // Location risk score
        engineered.put("location_risk_score", getNumericValue(raw, "locationRiskScore", 0.0));
        
        // Distance from home location
        engineered.put("distance_from_home", getNumericValue(raw, "distanceFromHome", 0.0));
        
        // Velocity features (distance over time)
        double distance = getNumericValue(raw, "distanceFromLastTransaction", 0.0);
        double timeDiff = getNumericValue(raw, "minutesSinceLastTransaction", 1.0);
        engineered.put("transaction_velocity", distance / Math.max(timeDiff, 1.0));
        
        // High-risk location indicators
        engineered.put("is_high_risk_country", getNumericValue(raw, "isHighRiskCountry", 0.0));
        engineered.put("is_different_country", getNumericValue(raw, "isDifferentCountry", 0.0));
        
        // Geographic anomaly score
        engineered.put("geo_anomaly_score", calculateGeoAnomalyScore(raw));
    }
    
    /**
     * Engineer user behavior features
     */
    private void engineerBehaviorFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        // Transaction frequency patterns
        engineered.put("transactions_per_day", getNumericValue(raw, "transactionsPerDay", 0.0));
        engineered.put("unique_merchants_per_month", getNumericValue(raw, "uniqueMerchantsPerMonth", 0.0));
        
        // Spending behavior
        engineered.put("spending_variance", getNumericValue(raw, "spendingVariance", 0.0));
        engineered.put("spending_trend", getNumericValue(raw, "spendingTrend", 0.0));
        
        // Time patterns
        engineered.put("preferred_transaction_hour", getNumericValue(raw, "preferredTransactionHour", 12.0));
        engineered.put("hour_deviation", Math.abs(getNumericValue(raw, "currentHour", 12.0) - 
                                                 getNumericValue(raw, "preferredTransactionHour", 12.0)));
        
        // Merchant loyalty
        engineered.put("merchant_frequency", getNumericValue(raw, "merchantFrequency", 0.0));
        engineered.put("is_new_merchant", getNumericValue(raw, "isNewMerchant", 0.0));
        
        // Payment method consistency
        engineered.put("payment_method_consistency", getNumericValue(raw, "paymentMethodConsistency", 0.5));
    }
    
    /**
     * Engineer transaction pattern features
     */
    private void engineerPatternFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        // Sequential transaction patterns
        engineered.put("rapid_fire_transactions", getNumericValue(raw, "rapidFireTransactions", 0.0));
        engineered.put("duplicate_transactions", getNumericValue(raw, "duplicateTransactions", 0.0));
        
        // Amount patterns
        engineered.put("amount_sequence_variance", getNumericValue(raw, "amountSequenceVariance", 0.0));
        engineered.put("is_amount_pattern_suspicious", getNumericValue(raw, "isAmountPatternSuspicious", 0.0));
        
        // Merchant switching patterns
        engineered.put("merchant_switches_per_hour", getNumericValue(raw, "merchantSwitchesPerHour", 0.0));
        
        // Time interval patterns
        engineered.put("avg_time_between_transactions", getNumericValue(raw, "avgTimeBetweenTransactions", 0.0));
        engineered.put("time_interval_variance", getNumericValue(raw, "timeIntervalVariance", 0.0));
    }
    
    /**
     * Engineer device and channel features
     */
    private void engineerDeviceFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        // Device fingerprinting
        engineered.put("device_risk_score", getNumericValue(raw, "deviceRiskScore", 0.0));
        engineered.put("is_new_device", getNumericValue(raw, "isNewDevice", 0.0));
        engineered.put("device_reputation_score", getNumericValue(raw, "deviceReputationScore", 0.5));
        
        // Browser/app information
        engineered.put("browser_risk_score", getNumericValue(raw, "browserRiskScore", 0.0));
        engineered.put("is_mobile_device", getNumericValue(raw, "isMobileDevice", 0.0));
        
        // IP address features
        engineered.put("ip_risk_score", getNumericValue(raw, "ipRiskScore", 0.0));
        engineered.put("is_proxy_or_vpn", getNumericValue(raw, "isProxyOrVPN", 0.0));
        engineered.put("ip_geolocation_mismatch", getNumericValue(raw, "ipGeolocationMismatch", 0.0));
        
        // Session features
        engineered.put("session_duration", getNumericValue(raw, "sessionDuration", 0.0));
        engineered.put("actions_per_session", getNumericValue(raw, "actionsPerSession", 0.0));
    }
    
    /**
     * Engineer aggregated risk features
     */
    private void engineerRiskFeatures(Map<String, Object> raw, Map<String, Double> engineered) {
        // Combine multiple risk signals
        double locationRisk = getNumericValue(raw, "locationRiskScore", 0.0);
        double deviceRisk = getNumericValue(raw, "deviceRiskScore", 0.0);
        double behaviorRisk = calculateBehaviorRisk(raw);
        double velocityRisk = calculateVelocityRisk(raw);
        
        engineered.put("combined_risk_score", (locationRisk + deviceRisk + behaviorRisk + velocityRisk) / 4.0);
        
        // Risk thresholds
        double combinedRisk = engineered.get("combined_risk_score");
        engineered.put("is_high_risk", combinedRisk > 0.7 ? 1.0 : 0.0);
        engineered.put("is_medium_risk", (combinedRisk > 0.4 && combinedRisk <= 0.7) ? 1.0 : 0.0);
        engineered.put("is_low_risk", combinedRisk <= 0.4 ? 1.0 : 0.0);
        
        // Anomaly scores
        engineered.put("user_anomaly_score", calculateUserAnomalyScore(raw));
        engineered.put("merchant_anomaly_score", calculateMerchantAnomalyScore(raw));
        engineered.put("global_anomaly_score", calculateGlobalAnomalyScore(raw));
    }
    
    /**
     * Normalize features using stored statistics
     */
    private void normalizeFeatures(Map<String, Double> features) {
        features.replaceAll((key, value) -> {
            if (value == null) return 0.0;
            
            // Z-score normalization for most features
            Double mean = FEATURE_MEANS.get(key);
            Double stdDev = FEATURE_STD_DEVS.get(key);
            
            if (mean != null && stdDev != null && stdDev > 0) {
                return (value - mean) / stdDev;
            }
            
            // Min-max normalization as fallback
            Double min = FEATURE_MIN_VALUES.get(key);
            Double max = FEATURE_MAX_VALUES.get(key);
            
            if (min != null && max != null && !min.equals(max)) {
                return (value - min) / (max - min);
            }
            
            return value;
        });
    }
    
    // Helper methods
    
    private double getNumericValue(Map<String, Object> data, String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.debug("Could not parse numeric value for key: {} value: {}", key, value);
            }
        }
        return defaultValue;
    }
    
    private LocalDateTime getTimestamp(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value, TIME_FORMATTER);
            } catch (Exception e) {
                log.debug("Could not parse timestamp for key: {} value: {}", key, value);
            }
        }
        return LocalDateTime.now();
    }
    
    private double encodeCategorical(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0.0;
        
        // Simple hash-based encoding
        return Math.abs(value.toString().hashCode() % 1000) / 1000.0;
    }
    
    private double calculateGeoAnomalyScore(Map<String, Object> raw) {
        double distanceFromHome = getNumericValue(raw, "distanceFromHome", 0.0);
        double velocity = getNumericValue(raw, "transactionVelocity", 0.0);
        
        // Combine distance and velocity for anomaly score
        return Math.min(1.0, (distanceFromHome / 1000.0) + (velocity / 100.0));
    }
    
    private double calculateBehaviorRisk(Map<String, Object> raw) {
        double spendingVariance = getNumericValue(raw, "spendingVariance", 0.0);
        double merchantSwitches = getNumericValue(raw, "merchantSwitchesPerHour", 0.0);
        double rapidFire = getNumericValue(raw, "rapidFireTransactions", 0.0);
        
        return Math.min(1.0, (spendingVariance + merchantSwitches + rapidFire) / 3.0);
    }
    
    private double calculateVelocityRisk(Map<String, Object> raw) {
        double transactionsPerDay = getNumericValue(raw, "transactionsPerDay", 0.0);
        double avgTime = getNumericValue(raw, "avgTimeBetweenTransactions", 60.0);
        
        // High velocity = more transactions, less time between
        return Math.min(1.0, transactionsPerDay / 100.0 + (60.0 - avgTime) / 60.0);
    }
    
    private double calculateUserAnomalyScore(Map<String, Object> raw) {
        // Simplified user anomaly calculation
        double hourDeviation = getNumericValue(raw, "hourDeviation", 0.0);
        double amountDeviation = getNumericValue(raw, "amountToAvgRatio", 1.0);
        
        return Math.min(1.0, (hourDeviation / 12.0) + Math.abs(amountDeviation - 1.0));
    }
    
    private double calculateMerchantAnomalyScore(Map<String, Object> raw) {
        double isNewMerchant = getNumericValue(raw, "isNewMerchant", 0.0);
        double merchantRisk = getNumericValue(raw, "merchantRiskScore", 0.0);
        
        return Math.max(isNewMerchant, merchantRisk);
    }
    
    private double calculateGlobalAnomalyScore(Map<String, Object> raw) {
        // Overall anomaly based on multiple factors
        double locationRisk = getNumericValue(raw, "locationRiskScore", 0.0);
        double deviceRisk = getNumericValue(raw, "deviceRiskScore", 0.0);
        double timeRisk = getNumericValue(raw, "isLateNight", 0.0);
        
        return (locationRisk + deviceRisk + timeRisk) / 3.0;
    }
    
    private Map<String, Double> createDefaultFeatures() {
        Map<String, Double> defaults = new HashMap<>();
        
        // Populate with default values for required features
        defaults.put("amount", 0.0);
        defaults.put("combined_risk_score", 0.5);
        defaults.put("user_anomaly_score", 0.5);
        defaults.put("hour_of_day", 12.0);
        defaults.put("day_of_week", 3.0);
        defaults.put("is_weekend", 0.0);
        defaults.put("is_business_hours", 1.0);
        
        log.warn("Using default features due to engineering error");
        return defaults;
    }
    
    /**
     * Update feature statistics for normalization
     */
    public void updateFeatureStatistics(String featureName, double value) {
        FEATURE_MIN_VALUES.merge(featureName, value, Math::min);
        FEATURE_MAX_VALUES.merge(featureName, value, Math::max);
        
        // Simple running average (in production, use proper statistical tracking)
        FEATURE_MEANS.merge(featureName, value, (existing, newValue) -> (existing + newValue) / 2.0);
    }
    
    /**
     * Get feature importance map for interpretability
     */
    public Map<String, Double> getFeatureImportance() {
        Map<String, Double> importance = new HashMap<>();
        
        // Predefined importance scores based on fraud detection domain knowledge
        importance.put("combined_risk_score", 0.95);
        importance.put("user_anomaly_score", 0.90);
        importance.put("amount_to_avg_ratio", 0.85);
        importance.put("distance_from_home", 0.80);
        importance.put("transaction_velocity", 0.75);
        importance.put("device_risk_score", 0.70);
        importance.put("is_new_device", 0.65);
        importance.put("is_late_night", 0.60);
        importance.put("merchant_frequency", 0.55);
        importance.put("rapid_fire_transactions", 0.50);
        
        return importance;
    }
}