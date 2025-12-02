package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * DTO representing monthly budget variance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyVariance {
    private YearMonth month;
    private BigDecimal budgetAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BigDecimal variancePercentage;
}