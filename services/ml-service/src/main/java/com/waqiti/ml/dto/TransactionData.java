package com.waqiti.ml.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * Comprehensive transaction data for ML analysis.
 * Production-ready DTO with validation, serialization, and security features.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionData {

    @NotBlank(message = "Transaction ID is required")
    @Size(max = 36, message = "Transaction ID must not exceed 36 characters")
    @JsonProperty("transaction_id")
    private String transactionId;

    @NotBlank(message = "User ID is required")
    @Size(max = 36, message = "User ID must not exceed 36 characters")
    @JsonProperty("user_id")
    private String userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum limit")
    @Digits(integer = 12, fraction = 2, message = "Amount format invalid")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    private String currency;

    @NotNull(message = "Timestamp is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @Size(max = 100, message = "Source account must not exceed 100 characters")
    @JsonProperty("source_account")
    private String sourceAccount;

    @Size(max = 100, message = "Target account must not exceed 100 characters")
    @JsonProperty("target_account")
    private String targetAccount;

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "P2P_TRANSFER|DEPOSIT|WITHDRAWAL|PAYMENT|REFUND|INTERNATIONAL|CRYPTO|BILL_PAYMENT", 
             message = "Invalid transaction type")
    @JsonProperty("transaction_type")
    private String transactionType;

    @Size(max = 100, message = "Device ID must not exceed 100 characters")
    @JsonProperty("device_id")
    private String deviceId;

    @Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$", 
             message = "Invalid IP address format")
    @JsonProperty("ip_address")
    private String ipAddress;

    @Size(max = 200, message = "Location must not exceed 200 characters")
    private String location;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @JsonProperty("reference_number")
    private String referenceNumber;

    @JsonProperty("channel")
    private String channel; // MOBILE, WEB, API, ATM

    @Valid
    @JsonProperty("geolocation")
    private GeolocationData geolocation;

    @Valid
    @JsonProperty("device_info")
    private DeviceInfo deviceInfo;

    @Valid
    @JsonProperty("network_info")
    private NetworkInfo networkInfo;

    @Valid
    @JsonProperty("authentication_data")
    private AuthenticationData authenticationData;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("tags")
    private List<String> tags;

    /**
     * Nested class for geolocation data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationData {
        
        @DecimalMin(value = "-90.0", message = "Invalid latitude")
        @DecimalMax(value = "90.0", message = "Invalid latitude")
        private Double latitude;
        
        @DecimalMin(value = "-180.0", message = "Invalid longitude")
        @DecimalMax(value = "180.0", message = "Invalid longitude")
        private Double longitude;
        
        @Size(max = 100, message = "Country must not exceed 100 characters")
        private String country;
        
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;
        
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        @JsonProperty("postal_code")
        private String postalCode;
        
        @JsonProperty("accuracy_meters")
        private Double accuracyMeters;
        
        @JsonProperty("is_mock_location")
        private Boolean isMockLocation;
    }

    /**
     * Nested class for device information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        
        @Size(max = 100, message = "Device model must not exceed 100 characters")
        @JsonProperty("device_model")
        private String deviceModel;
        
        @Size(max = 50, message = "OS version must not exceed 50 characters")
        @JsonProperty("os_version")
        private String osVersion;
        
        @Size(max = 50, message = "App version must not exceed 50 characters")
        @JsonProperty("app_version")
        private String appVersion;
        
        @JsonProperty("screen_resolution")
        private String screenResolution;
        
        @JsonProperty("device_fingerprint")
        private String deviceFingerprint;
        
        @JsonProperty("is_rooted")
        private Boolean isRooted;
        
        @JsonProperty("is_emulator")
        private Boolean isEmulator;
        
        @JsonProperty("biometric_available")
        private Boolean biometricAvailable;
        
        @JsonProperty("first_seen")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        private LocalDateTime firstSeen;
        
        @JsonProperty("trust_score")
        private Double trustScore;
    }

    /**
     * Nested class for network information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfo {
        
        @JsonProperty("connection_type")
        private String connectionType; // WIFI, CELLULAR, ETHERNET
        
        @JsonProperty("user_agent")
        private String userAgent;
        
        @JsonProperty("is_vpn")
        private Boolean isVpn;
        
        @JsonProperty("is_proxy")
        private Boolean isProxy;
        
        @JsonProperty("is_tor")
        private Boolean isTor;
        
        @JsonProperty("network_operator")
        private String networkOperator;
        
        @JsonProperty("asn")
        private String asn; // Autonomous System Number
        
        @JsonProperty("threat_intel_score")
        private Double threatIntelScore;
    }

    /**
     * Nested class for authentication data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticationData {
        
        @JsonProperty("authentication_method")
        private String authenticationMethod; // PASSWORD, BIOMETRIC, MFA, SSO
        
        @JsonProperty("authentication_strength")
        private String authenticationStrength; // WEAK, MEDIUM, STRONG
        
        @JsonProperty("session_age_minutes")
        private Integer sessionAgeMinutes;
        
        @JsonProperty("mfa_verified")
        private Boolean mfaVerified;
        
        @JsonProperty("biometric_verified")
        private Boolean biometricVerified;
        
        @JsonProperty("risk_based_auth_score")
        private Double riskBasedAuthScore;
        
        @JsonProperty("authentication_timestamp")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        private LocalDateTime authenticationTimestamp;
    }

    /**
     * Helper method to check if transaction is high value
     */
    public boolean isHighValue() {
        return amount != null && amount.compareTo(BigDecimal.valueOf(10000)) > 0;
    }

    /**
     * Helper method to check if transaction is international
     */
    public boolean isInternational() {
        return "INTERNATIONAL".equals(transactionType) || 
               (location != null && location.contains("INTERNATIONAL"));
    }

    /**
     * Helper method to check if transaction is crypto-related
     */
    public boolean isCrypto() {
        return "CRYPTO".equals(transactionType) || 
               (tags != null && tags.contains("CRYPTO"));
    }

    /**
     * Helper method to get transaction age in minutes
     */
    public long getTransactionAgeMinutes() {
        if (timestamp == null) return 0;
        return java.time.temporal.ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now());
    }

    /**
     * Helper method to check if device is trusted
     */
    public boolean isFromTrustedDevice() {
        return deviceInfo != null && 
               deviceInfo.trustScore != null && 
               deviceInfo.trustScore >= 0.8 &&
               !Boolean.TRUE.equals(deviceInfo.isRooted) &&
               !Boolean.TRUE.equals(deviceInfo.isEmulator);
    }

    /**
     * Helper method to check if network is suspicious
     */
    public boolean isFromSuspiciousNetwork() {
        if (networkInfo == null) return false;
        
        return Boolean.TRUE.equals(networkInfo.isVpn) ||
               Boolean.TRUE.equals(networkInfo.isProxy) ||
               Boolean.TRUE.equals(networkInfo.isTor) ||
               (networkInfo.threatIntelScore != null && networkInfo.threatIntelScore > 0.7);
    }

    /**
     * Helper method to get authentication strength score
     */
    public double getAuthenticationStrengthScore() {
        if (authenticationData == null) return 0.3; // Default low score
        
        double score = 0.0;
        
        if (Boolean.TRUE.equals(authenticationData.mfaVerified)) score += 0.4;
        if (Boolean.TRUE.equals(authenticationData.biometricVerified)) score += 0.3;
        if (authenticationData.riskBasedAuthScore != null) score += authenticationData.riskBasedAuthScore * 0.3;
        
        return Math.min(score, 1.0);
    }

    /**
     * Helper method to validate transaction data integrity
     */
    public boolean isDataIntegrityValid() {
        // Basic integrity checks
        if (transactionId == null || userId == null || amount == null) return false;
        if (timestamp == null || timestamp.isAfter(LocalDateTime.now())) return false;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        
        // Advanced integrity checks
        if (sourceAccount != null && sourceAccount.equals(targetAccount)) return false;
        if (currency == null || currency.length() != 3) return false;
        
        return true;
    }
}