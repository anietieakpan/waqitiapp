package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Transaction Monitoring Request
 * 
 * Request to monitor transactions for fraud patterns and suspicious activity.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonitoringRequest {
    
    /**
     * Transaction ID to monitor
     */
    private String transactionId;
    
    /**
     * User ID associated with the transaction
     */
    private String userId;
    
    /**
     * Merchant ID
     */
    private String merchantId;
    
    /**
     * Transaction amount
     */
    private BigDecimal amount;
    
    /**
     * Transaction currency
     */
    private String currency;
    
    /**
     * Transaction type
     */
    private TransactionType transactionType;
    
    /**
     * Payment method used
     */
    private String paymentMethod;
    
    /**
     * Transaction timestamp
     */
    private LocalDateTime transactionTime;
    
    /**
     * Source IP address
     */
    private String sourceIpAddress;
    
    /**
     * Device information
     */
    private DeviceInfo deviceInfo;
    
    /**
     * Geographic location
     */
    private LocationInfo locationInfo;
    
    /**
     * Monitoring scope
     */
    private MonitoringScope scope;
    
    /**
     * Time window for pattern detection
     */
    private TimeWindow timeWindow;
    
    /**
     * Specific patterns to look for
     */
    private List<PatternType> patternsToCheck;
    
    /**
     * Additional transaction metadata
     */
    private Map<String, Object> transactionMetadata;
    
    /**
     * Risk thresholds for alerting
     */
    private RiskThresholds riskThresholds;
    
    /**
     * Whether to include real-time analysis
     */
    @Builder.Default
    private Boolean realTimeAnalysis = true;
    
    /**
     * Whether to check velocity limits
     */
    @Builder.Default
    private Boolean checkVelocityLimits = true;
    
    /**
     * Whether to analyze behavioral patterns
     */
    @Builder.Default
    private Boolean analyzeBehavioralPatterns = true;
    
    /**
     * Priority level for monitoring
     */
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    /**
     * Request timestamp
     */
    private LocalDateTime requestedAt;
    
    /**
     * System making the request
     */
    private String requestSource;
    
    public enum TransactionType {
        PAYMENT,
        REFUND,
        TRANSFER,
        WITHDRAWAL,
        DEPOSIT,
        PURCHASE,
        SUBSCRIPTION,
        CHARGEBACK
    }
    
    public enum MonitoringScope {
        TRANSACTION_ONLY,
        USER_ACTIVITY,
        MERCHANT_ACTIVITY,
        CROSS_USER_PATTERNS,
        GLOBAL_PATTERNS
    }
    
    public enum PatternType {
        VELOCITY_ABUSE,
        AMOUNT_PATTERNS,
        TIME_PATTERNS,
        LOCATION_PATTERNS,
        DEVICE_PATTERNS,
        BEHAVIORAL_ANOMALIES,
        MERCHANT_FRAUD,
        CARD_TESTING,
        ACCOUNT_TAKEOVER,
        MONEY_LAUNDERING
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private String deviceId;
        private String deviceType;
        private String operatingSystem;
        private String browser;
        private String userAgent;
        private String screenResolution;
        private String timezone;
        private Boolean cookiesEnabled;
        private Boolean javascriptEnabled;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String country;
        private String state;
        private String city;
        private Double latitude;
        private Double longitude;
        private String zipCode;
        private String isp;
        private Boolean vpnDetected;
        private Boolean proxyDetected;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeWindow {
        private Integer minutes;
        private Integer hours;
        private Integer days;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskThresholds {
        private Double lowRiskThreshold;
        private Double mediumRiskThreshold;
        private Double highRiskThreshold;
        private Double criticalRiskThreshold;
        private BigDecimal amountThreshold;
        private Integer velocityThreshold;
    }
    
    /**
     * Check if high priority monitoring is needed
     */
    public boolean isHighPriority() {
        return priority == Priority.HIGH || priority == Priority.CRITICAL;
    }
    
    /**
     * Check if amount exceeds threshold
     */
    public boolean exceedsAmountThreshold() {
        return riskThresholds != null && 
               riskThresholds.getAmountThreshold() != null &&
               amount.compareTo(riskThresholds.getAmountThreshold()) > 0;
    }
    
    /**
     * Get effective time window in minutes
     */
    public Integer getEffectiveTimeWindowMinutes() {
        if (timeWindow == null) {
            return 60; // Default 1 hour
        }
        
        if (timeWindow.getMinutes() != null) {
            return timeWindow.getMinutes();
        }
        if (timeWindow.getHours() != null) {
            return timeWindow.getHours() * 60;
        }
        if (timeWindow.getDays() != null) {
            return timeWindow.getDays() * 24 * 60;
        }
        
        return 60; // Default fallback
    }
}