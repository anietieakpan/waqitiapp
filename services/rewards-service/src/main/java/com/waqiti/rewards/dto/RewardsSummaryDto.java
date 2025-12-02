package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.LoyaltyTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardsSummaryDto {
    private String userId;
    private BigDecimal cashbackBalance;
    private Long pointsBalance;
    private BigDecimal lifetimeCashback;
    private Long lifetimePoints;
    private LoyaltyTier currentTier;
    private BigDecimal tierProgress;
    private BigDecimal tierProgressTarget;
    private LoyaltyTierInfo nextTierInfo;
    private BigDecimal monthlyDollarsEarned;
    private Long monthlyPointsEarned;
    private BigDecimal weeklyDollarsEarned;
    private Long weeklyPointsEarned;
    private List<CashbackTransactionDto> recentCashback;
    private List<PointsTransactionDto> recentPoints;
    private List<CampaignDto> availableCampaigns;
    private List<RedemptionTransactionDto> recentRedemptions;
    private Instant lastActivity;
    private int totalTransactions;
    private int streakDays;
    private BigDecimal averageTransactionAmount;
    private String favoriteCategory;
    private BigDecimal projectedMonthlyEarnings;
}