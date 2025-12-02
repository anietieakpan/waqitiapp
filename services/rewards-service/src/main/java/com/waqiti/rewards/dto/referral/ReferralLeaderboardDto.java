package com.waqiti.rewards.dto.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Leaderboard entry for referral rankings")
public class ReferralLeaderboardDto {

    @Schema(description = "Rank position", example = "1")
    private Integer rank;

    @Schema(description = "Previous rank (for trend indication)")
    private Integer previousRank;

    @Schema(description = "User ID")
    private UUID userId;

    @Schema(description = "User display name (masked for privacy)")
    private String userName;

    @Schema(description = "Program ID")
    private String programId;

    @Schema(description = "Program name")
    private String programName;

    @Schema(description = "Period type", example = "WEEKLY")
    private String periodType;

    @Schema(description = "Period start date")
    private LocalDate periodStart;

    @Schema(description = "Period end date")
    private LocalDate periodEnd;

    @Schema(description = "Total referrals made")
    private Integer totalReferrals;

    @Schema(description = "Successful referrals")
    private Integer successfulReferrals;

    @Schema(description = "Total points earned")
    private Long totalPointsEarned;

    @Schema(description = "Total cashback earned")
    private BigDecimal totalCashbackEarned;

    @Schema(description = "Conversion rate")
    private Double conversionRate;

    @Schema(description = "Whether this is the current user")
    private Boolean isCurrentUser;

    @Schema(description = "Badge or achievement level", example = "GOLD")
    private String badge;
}
