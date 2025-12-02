package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * General transaction fraud evaluation request DTO
 * For any transaction type fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sensitiveData", "authenticationData"})
public class TransactionFraudRequest {
    
    @NotNull
    private UUID transactionId;
    
    @NotNull
    private UUID userId;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotNull
    @Size(min = 3, max = 3)
    private String currency;
    
    @NotNull
    private TransactionType transactionType;
    
    private TransactionCategory category;
    
    @NotNull
    private LocalDateTime timestamp;
    
    private String description;
    
    // Transaction context
    private TransactionContext context;
    
    // User profile data
    private UserContext userContext;
    
    // Merchant/recipient data  
    private MerchantContext merchantContext;
    
    // Payment method details
    private PaymentMethodContext paymentMethodContext;
    
    // Geographic context
    private GeographicContext geographicContext;
    
    // Device and session context
    private DeviceSessionContext deviceSessionContext;
    
    // Behavioral context
    private BehavioralContext behavioralContext;
    
    // Risk indicators
    private Map<String, Object> riskIndicators;
    
    // Sensitive data (encrypted/masked)
    private Map<String, String> sensitiveData;
    
    // Authentication context
    private Map<String, Object> authenticationData;
    
    public enum TransactionType {
        PURCHASE,
        TRANSFER,
        WITHDRAWAL,
        DEPOSIT,
        REFUND,
        CHARGEBACK,
        SUBSCRIPTION,
        BILL_PAYMENT,
        P2P,
        B2B,
        B2C,
        CRYPTO,
        OTHER
    }
    
    public enum TransactionCategory {
        RETAIL,
        DIGITAL_GOODS,
        SERVICES,
        TRAVEL,
        GAMBLING,
        FINANCIAL_SERVICES,
        HEALTHCARE,
        EDUCATION,
        CHARITY,
        GOVERNMENT,
        UTILITIES,
        TELECOM,
        OTHER
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionContext {
        private String channel; // WEB, MOBILE, API, POS
        private String platform;
        private String applicationVersion;
        private String referenceNumber;
        private boolean isRecurring;
        private boolean isFirstTransaction;
        private String initiationMethod; // USER, SYSTEM, SCHEDULED
        private LocalDateTime scheduledTime;
        private Map<String, Object> customFields;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserContext {
        private LocalDateTime accountCreatedAt;
        private LocalDateTime lastLoginAt;
        private Integer totalTransactions;
        private BigDecimal totalVolume;
        private String accountStatus;
        private String verificationLevel;
        private boolean hasFailedTransactions;
        private Integer failedTransactionCount;
        private LocalDateTime lastFailedTransaction;
        private String riskProfile;
        private Map<String, Object> userAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantContext {
        private String merchantId;
        private String merchantName;
        private String merchantCategory;
        private String merchantCountry;
        private String merchantRiskRating;
        private boolean isNewMerchant;
        private boolean isHighRiskMerchant;
        private boolean isBlacklisted;
        private BigDecimal merchantVolume;
        private Integer merchantTransactionCount;
        private Map<String, Object> merchantAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodContext {
        private String paymentMethodType; // CARD, BANK, WALLET, CRYPTO
        private String paymentMethodId;
        private boolean isNewPaymentMethod;
        private LocalDateTime paymentMethodCreatedAt;
        private LocalDateTime lastUsedAt;
        private Integer usageCount;
        private boolean isVerified;
        private String issuerCountry;
        private String issuerBank;
        private String fundingSource;
        private Map<String, Object> paymentMethodAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicContext {
        private String ipAddress;
        private String country;
        private String city;
        private String region;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private boolean isVpnDetected;
        private boolean isTorDetected;
        private boolean isProxyDetected;
        private String ipReputationScore;
        private boolean isUnusualLocation;
        private Double distanceFromUsual;
        private Map<String, Object> locationAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSessionContext {
        private String deviceId;
        private String deviceFingerprint;
        private String userAgent;
        private String browserType;
        private String browserVersion;
        private String operatingSystem;
        private String deviceType; // MOBILE, DESKTOP, TABLET
        private boolean isNewDevice;
        private boolean isCompromisedDevice;
        private String sessionId;
        private LocalDateTime sessionStartTime;
        private Integer sessionDuration;
        private Map<String, Object> deviceAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralContext {
        private String navigationPattern;
        private Integer pageViews;
        private Integer timeOnSite;
        private boolean hasTypingPatternAnalomies;
        private boolean hasMousePatternAnomalies;
        private String interactionRisk;
        private LocalDateTime lastActivity;
        private Integer activityScore;
        private String behavioralProfile;
        private Map<String, Object> behavioralMetrics;
    }
    
    // Business logic methods
    public boolean isHighValue() {
        return amount.compareTo(new BigDecimal("5000")) > 0;
    }
    
    public boolean isInternational() {
        return geographicContext != null && 
               merchantContext != null &&
               !geographicContext.getCountry().equals(merchantContext.getMerchantCountry());
    }
    
    public boolean isFirstTimeUser() {
        return userContext != null && 
               (userContext.getTotalTransactions() == null || userContext.getTotalTransactions() <= 1);
    }
    
    public boolean isHighRiskMerchant() {
        return merchantContext != null && 
               (merchantContext.isHighRiskMerchant() || merchantContext.isBlacklisted());
    }
    
    public boolean hasGeographicRisks() {
        return geographicContext != null &&
               (geographicContext.isVpnDetected() || 
                geographicContext.isTorDetected() ||
                geographicContext.isUnusualLocation());
    }
    
    public boolean hasDeviceRisks() {
        return deviceSessionContext != null &&
               (deviceSessionContext.isNewDevice() || 
                deviceSessionContext.isCompromisedDevice());
    }
    
    public boolean hasBehavioralRisks() {
        return behavioralContext != null &&
               (behavioralContext.hasTypingPatternAnalomies() ||
                behavioralContext.hasMousePatternAnomalies() ||
                "HIGH".equals(behavioralContext.getInteractionRisk()));
    }
    
    public boolean requiresEnhancedVerification() {
        return isHighValue() || 
               isInternational() || 
               isFirstTimeUser() ||
               isHighRiskMerchant() ||
               hasGeographicRisks() ||
               hasDeviceRisks() ||
               hasBehavioralRisks();
    }
}