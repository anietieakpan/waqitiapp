package com.waqiti.accounting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account reconciliation result")
public class ReconciliationResult {

    @Schema(description = "Reconciliation record identifier")
    @JsonProperty("reconciliation_id")
    private UUID reconciliationId;

    @Schema(description = "Account code")
    @JsonProperty("account_code")
    private String accountCode;

    @Schema(description = "Whether account is reconciled")
    @JsonProperty("is_reconciled")
    private boolean isReconciled;

    @Schema(description = "Difference between internal and external balance")
    @JsonProperty("difference")
    private BigDecimal difference;

    @Schema(description = "Whether adjustment entry was created")
    @JsonProperty("adjustment_created")
    private boolean adjustmentCreated;
}
