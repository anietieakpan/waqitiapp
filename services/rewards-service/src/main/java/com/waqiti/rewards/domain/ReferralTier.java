package com.waqiti.rewards.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Referral Tier Entity
 *
 * Defines tiered reward structures for high-performing referrers
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_tiers", indexes = {
    @Index(name = "idx_referral_tiers_program", columnList = "program_id"),
    @Index(name = "idx_referral_tiers_level", columnList = "tierLevel"),
    @Index(name = "idx_referral_tiers_active", columnList = "isActive")
}, uniqueConstraints = {
    @UniqueConstraint(name = "unique_program_tier_level", columnNames = {"program_id", "tierLevel"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "program")
public class ReferralTier {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Tier ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String tierId;

    @NotNull(message = "Program is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", referencedColumnName = "programId", nullable = false)
    private ReferralProgram program;

    // ============================================================================
    // TIER DETAILS
    // ============================================================================

    @NotBlank(message = "Tier name is required")
    @Column(nullable = false)
    private String tierName;

    @NotNull(message = "Tier level is required")
    @Min(value = 1, message = "Tier level must be at least 1")
    @Column(nullable = false)
    private Integer tierLevel;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ============================================================================
    // REQUIREMENTS TO REACH THIS TIER
    // ============================================================================

    @NotNull(message = "Minimum referrals is required")
    @Min(value = 0, message = "Minimum referrals must be non-negative")
    @Column(nullable = false)
    private Integer minReferrals;

    @Min(value = 0, message = "Maximum referrals must be non-negative")
    @Column
    private Integer maxReferrals;

    @Builder.Default
    @Min(value = 0, message = "Minimum successful conversions must be non-negative")
    @Column
    private Integer minSuccessfulConversions = 0;

    @DecimalMin(value = "0.00", message = "Minimum revenue must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal minRevenueGenerated;

    // ============================================================================
    // REWARDS MULTIPLIER AND BONUSES
    // ============================================================================

    @NotNull(message = "Reward multiplier is required")
    @DecimalMin(value = "1.0000", message = "Reward multiplier must be at least 1.0")
    @Builder.Default
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rewardMultiplier = BigDecimal.ONE;

    @Min(value = 0, message = "Bonus points must be non-negative")
    @Column
    private Long bonusPoints;

    @DecimalMin(value = "0.00", message = "Bonus cashback must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal bonusCashbackAmount;

    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Builder.Default
    @Column(length = 3)
    private String bonusCashbackCurrency = "USD";

    // ============================================================================
    // ADDITIONAL PERKS
    // ============================================================================

    @ElementCollection
    @CollectionTable(name = "referral_tier_benefits",
                     joinColumns = @JoinColumn(name = "tier_id"))
    @Column(name = "benefit")
    @Builder.Default
    private Set<String> additionalBenefits = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "referral_tier_exclusive_offers",
                     joinColumns = @JoinColumn(name = "tier_id"))
    @Column(name = "offer")
    @Builder.Default
    private Set<String> exclusiveOffers = new HashSet<>();

    @Builder.Default
    @Column(nullable = false)
    private Boolean prioritySupport = false;

    // ============================================================================
    // DISPLAY CONFIGURATION
    // ============================================================================

    @Column(columnDefinition = "TEXT")
    private String badgeIconUrl;

    @Size(min = 4, max = 7, message = "Badge color must be a valid hex color code")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Badge color must be a valid hex color code")
    @Column(length = 7)
    private String badgeColor;

    // ============================================================================
    // STATUS
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

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
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Checks if a user qualifies for this tier based on their metrics
     */
    public boolean qualifiesForTier(int referralCount, int conversionCount, BigDecimal revenue) {
        // Check minimum referrals
        if (referralCount < minReferrals) {
            return false;
        }

        // Check maximum referrals (if specified)
        if (maxReferrals != null && referralCount > maxReferrals) {
            return false;
        }

        // Check minimum conversions
        if (conversionCount < minSuccessfulConversions) {
            return false;
        }

        // Check minimum revenue
        if (minRevenueGenerated != null && revenue.compareTo(minRevenueGenerated) < 0) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the actual reward amount after applying tier multiplier
     */
    public BigDecimal calculateTieredReward(BigDecimal baseReward) {
        return baseReward.multiply(rewardMultiplier);
    }

    /**
     * Calculates total bonus for reaching this tier
     */
    public BigDecimal getTotalBonusValue() {
        BigDecimal total = BigDecimal.ZERO;

        if (bonusPoints != null) {
            // Assume 1 point = $0.01 for calculation purposes
            total = total.add(BigDecimal.valueOf(bonusPoints).divide(BigDecimal.valueOf(100)));
        }

        if (bonusCashbackAmount != null) {
            total = total.add(bonusCashbackAmount);
        }

        return total;
    }

    /**
     * Checks if this tier has special perks
     */
    public boolean hasSpecialPerks() {
        return !additionalBenefits.isEmpty() ||
               !exclusiveOffers.isEmpty() ||
               prioritySupport;
    }

    /**
     * Gets the referral range for this tier as a string
     */
    public String getReferralRange() {
        if (maxReferrals == null) {
            return minReferrals + "+";
        }
        return minReferrals + "-" + maxReferrals;
    }
}
