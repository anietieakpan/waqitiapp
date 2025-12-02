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
public class WalletLimitsUpdateRequest {
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
    private String updatedBy;
    private String reason;
}