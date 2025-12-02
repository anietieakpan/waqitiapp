package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for comprehensive fraud assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudAssessmentRequest {

    @NotNull
    @Size(min = 1, max = 255)
    private String transactionId;
    
    @NotNull
    @Size(min = 1, max = 255)
    private String payerId;
    
    @Size(max = 255)
    private String payeeId;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    
    @NotNull
    @Size(min = 3, max = 3)
    private String currency;
    
    @NotNull
    @Size(min = 1, max = 100)
    private String paymentType;
    
    @NotNull
    private LocalDateTime timestamp;
    
    // Device information
    private String deviceId;
    private String deviceFingerprint;
    private String deviceType;
    private String deviceOs;
    private String deviceBrowser;
    private String deviceModel;
    private boolean deviceTrusted;
    
    // Network information
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private boolean vpnDetected;
    private boolean proxyDetected;
    
    // Geographic information
    private String country;
    private String city;
    private String region;
    private Double latitude;
    private Double longitude;
    private String timezone;
    
    // Account information
    private LocalDateTime accountCreatedAt;
    private Integer accountAge;
    private String accountStatus;
    private String kycStatus;
    private Integer previousTransactionCount;
    private BigDecimal accountBalance;
    
    // Transaction context
    private String merchantId;
    private String merchantCategory;
    private String paymentMethod;
    private String paymentProvider;
    private String channelType; // WEB, MOBILE, API, NFC
    
    // Behavioral data
    private Integer transactionCount24h;
    private BigDecimal transactionAmount24h;
    private Integer failedAttempts24h;
    private LocalDateTime lastTransactionAt;
    private String usualTransactionPattern;
    
    // Security indicators
    private boolean multiFactorAuthenticated;
    private String authenticationMethod;
    private boolean biometricAuthenticated;
    private Integer loginAttempts;
    private LocalDateTime lastLoginAt;
    
    // Additional context
    private String referenceTransactionId;
    private String customerRiskProfile;
    private Map<String, Object> customAttributes;
    private Map<String, String> metadata;
    
    // Velocity check parameters
    private boolean checkVelocity;
    private boolean checkDeviceHistory;
    private boolean checkLocationHistory;
    private boolean checkBehaviorPattern;
    
    // ML model preferences
    private String preferredModel;
    private boolean enableMLScoring;
    private boolean enableRuleEngine;
    
    /**
     * Creates a basic fraud assessment request
     */
    public static FraudAssessmentRequest basic(String transactionId, String payerId, String payeeId,
                                             BigDecimal amount, String currency, String paymentType) {
        return FraudAssessmentRequest.builder()
                .transactionId(transactionId)
                .payerId(payerId)
                .payeeId(payeeId)
                .amount(amount)
                .currency(currency)
                .paymentType(paymentType)
                .timestamp(LocalDateTime.now())
                .checkVelocity(true)
                .enableMLScoring(true)
                .enableRuleEngine(true)
                .build();
    }
    
    /**
     * Creates an NFC payment fraud assessment request
     */
    public static FraudAssessmentRequest nfcPayment(String transactionId, String customerId, String merchantId,
                                                   BigDecimal amount, String currency, String deviceId, String ipAddress) {
        return FraudAssessmentRequest.builder()
                .transactionId(transactionId)
                .payerId(customerId)
                .payeeId(merchantId)
                .amount(amount)
                .currency(currency)
                .paymentType("NFC_PAYMENT")
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .channelType("NFC")
                .timestamp(LocalDateTime.now())
                .checkVelocity(true)
                .checkDeviceHistory(true)
                .checkLocationHistory(true)
                .enableMLScoring(true)
                .enableRuleEngine(true)
                .build();
    }
    
    /**
     * Creates a P2P transfer fraud assessment request
     */
    public static FraudAssessmentRequest p2pTransfer(String transactionId, String senderId, String recipientId,
                                                    BigDecimal amount, String currency, String deviceId) {
        return FraudAssessmentRequest.builder()
                .transactionId(transactionId)
                .payerId(senderId)
                .payeeId(recipientId)
                .amount(amount)
                .currency(currency)
                .paymentType("P2P_TRANSFER")
                .deviceId(deviceId)
                .channelType("MOBILE")
                .timestamp(LocalDateTime.now())
                .checkVelocity(true)
                .checkBehaviorPattern(true)
                .enableMLScoring(true)
                .enableRuleEngine(true)
                .build();
    }
}