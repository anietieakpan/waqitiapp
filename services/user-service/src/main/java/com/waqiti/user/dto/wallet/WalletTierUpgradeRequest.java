package com.waqiti.user.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTierUpgradeRequest {
    private String walletId;
    private String userId;
    private String newTier;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
    private String reason;
    private String upgradedBy;
}