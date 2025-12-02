package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.common.validation.ValidationConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive fraud assessment request model with full validation and business context
 * Used for evaluating transaction risk across multiple fraud detection dimensions
 */
@Data
@Builder
@Jacksonized
public class FraudAssessmentRequest {
    
    @NotBlank(message = "Request ID is mandatory")
    @Pattern(regexp = "^REQ-[A-Z0-9]{16,32}$", message = "Invalid request ID format")
    private String requestId;
    
    @NotBlank(message = "Transaction ID is mandatory")
    @Pattern(regexp = "^TXN-[A-Z0-9]{16,32}$", message = "Invalid transaction ID format")
    private String transactionId;
    
    @NotBlank(message = "User ID is mandatory") 
    @Pattern(regexp = "^USR-[A-Z0-9]{16,32}$", message = "Invalid user ID format")
    private String userId;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Account ID is mandatory")
    @Pattern(regexp = "^ACC-[A-Z0-9]{16,32}$", message = "Invalid account ID format")
    private String accountId;
    
    private AccountInfo accountInfo;
    
    private UserLocation userLocation;
    
    @NotBlank(message = "IP address is mandatory")
    @Pattern(regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$", 
             message = "Invalid IP address format")
    private String ipAddress;
    
    @Pattern(regexp = "^DEV-[A-Z0-9]{16,32}$", message = "Invalid device ID format")
    private String deviceId;
    
