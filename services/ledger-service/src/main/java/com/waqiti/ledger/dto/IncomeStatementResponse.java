package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private IncomeStatementSection revenue;
    private IncomeStatementSection expenses;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal grossIncome;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal netMarginPercentage;
    private BigDecimal grossMarginPercentage;
    private BigDecimal operatingMarginPercentage;
    private String currency;
    private LocalDateTime generatedAt;
    private String generatedBy;
}