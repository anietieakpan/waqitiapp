package com.waqiti.rewards.dto;

import com.waqiti.rewards.domain.UserRewardsPreferences;
import com.waqiti.rewards.enums.AccountStatus;
import com.waqiti.rewards.enums.LoyaltyTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardsAccountDto {
    private String userId;
    private BigDecimal cashbackBalance;
    private Long pointsBalance;
    private BigDecimal lifetimeCashback;
    private Long lifetimePoints;
    private LoyaltyTier currentTier;
    private BigDecimal tierProgress;
    private BigDecimal tierProgressTarget;
    private AccountStatus status;
    private UserRewardsPreferences preferences;
    private Instant enrollmentDate;
    private Instant lastActivity;
}