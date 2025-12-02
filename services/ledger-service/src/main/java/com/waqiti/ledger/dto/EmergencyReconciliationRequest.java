package com.waqiti.ledger.dto;

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
public class EmergencyReconciliationRequest {
    private UUID accountId;
    private String currency;
    private BigDecimal discrepancyAmount;
    private String reason;
    private String sourceSystem;
    private String transactionId;
    private String metadata;
}
