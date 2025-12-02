package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationDiscrepancy {
    private String transactionId;
    private String discrepancyType;
    private String description;
    private BigDecimal internalAmount;
    private BigDecimal externalAmount;
}