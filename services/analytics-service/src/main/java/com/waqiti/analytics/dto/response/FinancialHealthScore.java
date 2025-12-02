package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Financial Health Score DTO
 *
 * Comprehensive financial health assessment with component scores.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialHealthScore {

    @Min(0) @Max(100)
    private Integer overallScore; // 0-100

    private String healthGrade; // A+, A, B, C, D, F

    private Map<String, Integer> componentScores; // SAVINGS, SPENDING, DEBT, INCOME_STABILITY, etc.

    private BigDecimal savingsRate;
    private BigDecimal debtToIncomeRatio;
    private String incomeStability;
    private String spendingBehavior;

    private java.util.List<String> strengths;
    private java.util.List<String> weaknesses;
    private java.util.List<String> recommendations;
}
