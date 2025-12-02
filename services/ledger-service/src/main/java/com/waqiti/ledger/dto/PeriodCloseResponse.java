package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseResponse {
    private LocalDate periodEndDate;
    private boolean success;
    private TrialBalanceResponse trialBalance;
    private BalanceSheetResponse balanceSheet;
    private IncomeStatementResponse incomeStatement;
    private List<JournalEntryResponse> closingEntries;
    private BigDecimal retainedEarningsAdjustment;
    private BigDecimal netIncome;
    private LocalDateTime closedAt;
    private String closedBy;
    private List<String> warnings;
    private List<String> errors;
    private PeriodCloseSummary summary;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PeriodCloseSummary {
    private int accountsClosed;
    private int journalEntriesCreated;
    private BigDecimal totalRevenueClosed;
    private BigDecimal totalExpensesClosed;
    private BigDecimal netIncome;
    private UUID nextPeriodId;
    private LocalDate nextPeriodStartDate;
}