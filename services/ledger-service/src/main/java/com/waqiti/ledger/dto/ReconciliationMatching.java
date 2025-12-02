package com.waqiti.ledger.dto;

import com.waqiti.ledger.domain.LedgerEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reconciliation Matching DTO
 * 
 * Contains the results of matching bank statement items with ledger entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationMatching {
    
    // Matched items
    private List<ReconciliationMatch> exactMatches;
    private List<ReconciliationMatch> fuzzyMatches;
    private List<ReconciliationMatch> manualMatches;
    
    // Unmatched items
    private List<BankStatementItem> unmatchedStatementItems;
    private List<LedgerEntry> unmatchedLedgerEntries;
    
    // Matching statistics
    private Integer totalMatches;
    private Integer totalStatementItems;
    private Integer totalLedgerEntries;
    private BigDecimal matchingRate;
    
    // Confidence scoring
    private BigDecimal averageMatchConfidence;
    private Integer highConfidenceMatches;
    private Integer lowConfidenceMatches;
    
    // Matching rules applied
    private List<String> matchingRulesApplied;
    private String matchingStrategy;
}