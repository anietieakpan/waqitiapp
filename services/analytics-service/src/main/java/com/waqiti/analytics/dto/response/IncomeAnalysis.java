package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Income Analysis DTO
 *
 * Comprehensive analysis of user income streams including source breakdown,
 * stability metrics, and growth trends.
 *
 * Key Metrics:
 * - Total income received
 * - Income source diversification
 * - Income stability and consistency
 * - Growth rate analysis
 * - Primary income identification
 *
 * Used for:
 * - Credit risk assessment
 * - Loan eligibility evaluation
 * - Financial health scoring
 * - Income forecasting
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeAnalysis {

    /**
     * Total income received in the period
     */
    private BigDecimal totalIncome;

    /**
     * Breakdown of income by source
     */
    private List<IncomeSource> incomeSources;

    /**
     * Daily income trend
     */
    private List<DailyIncome> dailyIncome;

    /**
     * Average income per day
     */
    private BigDecimal averageDailyIncome;

    /**
     * Income stability assessment
     */
    private IncomeStability incomeStability;

    /**
     * Income growth rate (percentage)
     */
    private BigDecimal incomeGrowthRate;

    /**
     * Primary income source name
     */
    private String primaryIncomeSource;

    /**
     * Income consistency score (0.0 - 1.0, higher = more consistent)
     */
    private BigDecimal incomeConsistency;

    /**
     * Number of active income sources
     */
    private Integer numberOfSources;

    /**
     * Income diversification score (0.0 - 1.0, higher = more diversified)
     */
    private BigDecimal diversificationScore;

    /**
     * Predicted next income date
     */
    private java.time.LocalDate nextIncomeDate;

    /**
     * Income reliability score (0-100)
     */
    private Integer reliabilityScore;
}
