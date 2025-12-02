package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bank Statement Item DTO
 * 
 * Represents an individual transaction from a bank statement
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementItem {
    
    private UUID itemId;
    private LocalDate transactionDate;
    private LocalDate valueDate;
    private BigDecimal amount;
    private String description;
    private String reference;
    private String transactionCode;
    private String transactionType;
    
    // Counterparty information
    private String counterpartyName;
    private String counterpartyAccount;
    private String counterpartyBank;
    
    // Additional details
    private String category;
    private String currency;
    private String notes;
    
    // Reconciliation fields
    private Boolean matched;
    private UUID matchedLedgerEntryId;
    private String matchingNotes;
}