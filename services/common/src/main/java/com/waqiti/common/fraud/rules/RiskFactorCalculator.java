package com.waqiti.common.fraud.rules;

import com.waqiti.common.fraud.FraudContext;
import com.waqiti.common.fraud.scoring.RiskFactorResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced risk factor calculation engine for fraud detection.
 * Analyzes transaction patterns, user behavior, and contextual data to calculate risk scores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskFactorCalculator {
    
    // Risk factor weights and thresholds
    private static final Map<String, Double> RISK_FACTOR_WEIGHTS = new ConcurrentHashMap<>();
    private static final Map<String, Double> RISK_THRESHOLDS = new ConcurrentHashMap<>();
    
    static {
        // Initialize default risk factor weights
        RISK_FACTOR_WEIGHTS.put("amount_deviation", 0.25);
        RISK_FACTOR_WEIGHTS.put("velocity_risk", 0.20);
        RISK_FACTOR_WEIGHTS.put("geographic_risk", 0.15);
        RISK_FACTOR_WEIGHTS.put("temporal_risk", 0.10);
        RISK_FACTOR_WEIGHTS.put("device_risk", 0.10);
        RISK_FACTOR_WEIGHTS.put("behavioral_risk", 0.10);
        RISK_FACTOR_WEIGHTS.put("merchant_risk", 0.05);
        RISK_FACTOR_WEIGHTS.put("payment_method_risk", 0.05);
        
        // Initialize risk thresholds
        RISK_THRESHOLDS.put("high_risk_threshold", 0.7);
        RISK_THRESHOLDS.put("medium_risk_threshold", 0.4);
        RISK_THRESHOLDS.put("low_risk_threshold", 0.2);
    }
    
    /**
     * Calculate comprehensive risk score for a transaction
     */
    public RiskCalculationResult calculateRiskScore(Map<String, Object> transactionData) {
        if (transactionData == null || transactionData.isEmpty()) {
            log.warn("Empty transaction data provided for risk calculation");
            return createDefaultRiskResult();
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Calculate individual risk factors
            Map<String, Double> riskFactors = calculateIndividualRiskFactors(transactionData);
            
            // Calculate weighted overall risk score
            double overallRiskScore = calculateWeightedRiskScore(riskFactors);
            
            // Determine risk level
            RiskLevel riskLevel = determineRiskLevel(overallRiskScore);
            
            // Identify top risk contributors
            List<String> topRiskFactors = identifyTopRiskFactors(riskFactors);
            
            // Generate risk explanation
            String explanation = generateRiskExplanation(riskFactors, overallRiskScore);
            
            // Calculate confidence score
            double confidence = calculateConfidenceScore(riskFactors, transactionData);
            
            long calculationTime = System.currentTimeMillis() - startTime;
            
            RiskCalculationResult result = RiskCalculationResult.builder()
                    .overallRiskScore(overallRiskScore)
                    .riskLevel(riskLevel)
                    .confidence(confidence)
                    .individualRiskFactors(riskFactors)
                    .topRiskFactors(topRiskFactors)
                    .explanation(explanation)
                    .calculationTimeMs(calculationTime)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            log.debug("Risk calculation completed in {}ms - Score: {}, Level: {}", 
                calculationTime, overallRiskScore, riskLevel);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error calculating risk score", e);
            return createErrorRiskResult(e.getMessage());
        }
    }
    
    /**
     * Calculate individual risk factors
     */
    private Map<String, Double> calculateIndividualRiskFactors(Map<String, Object> transactionData) {
        Map<String, Double> riskFactors = new HashMap<>();
        
        // Amount-based risk
        riskFactors.put("amount_deviation", calculateAmountDeviationRisk(transactionData));
        
        // Velocity risk (frequency and speed of transactions)
        riskFactors.put("velocity_risk", calculateVelocityRisk(transactionData));
        
        // Geographic risk
        riskFactors.put("geographic_risk", calculateGeographicRisk(transactionData));
        
        // Temporal risk (time-based patterns)
        riskFactors.put("temporal_risk", calculateTemporalRisk(transactionData));
        
        // Device and digital fingerprint risk
        riskFactors.put("device_risk", calculateDeviceRisk(transactionData));
        
        // Behavioral pattern risk
        riskFactors.put("behavioral_risk", calculateBehavioralRisk(transactionData));
        
        // Merchant risk
        riskFactors.put("merchant_risk", calculateMerchantRisk(transactionData));
        
        // Payment method risk
        riskFactors.put("payment_method_risk", calculatePaymentMethodRisk(transactionData));
        
        // Network and session risk
        riskFactors.put("network_risk", calculateNetworkRisk(transactionData));
        
        // Account age and history risk
        riskFactors.put("account_risk", calculateAccountRisk(transactionData));
        
        return riskFactors;
    }
    
    /**
     * Calculate amount deviation risk
     */
    private double calculateAmountDeviationRisk(Map<String, Object> data) {
        double amount = getDoubleValue(data, "amount", 0.0);
        double avgAmount = getDoubleValue(data, "userAvgTransactionAmount", 100.0);
        double accountBalance = getDoubleValue(data, "accountBalance", 1000.0);
        
        double risk = 0.0;
        
        // Risk based on deviation from user's average
        if (avgAmount > 0) {
            double amountRatio = amount / avgAmount;
            if (amountRatio > 10.0) { // 10x larger than average
                risk += 0.8;
            } else if (amountRatio > 5.0) { // 5x larger than average
                risk += 0.6;
            } else if (amountRatio > 3.0) { // 3x larger than average
                risk += 0.4;
            }
        }
        
        // Risk based on account balance ratio
        if (accountBalance > 0) {
            double balanceRatio = amount / accountBalance;
            if (balanceRatio > 0.8) { // More than 80% of balance
                risk += 0.6;
            } else if (balanceRatio > 0.5) { // More than 50% of balance
                risk += 0.3;
            }
        }
        
        // Round amount patterns (often fraud)
        if (amount % 100 == 0 && amount >= 1000) {
            risk += 0.2;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate velocity risk
     */
    private double calculateVelocityRisk(Map<String, Object> data) {
        int transactionsLast24h = getIntValue(data, "transactionsLast24Hours", 0);
        int transactionsLastHour = getIntValue(data, "transactionsLastHour", 0);
        double totalAmountLast24h = getDoubleValue(data, "totalAmountLast24Hours", 0.0);
        double avgDailyAmount = getDoubleValue(data, "avgDailyTransactionAmount", 100.0);
        
        double risk = 0.0;
        
        // Frequency risk
        if (transactionsLast24h > 20) {
            risk += 0.8;
        } else if (transactionsLast24h > 10) {
            risk += 0.5;
        } else if (transactionsLast24h > 5) {
            risk += 0.3;
        }
        
        // Burst risk (many transactions in short time)
        if (transactionsLastHour > 5) {
            risk += 0.7;
        } else if (transactionsLastHour > 3) {
            risk += 0.4;
        }
        
        // Amount velocity risk
        if (avgDailyAmount > 0) {
            double amountRatio = totalAmountLast24h / avgDailyAmount;
            if (amountRatio > 10.0) {
                risk += 0.6;
            } else if (amountRatio > 5.0) {
                risk += 0.3;
            }
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate geographic risk
     */
    private double calculateGeographicRisk(Map<String, Object> data) {
        String country = getStringValue(data, "country", "");
        String userCountry = getStringValue(data, "userCountry", "");
        double distanceFromHome = getDoubleValue(data, "distanceFromHomeKm", 0.0);
        boolean isHighRiskCountry = getBooleanValue(data, "isHighRiskCountry", false);
        double transactionVelocity = getDoubleValue(data, "transactionVelocityKmH", 0.0);
        
        double risk = 0.0;
        
        // High-risk country
        if (isHighRiskCountry) {
            risk += 0.7;
        }
        
        // Cross-border transaction
        if (!country.equals(userCountry) && !country.isEmpty() && !userCountry.isEmpty()) {
            risk += 0.4;
        }
        
        // Distance from home location
        if (distanceFromHome > 1000) { // More than 1000km from home
            risk += 0.6;
        } else if (distanceFromHome > 500) { // More than 500km from home
            risk += 0.3;
        } else if (distanceFromHome > 100) { // More than 100km from home
            risk += 0.1;
        }
        
        // Impossible velocity (suggests location spoofing)
        if (transactionVelocity > 1000) { // More than 1000 km/h between transactions
            risk += 0.9;
        } else if (transactionVelocity > 500) { // More than 500 km/h
            risk += 0.6;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate temporal risk
     */
    private double calculateTemporalRisk(Map<String, Object> data) {
        int hourOfDay = getIntValue(data, "hourOfDay", 12);
        int dayOfWeek = getIntValue(data, "dayOfWeek", 3);
        int userPreferredHour = getIntValue(data, "userPreferredTransactionHour", 12);
        boolean isHoliday = getBooleanValue(data, "isHoliday", false);
        
        double risk = 0.0;
        
        // Off-hours risk (late night/early morning)
        if (hourOfDay >= 2 && hourOfDay <= 5) {
            risk += 0.6;
        } else if (hourOfDay >= 23 || hourOfDay <= 1) {
            risk += 0.4;
        }
        
        // Weekend risk (slightly higher for certain transaction types)
        if (dayOfWeek >= 6) {
            risk += 0.1;
        }
        
        // Deviation from user's preferred time
        int hourDeviation = Math.abs(hourOfDay - userPreferredHour);
        if (hourDeviation > 8) {
            risk += 0.3;
        } else if (hourDeviation > 4) {
            risk += 0.1;
        }
        
        // Holiday risk
        if (isHoliday) {
            risk += 0.2;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate device risk
     */
    private double calculateDeviceRisk(Map<String, Object> data) {
        boolean isNewDevice = getBooleanValue(data, "isNewDevice", false);
        double deviceRiskScore = getDoubleValue(data, "deviceRiskScore", 0.0);
        boolean isJailbrokenRooted = getBooleanValue(data, "isJailbrokenRooted", false);
        boolean isEmulator = getBooleanValue(data, "isEmulator", false);
        boolean isProxyVPN = getBooleanValue(data, "isProxyVPN", false);
        
        double risk = 0.0;
        
        // New device risk
        if (isNewDevice) {
            risk += 0.5;
        }
        
        // Device risk score
        risk += deviceRiskScore;
        
        // Compromised device indicators
        if (isJailbrokenRooted) {
            risk += 0.7;
        }
        
        if (isEmulator) {
            risk += 0.8;
        }
        
        // Network masking
        if (isProxyVPN) {
            risk += 0.6;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate behavioral risk
     */
    private double calculateBehavioralRisk(Map<String, Object> data) {
        double behaviorScore = getDoubleValue(data, "behaviorAnomalyScore", 0.0);
        boolean isNewMerchant = getBooleanValue(data, "isNewMerchant", false);
        int merchantFrequency = getIntValue(data, "merchantTransactionCount", 0);
        double sessionDuration = getDoubleValue(data, "sessionDurationMinutes", 5.0);
        
        double risk = 0.0;
        
        // Behavioral anomaly score
        risk += behaviorScore;
        
        // New merchant risk
        if (isNewMerchant) {
            risk += 0.3;
        }
        
        // Low merchant frequency (unusual for user)
        if (merchantFrequency == 1) {
            risk += 0.2;
        }
        
        // Very short session (rushed transaction)
        if (sessionDuration < 1.0) {
            risk += 0.4;
        } else if (sessionDuration < 2.0) {
            risk += 0.2;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate merchant risk
     */
    private double calculateMerchantRisk(Map<String, Object> data) {
        double merchantRiskScore = getDoubleValue(data, "merchantRiskScore", 0.0);
        String merchantCategory = getStringValue(data, "merchantCategory", "");
        boolean isHighRiskMerchant = getBooleanValue(data, "isHighRiskMerchant", false);
        
        double risk = 0.0;
        
        // Direct merchant risk score
        risk += merchantRiskScore;
        
        // High-risk merchant
        if (isHighRiskMerchant) {
            risk += 0.6;
        }
        
        // High-risk merchant categories
        Set<String> highRiskCategories = Set.of("gambling", "crypto", "money_transfer", "adult", "firearms");
        if (highRiskCategories.contains(merchantCategory.toLowerCase())) {
            risk += 0.4;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate payment method risk
     */
    private double calculatePaymentMethodRisk(Map<String, Object> data) {
        String paymentMethod = getStringValue(data, "paymentMethod", "");
        boolean isNewPaymentMethod = getBooleanValue(data, "isNewPaymentMethod", false);
        boolean isCardPresent = getBooleanValue(data, "isCardPresent", true);
        
        double risk = 0.0;
        
        // New payment method
        if (isNewPaymentMethod) {
            risk += 0.3;
        }
        
        // Card not present transactions (higher risk)
        if (!isCardPresent) {
            risk += 0.2;
        }
        
        // High-risk payment methods
        Set<String> highRiskMethods = Set.of("crypto", "wire_transfer", "money_order", "prepaid_card");
        if (highRiskMethods.contains(paymentMethod.toLowerCase())) {
            risk += 0.5;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate network risk
     */
    private double calculateNetworkRisk(Map<String, Object> data) {
        boolean isTor = getBooleanValue(data, "isTorNetwork", false);
        boolean isDataCenter = getBooleanValue(data, "isDataCenterIP", false);
        double ipReputationScore = getDoubleValue(data, "ipReputationScore", 0.0);
        
        double risk = 0.0;
        
        // Tor network usage
        if (isTor) {
            risk += 0.8;
        }
        
        // Data center IP (hosting provider)
        if (isDataCenter) {
            risk += 0.6;
        }
        
        // IP reputation
        risk += ipReputationScore;
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate account risk
     */
    private double calculateAccountRisk(Map<String, Object> data) {
        int accountAgedays = getIntValue(data, "accountAgeDays", 365);
        double userRiskScore = getDoubleValue(data, "userRiskScore", 0.0);
        boolean hasRecentPasswordChange = getBooleanValue(data, "hasRecentPasswordChange", false);
        int failedLoginAttempts = getIntValue(data, "recentFailedLogins", 0);
        
        double risk = 0.0;
        
        // New account risk
        if (accountAgedays < 7) {
            risk += 0.7;
        } else if (accountAgedays < 30) {
            risk += 0.4;
        } else if (accountAgedays < 90) {
            risk += 0.2;
        }
        
        // User risk score
        risk += userRiskScore;
        
        // Recent password change (could indicate compromise)
        if (hasRecentPasswordChange) {
            risk += 0.3;
        }
        
        // Failed login attempts
        if (failedLoginAttempts > 5) {
            risk += 0.6;
        } else if (failedLoginAttempts > 3) {
            risk += 0.3;
        }
        
        return Math.min(1.0, risk);
    }
    
    /**
     * Calculate weighted overall risk score
     */
    private double calculateWeightedRiskScore(Map<String, Double> riskFactors) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<String, Double> factor : riskFactors.entrySet()) {
            String factorName = factor.getKey();
            Double factorScore = factor.getValue();
            Double weight = RISK_FACTOR_WEIGHTS.get(factorName);
            
            if (weight != null && factorScore != null) {
                weightedSum += factorScore * weight;
                totalWeight += weight;
            }
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
    
    /**
     * Determine risk level from score
     */
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= RISK_THRESHOLDS.get("high_risk_threshold")) {
            return RiskLevel.HIGH;
        } else if (riskScore >= RISK_THRESHOLDS.get("medium_risk_threshold")) {
            return RiskLevel.MEDIUM;
        } else if (riskScore >= RISK_THRESHOLDS.get("low_risk_threshold")) {
            return RiskLevel.LOW;
        } else {
            return RiskLevel.MINIMAL;
        }
    }
    
    /**
     * Identify top risk factors
     */
    private List<String> identifyTopRiskFactors(Map<String, Double> riskFactors) {
        return riskFactors.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.3) // Only significant risk factors
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Generate risk explanation
     */
    private String generateRiskExplanation(Map<String, Double> riskFactors, double overallScore) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append(String.format("Overall risk score: %.2f. ", overallScore));
        
        List<String> highRiskFactors = riskFactors.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.5)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> String.format("%s (%.2f)", 
                    formatFactorName(entry.getKey()), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
        
        if (!highRiskFactors.isEmpty()) {
            explanation.append("High risk factors: ").append(String.join(", ", highRiskFactors)).append(". ");
        }
        
        return explanation.toString();
    }
    
    /**
     * Calculate confidence score
     */
    private double calculateConfidenceScore(Map<String, Double> riskFactors, Map<String, Object> data) {
        // Confidence based on data completeness and quality
        int availableFactors = (int) riskFactors.values().stream().filter(score -> score > 0).count();
        int totalFactors = riskFactors.size();
        
        double dataCompleteness = (double) availableFactors / totalFactors;
        
        // Adjust for data quality indicators
        boolean hasHistoricalData = getBooleanValue(data, "hasHistoricalData", false);
        boolean hasDeviceFingerprint = getBooleanValue(data, "hasDeviceFingerprint", false);
        
        double confidence = dataCompleteness * 0.7;
        
        if (hasHistoricalData) {
            confidence += 0.2;
        }
        
        if (hasDeviceFingerprint) {
            confidence += 0.1;
        }
        
        return Math.min(1.0, confidence);
    }
    
    // Utility methods
    
    private double getDoubleValue(Map<String, Object> data, String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private String getStringValue(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private boolean getBooleanValue(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    private String formatFactorName(String factorName) {
        return factorName.replace("_", " ").replace("-", " ");
    }
    
    private RiskCalculationResult createDefaultRiskResult() {
        return RiskCalculationResult.builder()
                .overallRiskScore(0.5)
                .riskLevel(RiskLevel.MEDIUM)
                .confidence(0.1)
                .explanation("Insufficient data for risk calculation")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    private RiskCalculationResult createErrorRiskResult(String errorMessage) {
        return RiskCalculationResult.builder()
                .overallRiskScore(0.5)
                .riskLevel(RiskLevel.MEDIUM)
                .confidence(0.0)
                .explanation("Error in risk calculation: " + errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public RiskFactorResult calculateRiskFactors(FraudContext context) {
        return null;
//        TODO here, you must fully implement - next iteration
    }

    // Supporting enums
    
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Risk calculation result
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskCalculationResult {
        private double overallRiskScore;
        private RiskLevel riskLevel;
        private double confidence;
        private Map<String, Double> individualRiskFactors;
        private List<String> topRiskFactors;
        private String explanation;
        private long calculationTimeMs;
        private LocalDateTime timestamp;
        
        public String getSummary() {
            return String.format("Risk: %.2f (%s), Confidence: %.2f, Top Factors: %s",
                overallRiskScore, riskLevel, confidence, 
                topRiskFactors != null ? String.join(", ", topRiskFactors) : "None");
        }
    }
}