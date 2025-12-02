package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.RewardType;
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
 * Referral Milestone Entity
 *
 * Defines achievement milestones within referral programs
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_milestones", indexes = {
    @Index(name = "idx_referral_milestones_program", columnList = "program_id"),
    @Index(name = "idx_referral_milestones_active", columnList = "isActive"),
    @Index(name = "idx_referral_milestones_display", columnList = "program_id,displayOrder")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"program", "achievements"})
public class ReferralMilestone {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Milestone ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String milestoneId;

    @NotNull(message = "Program is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", referencedColumnName = "programId", nullable = false)
    private ReferralProgram program;

    // ============================================================================
    // MILESTONE DETAILS
    // ============================================================================

    @NotBlank(message = "Milestone name is required")
    @Column(nullable = false)
    private String milestoneName;

    @NotBlank(message = "Milestone type is required")
    @Column(nullable = false, length = 50)
    private String milestoneType; // REFERRAL_COUNT, CONVERSION_COUNT, REVENUE_TARGET

    @Column(columnDefinition = "TEXT")
    private String description;

    // ============================================================================
    // ACHIEVEMENT CRITERIA
    // ============================================================================

    @Min(value = 0, message = "Required referrals must be non-negative")
    @Column
    private Integer requiredReferrals;

    @Min(value = 0, message = "Required conversions must be non-negative")
    @Column
    private Integer requiredConversions;

    @DecimalMin(value = "0.00", message = "Required revenue must be non-negative")
    @Column(precision = 18, scale = 2)
    private BigDecimal requiredRevenue;

    @Min(value = 1, message = "Required timeframe must be at least 1 day")
    @Column
    private Integer requiredTimeframeDays;

    // ============================================================================
    // REWARDS
    // ============================================================================

    @NotNull(message = "Reward type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RewardType rewardType;

    @Min(value = 0, message = "Points reward must be non-negative")
    @Column
    private Long pointsReward;

    @DecimalMin(value = "0.00", message = "Cashback reward must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal cashbackReward;

    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Builder.Default
    @Column(length = 3)
    private String currency = "USD";

    @Column(columnDefinition = "TEXT")
    private String specialOfferDescription;

    @Column(columnDefinition = "TEXT")
    private String badgeIconUrl;

    // ============================================================================
    // STATUS AND CONFIGURATION
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isRepeatable = false;

    @Min(value = 0, message = "Cooldown period must be non-negative")
    @Column
    private Integer cooldownPeriodDays;

    // ============================================================================
    // DISPLAY
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String progressMessageTemplate;

    @Column(columnDefinition = "TEXT")
    private String completionMessageTemplate;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ============================================================================
    // RELATIONSHIPS
    // ============================================================================

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ReferralMilestoneAchievement> achievements = new HashSet<>();

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
     * Checks if a user has met the milestone criteria
     */
    public boolean isCriteriaMet(int referralCount, int conversionCount, BigDecimal revenue) {
        boolean met = true;

        if (requiredReferrals != null) {
            met = met && referralCount >= requiredReferrals;
        }

        if (requiredConversions != null) {
            met = met && conversionCount >= requiredConversions;
        }

        if (requiredRevenue != null) {
            met = met && revenue.compareTo(requiredRevenue) >= 0;
        }

        return met;
    }

    /**
     * Checks if the milestone has any criteria defined
     */
    public boolean hasCriteria() {
        return requiredReferrals != null ||
               requiredConversions != null ||
               requiredRevenue != null;
    }

    /**
     * Gets the reward value (points or cashback as BigDecimal)
     */
    public BigDecimal getRewardValue() {
        if (rewardType == RewardType.POINTS && pointsReward != null) {
            return BigDecimal.valueOf(pointsReward);
        }
        return cashbackReward != null ? cashbackReward : BigDecimal.ZERO;
    }

    /**
     * Generates a progress message for a user
     */
    public String generateProgressMessage(int currentReferrals, int currentConversions, BigDecimal currentRevenue) {
        if (progressMessageTemplate == null) {
            return String.format("Progress: %d/%d referrals", currentReferrals, requiredReferrals);
        }

        return progressMessageTemplate
            .replace("{current_referrals}", String.valueOf(currentReferrals))
            .replace("{required_referrals}", String.valueOf(requiredReferrals))
            .replace("{current_conversions}", String.valueOf(currentConversions))
            .replace("{required_conversions}", String.valueOf(requiredConversions))
            .replace("{current_revenue}", currentRevenue.toString())
            .replace("{required_revenue}", requiredRevenue != null ? requiredRevenue.toString() : "0");
    }

    /**
     * Calculates progress percentage (0-100)
     */
    public int getProgressPercentage(int currentReferrals, int currentConversions, BigDecimal currentRevenue) {
        if (!hasCriteria()) {
            return 0;
        }

        double totalProgress = 0;
        int criteriaCount = 0;

        if (requiredReferrals != null && requiredReferrals > 0) {
            totalProgress += Math.min(100.0, (currentReferrals * 100.0) / requiredReferrals);
            criteriaCount++;
        }

        if (requiredConversions != null && requiredConversions > 0) {
            totalProgress += Math.min(100.0, (currentConversions * 100.0) / requiredConversions);
            criteriaCount++;
        }

        if (requiredRevenue != null && requiredRevenue.compareTo(BigDecimal.ZERO) > 0) {
            double revenueProgress = currentRevenue.divide(requiredRevenue, 4, BigDecimal.ROUND_HALF_UP)
                                                    .multiply(BigDecimal.valueOf(100))
                                                    .doubleValue();
            totalProgress += Math.min(100.0, revenueProgress);
            criteriaCount++;
        }

        return criteriaCount > 0 ? (int) (totalProgress / criteriaCount) : 0;
    }

    /**
     * Checks if a user can achieve this milestone again (based on cooldown)
     */
    public boolean canAchieveAgain(LocalDateTime lastAchievementDate) {
        if (!isRepeatable) {
            return false;
        }

        if (cooldownPeriodDays == null || lastAchievementDate == null) {
            return true;
        }

        LocalDateTime cooldownEndDate = lastAchievementDate.plusDays(cooldownPeriodDays);
        return LocalDateTime.now().isAfter(cooldownEndDate);
    }
}
