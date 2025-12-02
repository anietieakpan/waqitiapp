package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Feature vector for ML model input
 * Normalized and engineered features for risk prediction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureVector {

    private String transactionId;
    private String userId;

    // Numerical features (normalized 0-1)
    private Double amountNormalized;
    private Double accountAgeInDays;
    private Double transactionFrequency;
    private Double averageTransactionAmount;
    private Double velocityLast24h;
    private Double velocityLast7d;
    private Double velocityLast30d;

    // Categorical features (one-hot encoded)
    private Map<String, Integer> transactionTypeEncoded;
    private Map<String, Integer> channelEncoded;
    private Map<String, Integer> deviceTypeEncoded;
    private Map<String, Integer> countryEncoded;

    // Time-based features
    private Integer hourOfDay;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private Boolean isWeekend;
    private Boolean isBusinessHours;

    // Behavioral features
    private Double typingSpeedDeviation;
    private Double mouseMovementDeviation;
    private Double sessionDurationDeviation;
    private Boolean unusualTimePattern;
    private Boolean unusualAmountPattern;

    // Device features
    private Boolean newDevice;
    private Boolean trustedDevice;
    private Integer deviceRiskScore;
    private Boolean vpnDetected;
    private Boolean proxyDetected;

    // Geographic features
    private Boolean geoLocationMismatch;
    private Double distanceFromLastTransaction;
    private Boolean highRiskCountry;
    private Boolean countryMismatch;

    // Network features
    private Boolean ipReputationBad;
    private Integer ipRiskScore;
    private Boolean botDetected;

    // Account features
    private Double accountBalanceRatio; // transaction amount / balance
    private Boolean firstTransaction;
    private Integer failedTransactionCount;
    private Double chargebackRatio;

    // All features as array (for ML model input)
    private List<Double> featureArray;
    private List<String> featureNames;

    // Feature statistics
    private Double featureMean;
    private Double featureStdDev;
    private Integer totalFeatures;
    private Integer missingFeatures;

    private Map<String, Object> rawFeatures; // Original non-normalized values
}
