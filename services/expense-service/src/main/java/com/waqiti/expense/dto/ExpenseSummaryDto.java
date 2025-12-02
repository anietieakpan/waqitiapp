package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for expense summary statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSummaryDto {

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    // Overall Statistics
    private Long totalExpenses;
    private BigDecimal totalAmount;
    private BigDecimal averageExpense;
    private BigDecimal highestExpense;
    private BigDecimal lowestExpense;

    // By Status
    private Long pendingCount;
    private Long approvedCount;
    private Long rejectedCount;
    private Long processedCount;

    // By Type
    private Map<String, BigDecimal> byExpenseType;
    private Map<String, Long> countByExpenseType;

    // By Category
    private Map<String, BigDecimal> byCategory;
    private Map<String, Long> countByCategory;

    // By Payment Method
    private Map<String, BigDecimal> byPaymentMethod;

    // Reimbursement
    private BigDecimal totalReimbursable;
    private BigDecimal totalReimbursed;
    private BigDecimal pendingReimbursement;

    // Business Expenses
    private BigDecimal totalBusinessExpenses;
    private BigDecimal totalTaxDeductible;

    // Trends
    private BigDecimal comparisonToPreviousPeriod; // Percentage change
    private String trend; // INCREASING, DECREASING, STABLE

    // Top merchants
    private Map<String, BigDecimal> topMerchants;

    private String currency;
}
