package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Merchant risk profile for transaction risk assessment
 * Tracks merchant behavior and historical risk indicators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRiskProfile {

    private String merchantId;
    private String merchantName;
    private String merchantCategory; // MCC code
    private String industry;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    // Business information
    private LocalDateTime merchantOnboardingDate;
    private Boolean merchantVerified;
    private String merchantType; // INDIVIDUAL, BUSINESS, ENTERPRISE
    private String country;

    // Financial metrics
    private BigDecimal totalProcessingVolume;
    private BigDecimal averageTicketSize;
    private BigDecimal maxTicketSize;
    private Integer totalTransactions;

    // Recent performance (last 30 days)
    private Integer recentTransactionCount;
    private BigDecimal recentVolume;
    private BigDecimal chargebackRate; // percentage
    private BigDecimal refundRate; // percentage
    private BigDecimal disputeRate; // percentage

    // Risk indicators
    private Integer fraudIncidentCount;
    private Integer suspiciousActivityReports;
    private Boolean highRiskCategory; // Based on MCC
    private List<String> riskyCountries; // Countries flagged for this merchant

    // Operational metrics
    private Double averageAuthorizationRate; // percentage
    private Double averageCaptureRate; // percentage
    private Integer failedTransactionCount;
    private Integer declinedTransactionCount;

    // Velocity metrics
    private BigDecimal dailyVolume;
    private BigDecimal weeklyVolume;
    private BigDecimal monthlyVolume;
    private Integer peakHourlyTransactions;

    // Compliance
    private Boolean pciCompliant;
    private Boolean amlCompliant;
    private Boolean sanctionsScreened;
    private LocalDateTime lastComplianceAudit;

    // Historical patterns
    private Map<String, BigDecimal> countryVolumeDistribution; // country -> volume
    private Map<String, Integer> hourlyTransactionPattern; // hour -> count
    private List<String> typicalCustomerCountries;

    // Fraud signals
    private Double fraudScore; // 0.0 to 1.0
    private Boolean currentlyBlocked;
    private Boolean underInvestigation;
    private Integer consecutiveFailures;

    // Metadata
    private LocalDateTime profileLastUpdated;
    private Map<String, Object> customAttributes;
}
