package com.waqiti.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Risk Threshold Entity
 *
 * Configurable thresholds for risk assessment including:
 * - Amount limits
 * - Velocity limits
 * - Score thresholds
 * - Geographic limits
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "risk_thresholds")
public class RiskThreshold {

    @Id
    private String id;

    @Indexed
    @NotBlank
    private String thresholdName;

    @NotBlank
    private String thresholdType; // AMOUNT, VELOCITY, SCORE, GEOGRAPHIC, TIME

    @NotBlank
    private String applicableTo; // USER, MERCHANT, TRANSACTION, GLOBAL

    private String entityId; // Specific user/merchant ID, or null for global

    @NotNull
    @Builder.Default
    private Boolean enabled = true;

    // Amount Thresholds
    private BigDecimal maxTransactionAmount;
    private BigDecimal dailyTransactionLimit;
    private BigDecimal weeklyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;

    // Velocity Thresholds
    private Integer maxTransactionsPerHour;
    private Integer maxTransactionsPerDay;
    private Integer maxTransactionsPerWeek;

    private BigDecimal maxAmountPerHour;
    private BigDecimal maxAmountPerDay;
    private BigDecimal maxAmountPerWeek;

    private Integer maxUniqueMerchantsPerDay;
    private Integer maxFailedAttemptsPerHour;

    // Score Thresholds
    @Min(0)
    @Max(1)
    private Double lowRiskThreshold;

    @Min(0)
    @Max(1)
    private Double mediumRiskThreshold;

    @Min(0)
    @Max(1)
    private Double highRiskThreshold;

    @Min(0)
    @Max(1)
    private Double criticalRiskThreshold;

    // Behavioral Thresholds
    @Min(0)
    @Max(1)
    private Double behaviorDeviationThreshold;

    @Min(0)
    @Max(1)
    private Double deviceTrustThreshold;

    // Geographic Thresholds
    private Integer maxCountriesPerDay;
    private BigDecimal maxDistanceKm; // For impossible travel detection

    // Time-based Thresholds
    private Integer minTimeBetweenTransactionsSec;
    private Integer maxSessionDurationMin;
    private Integer minSessionDurationSec;

    // Priority and Enforcement
    @Min(0)
    @Max(100)
    private Integer priority; // Higher number = higher priority

    @NotNull
    @Builder.Default
    private String enforcementAction = "ALERT"; // ALERT, BLOCK, REVIEW

    // Threshold Lifecycle
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;

    private String createdBy;
    private String updatedBy;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Usage Statistics
    @Builder.Default
    private Long violationCount = 0L;

    private LocalDateTime lastViolationAt;

    // Metadata
    private Map<String, Object> metadata;
    private String description;
    private String category; // FRAUD, COMPLIANCE, AML, OPERATIONAL

    /**
     * Check if threshold is currently effective
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();

        if (!enabled) {
            return false;
        }

        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }

        if (effectiveUntil != null && now.isAfter(effectiveUntil)) {
            return false;
        }

        return true;
    }

    /**
     * Record threshold violation
     */
    public void recordViolation() {
        this.violationCount++;
        this.lastViolationAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if amount exceeds threshold
     */
    public boolean exceedsAmountThreshold(BigDecimal amount, String period) {
        switch (period.toUpperCase()) {
            case "TRANSACTION":
                return maxTransactionAmount != null &&
                       amount.compareTo(maxTransactionAmount) > 0;
            case "DAILY":
                return dailyTransactionLimit != null &&
                       amount.compareTo(dailyTransactionLimit) > 0;
            case "WEEKLY":
                return weeklyTransactionLimit != null &&
                       amount.compareTo(weeklyTransactionLimit) > 0;
            case "MONTHLY":
                return monthlyTransactionLimit != null &&
                       amount.compareTo(monthlyTransactionLimit) > 0;
            default:
                return false;
        }
    }

    /**
     * Check if velocity exceeds threshold
     */
    public boolean exceedsVelocityThreshold(int count, String period) {
        switch (period.toUpperCase()) {
            case "HOURLY":
                return maxTransactionsPerHour != null &&
                       count > maxTransactionsPerHour;
            case "DAILY":
                return maxTransactionsPerDay != null &&
                       count > maxTransactionsPerDay;
            case "WEEKLY":
                return maxTransactionsPerWeek != null &&
                       count > maxTransactionsPerWeek;
            default:
                return false;
        }
    }

    /**
     * Determine risk level based on score
     */
    public String determineRiskLevel(double score) {
        if (criticalRiskThreshold != null && score >= criticalRiskThreshold) {
            return "CRITICAL";
        }
        if (highRiskThreshold != null && score >= highRiskThreshold) {
            return "HIGH";
        }
        if (mediumRiskThreshold != null && score >= mediumRiskThreshold) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
