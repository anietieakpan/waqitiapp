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
public class ReconciliationResponse {

    private UUID reconciliationId;
    private String bankAccountCode;
    private LocalDate reconciliationDate;
    private BigDecimal bankBalance;
    private BigDecimal ledgerBalance;
    private BigDecimal reconciledBalance;
    private BigDecimal variance;
    private boolean reconciled;
    private int matchedTransactions;
    private int unmatchedBankItems;
    private int unmatchedLedgerItems;
    private List<OutstandingItem> outstandingItems;
    private List<ReconciliationDiscrepancyResponse> discrepancies;
    private LocalDateTime performedAt;
    private String performedBy;
    private String notes;
    private ReconciliationSummary summary;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OutstandingItem {
    
    private String type; // BANK_ITEM, LEDGER_ITEM
    private BigDecimal amount;
    private LocalDateTime date;
    private String description;
    private String reference;
    private int agingDays;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReconciliationSummary {
    
    private int totalBankItems;
    private int totalLedgerItems;
    private int exactMatches;
    private int fuzzyMatches;
    private int manualMatches;
    private BigDecimal totalMatchedAmount;
    private BigDecimal totalUnmatchedAmount;
    private double reconciliationRate;
}