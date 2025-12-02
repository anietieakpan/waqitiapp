package com.waqiti.gamification.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardClaimedEvent {
    private String userId;
    private Long rewardId;
    private String rewardName;
    private String rewardType;
    private String category;
    private Long pointsSpent;
    private BigDecimal cashValue;
    private String redemptionCode;
    private LocalDateTime timestamp;
}