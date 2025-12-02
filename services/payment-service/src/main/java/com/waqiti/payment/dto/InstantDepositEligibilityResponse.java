package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for instant deposit eligibility check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantDepositEligibilityResponse {
    private boolean eligible;
    private String reason;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private BigDecimal dailyLimit;
    private BigDecimal remainingDailyLimit;
    private Integer dailyTransactionCount;
    private Integer remainingDailyTransactions;
    private List<String> requiredVerifications;
    private LocalDateTime nextEligibilityCheck;
}