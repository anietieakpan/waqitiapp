package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive user risk profile
 * Aggregates historical behavior and risk patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskProfile {

    private String userId;
    private String userTier; // BRONZE, SILVER, GOLD, PLATINUM
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    // Account information
    private LocalDateTime accountCreationDate;
    private Boolean accountVerified;
    private Boolean kycCompleted;
    private String kycLevel; // BASIC, ENHANCED, FULL

    // Historical metrics
    private Integer totalTransactions;
    private BigDecimal totalTransactionVolume;
    private BigDecimal averageTransactionAmount;
    private BigDecimal maxTransactionAmount;

    // Recent activity (last 30 days)
    private Integer recentTransactionCount;
    private BigDecimal recentTransactionVolume;
    private Integer failedTransactionCount;
    private Integer chargebackCount;
    private Integer disputeCount;

    // Behavioral patterns
    private List<String> typicalTransactionTypes;
    private List<String> typicalMerchantCategories;
    private List<String> typicalCountries;
    private Map<String, Integer> hourlyActivityPattern; // hour -> transaction count
    private Map<String, Integer> dailyActivityPattern; // day of week -> transaction count

    // Device trust
    private List<String> trustedDevices;
    private List<String> trustedIpAddresses;
    private Boolean multiDeviceUser;

    // Risk indicators
    private Integer suspiciousActivityCount;
    private Integer blockedTransactionCount;
    private Boolean currentlyFrozen;
    private Instant lastFraudIncident;

    // Velocity metrics
    private BigDecimal dailyVelocity; // amount in last 24 hours
    private BigDecimal weeklyVelocity; // amount in last 7 days
    private BigDecimal monthlyVelocity; // amount in last 30 days
    private Integer dailyTransactionCount;

    // Credit/Risk score
    private Integer creditScore;
    private Double overallRiskScore; // 0.0 (low risk) to 1.0 (high risk)

    // Compliance
    private Boolean pepScreened; // Politically Exposed Person
    private Boolean sanctionsScreened;
    private Boolean adverseMediaScreened;
    private Instant lastComplianceCheck;

    // Geographic information
    private String primaryCountry;
    private String primaryCity;
    private Boolean frequentTraveler;

    // Profile metadata
    private Instant profileLastUpdated;
    private Map<String, Object> customAttributes;
}
