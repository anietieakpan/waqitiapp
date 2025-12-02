package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseResult {
    private UUID periodId;
    private List<JournalEntryResponse> closingEntries;
    private BigDecimal retainedEarningsAdjustment;
    private BigDecimal netIncome;
    private int revenueAccountsClosed;
    private int expenseAccountsClosed;
    private List<ClosingEntryDetail> closingDetails;
    private boolean successful;
    private List<String> errors;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ClosingEntryDetail {
    private String accountCode;
    private String accountName;
    private String accountType;
    private BigDecimal closingAmount;
    private String closingEntryNumber;
}