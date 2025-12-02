package com.waqiti.analytics.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash Flow Data DTO
 *
 * Represents cash flow metrics for a single day including
 * income, spending, and net flow.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowData {

    @NotNull(message = "Date cannot be null")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @NotNull(message = "Income amount cannot be null")
    private BigDecimal incomeAmount;

    @NotNull(message = "Spending amount cannot be null")
    private BigDecimal spendingAmount;

    @NotNull(message = "Net flow cannot be null")
    private BigDecimal netFlow;

    private String dayOfWeek;

    /**
     * Helper: Check if day has positive cash flow
     */
    public boolean isPositive() {
        return netFlow != null && netFlow.compareTo(BigDecimal.ZERO) > 0;
    }
}