    @NotNull(message = "Transaction amount is mandatory")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @DecimalMax(value = "1000000.00", message = "Amount exceeds maximum limit")
    @Digits(integer = 10, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is mandatory")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    @Pattern(regexp = "^MER-[A-Z0-9]{16,32}$", message = "Invalid merchant ID format")
    private String merchantId;
    
    @NotBlank(message = "Transaction type is mandatory")
    @Pattern(regexp = "^(PAYMENT|TRANSFER|WITHDRAWAL|DEPOSIT|REFUND)$", message = "Invalid transaction type")
    private String transactionType;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @NotNull(message = "Transaction timestamp is mandatory")
    private Instant transactionTimestamp;
    
    // Geolocation data
    @Valid
    private GeolocationData geolocationData;
    
    // Device fingerprinting
    @Valid
    private DeviceFingerprint deviceFingerprint;
    
    // User behavioral context
    @Valid 
    private UserBehavioralContext userBehavioralContext;
    
    // Transaction context
    @Valid
    private TransactionContext transactionContext;
    
    // Risk assessment parameters
    @Builder.Default
    @Min(value = 0, message = "Risk threshold must be non-negative")
    @Max(value = 100, message = "Risk threshold cannot exceed 100")
    private Integer riskThreshold = 50;
    
    @Builder.Default
    private Boolean enableMLAnalysis = true;
    
    @Builder.Default
    private Boolean enableBehavioralAnalysis = true;
    
    @Builder.Default
    private Boolean enableVelocityChecks = true;
    
    @Builder.Default
    private Boolean enableGeolocationAnalysis = true;
    
    // Compliance flags
    @Builder.Default
    private Boolean requireAMLCheck = true;
    
    @Builder.Default
    private Boolean requireSanctionsCheck = true;
    
    // Historical context
    private List<String> recentTransactionIds;
    
    private Set<String> associatedAccountIds;
    
    // External data sources
    private Map<String, Object> thirdPartyData;
    
    // Custom attributes for extensibility
    private Map<String, String> customAttributes;
    
    // Additional fields for compatibility
    private String routingNumber;
    private BufferedImage checkImage;
    private LocalDateTime timestamp;
    private String locationData;
    
    /**
     * Geolocation data for fraud analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class GeolocationData {
        private Double latitude;
        private Double longitude;
        private String country;
        private String region;
        private String city;
        private String postalCode;
        private String timezone;
        private Boolean isVpnDetected;
        private Boolean isProxyDetected;
        private Boolean isTorDetected;
        private String ispName;
        private String organizationName;
        private Integer accuracyRadius;
    }
    
    /**
     * Device fingerprinting data
     */
    @Data
    @Builder
    @Jacksonized
    public static class DeviceFingerprint {
        private String userAgent;
        private String browserName;
        private String browserVersion;
        private String operatingSystem;
        private String deviceType;
        private String screenResolution;
        private String timezone;
        private String language;
        private Boolean cookiesEnabled;
        private Boolean javascriptEnabled;
        private String canvasFingerprint;
        private String webglFingerprint;
        private List<String> installedPlugins;
        private List<String> availableFonts;
        private Boolean isJailbroken;
        private Boolean isEmulator;
        private String batteryLevel;
        private String networkType;
        
        /**
         * Generate a composite fingerprint from available data
         */
        public String getFingerprint() {
            StringBuilder fingerprint = new StringBuilder();
            
            if (userAgent != null) fingerprint.append(userAgent).append("|");
            if (browserName != null) fingerprint.append(browserName).append("|");
            if (browserVersion != null) fingerprint.append(browserVersion).append("|");
            if (operatingSystem != null) fingerprint.append(operatingSystem).append("|");
            if (screenResolution != null) fingerprint.append(screenResolution).append("|");
            if (timezone != null) fingerprint.append(timezone).append("|");
            if (language != null) fingerprint.append(language).append("|");
            if (canvasFingerprint != null) fingerprint.append(canvasFingerprint).append("|");
            if (webglFingerprint != null) fingerprint.append(webglFingerprint).append("|");
            
            return fingerprint.toString();
        }
    }
    
    /**
     * User behavioral context
     */
    @Data
    @Builder
    @Jacksonized
    public static class UserBehavioralContext {
        private Integer sessionDurationMinutes;
        private Integer pageViews;
        private Integer clickEvents;
        private Integer keystrokes;
        private Double typingSpeed;
        private Double mouseMovementEntropy;
        private Boolean copiedPastedData;
        private Integer formFillingTime;
        private List<String> navigationPath;
        private Integer authenticationAttempts;
        private Instant lastLoginTimestamp;
        private String lastKnownLocation;
        private Integer accountAgeDays;
        private Integer transactionHistoryDays;
        private BigDecimal averageTransactionAmount;
        private Integer typicalTransactionFrequency;
        private Set<String> usualMerchants;
        private Set<String> usualCountries;
        private Map<String, Integer> behaviorScores;
    }
    
    /**
     * Transaction context
     */
    @Data
    @Builder
    @Jacksonized
    public static class TransactionContext {
        private String paymentMethod;
        private String cardBin;
        private String cardLast4;
        private String cardExpiryMonth;
        private String cardExpiryYear;
        private String authorizationCode;
        private String processorResponse;
        private BigDecimal availableBalance;
        private Integer dailyTransactionCount;
        private BigDecimal dailyTransactionVolume;
        private Integer monthlyTransactionCount;
        private BigDecimal monthlyTransactionVolume;
        private String merchantCategoryCode;
        private String merchantCountry;
        private Boolean isRecurringTransaction;
        private Boolean isInternational;
        private Integer minutesSinceLastTransaction;
        private BigDecimal lastTransactionAmount;
        private String referenceNumber;
        private Map<String, String> merchantMetadata;
        private List<String> transactionTags;
        private String riskCategory;
    }
    
    /**
     * Validates the completeness and consistency of the fraud assessment request
     */
    public boolean isValid() {
        return transactionId != null && !transactionId.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty() &&
               accountId != null && !accountId.trim().isEmpty() &&
               ipAddress != null && !ipAddress.trim().isEmpty() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && currency.length() == 3 &&
               transactionType != null && !transactionType.trim().isEmpty() &&
               transactionTimestamp != null;
    }
    
    /**
     * Calculates the base risk score based on transaction amount and user history
     */
    public double calculateBaseRiskScore() {
        double amountRisk = Math.min(amount.doubleValue() / 10000.0 * 30, 30); // Max 30 points for amount
        
        double userRisk = 0;
        if (userBehavioralContext != null && userBehavioralContext.getAccountAgeDays() != null) {
            // Newer accounts are riskier
            userRisk = Math.max(0, 20 - userBehavioralContext.getAccountAgeDays() / 30.0);
        }
        
        double velocityRisk = 0;
        if (transactionContext != null && transactionContext.getDailyTransactionCount() != null) {
            // High frequency transactions increase risk
            velocityRisk = Math.min(transactionContext.getDailyTransactionCount() * 2.0, 20);
        }
        
        return Math.min(amountRisk + userRisk + velocityRisk, 70); // Base score max 70
    }
    
    /**
     * Determines if this transaction requires enhanced due diligence
     */
    public boolean requiresEnhancedDueDiligence() {
        return amount.compareTo(new BigDecimal("10000")) >= 0 ||
               (transactionContext != null && transactionContext.getIsInternational()) ||
               (geolocationData != null && Boolean.TRUE.equals(geolocationData.getIsVpnDetected())) ||
               (deviceFingerprint != null && Boolean.TRUE.equals(deviceFingerprint.getIsJailbroken()));
    }
    
    /**
     * Gets the risk category based on amount and transaction type
     */
    public String getRiskCategory() {
        if (amount.compareTo(new BigDecimal("100000")) >= 0) {
            return "CRITICAL";
        } else if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            return "HIGH";
        } else if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Get merchant information from transaction context
     */
    public String getMerchantInfo() {
        if (merchantId != null) {
            return merchantId;
        }
        if (transactionContext != null && transactionContext.getMerchantCategoryCode() != null) {
            return transactionContext.getMerchantCategoryCode();
        }
        return null;
    }

    /**
     * Get device information
     */
    public String getDeviceInfo() {
        if (deviceId != null) {
            return deviceId;
        }
        if (deviceFingerprint != null && deviceFingerprint.getFingerprint() != null) {
            return deviceFingerprint.getFingerprint();
        }
        return null;
    }

    /**
     * Get location information
     */
    public String getLocationInfo() {
        if (locationData != null) {
            return locationData;
        }
        if (geolocationData != null && geolocationData.getCountry() != null) {
            return geolocationData.getCountry() +
                   (geolocationData.getCity() != null ? ", " + geolocationData.getCity() : "");
        }
        return null;
    }

    /**
     * Get session ID from device or behavioral context
     */
    public String getSessionId() {
        if (userBehavioralContext != null) {
            return userId + "-" + transactionTimestamp.toEpochMilli();
        }
        return null;
    }
}