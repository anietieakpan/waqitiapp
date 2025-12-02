package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Request DTO for transaction risk assessment
 * Contains all necessary data for comprehensive risk analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRiskRequest {

    @NotNull(message = "Transaction ID is required")
    private String transactionId;

    @NotNull(message = "User ID is required")
    private String userId;

    private String merchantId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    @NotNull(message = "Transaction type is required")
    private String transactionType; // PAYMENT, TRANSFER, WITHDRAWAL, DEPOSIT

    private String deviceId;
    private String deviceFingerprint;
    private String ipAddress;
    private String userAgent;

    private String geoLocation; // Lat,Long format
    private String country;
    private String city;

    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    // Channel information
    private String channel; // WEB, MOBILE, API, ATM

    // Session information
    private String sessionId;
    private Integer sessionDuration; // seconds

    // Merchant information
    private String merchantCategory;
    private String merchantCountry;

    // Account information
    private String accountId;
    private String accountType;
    private BigDecimal accountBalance;

    // Transaction metadata
    private Map<String, Object> metadata;

    // Behavioral context
    private Integer typingSpeed; // characters per second
    private Integer mouseMovements;
    private Boolean biometricAuthenticated;

    // Risk hints from upstream
    private Double upstreamRiskScore;
    private String upstreamRiskReason;
}
