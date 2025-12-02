package com.waqiti.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for fraud detection checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {
    
    // Transaction details
    private String transactionId;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionTime;
    private String merchantId;
    private String merchantCategory;
    
    // User details
    private UUID userId;
    private String userEmail;
    private String phoneNumber;
    private LocalDateTime userRegistrationDate;
    private String userStatus;
    
    // Device and session details
    private String deviceId;
    private String deviceFingerprint;
    private String userAgent;
    private String sessionId;
    private String browserLanguage;
    private String screenResolution;
    private String timezone;
    
    // Location details
    private String ipAddress;
    private String countryCode;
    private String city;
    private Double latitude;
    private Double longitude;
    private String vpnStatus;
    private String proxyStatus;
    
    // Account details
    private String fromAccountId;
    private String toAccountId;
    private String fromAccountType;
    private String toAccountType;
    private BigDecimal fromAccountBalance;
    private BigDecimal toAccountBalance;
    
    // Behavioral context
    private Integer transactionCountLast24h;
    private Integer transactionCountLast7d;
    private Integer transactionCountLast30d;
    private BigDecimal totalAmountLast24h;
    private BigDecimal totalAmountLast7d;
    private BigDecimal totalAmountLast30d;
    private LocalDateTime lastTransactionTime;
    private LocalDateTime lastSuccessfulLogin;
    private Integer failedLoginAttempts;
    
    // Payment method details
    private String paymentMethod;
    private String cardBin;
    private String cardLast4;
    private String cardType;
    private String cardCountry;
    private String cardIssuer;
    private LocalDateTime cardExpiryDate;
    
    // Risk context
    private Boolean isFirstTimeTransaction;
    private Boolean isNewDevice;
    private Boolean isNewLocation;
    private Boolean isOffHours;
    private Boolean isWeekend;
    private Boolean isHighRiskMerchant;
    private Boolean isInternationalTransaction;
    private Boolean isCrossBorderTransaction;
    
    // Metadata
    private Map<String, Object> additionalData;
    private String referenceId;
    private String source;
    private Integer priority;
    
    /**
     * Convenience methods
     */
    public boolean isHighValueTransaction(BigDecimal threshold) {
        return amount != null && amount.compareTo(threshold) > 0;
    }
    
    public boolean isNewUser(int daysThreshold) {
        if (userRegistrationDate == null) return false;
        return userRegistrationDate.isAfter(LocalDateTime.now().minusDays(daysThreshold));
    }
    
    public boolean isRecentTransaction(int minutesThreshold) {
        if (lastTransactionTime == null) return false;
        return lastTransactionTime.isAfter(LocalDateTime.now().minusMinutes(minutesThreshold));
    }
    
    public boolean isVelocityExceeded(int maxTransactions) {
        return transactionCountLast24h != null && transactionCountLast24h > maxTransactions;
    }
    
    public boolean isAmountVelocityExceeded(BigDecimal maxAmount) {
        return totalAmountLast24h != null && totalAmountLast24h.compareTo(maxAmount) > 0;
    }
    
    public boolean isLocationChanged() {
        return Boolean.TRUE.equals(isNewLocation);
    }
    
    public boolean isDeviceChanged() {
        return Boolean.TRUE.equals(isNewDevice);
    }
    
    public boolean isSuspiciousTime() {
        return Boolean.TRUE.equals(isOffHours) || Boolean.TRUE.equals(isWeekend);
    }
}