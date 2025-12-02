package com.waqiti.accounting.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account reconciliation request")
public class ReconciliationRequest {

    @NotBlank(message = "Account code is required")
    @Schema(description = "Account code to reconcile", required = true)
    @JsonProperty("account_code")
    private String accountCode;

    @NotNull(message = "Reconciliation date is required")
    @Schema(description = "Date of reconciliation", required = true)
    @JsonProperty("reconciliation_date")
    private LocalDate reconciliationDate;

    @NotNull(message = "External balance is required")
    @Digits(integer = 15, fraction = 4, message = "External balance precision exceeded")
    @Schema(description = "Balance from external system (e.g., bank statement)", required = true, example = "10000.0000")
    @JsonProperty("external_balance")
    private BigDecimal externalBalance;

    @Schema(description = "Automatically create adjustment entry if discrepancy found (default: false)")
    @JsonProperty("auto_adjust")
    @Builder.Default
    private boolean autoAdjust = false;

    @jakarta.validation.constraints.Size(max = 2000, message = "Notes must not exceed 2000 characters")
    @Schema(description = "Notes about the reconciliation (max 2000 chars)")
    @JsonProperty("notes")
    private String notes;

    @NotBlank(message = "Reconciled by is required")
    @Schema(description = "User performing reconciliation", required = true)
    @JsonProperty("reconciled_by")
    private String reconciledBy;
}
