package com.waqiti.accounting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Balance sheet report")
public class BalanceSheet {

    @Schema(description = "As of date")
    @JsonProperty("as_of_date")
    private LocalDate asOfDate;

    @Schema(description = "Asset line items")
    @JsonProperty("asset_lines")
    private List<FinancialStatementLine> assetLines;

    @Schema(description = "Total assets")
    @JsonProperty("total_assets")
    private BigDecimal totalAssets;

    @Schema(description = "Current assets")
    @JsonProperty("current_assets")
    private BigDecimal currentAssets;

    @Schema(description = "Fixed assets")
    @JsonProperty("fixed_assets")
    private BigDecimal fixedAssets;

    @Schema(description = "Liability line items")
    @JsonProperty("liability_lines")
    private List<FinancialStatementLine> liabilityLines;

    @Schema(description = "Total liabilities")
    @JsonProperty("total_liabilities")
    private BigDecimal totalLiabilities;

    @Schema(description = "Current liabilities")
    @JsonProperty("current_liabilities")
    private BigDecimal currentLiabilities;

    @Schema(description = "Long-term liabilities")
    @JsonProperty("long_term_liabilities")
    private BigDecimal longTermLiabilities;

    @Schema(description = "Equity line items")
    @JsonProperty("equity_lines")
    private List<FinancialStatementLine> equityLines;

    @Schema(description = "Total equity")
    @JsonProperty("total_equity")
    private BigDecimal totalEquity;

    @Schema(description = "Retained earnings")
    @JsonProperty("retained_earnings")
    private BigDecimal retainedEarnings;

    @Schema(description = "Whether balance sheet is balanced (Assets = Liabilities + Equity)")
    @JsonProperty("is_balanced")
    private boolean isBalanced;

    @Schema(description = "Report generation timestamp")
    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;
}
