package com.waqiti.rewards.dto;

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
public class CampaignDto {
    private String id;
    private String name;
    private String description;
    private String targetType;
    private String merchantId;
    private String merchantCategory;
    private BigDecimal cashbackRate;
    private BigDecimal pointsMultiplier;
    private BigDecimal bonusCashback;
    private Long bonusPoints;
    private BigDecimal minTransactionAmount;
    private BigDecimal maxCashbackPerTransaction;
    private BigDecimal maxCashbackTotal;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Integer minTierLevel;
    private Instant startDate;
    private Instant endDate;
    private String terms;
    private String imageUrl;
    private String bannerUrl;
    private Boolean isFeatured;
    private Boolean requiresOptIn;
    private String campaignCode;
    private int daysRemaining;
    private boolean isEligible;
    private String eligibilityReason;
}