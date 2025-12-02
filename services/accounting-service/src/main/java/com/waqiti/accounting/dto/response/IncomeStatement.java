package com.waqiti.accounting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waqiti.accounting.domain.FinancialPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Income statement (Profit & Loss)")
public class IncomeStatement {

    @Schema(description = "Financial period")
    @JsonProperty("period")
    private FinancialPeriod period;

    @Schema(description = "Revenue line items")
    @JsonProperty("revenue_lines")
    private List<FinancialStatementLine> revenueLines;

    @Schema(description = "Total revenue")
    @JsonProperty("total_revenue")
    private BigDecimal totalRevenue;

    @Schema(description = "Expense line items")
    @JsonProperty("expense_lines")
    private List<FinancialStatementLine> expenseLines;

    @Schema(description = "Total expenses")
    @JsonProperty("total_expenses")
    private BigDecimal totalExpenses;

    @Schema(description = "Gross profit")
    @JsonProperty("gross_profit")
    private BigDecimal grossProfit;

    @Schema(description = "Operating income")
    @JsonProperty("operating_income")
    private BigDecimal operatingIncome;

    @Schema(description = "Net income")
    @JsonProperty("net_income")
    private BigDecimal netIncome;

    @Schema(description = "Profit margin percentage")
    @JsonProperty("profit_margin")
    private BigDecimal profitMargin;

    @Schema(description = "Report generation timestamp")
    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;
}
