package com.waqiti.rewards.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Referral Leaderboard Entity
 *
 * Tracks user rankings in referral programs for competitive gamification
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_leaderboard", indexes = {
    @Index(name = "idx_referral_leaderboard_user", columnList = "userId"),
    @Index(name = "idx_referral_leaderboard_program", columnList = "programId"),
    @Index(name = "idx_referral_leaderboard_period", columnList = "periodType,periodEnd"),
    @Index(name = "idx_referral_leaderboard_rank", columnList = "programId,periodType,rank")
}, uniqueConstraints = {
    @UniqueConstraint(name = "unique_user_program_period",
                     columnNames = {"userId", "programId", "periodType", "periodStart", "periodEnd"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReferralLeaderboard {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "User ID is required")
    @Column(nullable = false)
    private UUID userId;

    @NotBlank(message = "Program ID is required")
    @Column(nullable = false, length = 100)
    private String programId;

    // ============================================================================
    // PERIOD INFORMATION
    // ============================================================================

    @NotBlank(message = "Period type is required")
    @Column(nullable = false, length = 20)
    private String periodType; // DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, ALL_TIME

    @NotNull(message = "Period start is required")
    @Column(nullable = false)
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    @Column(nullable = false)
    private LocalDate periodEnd;

    // ============================================================================
    // PERFORMANCE METRICS
    // ============================================================================

    @Builder.Default
    @Min(value = 0, message = "Total referrals must be non-negative")
    @Column(nullable = false)
    private Integer totalReferrals = 0;

    @Builder.Default
    @Min(value = 0, message = "Successful referrals must be non-negative")
    @Column
    private Integer successfulReferrals = 0;

    @Builder.Default
    @Min(value = 0, message = "Pending referrals must be non-negative")
    @Column
    private Integer pendingReferrals = 0;

    @Builder.Default
    @DecimalMin(value = "0.0000", message = "Conversion rate must be non-negative")
    @DecimalMax(value = "1.0000", message = "Conversion rate cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal conversionRate = BigDecimal.ZERO;

    // ============================================================================
    // REWARDS EARNED
    // ============================================================================

    @Builder.Default
    @Min(value = 0, message = "Total points must be non-negative")
    @Column
    private Long totalPointsEarned = 0L;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total cashback must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal totalCashbackEarned = BigDecimal.ZERO;

    @Builder.Default
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Column(length = 3)
    private String currency = "USD";

    // ============================================================================
    // REVENUE IMPACT
    // ============================================================================

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Total revenue must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal totalRevenueGenerated = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Average revenue must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal averageRevenuePerReferral = BigDecimal.ZERO;

    // ============================================================================
    // RANKING
    // ============================================================================

    @Min(value = 1, message = "Rank must be at least 1")
    @Column
    private Integer rank;

    @Min(value = 1, message = "Previous rank must be at least 1")
    @Column
    private Integer previousRank;

    @Builder.Default
    @Column
    private Integer rankChange = 0;

    @Min(value = 1, message = "Tier level must be at least 1")
    @Column
    private Integer tierLevel;

    // ============================================================================
    // STREAKS
    // ============================================================================

    @Builder.Default
    @Min(value = 0, message = "Current streak must be non-negative")
    @Column
    private Integer currentStreakDays = 0;

    @Builder.Default
    @Min(value = 0, message = "Longest streak must be non-negative")
    @Column
    private Integer longestStreakDays = 0;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @NotNull(message = "Last updated timestamp is required")
    @Column(nullable = false)
    private LocalDateTime lastUpdatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastUpdatedAt == null) {
            lastUpdatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Updates rank and calculates rank change
     */
    public void updateRank(int newRank) {
        this.previousRank = this.rank;
        this.rank = newRank;

        if (previousRank != null) {
            // Rank change is negative when improving (going from 5 to 3 = -2)
            this.rankChange = previousRank - newRank;
        } else {
            this.rankChange = 0;
        }
    }

    /**
     * Increments total referrals
     */
    public void incrementTotalReferrals() {
        this.totalReferrals++;
        updateConversionRate();
    }

    /**
     * Increments successful referrals
     */
    public void incrementSuccessfulReferrals() {
        this.successfulReferrals++;
        updateConversionRate();
    }

    /**
     * Adds points earned
     */
    public void addPointsEarned(long points) {
        this.totalPointsEarned += points;
    }

    /**
     * Adds cashback earned
     */
    public void addCashbackEarned(BigDecimal amount) {
        this.totalCashbackEarned = this.totalCashbackEarned.add(amount);
    }

    /**
     * Adds revenue generated
     */
    public void addRevenueGenerated(BigDecimal revenue) {
        this.totalRevenueGenerated = this.totalRevenueGenerated.add(revenue);
        updateAverageRevenue();
    }

    /**
     * Updates the conversion rate
     */
    private void updateConversionRate() {
        if (totalReferrals == 0) {
            this.conversionRate = BigDecimal.ZERO;
            return;
        }

        this.conversionRate = BigDecimal.valueOf(successfulReferrals)
            .divide(BigDecimal.valueOf(totalReferrals), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Updates the average revenue per referral
     */
    private void updateAverageRevenue() {
        if (totalReferrals == 0) {
            this.averageRevenuePerReferral = BigDecimal.ZERO;
            return;
        }

        this.averageRevenuePerReferral = this.totalRevenueGenerated
            .divide(BigDecimal.valueOf(totalReferrals), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Updates the current streak
     */
    public void updateStreak(int newStreakDays) {
        this.currentStreakDays = newStreakDays;

        if (newStreakDays > longestStreakDays) {
            this.longestStreakDays = newStreakDays;
        }
    }

    /**
     * Resets the current streak
     */
    public void resetStreak() {
        this.currentStreakDays = 0;
    }

    /**
     * Checks if rank improved
     */
    public boolean isRankImproved() {
        return rankChange != null && rankChange > 0;
    }

    /**
     * Checks if rank declined
     */
    public boolean isRankDeclined() {
        return rankChange != null && rankChange < 0;
    }

    /**
     * Gets the total reward value (combining points and cashback)
     * Assumes 1 point = $0.01 for calculation
     */
    public BigDecimal getTotalRewardValue() {
        BigDecimal pointsValue = BigDecimal.valueOf(totalPointsEarned)
            .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        return pointsValue.add(totalCashbackEarned);
    }

    /**
     * Calculates success rate percentage (0-100)
     */
    public double getSuccessRatePercentage() {
        return conversionRate.multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /**
     * Checks if the period is currently active
     */
    public boolean isActivePeriod() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(periodStart) && !now.isAfter(periodEnd);
    }

    /**
     * Gets rank change as a display string (e.g., "+5", "-3", "NEW")
     */
    public String getRankChangeDisplay() {
        if (previousRank == null) {
            return "NEW";
        }
        if (rankChange == 0) {
            return "-";
        }
        return rankChange > 0 ? "+" + rankChange : String.valueOf(rankChange);
    }
}
