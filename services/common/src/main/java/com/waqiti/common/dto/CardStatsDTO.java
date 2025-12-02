package com.waqiti.common.dto;

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
public class CardStatsDTO {
    private Long totalTransactions;
    private BigDecimal totalSpent;
    private BigDecimal dailySpent;
    private BigDecimal monthlySpent;
    private BigDecimal yearlySpent;
    private LocalDateTime lastUsedAt;
    private String lastUsedMerchant;
    private BigDecimal lastTransactionAmount;
    private BigDecimal averageTransactionAmount;
    private String mostUsedCategory;
}