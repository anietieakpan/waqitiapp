package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {
    private UUID accountId;
    private LocalDateTime reconciliationDate;
    private BigDecimal calculatedBalance;
    private BigDecimal storedBalance;
    private BigDecimal variance;
    private boolean reconciled;
    private LocalDateTime reconciledAt;
    private String issue;
}