package com.waqiti.ledger.dto;

import com.waqiti.ledger.domain.LedgerEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reconciliation Match DTO
 * 
 * Represents a match between a bank statement item and a ledger entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationMatch {
    
    private UUID matchId;
    private BankStatementItem statementItem;
    private LedgerEntry ledgerEntry;
    private String matchType; // EXACT, FUZZY, MANUAL
    
    // Match quality metrics
    private Integer matchConfidence; // 0-100
    private BigDecimal varianceAmount;
    private Integer dateDifferenceDays;
    
    // Match criteria
    private Boolean amountMatched;
    private Boolean dateMatched;
    private Boolean descriptionSimilarity;
    private Boolean referenceMatched;
    
    // Match details
    private String matchingRule;
    private String matchNotes;
    private LocalDateTime matchedAt;
    private String matchedBy;
    
    // Override information
    private Boolean manualOverride;
    private String overrideReason;
}