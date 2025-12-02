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
@Schema(description = "Trial balance report")
public class TrialBalance {

    @Schema(description = "Financial period")
    @JsonProperty("period")
    private FinancialPeriod period;

    @Schema(description = "Trial balance entries")
    @JsonProperty("entries")
    private List<TrialBalanceEntry> entries;

    @Schema(description = "Total debits")
    @JsonProperty("total_debits")
    private BigDecimal totalDebits;

    @Schema(description = "Total credits")
    @JsonProperty("total_credits")
    private BigDecimal totalCredits;

    @Schema(description = "Whether trial balance is balanced")
    @JsonProperty("is_balanced")
    private boolean isBalanced;

    @Schema(description = "Report generation timestamp")
    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;
}
