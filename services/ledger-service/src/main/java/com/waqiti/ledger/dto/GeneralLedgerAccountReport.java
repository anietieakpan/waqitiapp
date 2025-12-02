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
public class GeneralLedgerAccountReport {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal netChange;
    private List<TransactionDetail> transactions;
    private AccountPeriodSummary periodSummary;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TransactionDetail {
    private UUID transactionId;
    private String journalEntryNumber;
    private LocalDate transactionDate;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal runningBalance;
    private String reference;
    private String source;
    private LocalDateTime createdAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AccountPeriodSummary {
    private int transactionCount;
    private BigDecimal averageBalance;
    private BigDecimal highestBalance;
    private BigDecimal lowestBalance;
    private LocalDate lastTransactionDate;
}