package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response for instant deposit limits
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantDepositLimitsResponse {
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private BigDecimal weeklyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal remainingDailyLimit;
    private BigDecimal remainingWeeklyLimit;
    private BigDecimal remainingMonthlyLimit;
    private Integer maxDailyTransactions;
    private Integer remainingDailyTransactions;
    private String currency;
}