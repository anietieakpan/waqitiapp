package com.waqiti.ml.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionModel {
    
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private Instant timestamp;
    
    // User profile features
    private Integer userAge;
    private String userLocation;
    private String accountType;
    private Integer daysSinceRegistration;
    private BigDecimal averageTransactionAmount;
    private Integer transactionCountLast24h;
    private Integer transactionCountLast7d;
    private Integer transactionCountLast30d;
    
    // Transaction features
    private Integer hourOfDay;
    private Integer dayOfWeek;
    private Boolean isWeekend;
    private Boolean isHoliday;
    private BigDecimal amountDeviationFromAverage;
    private Boolean isRoundAmount;
    private Integer transactionVelocity;
    
    // Device and location features
    private String deviceId;
    private String ipAddress;
    private String browserFingerprint;
    private Boolean isNewDevice;
    private Boolean isVpn;
    private Boolean isProxy;
    private Boolean isTor;
    private String geoLocation;
    private Double distanceFromUsualLocation;
    private Boolean isLocationChange;
    
    // Network features
    private String merchantCategory;
    private Boolean isHighRiskMerchant;
    private Boolean isInternationalTransaction;
    private String paymentMethod;
    private Boolean isCardPresent;
    
    // Behavioral features
    private Double behavioralScore;
    private Boolean isTypingPatternAnomaly;
    private Boolean isClickPatternAnomaly;
    private Integer sessionDuration;
    private Boolean isRushTransaction;
    
    // Historical features
    private Integer failedAttemptsLast24h;
    private Boolean hasRecentChargebacks;
    private Boolean hasRecentDisputes;
    private Double historicalFraudRate;
    private Boolean isFirstTimeUser;
    
    // External data features
    private Boolean isOnWatchlist;
    private Boolean isPep;
    private Boolean isSanctioned;
    private Integer externalRiskScore;
    
    // Model outputs
    private Double fraudProbability;
    private String riskLevel;
    private String recommendation;
    private Map<String, Object> featureImportance;
    private String modelVersion;
}