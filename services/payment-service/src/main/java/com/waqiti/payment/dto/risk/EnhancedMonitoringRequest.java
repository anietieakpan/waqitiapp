/**
 * Enhanced Monitoring Request DTO
 * Used for enabling or configuring enhanced monitoring for users
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedMonitoringRequest {
    
    /**
     * User ID to enable monitoring for
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Monitoring action (ENABLE, DISABLE, UPDATE)
     */
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "ENABLE|DISABLE|UPDATE", message = "Action must be ENABLE, DISABLE, or UPDATE")
    private String action;
    
    /**
     * Reason for enhanced monitoring
     */
    @NotBlank(message = "Reason is required")
    private String reason;
    
    /**
     * Monitoring level (BASIC, STANDARD, INTENSIVE, MAXIMUM)
     */
    @Pattern(regexp = "BASIC|STANDARD|INTENSIVE|MAXIMUM", message = "Level must be BASIC, STANDARD, INTENSIVE, or MAXIMUM")
    private String monitoringLevel;
    
    /**
     * Monitoring type (FRAUD, AML, SANCTIONS, BEHAVIORAL, ALL)
     */
    @NotBlank(message = "Monitoring type is required")
    private String monitoringType;
    
    /**
     * Duration in days (null for indefinite)
     */
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;
    
    /**
     * Start date for monitoring
     */
    private Instant startDate;
    
    /**
     * End date for monitoring
     */
    private Instant endDate;
    
    /**
     * Specific monitoring rules to apply
     */
    private List<String> monitoringRules;
    
    /**
     * Alert thresholds to set
     */
    private Map<String, Object> alertThresholds;
    
    /**
     * Transaction limits to apply
     */
    private Map<String, Object> transactionLimits;
    
    /**
     * Monitoring frequency (REAL_TIME, HOURLY, DAILY, WEEKLY)
     */
    @Pattern(regexp = "REAL_TIME|HOURLY|DAILY|WEEKLY", message = "Frequency must be REAL_TIME, HOURLY, DAILY, or WEEKLY")
    private String monitoringFrequency;
    
    /**
     * Notification settings for alerts
     */
    private Map<String, Object> notificationSettings;
    
    /**
     * Risk indicators to monitor specifically
     */
    private List<String> riskIndicators;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority;
    
    /**
     * Alert ID that triggered this request
     */
    private String alertId;
    
    /**
     * Fraud type that triggered monitoring
     */
    private String fraudType;
    
    /**
     * Requesting user/system
     */
    private String requestedBy;
    
    /**
     * Request source (SYSTEM, MANUAL, ALERT)
     */
    @Pattern(regexp = "SYSTEM|MANUAL|ALERT", message = "Source must be SYSTEM, MANUAL, or ALERT")
    private String requestSource;
    
    /**
     * Additional configuration parameters
     */
    private Map<String, Object> configuration;
    
    /**
     * Comments or notes
     */
    @Size(max = 1000, message = "Comments cannot exceed 1000 characters")
    private String comments;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Request timestamp
     */
    private Instant requestTime;
}