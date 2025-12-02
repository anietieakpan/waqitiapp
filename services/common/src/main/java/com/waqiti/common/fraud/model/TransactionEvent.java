package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Transaction event for real-time fraud monitoring
 * Contains comprehensive transaction data for fraud analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("merchant_category")
    private String merchantCategory;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("transaction_type")
    private TransactionType transactionType;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("original_amount")
    private BigDecimal originalAmount;

    @JsonProperty("original_currency")
    private String originalCurrency;

    @JsonProperty("exchange_rate")
    private BigDecimal exchangeRate;

    @JsonProperty("fee_amount")
    private BigDecimal feeAmount;

    @JsonProperty("net_amount")
    private BigDecimal netAmount;

    @JsonProperty("status")
    private TransactionStatus status;

    @JsonProperty("channel")
    private TransactionChannel channel;

    @JsonProperty("device_info")
    private DeviceInfo deviceInfo;

    @JsonProperty("location_info")
    private LocationInfo locationInfo;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // Top-level device/location fields for backward compatibility
    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("user_agent")
    private String userAgent;

    @JsonProperty("location")
    private Location location;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("correlation_id")
    private String correlationId;

    @JsonProperty("merchant_country")
    private String merchantCountry;

    @JsonProperty("card_info")
    private CardInfo cardInfo;

    @JsonProperty("authentication_method")
    private String authenticationMethod;

    @JsonProperty("authentication_score")
    private BigDecimal authenticationScore;

    @JsonProperty("risk_factors")
    private Map<String, Object> riskFactors;

    @JsonProperty("velocity_data")
    private VelocityData velocityData;

    @JsonProperty("behavioral_data")
    private BehavioralData behavioralData;

    @JsonProperty("external_references")
    private Map<String, String> externalReferences;

    @JsonProperty("compliance_data")
    private ComplianceData complianceData;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * Transaction types
     */
    public enum TransactionType {
        PAYMENT,
        TRANSFER,
        WITHDRAWAL,
        DEPOSIT,
        REFUND,
        CHARGEBACK,
        ADJUSTMENT,
        FEE,
        INTEREST,
        DIVIDEND,
        PURCHASE,
        SALE,
        EXCHANGE,
        LOAN,
        INVESTMENT,
        INSURANCE,
        UTILITY_PAYMENT,
        SUBSCRIPTION,
        DONATION,
        GIFT,
        CASHBACK,
        REWARD_REDEMPTION,
        P2P_TRANSFER,
        BILL_PAYMENT,
        TOP_UP,
        QR_PAYMENT,
        CONTACTLESS_PAYMENT,
        ONLINE_PAYMENT,
        ATM_TRANSACTION,
        WIRE_TRANSFER,
        ACH_TRANSFER
    }

    /**
     * Transaction status
     */
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        AUTHORIZED,
        COMPLETED,
        FAILED,
        CANCELLED,
        DECLINED,
        REVERSED,
        DISPUTED,
        CHARGEBACK,
        REFUNDED,
        EXPIRED,
        ON_HOLD,
        FLAGGED,
        UNDER_REVIEW
    }

    /**
     * Transaction channels
     */
    public enum TransactionChannel {
        MOBILE_APP,
        WEB_BROWSER,
        ATM,
        POS_TERMINAL,
        PHONE,
        BRANCH,
        KIOSK,
        API,
        BATCH,
        AUTOMATED,
        THIRD_PARTY,
        PARTNER,
        CHATBOT,
        VOICE_ASSISTANT,
        WEARABLE,
        IOT_DEVICE,
        QR_CODE,
        NFC,
        CARD_PRESENT,
        CARD_NOT_PRESENT,
        ONLINE,
        MAIL_ORDER,
        TELEPHONE_ORDER
    }

    /**
     * Device information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        @JsonProperty("device_id")
        private String deviceId;
        
        @JsonProperty("device_type")
        private String deviceType;
        
        @JsonProperty("device_fingerprint")
        private String deviceFingerprint;
        
        @JsonProperty("ip_address")
        private String ipAddress;
        
        @JsonProperty("user_agent")
        private String userAgent;
        
        @JsonProperty("operating_system")
        private String operatingSystem;
        
        @JsonProperty("browser")
        private String browser;
        
        @JsonProperty("app_version")
        private String appVersion;
        
        @JsonProperty("screen_resolution")
        private String screenResolution;
        
        @JsonProperty("timezone")
        private String timezone;
        
        @JsonProperty("language")
        private String language;
        
        @JsonProperty("is_vpn")
        private Boolean isVpn;
        
        @JsonProperty("is_proxy")
        private Boolean isProxy;
        
        @JsonProperty("is_tor")
        private Boolean isTor;
        
        @JsonProperty("is_emulator")
        private Boolean isEmulator;
        
        @JsonProperty("is_jailbroken")
        private Boolean isJailbroken;
        
        @JsonProperty("is_rooted")
        private Boolean isRooted;
        
        @JsonProperty("trust_score")
        private BigDecimal trustScore;
    }

    /**
     * Location information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        @JsonProperty("latitude")
        private Double latitude;
        
        @JsonProperty("longitude")
        private Double longitude;
        
        @JsonProperty("accuracy")
        private Double accuracy;
        
        @JsonProperty("country")
        private String country;
        
        @JsonProperty("country_code")
        private String countryCode;
        
        @JsonProperty("region")
        private String region;
        
        @JsonProperty("city")
        private String city;
        
        @JsonProperty("postal_code")
        private String postalCode;
        
        @JsonProperty("timezone")
        private String timezone;
        
        @JsonProperty("isp")
        private String isp;
        
        @JsonProperty("asn")
        private String asn;
        
        @JsonProperty("is_high_risk_country")
        private Boolean isHighRiskCountry;
        
        @JsonProperty("sanctions_check")
        private Boolean sanctionsCheck;
    }

    /**
     * Card information (masked for security)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardInfo {
        @JsonProperty("masked_pan")
        private String maskedPan;
        
        @JsonProperty("card_brand")
        private String cardBrand;
        
        @JsonProperty("card_type")
        private String cardType;
        
        @JsonProperty("card_category")
        private String cardCategory;
        
        @JsonProperty("issuing_bank")
        private String issuingBank;
        
        @JsonProperty("issuing_country")
        private String issuingCountry;
        
        @JsonProperty("is_prepaid")
        private Boolean isPrepaid;
        
        @JsonProperty("is_commercial")
        private Boolean isCommercial;
        
        @JsonProperty("is_virtual")
        private Boolean isVirtual;
        
        @JsonProperty("funding_source")
        private String fundingSource;
        
        @JsonProperty("expiry_month")
        private String expiryMonth;
        
        @JsonProperty("expiry_year")
        private String expiryYear;
        
        @JsonProperty("cvv_result")
        private String cvvResult;
        
        @JsonProperty("avs_result")
        private String avsResult;
    }

    /**
     * Velocity tracking data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityData {
        @JsonProperty("transactions_last_hour")
        private Integer transactionsLastHour;
        
        @JsonProperty("transactions_last_day")
        private Integer transactionsLastDay;
        
        @JsonProperty("transactions_last_week")
        private Integer transactionsLastWeek;
        
        @JsonProperty("amount_last_hour")
        private BigDecimal amountLastHour;
        
        @JsonProperty("amount_last_day")
        private BigDecimal amountLastDay;
        
        @JsonProperty("amount_last_week")
        private BigDecimal amountLastWeek;
        
        @JsonProperty("unique_merchants_last_day")
        private Integer uniqueMerchantsLastDay;
        
        @JsonProperty("unique_countries_last_day")
        private Integer uniqueCountriesLastDay;
        
        @JsonProperty("failed_attempts_last_hour")
        private Integer failedAttemptsLastHour;
    }

    /**
     * Behavioral analysis data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralData {
        @JsonProperty("typing_pattern")
        private String typingPattern;
        
        @JsonProperty("mouse_movements")
        private String mouseMovements;
        
        @JsonProperty("session_duration")
        private Long sessionDuration;
        
        @JsonProperty("pages_visited")
        private Integer pagesVisited;
        
        @JsonProperty("time_on_payment_page")
        private Long timeOnPaymentPage;
        
        @JsonProperty("form_completion_time")
        private Long formCompletionTime;
        
        @JsonProperty("copy_paste_detected")
        private Boolean copyPasteDetected;
        
        @JsonProperty("multiple_tabs_detected")
        private Boolean multipleTabsDetected;
        
        @JsonProperty("behavioral_score")
        private BigDecimal behavioralScore;
    }

    /**
     * Compliance and regulatory data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceData {
        @JsonProperty("kyc_status")
        private String kycStatus;
        
        @JsonProperty("aml_check_result")
        private String amlCheckResult;
        
        @JsonProperty("sanctions_check_result")
        private String sanctionsCheckResult;
        
        @JsonProperty("pep_check_result")
        private String pepCheckResult;
        
        @JsonProperty("source_of_funds")
        private String sourceOfFunds;
        
        @JsonProperty("regulatory_flags")
        private Map<String, String> regulatoryFlags;
        
        @JsonProperty("reporting_threshold_breached")
        private Boolean reportingThresholdBreached;
        
        @JsonProperty("suspicious_activity_indicators")
        private Map<String, String> suspiciousActivityIndicators;
    }

    /**
     * Calculate transaction risk score based on various factors
     */
    public BigDecimal calculateRiskScore() {
        BigDecimal baseScore = BigDecimal.ZERO;
        
        // Amount-based risk
        if (amount != null) {
            if (amount.compareTo(new BigDecimal("10000")) > 0) {
                baseScore = baseScore.add(new BigDecimal("20"));
            } else if (amount.compareTo(new BigDecimal("1000")) > 0) {
                baseScore = baseScore.add(new BigDecimal("10"));
            }
        }
        
        // Device risk
        if (deviceInfo != null) {
            if (Boolean.TRUE.equals(deviceInfo.getIsVpn())) {
                baseScore = baseScore.add(new BigDecimal("15"));
            }
            if (Boolean.TRUE.equals(deviceInfo.getIsProxy())) {
                baseScore = baseScore.add(new BigDecimal("20"));
            }
            if (Boolean.TRUE.equals(deviceInfo.getIsJailbroken())) {
                baseScore = baseScore.add(new BigDecimal("10"));
            }
        }
        
        // Location risk
        if (locationInfo != null && Boolean.TRUE.equals(locationInfo.getIsHighRiskCountry())) {
            baseScore = baseScore.add(new BigDecimal("25"));
        }
        
        // Velocity risk
        if (velocityData != null) {
            if (velocityData.getTransactionsLastHour() != null && velocityData.getTransactionsLastHour() > 10) {
                baseScore = baseScore.add(new BigDecimal("30"));
            }
            if (velocityData.getFailedAttemptsLastHour() != null && velocityData.getFailedAttemptsLastHour() > 3) {
                baseScore = baseScore.add(new BigDecimal("25"));
            }
        }
        
        return baseScore.min(new BigDecimal("100")); // Cap at 100
    }

    /**
     * Check if transaction is high value
     */
    public boolean isHighValue() {
        return amount != null && amount.compareTo(new BigDecimal("10000")) > 0;
    }

    /**
     * Check if transaction is cross-border
     */
    public boolean isCrossBorder() {
        return locationInfo != null && merchantCountry != null && 
               !merchantCountry.equals(locationInfo.getCountryCode());
    }

    /**
     * Check if transaction is suspicious based on basic criteria
     */
    public boolean isSuspicious() {
        return calculateRiskScore().compareTo(new BigDecimal("50")) > 0 ||
               (deviceInfo != null && (Boolean.TRUE.equals(deviceInfo.getIsVpn()) ||
                                     Boolean.TRUE.equals(deviceInfo.getIsProxy()))) ||
               (velocityData != null && velocityData.getFailedAttemptsLastHour() != null &&
                velocityData.getFailedAttemptsLastHour() > 5);
    }

    /**
     * PRODUCTION FIX: Convenience methods to access nested device and location data
     */
    public String getIpAddress() {
        return deviceInfo != null ? deviceInfo.getIpAddress() : null;
    }

    public String getDeviceId() {
        return deviceInfo != null ? deviceInfo.getDeviceId() : null;
    }

    public String getUserAgent() {
        return deviceInfo != null ? deviceInfo.getUserAgent() : null;
    }

    public com.waqiti.common.fraud.model.Location getLocation() {
        if (locationInfo == null) return null;

        return com.waqiti.common.fraud.model.Location.builder()
            .latitude(locationInfo.getLatitude())
            .longitude(locationInfo.getLongitude())
            .country(locationInfo.getCountry())
            .countryCode(locationInfo.getCountryCode())
            .city(locationInfo.getCity())
            .build();
    }
}