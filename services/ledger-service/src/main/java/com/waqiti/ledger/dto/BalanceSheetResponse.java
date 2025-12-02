package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetResponse {
    private LocalDate asOfDate;
    private BalanceSheetSection assets;
    private BalanceSheetSection liabilities;
    private BalanceSheetSection equity;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal totalLiabilitiesAndEquity;
    private boolean balanced;
    private BigDecimal variance;
    private String currency;
    private LocalDateTime generatedAt;
    private String generatedBy;
    
    // Enhanced fields for GAAP compliance and analysis
    private boolean gaapCompliant;
    private List<String> gaapViolations;
    private Map<String, BigDecimal> financialRatios;
    private List<String> disclosureNotes;
    private String auditTrail;
    
    // Additional balance sheet sections for enhanced reporting
    private BalanceSheetSection currentAssets;
    private BalanceSheetSection fixedAssets;
    private BalanceSheetSection otherAssets;
    private BalanceSheetSection totalAssets;
    private BalanceSheetSection currentLiabilities;
    private BalanceSheetSection longTermLiabilities;
    private BalanceSheetSection totalLiabilities;
    
    // Totals for enhanced analysis
    private BigDecimal totalAssetsAmount;
    private BigDecimal totalLiabilitiesAndEquity;
    
    // Format information
    private String format;
}