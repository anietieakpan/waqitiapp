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
 * Velocity Check Request
 * 
 * Request to check velocity limits for fraud detection.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityCheckRequest {
    
    /**
     * Check ID for tracking
     */
    private String checkId;
    
    /**
     * Entity to check velocity for
     */
    private VelocityEntity entity;
    
    /**
     * Transaction details
     */
    private TransactionDetails transaction;
    
    /**
     * Velocity checks to perform
     */
    private List<VelocityCheckType> checkTypes;
    
    /**
     * Time windows to check
     */
    private List<TimeWindow> timeWindows;
    
    /**
     * Custom velocity limits (override defaults)
     */
    private List<VelocityLimit> customLimits;
    
    /**
     * Include historical data
     */
    @Builder.Default
    private Boolean includeHistorical = true;
    
    /**
     * Include pending transactions
     */
    @Builder.Default
    private Boolean includePending = true;
    
    /**
     * Real-time processing
     */
    @Builder.Default
    private Boolean realTime = true;
    
    /**
     * Check priority
     */
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    /**
     * Additional filters
     */
    private VelocityFilters filters;
    
    /**
     * Request metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Request timestamp
     */
    private LocalDateTime requestedAt;
    
    /**
     * Source system
     */
    private String requestSource;
    
    public enum VelocityCheckType {
        TRANSACTION_COUNT,
        TRANSACTION_AMOUNT,
        UNIQUE_MERCHANTS,
        UNIQUE_CARDS,
        UNIQUE_DEVICES,
        UNIQUE_LOCATIONS,
        FAILED_ATTEMPTS,
        REFUND_COUNT,
        CHARGEBACK_COUNT,
        DECLINED_COUNT
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
    public static class VelocityEntity {
        private String entityType; // USER, MERCHANT, CARD, DEVICE, IP
        private String entityId;
        private String entityValue;
        private Map<String, Object> entityAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetails {
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private String transactionType;
        private String paymentMethod;
        private String merchantId;
        private String deviceId;
        private String ipAddress;
        private String location;
        private LocalDateTime timestamp;
        private Map<String, Object> additionalDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeWindow {
        private String windowType; // MINUTES, HOURS, DAYS, WEEKS
        private Integer windowSize;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Boolean rolling;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityFilters {
        private List<String> includedStatuses;
        private List<String> excludedStatuses;
        private List<String> includedTypes;
        private List<String> excludedTypes;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private List<String> includedMerchants;
        private List<String> excludedMerchants;
        private Map<String, Object> customFilters;
    }
    
    /**
     * Check if high priority
     */
    public boolean isHighPriority() {
        return priority == Priority.HIGH || priority == Priority.CRITICAL;
    }
    
    /**
     * Get default time windows if none specified
     */
    public List<TimeWindow> getEffectiveTimeWindows() {
        if (timeWindows != null && !timeWindows.isEmpty()) {
            return timeWindows;
        }
        
        // Default time windows
        return List.of(
            TimeWindow.builder()
                    .windowType("MINUTES")
                    .windowSize(15)
                    .rolling(true)
                    .build(),
            TimeWindow.builder()
                    .windowType("HOURS")
                    .windowSize(1)
                    .rolling(true)
                    .build(),
            TimeWindow.builder()
                    .windowType("HOURS")
                    .windowSize(24)
                    .rolling(true)
                    .build(),
            TimeWindow.builder()
                    .windowType("DAYS")
                    .windowSize(7)
                    .rolling(true)
                    .build()
        );
    }
    
    /**
     * Get default check types if none specified
     */
    public List<VelocityCheckType> getEffectiveCheckTypes() {
        if (checkTypes != null && !checkTypes.isEmpty()) {
            return checkTypes;
        }
        
        // Default check types
        return List.of(
            VelocityCheckType.TRANSACTION_COUNT,
            VelocityCheckType.TRANSACTION_AMOUNT,
            VelocityCheckType.FAILED_ATTEMPTS
        );
    }
    
    /**
     * Validate required fields
     */
    public boolean isValid() {
        return entity != null &&
               entity.getEntityType() != null &&
               entity.getEntityId() != null &&
               transaction != null;
    }
    
    /**
     * Check if amount-based checks are needed
     */
    public boolean needsAmountChecks() {
        List<VelocityCheckType> types = getEffectiveCheckTypes();
        return types.contains(VelocityCheckType.TRANSACTION_AMOUNT);
    }
    
    /**
     * Check if count-based checks are needed
     */
    public boolean needsCountChecks() {
        List<VelocityCheckType> types = getEffectiveCheckTypes();
        return types.contains(VelocityCheckType.TRANSACTION_COUNT) ||
               types.contains(VelocityCheckType.FAILED_ATTEMPTS) ||
               types.contains(VelocityCheckType.DECLINED_COUNT) ||
               types.contains(VelocityCheckType.REFUND_COUNT) ||
               types.contains(VelocityCheckType.CHARGEBACK_COUNT);
    }
    
    /**
     * Check if uniqueness checks are needed
     */
    public boolean needsUniquenessChecks() {
        List<VelocityCheckType> types = getEffectiveCheckTypes();
        return types.contains(VelocityCheckType.UNIQUE_MERCHANTS) ||
               types.contains(VelocityCheckType.UNIQUE_CARDS) ||
               types.contains(VelocityCheckType.UNIQUE_DEVICES) ||
               types.contains(VelocityCheckType.UNIQUE_LOCATIONS);
    }
    
    /**
     * Get time window in minutes
     */
    public static long getTimeWindowMinutes(TimeWindow window) {
        if (window.getWindowType() == null || window.getWindowSize() == null) {
            return 60; // Default 1 hour
        }
        
        switch (window.getWindowType().toUpperCase()) {
            case "MINUTES":
                return window.getWindowSize();
            case "HOURS":
                return window.getWindowSize() * 60L;
            case "DAYS":
                return window.getWindowSize() * 24L * 60L;
            case "WEEKS":
                return window.getWindowSize() * 7L * 24L * 60L;
            default:
                return 60; // Default fallback
        }
    }
}