package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for fraud detection checks.
 * Contains comprehensive transaction details for fraud analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    @NotNull(message = "Transaction type is required")
    private String transactionType;

    private UUID recipientId;
    private String recipientAccountNumber;
    private String recipientBankCode;

    @NotNull(message = "Transaction timestamp is required")
    private LocalDateTime transactionTimestamp;

    // Device and location information
    private String deviceId;
    private String deviceFingerprint;
    private String ipAddress;
    private String userAgent;
    private Double latitude;
    private Double longitude;
    private String country;
    private String city;

    // Behavioral indicators
    private Integer recentTransactionCount;
    private BigDecimal dailyTransactionVolume;
    private Boolean isFirstTransaction;
    private Boolean isInternational;
    private Integer failedAttempts;

    // Payment method details
    private String paymentMethod;
    private String cardLast4Digits;
    private String cardBrand;
    private Boolean isNewPaymentMethod;

    // Risk indicators
    private String merchantCategory;
    private String merchantName;
    private Boolean isHighRiskMerchant;
    private Double previousRiskScore;

    // Session information
    private String sessionId;
    private Integer sessionDuration;
    private LocalDateTime lastLoginTime;

    // Additional metadata for ML models
    private Map<String, Object> additionalMetadata;

    // Velocity checks
    private Integer transactionsInLastHour;
    private Integer transactionsInLastDay;
    private BigDecimal amountInLastHour;
    private BigDecimal amountInLastDay;

    // Network analysis
    private Integer accountAge; // in days
    private Integer linkedAccounts;
    private Boolean hasKycVerification;
    private String kycLevel;

    // Transaction pattern
    private BigDecimal averageTransactionAmount;
    private String typicalTransactionTime;
    private Boolean isUnusualAmount;
    private Boolean isUnusualTime;

    // Channel information
    private String channel; // WEB, MOBILE, API, ATM
    private String applicationVersion;
    private String platformType; // iOS, Android, Web

    /**
     * Creates a minimal fraud check request with required fields only.
     */
    public static FraudCheckRequest minimal(UUID transactionId, UUID userId, 
                                           BigDecimal amount, String currency) {
        return FraudCheckRequest.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .transactionType("TRANSFER")
                .transactionTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Validates if the request has sufficient data for fraud checking.
     */
    public boolean hasMinimalData() {
        return transactionId != null && 
               userId != null && 
               amount != null && 
               currency != null && 
               transactionType != null;
    }

    /**
     * Calculates a simple risk indicator based on available data.
     */
    public int getBasicRiskIndicator() {
        int riskScore = 0;
        
        if (isInternational != null && isInternational) riskScore += 20;
        if (isFirstTransaction != null && isFirstTransaction) riskScore += 15;
        if (isHighRiskMerchant != null && isHighRiskMerchant) riskScore += 25;
        if (failedAttempts != null && failedAttempts > 2) riskScore += 30;
        if (isUnusualAmount != null && isUnusualAmount) riskScore += 20;
        if (isUnusualTime != null && isUnusualTime) riskScore += 15;
        if (hasKycVerification != null && !hasKycVerification) riskScore += 25;
        
        return Math.min(riskScore, 100);
    }
}