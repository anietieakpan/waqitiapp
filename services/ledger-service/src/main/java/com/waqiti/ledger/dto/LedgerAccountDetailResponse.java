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
public class LedgerAccountDetailResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private UUID parentAccountId;
    private String parentAccountCode;
    private String parentAccountName;
    private String description;
    private Boolean isActive;
    private Boolean allowsTransactions;
    private String currency;
    private String normalBalance;
    
    // Balance Information
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    
    // Activity Statistics
    private LocalDateTime lastTransactionDate;
    private Long totalTransactions;
    private BigDecimal monthlyDebitAmount;
    private BigDecimal monthlyCreditAmount;
    private BigDecimal yearToDateDebitAmount;
    private BigDecimal yearToDateCreditAmount;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private String createdBy;
    private String updatedBy;
}