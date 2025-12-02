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
public class TrialBalanceEntry {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal debitBalance;
    private BigDecimal creditBalance;
    private BigDecimal netBalance;
    private String currency;
}