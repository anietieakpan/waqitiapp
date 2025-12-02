package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatistics {
    private Long totalTransactions;
    private BigDecimal monthlyDebitAmount;
    private BigDecimal monthlyCreditAmount;
    private BigDecimal yearToDateDebitAmount;
    private BigDecimal yearToDateCreditAmount;
    private BigDecimal averageMonthlyDebit;
    private BigDecimal averageMonthlyCredit;
    private Long monthlyTransactionCount;
    private Long yearToDateTransactionCount;
}