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
public class ReconciliationMatchingResult {
    private UUID matchId;
    private String matchType;
    private double matchScore;
    private MatchConfidence confidence;
    private BankStatementEntry bankEntry;
    private List<LedgerTransactionEntry> ledgerEntries;
    private BigDecimal variance;
    private List<MatchingCriterion> criteriaUsed;
    private LocalDateTime matchedAt;
    private String matchedBy;
    private boolean requiresManualReview;
    private String notes;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BankStatementEntry {
    private UUID entryId;
    private String transactionId;
    private LocalDate valueDate;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String description;
    private String reference;
    private String currency;
    private String counterparty;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class LedgerTransactionEntry {
    private UUID transactionId;
    private UUID journalEntryId;
    private String entryNumber;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String description;
    private String reference;
    private UUID accountId;
    private String accountCode;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MatchingCriterion {
    private String criterionName;
    private String sourceValue;
    private String targetValue;
    private double weight;
    private double score;
    private boolean matched;
}

enum MatchConfidence {
    LOW,
    MEDIUM,
    HIGH,
    EXACT
}