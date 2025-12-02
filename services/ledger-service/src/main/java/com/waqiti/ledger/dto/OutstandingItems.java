package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outstanding Items DTO
 * 
 * Contains categorized outstanding items from bank reconciliation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutstandingItems {
    
    // Outstanding item categories
    private List<OutstandingItem> outstandingChecks;
    private List<OutstandingItem> depositsInTransit;
    private List<OutstandingItem> bankErrors;
    private List<OutstandingItem> bookErrors;
    private List<OutstandingItem> otherItems;
    
    // Category totals
    private BigDecimal totalOutstandingChecks;
    private BigDecimal totalDepositsInTransit;
    private BigDecimal totalBankErrors;
    private BigDecimal totalBookErrors;
    private BigDecimal totalOtherItems;
    
    // Summary totals
    private BigDecimal totalOutstandingAmount;
    private Integer totalOutstandingCount;
    
    // Age analysis
    private OutstandingItemsAgeAnalysis ageAnalysis;
    
    // Trend information
    private String trendDirection; // INCREASING, DECREASING, STABLE
    private BigDecimal changeFromPriorPeriod;
}