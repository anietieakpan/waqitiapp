package com.waqiti.ml.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Transaction Features for ML Model Input
 * 
 * Comprehensive feature vector containing all relevant
 * transaction attributes for fraud detection models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFeatures {
    
    // Basic transaction attributes
    private String transactionId;
    private String userId;
    private String recipientId;

    /** Transaction amount - CRITICAL: Using BigDecimal for financial precision in ML models */
    private BigDecimal amount;

    private String currency;
    private String transactionType;
    private Instant timestamp;

    // User profile features
    private int userAge;
    private String userTier;
    private int accountAgeInDays;

    /** Total transaction volume in last 30 days - BigDecimal for precision */
    private BigDecimal totalTransactionVolume30d;

    private int transactionCount30d;

    /** Average transaction amount - BigDecimal for precision */
    private BigDecimal averageTransactionAmount;
    private boolean isVerifiedUser;
    private int failedLoginAttempts24h;
    
    // Recipient features
    private boolean isNewRecipient;
    private int transactionsWithRecipient;

    /** Total amount sent to this recipient - BigDecimal for precision */
    private BigDecimal totalAmountToRecipient;
    private boolean recipientInContactList;
    private int recipientRiskScore;
    
    // Device and session features
    private String deviceId;
    private String ipAddress;
    private String userAgent;
    private String geolocation;
    private boolean isNewDevice;
    private boolean isNewLocation;
    private int sessionDurationMinutes;
    private boolean isMobileDevice;
    private boolean isVpnDetected;
    
    // Temporal features
    private int hourOfDay;
    private int dayOfWeek;
    private boolean isWeekend;
    private boolean isHoliday;
    private boolean isBusinessHours;
    
    // Velocity features
    private int transactionsLast5min;
    private int transactionsLast15min;
    private int transactionsLast1hour;

    /** Amount transacted in last 5 minutes - BigDecimal for precision */
    private BigDecimal amountLast5min;

    /** Amount transacted in last 15 minutes - BigDecimal for precision */
    private BigDecimal amountLast15min;

    /** Amount transacted in last 1 hour - BigDecimal for precision */
    private BigDecimal amountLast1hour;
    private double velocityScore;
    
    // Behavioral features
    private double typingPattern;
    private double mouseMovementPattern;
    private double sessionInteractionScore;
    private boolean isRushBehavior;
    private double behaviorConsistencyScore;
    
    // Network analysis features
    private int networkConnectionDegree;
    private double networkClusteringCoefficient;
    private boolean inSuspiciousCluster;
    private double networkRiskScore;
    private int mutualConnections;
    
    // Business rule features
    private boolean exceedsLimits;
    private boolean crossBorderTransaction;
    private boolean highRiskCountry;
    private boolean sanctionedEntity;
    private double complianceScore;
    
    // Historical patterns
    private double deviationFromPattern;
    private boolean unusualAmount;
    private boolean unusualTime;
    private boolean unusualRecipient;
    private double historicalSimilarity;
    
    // External data features
    private double creditScore;
    private boolean blacklistMatch;
    private boolean whitelistMatch;
    private double socialScore;
    private boolean deviceCompromised;
    
    // Additional metadata
    private Map<String, Object> customFeatures;
    private String featureVersion;
    private Instant featuresGeneratedAt;
    
    /**
     * Converts features to double array for ML model input
     */
    public double[] toDoubleArray() {
        return new double[] {
            amount,
            userAge,
            accountAgeInDays,
            totalTransactionVolume30d,
            transactionCount30d,
            averageTransactionAmount,
            isVerifiedUser ? 1.0 : 0.0,
            failedLoginAttempts24h,
            isNewRecipient ? 1.0 : 0.0,
            transactionsWithRecipient,
            totalAmountToRecipient,
            recipientInContactList ? 1.0 : 0.0,
            recipientRiskScore,
            isNewDevice ? 1.0 : 0.0,
            isNewLocation ? 1.0 : 0.0,
            sessionDurationMinutes,
            isMobileDevice ? 1.0 : 0.0,
            isVpnDetected ? 1.0 : 0.0,
            hourOfDay,
            dayOfWeek,
            isWeekend ? 1.0 : 0.0,
            isHoliday ? 1.0 : 0.0,
            isBusinessHours ? 1.0 : 0.0,
            transactionsLast5min,
            transactionsLast15min,
            transactionsLast1hour,
            amountLast5min,
            amountLast15min,
            amountLast1hour,
            velocityScore,
            typingPattern,
            mouseMovementPattern,
            sessionInteractionScore,
            isRushBehavior ? 1.0 : 0.0,
            behaviorConsistencyScore,
            networkConnectionDegree,
            networkClusteringCoefficient,
            inSuspiciousCluster ? 1.0 : 0.0,
            networkRiskScore,
            mutualConnections,
            exceedsLimits ? 1.0 : 0.0,
            crossBorderTransaction ? 1.0 : 0.0,
            highRiskCountry ? 1.0 : 0.0,
            sanctionedEntity ? 1.0 : 0.0,
            complianceScore,
            deviationFromPattern,
            unusualAmount ? 1.0 : 0.0,
            unusualTime ? 1.0 : 0.0,
            unusualRecipient ? 1.0 : 0.0,
            historicalSimilarity,
            creditScore,
            blacklistMatch ? 1.0 : 0.0,
            whitelistMatch ? 1.0 : 0.0,
            socialScore,
            deviceCompromised ? 1.0 : 0.0
        };
    }
    
    /**
     * Gets feature names for model interpretation
     */
    public static String[] getFeatureNames() {
        return new String[] {
            "amount", "userAge", "accountAgeInDays", "totalTransactionVolume30d",
            "transactionCount30d", "averageTransactionAmount", "isVerifiedUser",
            "failedLoginAttempts24h", "isNewRecipient", "transactionsWithRecipient",
            "totalAmountToRecipient", "recipientInContactList", "recipientRiskScore",
            "isNewDevice", "isNewLocation", "sessionDurationMinutes", "isMobileDevice",
            "isVpnDetected", "hourOfDay", "dayOfWeek", "isWeekend", "isHoliday",
            "isBusinessHours", "transactionsLast5min", "transactionsLast15min",
            "transactionsLast1hour", "amountLast5min", "amountLast15min",
            "amountLast1hour", "velocityScore", "typingPattern", "mouseMovementPattern",
            "sessionInteractionScore", "isRushBehavior", "behaviorConsistencyScore",
            "networkConnectionDegree", "networkClusteringCoefficient",
            "inSuspiciousCluster", "networkRiskScore", "mutualConnections",
            "exceedsLimits", "crossBorderTransaction", "highRiskCountry",
            "sanctionedEntity", "complianceScore", "deviationFromPattern",
            "unusualAmount", "unusualTime", "unusualRecipient", "historicalSimilarity",
            "creditScore", "blacklistMatch", "whitelistMatch", "socialScore",
            "deviceCompromised"
        };
    }
}