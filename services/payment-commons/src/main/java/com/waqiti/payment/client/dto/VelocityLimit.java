package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Velocity Limit
 * 
 * Represents a velocity limit configuration for fraud prevention.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityLimit {
    
    /**
     * Limit ID
     */
    private String limitId;
    
    /**
     * Limit name
     */
    private String limitName;
    
    /**
     * Limit description
     */
    private String description;
    
    /**
     * Entity type this limit applies to
     */
    private EntityType entityType;
    
    /**
     * Velocity check type
     */
    private VelocityCheckRequest.VelocityCheckType checkType;
    
    /**
     * Time window configuration
     */
    private TimeWindow timeWindow;
    
    /**
     * Limit value
     */
    private LimitValue limitValue;
    
    /**
     * Limit status
     */
    private LimitStatus status;
    
    /**
     * Priority level
     */
    private Priority priority;
    
    /**
     * Enforcement action
     */
    private EnforcementAction enforcementAction;
    
    /**
     * Application scope
     */
    private ApplicationScope scope;
    
    /**
     * Conditions for applying this limit
     */
    private LimitConditions conditions;
    
    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Created by user
     */
    private String createdBy;
    
    /**
     * Last updated by user
     */
    private String updatedBy;
    
    /**
     * Effective from date
     */
    private LocalDateTime effectiveFrom;
    
    /**
     * Effective until date (null for indefinite)
     */
    private LocalDateTime effectiveUntil;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Tags for categorization
     */
    private String tags;
    
    /**
     * Notes about this limit
     */
    private String notes;
    
    public enum EntityType {
        USER,
        MERCHANT,
        CARD,
        DEVICE,
        IP_ADDRESS,
        EMAIL,
        PHONE,
        ACCOUNT,
        GLOBAL
    }
    
    public enum LimitStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        EXPIRED,
        DRAFT
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    public enum EnforcementAction {
        ALLOW,
        WARN,
        REVIEW,
        DECLINE,
        BLOCK
    }
    
    public enum ApplicationScope {
        ALL_TRANSACTIONS,
        SPECIFIC_MERCHANTS,
        SPECIFIC_REGIONS,
        SPECIFIC_AMOUNTS,
        SPECIFIC_TYPES,
        CUSTOM
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeWindow {
        private String windowType; // MINUTES, HOURS, DAYS, WEEKS, MONTHS
        private Integer windowSize;
        private Boolean rolling;
        private String timezone;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitValue {
        private LimitValueType valueType;
        private BigDecimal amount;
        private Integer count;
        private String stringValue;
        private Map<String, Object> complexValue;
    }
    
    public enum LimitValueType {
        AMOUNT,
        COUNT,
        PERCENTAGE,
        RATIO,
        CUSTOM
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitConditions {
        private AmountConditions amountConditions;
        private GeographicConditions geographicConditions;
        private MerchantConditions merchantConditions;
        private UserConditions userConditions;
        private TimeConditions timeConditions;
        private Map<String, Object> customConditions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountConditions {
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private String currency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicConditions {
        private java.util.List<String> includedCountries;
        private java.util.List<String> excludedCountries;
        private java.util.List<String> includedRegions;
        private java.util.List<String> excludedRegions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantConditions {
        private java.util.List<String> includedMerchants;
        private java.util.List<String> excludedMerchants;
        private java.util.List<String> includedCategories;
        private java.util.List<String> excludedCategories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserConditions {
        private java.util.List<String> includedUserTypes;
        private java.util.List<String> excludedUserTypes;
        private java.util.List<String> includedUserGroups;
        private java.util.List<String> excludedUserGroups;
        private Integer minAccountAge;
        private String riskLevel;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeConditions {
        private String startTime;
        private String endTime;
        private java.util.List<String> daysOfWeek;
        private java.util.List<String> excludedDates;
        private String timezone;
    }
    
    /**
     * Check if limit is currently active
     */
    public boolean isActive() {
        return status == LimitStatus.ACTIVE && isCurrentlyEffective();
    }
    
    /**
     * Check if limit is currently effective (within date range)
     */
    public boolean isCurrentlyEffective() {
        LocalDateTime now = LocalDateTime.now();
        
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }
        
        if (effectiveUntil != null && now.isAfter(effectiveUntil)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if limit is expired
     */
    public boolean isExpired() {
        return status == LimitStatus.EXPIRED ||
               (effectiveUntil != null && LocalDateTime.now().isAfter(effectiveUntil));
    }
    
    /**
     * Check if limit is high priority
     */
    public boolean isHighPriority() {
        return priority == Priority.HIGH || priority == Priority.CRITICAL;
    }
    
    /**
     * Check if limit will block transactions
     */
    public boolean isBlockingLimit() {
        return enforcementAction == EnforcementAction.BLOCK ||
               enforcementAction == EnforcementAction.DECLINE;
    }
    
    /**
     * Get time window in minutes
     */
    public Long getTimeWindowMinutes() {
        if (timeWindow == null || timeWindow.getWindowType() == null || timeWindow.getWindowSize() == null) {
            return null;
        }
        
        switch (timeWindow.getWindowType().toUpperCase()) {
            case "MINUTES":
                return timeWindow.getWindowSize().longValue();
            case "HOURS":
                return timeWindow.getWindowSize() * 60L;
            case "DAYS":
                return timeWindow.getWindowSize() * 24L * 60L;
            case "WEEKS":
                return timeWindow.getWindowSize() * 7L * 24L * 60L;
            case "MONTHS":
                return timeWindow.getWindowSize() * 30L * 24L * 60L; // Approximate
            default:
                return null;
        }
    }
    
    /**
     * Get limit display value
     */
    public String getLimitDisplayValue() {
        if (limitValue == null) {
            return "Not set";
        }
        
        switch (limitValue.getValueType()) {
            case AMOUNT:
                return limitValue.getAmount() != null ? limitValue.getAmount().toString() : "0";
            case COUNT:
                return limitValue.getCount() != null ? limitValue.getCount().toString() : "0";
            case PERCENTAGE:
                return limitValue.getAmount() != null ? limitValue.getAmount() + "%" : "0%";
            case RATIO:
                return limitValue.getStringValue() != null ? limitValue.getStringValue() : "1:1";
            default:
                return limitValue.getStringValue() != null ? limitValue.getStringValue() : "Custom";
        }
    }
    
    /**
     * Get time window display text
     */
    public String getTimeWindowDisplay() {
        if (timeWindow == null) {
            return "Not configured";
        }
        
        String size = timeWindow.getWindowSize() != null ? timeWindow.getWindowSize().toString() : "1";
        String type = timeWindow.getWindowType() != null ? timeWindow.getWindowType().toLowerCase() : "hour";
        
        if (timeWindow.getWindowSize() != null && timeWindow.getWindowSize() == 1) {
            type = type.endsWith("s") ? type.substring(0, type.length() - 1) : type;
        }
        
        String rolling = timeWindow.getRolling() != null && timeWindow.getRolling() ? " (rolling)" : "";
        
        return size + " " + type + rolling;
    }
    
    /**
     * Validate limit configuration
     */
    public boolean isValid() {
        return limitId != null && !limitId.trim().isEmpty() &&
               entityType != null &&
               checkType != null &&
               timeWindow != null &&
               limitValue != null &&
               status != null;
    }
}