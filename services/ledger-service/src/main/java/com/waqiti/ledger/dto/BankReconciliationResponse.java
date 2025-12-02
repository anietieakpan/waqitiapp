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

/**
 * Bank Reconciliation Response DTO
 * 
 * Contains the complete results of a bank reconciliation process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationResponse {
    
    // Identification
    private UUID reconciliationId;
    private UUID bankAccountId;
    private String bankAccountName;
    private String bankAccountNumber;
    
    // Period information
    private LocalDate reconciliationDate;
    private LocalDate startDate;
    private String currency;
    
    // Balance information
    private BigDecimal bookBalance;
    private BigDecimal bankBalance;
    private BigDecimal reconciledBalance;
    private BigDecimal variance;
    
    // Reconciliation details
    private ReconciliationSummary summary;
    private OutstandingItems outstandingItems;
    private ReconciliationMatching matching;
    private ReconciliationVariance varianceAnalysis;
    private List<ReconciliationRecommendation> recommendations;
    
    // Status and metadata
    private Boolean reconciled;
    private String reconciledBy;
    private LocalDateTime reconciledAt;
    private String notes;
    
    // Report generation
    private byte[] reportContent;
    private String reportFormat;
}