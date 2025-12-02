package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash Flow Data Point DTO
 *
 * Represents a single data point in time-series cash flow analysis.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowData {

    // Time Period
    private LocalDate date;
    private String periodLabel; // e.g., "2025-W42", "2025-10", "2025-Q4"
    private String periodType; // "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"

    // Cash Flow Components
    private BigDecimal inflow;
    private BigDecimal outflow;
    private BigDecimal netCashFlow;

    // Balances
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal highestBalance;
    private BigDecimal lowestBalance;

    // Transaction Counts
    private Integer inflowTransactionCount;
    private Integer outflowTransactionCount;
    private Integer totalTransactionCount;

    // Averages
    private BigDecimal averageInflowAmount;
    private BigDecimal averageOutflowAmount;

    // Flags
    private Boolean hasNegativeCashFlow;
    private Boolean hasAnomalies;
    private Boolean isProjected; // true if forecasted, false if actual

    // Metadata
    private String currency;
    private String notes;

    // Helper Methods
    public boolean isPositive() {
        return netCashFlow != null && netCashFlow.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getInflowOutflowRatio() {
        if (outflow == null || outflow.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return inflow.divide(outflow, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getSavingsRate() {
        if (inflow == null || inflow.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return netCashFlow.divide(inflow, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
