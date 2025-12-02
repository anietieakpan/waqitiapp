package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Transaction data for fraud analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionData {
    private String transactionId;
    private String userId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String merchantId;
    private String merchantName;
    private String merchantCategory;
    private LocalDateTime timestamp;
    
    // Location data
    private Location location;
    private Double latitude;
    private Double longitude;
    private String ipAddress;
    private String country;
    private String city;

    // Device data
    private String deviceId;
    private String deviceType;
    private String userAgent;
    private String browserFingerprint;
    
    // Behavior data
    private Integer hourOfDay;
    private Integer dayOfWeek;
    private Boolean isWeekend;
    private Boolean isHoliday;
    
    // Risk indicators
    private Boolean isFirstTransaction;
    private Boolean isInternational;
    private Boolean isHighRiskMerchant;
    private Integer recentFailedAttempts;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    // Methods for convenience
    public BigDecimal getAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp != null ? timestamp : LocalDateTime.now();
    }
}