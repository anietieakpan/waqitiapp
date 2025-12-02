package com.waqiti.payment.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ML Feature Vector for Fraud Detection
 *
 * Contains 100+ engineered features for production ML model inference.
 * Features are grouped by category for maintainability.
 *
 * Feature Categories:
 * - Transaction Features (20+)
 * - User Behavior Features (30+)
 * - Device Features (15+)
 * - Network Features (15+)
 * - Temporal Features (10+)
 * - Merchant Features (10+)
 * - Derived Features (20+)
 *
 * @author Waqiti ML Engineering Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLFeatureVector {

    // ========== TRANSACTION FEATURES (20+) ==========
    private Double transactionAmount;
    private Double transactionAmountLog;
    private Double transactionAmountSquared;
    private Double transactionAmountSqrt;
    private Boolean isRoundAmount;
    private String transactionCurrency;
    private Boolean isForeignCurrency;
    private String transactionType;
    private Boolean isP2P;
    private Boolean isMerchantPayment;
    private Boolean isWithdrawal;
    private Integer failedAttempts;
    private Boolean hasFailedAttempts;
    private Double failedAttemptsLog;
    private Boolean isKnownDevice;
    private Boolean isTrustedLocation;

    // ========== USER BEHAVIOR FEATURES (30+) ==========
    private Integer userTransactionCount30d;
    private Integer userTransactionCount7d;
    private Integer userTransactionCount24h;
    private Integer userTransactionCount1h;
    private Double userVelocity1h;
    private Double userVelocity24h;
    private Double userVelocity7d;
    private Double userAvgAmount30d;
    private Double userAvgAmount7d;
    private Double userStdDevAmount30d;
    private Double userMaxAmount30d;
    private Double amountDeviationFromAvg;
    private Boolean isAmountAnomaly;
    private Long minutesSinceLastTransaction;
    private Double hoursSinceLastTransaction;
    private Boolean isFirstTransaction;
    private Integer userHighRiskCount30d;
    private Integer userBlockedCount30d;
    private Boolean hasRecentHighRisk;

    // ========== DEVICE FEATURES (15+) ==========
    private Long deviceAgeHours;
    private Double deviceAgeDays;
    private Boolean isNewDevice;
    private Integer deviceTransactionCount;
    private Integer deviceUserCount;
    private Boolean isSharedDevice;
    private Integer deviceBlockedCount;
    private Double deviceRiskScore;
    private Boolean isUserPrimaryDevice;

    // ========== NETWORK FEATURES (15+) ==========
    private Integer ipTransactionCount24h;
    private Boolean isHighVelocityIP;
    private Integer ipUserCount;
    private Boolean isSharedIP;
    private Boolean isIPBlacklisted;
    private String ipCountry;
    private String ipCity;
    private Double ipLatitude;
    private Double ipLongitude;
    private Boolean isVPN;
    private Boolean isTor;
    private Boolean isProxy;
    private Integer ipBlockedCount;

    // ========== TEMPORAL FEATURES (10+) ==========
    private Integer hour;
    private Integer minute;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private Integer monthOfYear;
    private Boolean isWeekend;
    private Boolean isBusinessHours;
    private Boolean isOffHours;
    private Boolean isLateNight;
    private Double hourSin;
    private Double hourCos;
    private Double dayOfWeekSin;
    private Double dayOfWeekCos;

    // ========== MERCHANT FEATURES (10+) ==========
    private Integer merchantTransactionCount30d;
    private Double merchantFraudRate;
    private Boolean isHighRiskMerchant;
    private String merchantCategory;
    private Boolean isHighRiskMerchantCategory;

    // ========== DERIVED/ENGINEERED FEATURES (20+) ==========
    private Double amountVelocityRatio;
    private Double deviceDiversityScore;
    private Integer ipRiskFactorCount;
    private Double ipCompositeRiskScore;
    private Integer anomalyFactorCount;
    private Double transactionAnomalyScore;

    // ========== METADATA ==========
    private Long extractionDurationMs;
    private Boolean extractionError;
    private String extractionErrorMessage;

    /**
     * Get total feature count (for validation)
     */
    public int getFeatureCount() {
        int count = 0;

        // Count non-null numeric features
        if (transactionAmount != null) count++;
        if (transactionAmountLog != null) count++;
        if (transactionAmountSquared != null) count++;
        if (transactionAmountSqrt != null) count++;
        if (failedAttempts != null) count++;
        if (failedAttemptsLog != null) count++;

        if (userTransactionCount30d != null) count++;
        if (userTransactionCount7d != null) count++;
        if (userTransactionCount24h != null) count++;
        if (userTransactionCount1h != null) count++;
        if (userVelocity1h != null) count++;
        if (userVelocity24h != null) count++;
        if (userVelocity7d != null) count++;
        if (userAvgAmount30d != null) count++;
        if (userAvgAmount7d != null) count++;
        if (userStdDevAmount30d != null) count++;
        if (userMaxAmount30d != null) count++;
        if (amountDeviationFromAvg != null) count++;
        if (minutesSinceLastTransaction != null) count++;
        if (hoursSinceLastTransaction != null) count++;
        if (userHighRiskCount30d != null) count++;
        if (userBlockedCount30d != null) count++;

        if (deviceAgeHours != null) count++;
        if (deviceAgeDays != null) count++;
        if (deviceTransactionCount != null) count++;
        if (deviceUserCount != null) count++;
        if (deviceBlockedCount != null) count++;
        if (deviceRiskScore != null) count++;

        if (ipTransactionCount24h != null) count++;
        if (ipUserCount != null) count++;
        if (ipLatitude != null) count++;
        if (ipLongitude != null) count++;
        if (ipBlockedCount != null) count++;

        if (hour != null) count++;
        if (minute != null) count++;
        if (dayOfWeek != null) count++;
        if (dayOfMonth != null) count++;
        if (monthOfYear != null) count++;
        if (hourSin != null) count++;
        if (hourCos != null) count++;
        if (dayOfWeekSin != null) count++;
        if (dayOfWeekCos != null) count++;

        if (merchantTransactionCount30d != null) count++;
        if (merchantFraudRate != null) count++;

        if (amountVelocityRatio != null) count++;
        if (deviceDiversityScore != null) count++;
        if (ipRiskFactorCount != null) count++;
        if (ipCompositeRiskScore != null) count++;
        if (anomalyFactorCount != null) count++;
        if (transactionAnomalyScore != null) count++;

        // Count boolean features
        if (isRoundAmount != null) count++;
        if (isForeignCurrency != null) count++;
        if (isP2P != null) count++;
        if (isMerchantPayment != null) count++;
        if (isWithdrawal != null) count++;
        if (hasFailedAttempts != null) count++;
        if (isKnownDevice != null) count++;
        if (isTrustedLocation != null) count++;

        if (isAmountAnomaly != null) count++;
        if (isFirstTransaction != null) count++;
        if (hasRecentHighRisk != null) count++;

        if (isNewDevice != null) count++;
        if (isSharedDevice != null) count++;
        if (isUserPrimaryDevice != null) count++;

        if (isHighVelocityIP != null) count++;
        if (isSharedIP != null) count++;
        if (isIPBlacklisted != null) count++;
        if (isVPN != null) count++;
        if (isTor != null) count++;
        if (isProxy != null) count++;

        if (isWeekend != null) count++;
        if (isBusinessHours != null) count++;
        if (isOffHours != null) count++;
        if (isLateNight != null) count++;

        if (isHighRiskMerchant != null) count++;
        if (isHighRiskMerchantCategory != null) count++;

        return count;
    }
}
