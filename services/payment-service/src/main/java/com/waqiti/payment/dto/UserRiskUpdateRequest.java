package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO for user risk profile update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserRiskUpdateRequest {
    
    @NotNull
    private Integer riskScore;
    
    @NotNull
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    private String reason;
    private String updatedBy;
    
    // Risk factors
    private List<String> riskFactors;
    private Map<String, BigDecimal> riskScoreBreakdown;
    
    // Behavioral analysis
    private BigDecimal behaviorScore;
    private String behaviorProfile;
    private List<String> behaviorIndicators;
    
    // Transaction patterns
    private BigDecimal averageTransactionAmount;
    private Integer transactionFrequency;
    private String transactionPatterns;
    
    // Geographic patterns
    private List<String> usualLocations;
    private List<String> suspiciousLocations;
    
    // Device patterns
    private List<String> trustedDevices;
    private List<String> suspiciousDevices;
    
    // Manual overrides
    private boolean manualOverride;
    private String overrideReason;
    private String overrideExpiryDate;
    
    // Monitoring settings
    private boolean requiresMonitoring;
    private String monitoringLevel;
    private Integer monitoringDurationDays;
    
    private Map<String, Object> additionalData;
}