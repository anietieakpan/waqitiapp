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
public class InterCompanyReconciliationResponse {
    private UUID reconciliationId;
    private String reconciliationNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID sourceCompanyId;
    private String sourceCompanyName;
    private UUID targetCompanyId;
    private String targetCompanyName;
    private InterCompanyReconciliationStatus status;
    private List<InterCompanyMatch> matches;
    private List<UnmatchedInterCompanyTransaction> unmatchedSourceTransactions;
    private List<UnmatchedInterCompanyTransaction> unmatchedTargetTransactions;
    private ReconciliationSummary summary;
    private LocalDateTime performedAt;
    private String performedBy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InterCompanyMatch {
    private UUID matchId;
    private InterCompanyTransaction sourceTransaction;
    private InterCompanyTransaction targetTransaction;
    private MatchStatus matchStatus;
    private BigDecimal variance;
    private String matchType;
    private double matchScore;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InterCompanyTransaction {
    private UUID transactionId;
    private UUID companyId;
    private String companyName;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String reference;
    private String description;
    private String transactionType;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UnmatchedInterCompanyTransaction extends InterCompanyTransaction {
    private String unmatchedReason;
    private List<UUID> potentialMatches;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReconciliationSummary {
    private int totalSourceTransactions;
    private int totalTargetTransactions;
    private int matchedTransactions;
    private int unmatchedSourceTransactions;
    private int unmatchedTargetTransactions;
    private BigDecimal totalSourceAmount;
    private BigDecimal totalTargetAmount;
    private BigDecimal totalVariance;
    private double matchRate;
}

enum InterCompanyReconciliationStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    PARTIALLY_MATCHED,
    FULLY_MATCHED
}

enum MatchStatus {
    EXACT_MATCH,
    PARTIAL_MATCH,
    AMOUNT_VARIANCE,
    DATE_VARIANCE,
    MANUAL_MATCH
}