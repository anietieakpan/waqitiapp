package com.waqiti.rewards.domain;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Referral Statistics Entity
 *
 * Aggregated statistics for referral program performance analysis
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_statistics", indexes = {
    @Index(name = "idx_referral_statistics_program", columnList = "programId"),
    @Index(name = "idx_referral_statistics_period", columnList = "periodEnd")
}, uniqueConstraints = {
    @UniqueConstraint(name = "unique_program_period", columnNames = {"programId", "periodStart", "periodEnd"})
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReferralStatistics {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Program ID is required")
    @Column(nullable = false, length = 100)
    private String programId;

    // ============================================================================
    // PERIOD
    // ============================================================================

    @NotNull(message = "Period start is required")
    @Column(nullable = false)
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    @Column(nullable = false)
    private LocalDate periodEnd;

    // ============================================================================
    // REFERRAL METRICS
    // ============================================================================

    @Builder.Default
    @Min(value = 0, message = "Total referrals must be non-negative")
    @Column(nullable = false)
    private Integer totalReferrals = 0;

    @Builder.Default
    @Min(value = 0, message = "Pending referrals must be non-negative")
    @Column
    private Integer pendingReferrals = 0;

    @Builder.Default
    @Min(value = 0, message = "Successful referrals must be non-negative")
    @Column
    private Integer successfulReferrals = 0;

    @Builder.Default
    @Min(value = 0, message = "Rejected referrals must be non-negative")
    @Column
    private Integer rejectedReferrals = 0;

    @Builder.Default
    @Min(value = 0, message = "Expired referrals must be non-negative")
    @Column
    private Integer expiredReferrals = 0;

    // ============================================================================
    // CONVERSION METRICS
    // ============================================================================

    @Builder.Default
    @Min(value = 0, message = "Total clicks must be non-negative")
    @Column
    private Integer totalClicks = 0;

    @Builder.Default
    @Min(value = 0, message = "Unique clicks must be non-negative")
    @Column
    private Integer uniqueClicks = 0;

    @Builder.Default
    @Min(value = 0, message = "Total signups must be non-negative")
    @Column
    private Integer totalSignups = 0;

    @Builder.Default
    @DecimalMin(value = "0.0000", message = "Conversion rate must be non-negative")
    @DecimalMax(value = "1.0000", message = "Conversion rate cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal conversionRate = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.0000", message = "Click to signup rate must be non-negative")
    @DecimalMax(value = "1.0000", message = "Click to signup rate cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal clickToSignupRate = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.0000", message = "Signup to conversion rate must be non-negative")
    @DecimalMax(value = "1.0000", message = "Signup to conversion rate cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal signupToConversionRate = BigDecimal.ZERO;

    // ============================================================================
    // FINANCIAL METRICS
    // ============================================================================

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total rewards issued must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal totalRewardsIssued = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total rewards redeemed must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal totalRewardsRedeemed = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total rewards pending must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal totalRewardsPending = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total rewards expired must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal totalRewardsExpired = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Average reward amount must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal averageRewardAmount = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total revenue generated must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal totalRevenueGenerated = BigDecimal.ZERO;

    // ============================================================================
    // BREAKDOWN BY DIMENSION (JSONB)
    // ============================================================================

    /**
     * Breakdown by channel
     * Example: {"EMAIL": {"clicks": 1000, "conversions": 50}, "SMS": {...}}
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> byChannel = new HashMap<>();

    /**
     * Breakdown by source
     * Example: {"ORGANIC": 100, "PAID": 50}
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> bySource = new HashMap<>();

    /**
     * Breakdown by region
     * Example: {"US": 200, "UK": 100, "CA": 50}
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> byRegion = new HashMap<>();

    // ============================================================================
    // TOP PERFORMERS
    // ============================================================================

    /**
     * Top referrers
     * Example: [{"userId": "...", "referrals": 50, "rewards": 5000}, ...]
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> topReferrers = new HashMap<>();

    // ============================================================================
    // ENGAGEMENT METRICS
    // ============================================================================

    @DecimalMin(value = "0.00", message = "Average time to conversion must be non-negative")
    @Column(precision = 10, scale = 2)
    private BigDecimal averageTimeToConversionHours;

    @DecimalMin(value = "0.0000", message = "Bounce rate must be non-negative")
    @DecimalMax(value = "1.0000", message = "Bounce rate cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal bounceRate;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        recalculateMetrics();
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Recalculates all derived metrics
     */
    public void recalculateMetrics() {
        calculateConversionRate();
        calculateClickToSignupRate();
        calculateSignupToConversionRate();
        calculateAverageRewardAmount();
    }

    /**
     * Calculates overall conversion rate (successful / total)
     */
    private void calculateConversionRate() {
        if (totalReferrals == 0) {
            this.conversionRate = BigDecimal.ZERO;
            return;
        }

        this.conversionRate = BigDecimal.valueOf(successfulReferrals)
            .divide(BigDecimal.valueOf(totalReferrals), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculates click to signup rate
     */
    private void calculateClickToSignupRate() {
        if (totalClicks == 0) {
            this.clickToSignupRate = BigDecimal.ZERO;
            return;
        }

        this.clickToSignupRate = BigDecimal.valueOf(totalSignups)
            .divide(BigDecimal.valueOf(totalClicks), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculates signup to conversion rate
     */
    private void calculateSignupToConversionRate() {
        if (totalSignups == 0) {
            this.signupToConversionRate = BigDecimal.ZERO;
            return;
        }

        this.signupToConversionRate = BigDecimal.valueOf(successfulReferrals)
            .divide(BigDecimal.valueOf(totalSignups), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculates average reward amount
     */
    private void calculateAverageRewardAmount() {
        if (successfulReferrals == 0) {
            this.averageRewardAmount = BigDecimal.ZERO;
            return;
        }

        this.averageRewardAmount = totalRewardsIssued
            .divide(BigDecimal.valueOf(successfulReferrals), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Increments total referrals
     */
    public void incrementTotalReferrals() {
        this.totalReferrals++;
    }

    /**
     * Increments successful referrals
     */
    public void incrementSuccessfulReferrals() {
        this.successfulReferrals++;
    }

    /**
     * Increments total clicks
     */
    public void incrementTotalClicks() {
        this.totalClicks++;
    }

    /**
     * Increments unique clicks
     */
    public void incrementUniqueClicks() {
        this.uniqueClicks++;
    }

    /**
     * Increments total signups
     */
    public void incrementTotalSignups() {
        this.totalSignups++;
    }

    /**
     * Adds to total rewards issued
     */
    public void addRewardsIssued(BigDecimal amount) {
        this.totalRewardsIssued = this.totalRewardsIssued.add(amount);
    }

    /**
     * Adds to total rewards redeemed
     */
    public void addRewardsRedeemed(BigDecimal amount) {
        this.totalRewardsRedeemed = this.totalRewardsRedeemed.add(amount);
    }

    /**
     * Adds to total revenue generated
     */
    public void addRevenueGenerated(BigDecimal amount) {
        this.totalRevenueGenerated = this.totalRevenueGenerated.add(amount);
    }

    /**
     * Gets ROI (Return on Investment) as percentage
     */
    public BigDecimal getROI() {
        if (totalRewardsIssued.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalRevenueGenerated
            .subtract(totalRewardsIssued)
            .divide(totalRewardsIssued, 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Gets cost per acquisition (CPA)
     */
    public BigDecimal getCostPerAcquisition() {
        if (successfulReferrals == 0) {
            return BigDecimal.ZERO;
        }

        return totalRewardsIssued
            .divide(BigDecimal.valueOf(successfulReferrals), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Gets redemption rate (redeemed / issued)
     */
    public BigDecimal getRedemptionRate() {
        if (totalRewardsIssued.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalRewardsRedeemed
            .divide(totalRewardsIssued, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Checks if the period is currently active
     */
    public boolean isActivePeriod() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(periodStart) && !now.isAfter(periodEnd);
    }
}
