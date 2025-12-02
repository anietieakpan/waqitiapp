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
@Schema(description = "Trial balance entry for a single account")
public class TrialBalanceEntry {

    @Schema(description = "Account code")
    @JsonProperty("account_code")
    private String accountCode;

    @Schema(description = "Account name")
    @JsonProperty("account_name")
    private String accountName;

    @Schema(description = "Account type")
    @JsonProperty("account_type")
    private AccountType accountType;

    @Schema(description = "Debit balance")
    @JsonProperty("debit_balance")
    private BigDecimal debitBalance;

    @Schema(description = "Credit balance")
    @JsonProperty("credit_balance")
    private BigDecimal creditBalance;
}
