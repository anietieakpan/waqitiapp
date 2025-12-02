package com.waqiti.frauddetection.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rule-Based Fallback Fraud Model
 *
 * CRITICAL FAIL-SECURE COMPONENT
 *
 * Used when all ML models fail. Implements conservative rule-based scoring
 * that defaults to HIGH RISK to protect against fraud when ML is unavailable.
 *
 * DESIGN PHILOSOPHY: FAIL SECURE
 * - Default to HIGH RISK (0.70) rather than low risk
 * - Better to have false positives than false negatives
 * - Block high-value transactions
 * - Flag suspicious patterns for manual review
 * - Never auto-approve when ML unavailable
 *
 * PRODUCTION-GRADE IMPLEMENTATION
 * - No external dependencies (always available)
 * - Conservative risk scoring
 * - Multiple fraud indicators
 * - Comprehensive pattern detection
 * - Detailed logging for review
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation (CRITICAL)
 */
@Component
@Slf4j
public class RuleBasedFallbackModel implements IFraudMLModel {

    // High-risk country codes (OFAC, FATF blacklist)
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "IR", "KP", "SY", "CU", "SD", "MM", "BY", "VE", "AF", "YE", "IQ", "LY", "SO", "CF", "ER", "LB"
    );

    // High-risk merchant category codes
    private static final Set<String> HIGH_RISK_MCC = Set.of(
        "5967", // Direct marketing - inbound
        "7995", // Gambling
        "5122", // Drugs
        "7273", // Dating services
        "5912", // Drug stores/pharmacies
        "7841", // Video rental
        "5993"  // Cigar stores/stands
    );

    // Risk thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal MEDIUM_VALUE_THRESHOLD = new BigDecimal("1000");
    private static final int HIGH_VELOCITY_THRESHOLD = 10; // transactions per hour

    /**
     * Predict fraud score using rule-based logic
     *
     * FAIL SECURE: Starts at 0.70 (high risk) and adjusts based on indicators
     */
    @Override
    public double predict(Map<String, Object> features) throws Exception {
        log.warn("USING FALLBACK RULE-BASED MODEL - ML models unavailable");

        // START WITH HIGH RISK (fail secure)
        double riskScore = 0.70;

        // Extract features
        BigDecimal amount = toBigDecimal(features.get("amount"));
        String countryCode = toString(features.get("country_code"));
        Boolean isNewUser = toBoolean(features.get("is_new_user"));
        Boolean isVpn = toBoolean(features.get("location_is_vpn_or_proxy"));
        Boolean isHighRiskCountry = toBoolean(features.get("location_is_high_risk_country"));
        String mcc = toString(features.get("merchant_category_code"));
        Integer hourOfDay = toInteger(features.get("hour_of_day"));
        Integer velocityTxPerHour = toInteger(features.get("velocity_tx_per_hour"));
        Double deviceFraudRate = toDouble(features.get("device_fraud_rate"));
        Integer deviceUsers = toInteger(features.get("device_associated_users"));
        Double customerFraudRate = toDouble(features.get("customer_fraud_rate"));

        // CRITICAL BLOCK: High-value transactions -> BLOCK
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            log.error("BLOCK: High-value transaction: {}", amount);
            return 0.95; // Very high risk - block
        }

        // CRITICAL: Medium-value + new user -> REVIEW
        if (amount.compareTo(MEDIUM_VALUE_THRESHOLD) > 0 && Boolean.TRUE.equals(isNewUser)) {
            log.warn("HIGH RISK: Medium-value transaction from new user: {}", amount);
            riskScore = Math.max(riskScore, 0.85);
        }

        // HIGH RISK: High-risk country
        if (Boolean.TRUE.equals(isHighRiskCountry) || HIGH_RISK_COUNTRIES.contains(countryCode)) {
            log.warn("HIGH RISK: Transaction from high-risk country: {}", countryCode);
            riskScore += 0.15;
        }

        // HIGH RISK: VPN/Proxy usage
        if (Boolean.TRUE.equals(isVpn)) {
            log.warn("HIGH RISK: VPN/Proxy detected");
            riskScore += 0.10;
        }

        // HIGH RISK: High-risk merchant category
        if (HIGH_RISK_MCC.contains(mcc)) {
            log.warn("HIGH RISK: High-risk merchant category: {}", mcc);
            riskScore += 0.10;
        }

        // HIGH RISK: Night-time transactions (00:00 - 06:00)
        if (hourOfDay != null && (hourOfDay >= 0 && hourOfDay <= 6)) {
            log.warn("SUSPICIOUS: Night-time transaction at hour: {}", hourOfDay);
            riskScore += 0.08;
        }

        // CRITICAL: High velocity (rapid transactions)
        if (velocityTxPerHour != null && velocityTxPerHour > HIGH_VELOCITY_THRESHOLD) {
            log.error("CRITICAL: High transaction velocity: {} tx/hour", velocityTxPerHour);
            riskScore += 0.15;
        }

        // HIGH RISK: Device with fraud history
        if (deviceFraudRate != null && deviceFraudRate > 0.1) { // 10% fraud rate
            log.error("HIGH RISK: Device has fraud history: {}% fraud rate",
                deviceFraudRate * 100);
            riskScore += 0.12;
        }

        // SUSPICIOUS: Device farm (many users on one device)
        if (deviceUsers != null && deviceUsers >= 10) {
            log.error("CRITICAL: Device farm detected: {} users", deviceUsers);
            riskScore += 0.20;
        }

        // HIGH RISK: Customer with fraud history
        if (customerFraudRate != null && customerFraudRate > 0.05) { // 5% fraud rate
            log.warn("HIGH RISK: Customer has fraud history: {}% fraud rate",
                customerFraudRate * 100);
            riskScore += 0.10;
        }

        // Cap at 0.95 (leave room for critical alerts)
        riskScore = Math.min(0.95, riskScore);

        log.warn("Fallback model score: {} (CONSERVATIVE - ML unavailable)", riskScore);

        return riskScore;
    }

    /**
     * Model is always ready (no external dependencies)
     */
    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * Get model name
     */
    @Override
    public String getModelName() {
        return "RuleBasedFallback";
    }

    /**
     * Type conversion helpers (null-safe)
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String toString(Object value) {
        return value != null ? value.toString() : "";
    }

    private Boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return Boolean.parseBoolean(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
