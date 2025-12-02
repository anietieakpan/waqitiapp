package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordReconciliationUpdateResponse {
    
    @NotNull
    private boolean success;
    
    @NotBlank
    private String ledgerEntryId;
    
    @NotNull
    private BigDecimal reconciliationRecorded;
    
    @NotNull
    private Instant timestamp;
}