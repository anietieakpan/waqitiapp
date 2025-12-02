package com.waqiti.familyaccount.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Member Spending Summary DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberSpendingSummary {

    private BigDecimal dailySpent;
    private BigDecimal weeklySpent;
    private BigDecimal monthlySpent;

    private BigDecimal dailyRemaining;
    private BigDecimal weeklyRemaining;
    private BigDecimal monthlyRemaining;

    private BigDecimal dailyLimit;
    private BigDecimal weeklyLimit;
    private BigDecimal monthlyLimit;
}
