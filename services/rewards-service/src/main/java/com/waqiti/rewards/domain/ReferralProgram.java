package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.ProgramType;
import com.waqiti.rewards.enums.RewardType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Referral Program Entity
 *
 * Represents a complete referral program configuration with all rules,
 * rewards, and constraints for a promotional referral campaign.
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_programs", indexes = {
    @Index(name = "idx_referral_programs_active", columnList = "isActive"),
    @Index(name = "idx_referral_programs_type", columnList = "programType"),
    @Index(name = "idx_referral_programs_dates", columnList = "startDate,endDate"),
    @Index(name = "idx_referral_programs_public", columnList = "isPublic")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"links", "campaigns", "tiers", "milestones"})
public class ReferralProgram {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Program ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String programId;

    @NotBlank(message = "Program name is required")
    @Column(nullable = false)
    private String programName;

    @NotNull(message = "Program type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProgramType programType;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ============================================================================
    // PROGRAM SCHEDULE
    // ============================================================================

    @NotNull(message = "Start date is required")
    @Column(nullable = false)
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPublic = true;

    // ============================================================================
    // ELIGIBILITY CRITERIA
    // ============================================================================

    @ElementCollection
    @CollectionTable(name = "referral_program_target_audience",
                     joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "audience")
    @Builder.Default
    private Set<String> targetAudience = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "referral_program_eligible_products",
                     joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "product")
    @Builder.Default
    private Set<String> eligibleProducts = new HashSet<>();

    @Min(value = 0, message = "Minimum account age must be non-negative")
    @Builder.Default
    @Column
    private Integer minAccountAgeDays = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean requiresKycVerification = true;

    // ============================================================================
    // REFERRER REWARDS CONFIGURATION
    // ============================================================================

    @NotNull(message = "Referrer reward type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RewardType referrerRewardType;

    @DecimalMin(value = "0.00", message = "Reward amount must be positive")
    @Column(precision = 15, scale = 2)
    private BigDecimal referrerRewardAmount;

    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Builder.Default
    @Column(length = 3)
    private String referrerRewardCurrency = "USD";

    @DecimalMin(value = "0.0000", message = "Reward percentage must be positive")
    @DecimalMax(value = "1.0000", message = "Reward percentage cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal referrerRewardPercentage;

    @Min(value = 0, message = "Reward points must be non-negative")
    @Column
    private Long referrerRewardPoints;

    // ============================================================================
    // REFEREE (NEW USER) REWARDS CONFIGURATION
    // ============================================================================

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RewardType refereeRewardType;

    @DecimalMin(value = "0.00", message = "Reward amount must be positive")
    @Column(precision = 15, scale = 2)
    private BigDecimal refereeRewardAmount;

    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Builder.Default
    @Column(length = 3)
    private String refereeRewardCurrency = "USD";

    @DecimalMin(value = "0.0000", message = "Reward percentage must be positive")
    @DecimalMax(value = "1.0000", message = "Reward percentage cannot exceed 100%")
    @Column(precision = 5, scale = 4)
    private BigDecimal refereeRewardPercentage;

    @Min(value = 0, message = "Reward points must be non-negative")
    @Column
    private Long refereeRewardPoints;

    // ============================================================================
    // LIMITS AND CONSTRAINTS
    // ============================================================================

    @Min(value = 1, message = "Max referrals must be at least 1")
    @Column
    private Integer maxReferralsPerUser;

    @DecimalMin(value = "0.00", message = "Max rewards must be positive")
    @Column(precision = 15, scale = 2)
    private BigDecimal maxRewardsPerUser;

    @DecimalMin(value = "0.00", message = "Program budget must be positive")
    @Column(precision = 18, scale = 2)
    private BigDecimal maxProgramBudget;

    @Builder.Default
    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal totalRewardsIssued = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Minimum transaction amount must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal minimumTransactionAmount = BigDecimal.ZERO;

    @Column
    private Integer rewardExpiryDays;

    @Builder.Default
    @Min(value = 1, message = "Conversion window must be at least 1 day")
    @Column
    private Integer conversionWindowDays = 30;

    // ============================================================================
    // AUTO-ACTIVATION CONDITIONS
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean autoActivateOnSignup = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean autoActivateOnFirstDeposit = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean autoActivateOnKycComplete = false;

    @DecimalMin(value = "0.00", message = "Min deposit amount must be positive")
    @Column(precision = 15, scale = 2)
    private BigDecimal minDepositAmount;

    // ============================================================================
    // TERMS AND POLICIES
    // ============================================================================

    @Column(columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(columnDefinition = "TEXT")
    private String privacyPolicyUrl;

    // ============================================================================
    // PERFORMANCE TRACKING
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Integer totalReferrals = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer successfulReferrals = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalClicks = 0;

    @Builder.Default
    @Column(precision = 5, scale = 4, nullable = false)
    private BigDecimal conversionRate = BigDecimal.ZERO;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @NotBlank(message = "Creator is required")
    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 100)
    private String lastModifiedBy;

    // ============================================================================
    // RELATIONSHIPS
    // ============================================================================

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ReferralLink> links = new HashSet<>();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ReferralCampaign> campaigns = new HashSet<>();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ReferralTier> tiers = new HashSet<>();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ReferralMilestone> milestones = new HashSet<>();

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
     * Checks if the program is currently active and within valid date range
     */
    public boolean isCurrentlyActive() {
        if (!isActive) {
            return false;
        }

        LocalDate now = LocalDate.now();

        if (now.isBefore(startDate)) {
            return false;
        }

        if (endDate != null && now.isAfter(endDate)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if program budget has been exceeded
     */
    public boolean isBudgetExceeded() {
        if (maxProgramBudget == null) {
            return false;
        }
        return totalRewardsIssued.compareTo(maxProgramBudget) >= 0;
    }

    /**
     * Increments the total referrals counter
     */
    public void incrementTotalReferrals() {
        this.totalReferrals++;
        updateConversionRate();
    }

    /**
     * Increments the successful referrals counter
     */
    public void incrementSuccessfulReferrals() {
        this.successfulReferrals++;
        updateConversionRate();
    }

    /**
     * Increments the total clicks counter
     */
    public void incrementTotalClicks() {
        this.totalClicks++;
    }

    /**
     * Adds to the total rewards issued
     */
    public void addRewardAmount(BigDecimal amount) {
        this.totalRewardsIssued = this.totalRewardsIssued.add(amount);
    }

    /**
     * Updates the conversion rate based on current metrics
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
     * Checks if a user can receive more referrals based on program limits
     */
    public boolean canAcceptMoreReferrals(int currentReferralCount) {
        return maxReferralsPerUser == null || currentReferralCount < maxReferralsPerUser;
    }
}
