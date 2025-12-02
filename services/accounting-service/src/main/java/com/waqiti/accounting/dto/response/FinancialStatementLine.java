package com.waqiti.accounting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waqiti.accounting.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Financial statement line item")
public class FinancialStatementLine {

    @Schema(description = "Account code")
    @JsonProperty("account_code")
    private String accountCode;

    @Schema(description = "Account name")
    @JsonProperty("account_name")
    private String accountName;

    @Schema(description = "Account type")
    @JsonProperty("account_type")
    private AccountType accountType;

    @Schema(description = "Amount")
    @JsonProperty("amount")
    private BigDecimal amount;
}
