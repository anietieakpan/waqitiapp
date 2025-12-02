package com.waqiti.rewards.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "loyalty_accounts")
public class LoyaltyAccount {
    @Id
    private String id;
    private String userId;
    
    // Points balances
    private BigDecimal currentBalance;
    private BigDecimal lifetimeEarned;
    private BigDecimal lifetimeRedeemed;
    private BigDecimal totalExpired;
    private BigDecimal totalBonusEarned;
    private BigDecimal milestoneBonusEarned;
    private BigDecimal pointsExpiringNextMonth;
    
    // Tier information
    private RewardTier currentTier;
    private RewardTier previousTier;
    private BigDecimal tierPoints;
    private BigDecimal yearlySpend;
    private LocalDateTime tierUpgradedAt;
    private LocalDateTime tierDowngradedAt;
    private LocalDateTime tierExpiryDate;
    private BigDecimal tierUpgradeBonus;
    
    // Activity tracking
    private LocalDateTime lastActivityAt;
    private String lastTransactionId;
    private Integer transactionCount;
    private Integer currentStreak;
    private Integer longestStreak;
    
    // Account status
    private boolean isActive;
    private LocalDateTime joinedAt;
    private Set<String> achievements;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public void addAchievement(String achievement) {
        if (this.achievements == null) {
            this.achievements = new HashSet<>();
        }
        this.achievements.add(achievement);
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "points_transactions")
public class PointsTransaction {
    @Id
    private String id;
    private String eventId;
    private String accountId;
    private String userId;
    
    // Transaction details
    private PointsTransactionType type;
    private String category;
    private BigDecimal basePoints;
    private BigDecimal actualPoints;
    private BigDecimal bonusPoints;
    private String referenceId;
    private String referenceType;
    private String source;
    private String description;
    private String merchantId;
    
    // Multipliers
    private BigDecimal tierMultiplier;
    private BigDecimal categoryMultiplier;
    private BigDecimal campaignMultiplier;
    private BigDecimal specialMultiplier;
    private BigDecimal totalMultiplier;
    private String campaignId;
    
    // Status
    private PointsStatus status;
    private String rejectionReason;
    
    // Expiry
    private LocalDateTime expiryDate;
    private boolean expiryExtended;
    private LocalDateTime expiredAt;
    
    // Timestamps
    private LocalDateTime transactionDate;
    private LocalDateTime creditedAt;
    private LocalDateTime redeemedAt;
    private LocalDateTime adjustedAt;
    private LocalDateTime transferredAt;
    
    // Redemption details
    private String redemptionId;
    private String redemptionStatus;
    private String redemptionError;
    private BigDecimal redemptionValue;
    private String redemptionCurrency;
    
    // Transfer details
    private String transferToUserId;
    
    // Adjustment details
    private String adjustmentReason;
    
    // Flags
    private boolean isBonus;
    
    // Metadata
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

