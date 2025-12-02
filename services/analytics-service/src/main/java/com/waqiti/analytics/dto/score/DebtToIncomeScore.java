package com.waqiti.analytics.dto.score;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtToIncomeScore {
    private BigDecimal score; // 0-100
    private BigDecimal debtToIncomeRatio; // Percentage
    private String level; // EXCELLENT, GOOD, FAIR, POOR
    private BigDecimal totalDebt;
    private BigDecimal monthlyDebtPayments;
    private String recommendation;
}