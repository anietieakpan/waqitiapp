package com.waqiti.common.fraud.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a transaction event for fraud detection analysis.
 * Contains comprehensive transaction details, metadata, and contextual information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    
    /**
     * Unique transaction identifier
     */
    private String transactionId;
    
    /**
     * User identifier
     */
    private String userId;
    
    /**
     * Merchant identifier
     */
    private String merchantId;
    
    /**
     * Transaction amount - Using BigDecimal for fraud detection precision
     */
    private BigDecimal amount;
    
    /**
     * Currency code (ISO 4217)
     */
    private String currency;
    
    /**
     * Transaction type
     */
    private TransactionType transactionType;
    
    /**
     * Payment method used
     */
    private PaymentMethod paymentMethod;
    
    /**
     * Transaction channel
     */
    private TransactionChannel channel;
    
    /**
     * Transaction status
     */
    private TransactionStatus status;
    
    /**
     * Transaction timestamp
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Geographic information
     */
    private GeographicInfo geographicInfo;
    
    /**
     * Device and session information
     */
    private DeviceSessionInfo deviceSessionInfo;
    
    /**
     * Payment card information (if applicable)
     */
    private PaymentCardInfo paymentCardInfo;
    
    /**
     * Authentication information
     */
    private AuthenticationInfo authenticationInfo;
    
    /**
     * Risk scores and flags
     */
    private RiskIndicators riskIndicators;
    
    /**
     * Additional transaction metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Merchant category code
     */
    private String merchantCategoryCode;
    
    /**
     * Authorization code
     */
    private String authorizationCode;
    
    /**
     * Reference number
     */
    private String referenceNumber;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * External transaction ID (from payment processor)
     */
    private String externalTransactionId;
    
    /**
     * Check if transaction is successful
     */
    public boolean isSuccessful() {
        return status == TransactionStatus.APPROVED || status == TransactionStatus.COMPLETED;
    }
    
    /**
     * Check if transaction is declined
     */
    public boolean isDeclined() {
        return status == TransactionStatus.DECLINED || status == TransactionStatus.REJECTED;
    }
    
    /**
     * Check if transaction is pending
     */
    public boolean isPending() {
        return status == TransactionStatus.PENDING || status == TransactionStatus.PROCESSING;
    }
    
    /**
     * Check if transaction is high value
     */
    public boolean isHighValue() {
        return amount.compareTo(new BigDecimal("10000.0")) >= 0; // Default threshold, should be configurable
    }
    
    /**
     * Check if transaction is cross-border
     */
    public boolean isCrossBorder() {
        if (geographicInfo == null) {
            return false;
        }
        return geographicInfo.isCrossBorder();
    }
    
    /**
     * Check if card is present
     */
    public boolean isCardPresent() {
        return paymentCardInfo != null && paymentCardInfo.isCardPresent();
    }
    
    /**
     * Get risk level based on indicators
     */
    public RiskLevel getRiskLevel() {
        if (riskIndicators == null) {
            return RiskLevel.UNKNOWN;
        }
        return riskIndicators.getOverallRiskLevel();
    }
    
    /**
     * Create transaction summary
     */
    public String createSummary() {
        return String.format("Transaction[%s]: %s %.2f %s via %s - Status: %s, Risk: %s",
            transactionId, transactionType, amount, currency, 
            paymentMethod, status, getRiskLevel());
    }
    
    /**
     * Check if authentication was successful
     */
    public boolean isAuthenticationSuccessful() {
        return authenticationInfo != null && authenticationInfo.isSuccessful();
    }
    
    /**
     * Get transaction age in seconds
     */
    public long getAgeInSeconds() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).getSeconds();
    }

    // Supporting enums and classes
    
    public enum TransactionType {
        PURCHASE, WITHDRAWAL, TRANSFER, REFUND, REVERSAL, 
        AUTHORIZATION, PREAUTHORIZATION, CAPTURE, VOID,
        CHARGEBACK, ADJUSTMENT, FEE, DEPOSIT
    }
    
    public enum PaymentMethod {
        CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, DIGITAL_WALLET,
        CRYPTOCURRENCY, CHECK, CASH, GIFT_CARD, PREPAID_CARD,
        MOBILE_PAYMENT, CONTACTLESS, CHIP_AND_PIN
    }
    
    public enum TransactionChannel {
        ONLINE, MOBILE_APP, ATM, POS_TERMINAL, PHONE, 
        BRANCH, MAIL_ORDER, KIOSK, API, BATCH
    }
    
    public enum TransactionStatus {
        INITIATED, PROCESSING, PENDING, APPROVED, COMPLETED,
        DECLINED, REJECTED, CANCELLED, EXPIRED, FAILED,
        REVERSED, DISPUTED, UNDER_REVIEW
    }
    
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicInfo {
        private String country;
        private String region;
        private String city;
        private String postalCode;
        private double latitude;
        private double longitude;
        private String ipAddress;
        private String userCountry;
        private String merchantCountry;
        private double distanceFromUserKm;
        private boolean isHighRiskLocation;
        
        public boolean isCrossBorder() {
            return userCountry != null && merchantCountry != null && 
                   !userCountry.equals(merchantCountry);
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSessionInfo {
        private String deviceId;
        private String deviceType;
        private String operatingSystem;
        private String osVersion;
        private String appVersion;
        private String browser;
        private String userAgent;
        private String sessionId;
        private boolean isNewDevice;
        private boolean isTrustedDevice;
        private String fingerprint;
        private Map<String, String> deviceAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentCardInfo {
        private String cardNumber; // Masked/tokenized
        private String cardType;
        private String issuerBank;
        private String issuerCountry;
        private boolean isCardPresent;
        private String entryMode;
        private String cvvResult;
        private String avsResult;
        private boolean isNewCard;
        private int daysOnFile;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticationInfo {
        private boolean successful;
        private String authenticationMethod;
        private String authenticationResult;
        private boolean mfaUsed;
        private String mfaMethod;
        private int authenticationAttempts;
        private LocalDateTime lastAuthenticationTime;
        private Map<String, Object> authenticationMetadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskIndicators {
        private double velocityScore;
        private double amountScore;
        private double geographicScore;
        private double deviceScore;
        private double behaviorScore;
        private double overallRiskScore;
        private RiskLevel overallRiskLevel;
        private boolean hasAnomalies;
        private java.util.List<String> riskFlags;
        private Map<String, Double> additionalScores;
        
        public boolean isHighRisk() {
            return overallRiskLevel == RiskLevel.HIGH || overallRiskLevel == RiskLevel.CRITICAL;
        }
    }

    /**
     * Convenience getters for device and location info
     */
    public String getDeviceId() {
        return deviceSessionInfo != null ? deviceSessionInfo.getDeviceId() : null;
    }

    public String getDeviceType() {
        return deviceSessionInfo != null ? deviceSessionInfo.getDeviceType() : null;
    }

    public String getOsVersion() {
        return deviceSessionInfo != null ? deviceSessionInfo.getOsVersion() : null;
    }

    public String getAppVersion() {
        return deviceSessionInfo != null ? deviceSessionInfo.getAppVersion() : null;
    }

    public com.waqiti.common.fraud.model.Location getLocation() {
        if (geographicInfo != null) {
            return com.waqiti.common.fraud.model.Location.builder()
                .country(geographicInfo.getCountry())
                .region(geographicInfo.getRegion())
                .city(geographicInfo.getCity())
                .latitude(geographicInfo.getLatitude())
                .longitude(geographicInfo.getLongitude())
                .build();
        }
        return null;
    }
}