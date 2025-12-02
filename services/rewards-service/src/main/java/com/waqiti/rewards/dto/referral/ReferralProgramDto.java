package com.waqiti.rewards.dto.referral;

import com.waqiti.rewards.enums.ProgramType;
import com.waqiti.rewards.enums.RewardType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Referral program details")
public class ReferralProgramDto {

    @Schema(description = "Program unique identifier", example = "REF-PROG-001")
    private String programId;

    @Schema(description = "Program name", example = "Standard Referral Program")
    private String programName;

    @Schema(description = "Program type", example = "STANDARD")
    private ProgramType programType;

    @Schema(description = "Program description")
    private String description;

    @Schema(description = "Whether program is currently active")
    private Boolean isActive;

    @Schema(description = "Program start date")
    private LocalDate startDate;

    @Schema(description = "Program end date")
    private LocalDate endDate;

    // Referrer rewards
    @Schema(description = "Reward type for referrer", example = "CASHBACK")
    private RewardType referrerRewardType;

    @Schema(description = "Reward amount for referrer")
    private BigDecimal referrerRewardAmount;

    @Schema(description = "Reward points for referrer")
    private Long referrerRewardPoints;

    // Referee rewards
    @Schema(description = "Reward type for referee", example = "POINTS")
    private RewardType refereeRewardType;

    @Schema(description = "Reward amount for referee")
    private BigDecimal refereeRewardAmount;

    @Schema(description = "Reward points for referee")
    private Long refereeRewardPoints;

    // Requirements
    @Schema(description = "Minimum transaction amount required for reward eligibility")
    private BigDecimal minTransactionAmount;

    @Schema(description = "Number of days before reward expires")
    private Integer rewardExpiryDays;

    @Schema(description = "Maximum referrals allowed per user")
    private Integer maxReferralsPerUser;

    @Schema(description = "Whether referee must complete first transaction")
    private Boolean requiresFirstTransaction;

    // Budget
    @Schema(description = "Maximum program budget")
    private BigDecimal maxProgramBudget;

    @Schema(description = "Total rewards issued so far")
    private BigDecimal totalRewardsIssued;

    @Schema(description = "Remaining budget")
    private BigDecimal remainingBudget;

    // Performance
    @Schema(description = "Total number of referrals")
    private Integer totalReferrals;

    @Schema(description = "Number of successful referrals")
    private Integer successfulReferrals;

    @Schema(description = "Conversion rate")
    private BigDecimal conversionRate;

    @Schema(description = "Average time to conversion in hours")
    private Double averageTimeToConversion;

    // Metadata
    @Schema(description = "Additional program metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
