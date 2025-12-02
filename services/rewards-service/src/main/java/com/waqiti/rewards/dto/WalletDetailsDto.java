package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for wallet details from Wallet Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDetailsDto {
    
    private String walletId;
    private String userId;
    private String walletType; // PRIMARY, REWARDS, SAVINGS
    private String currency;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal blockedAmount;
    private String status; // ACTIVE, SUSPENDED, FROZEN, CLOSED
    private LocalDateTime createdAt;
    private LocalDateTime lastTransactionAt;
    
    // Rewards wallet specific fields
    private BigDecimal rewardsBalance;
    private BigDecimal pendingRewards;
    private BigDecimal lifetimeRewardsEarned;
    private BigDecimal lifetimeRewardsRedeemed;
    
    // Limits
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
}