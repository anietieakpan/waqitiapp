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
import java.util.UUID;

/**
 * DTO for bank reconciliation report response
 * Contains comprehensive bank reconciliation report data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationReportResponse {
    
    private UUID reportId;
    private String reportType; // SUMMARY, DETAILED, VARIANCE_ANALYSIS, AGING_REPORT
    private LocalDate reportDate;
    private LocalDateTime generatedAt;
    private String generatedBy;
    
    // Bank Account Information
    private String bankAccountCode;
    private String bankAccountName;
    private String bankName;
    private String accountNumber;
    
    // Reconciliation Period
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate statementDate;
    
    // Balance Information
    private BigDecimal openingBookBalance;
    private BigDecimal closingBookBalance;
    private BigDecimal openingBankBalance;
    private BigDecimal closingBankBalance;
    private BigDecimal reconciledBalance;
    private String currency;
    
    // Transaction Summary
    private int totalBankTransactions;
    private BigDecimal totalBankTransactionAmount;
    private int totalBookTransactions;
    private BigDecimal totalBookTransactionAmount;
    private int matchedTransactions;
    private BigDecimal matchedAmount;
    private BigDecimal matchingPercentage;
    
    // Outstanding Items
    private List<OutstandingItem> outstandingDeposits;
    private List<OutstandingItem> outstandingChecks;
    private List<OutstandingItem> bankAdjustments;
    private List<OutstandingItem> bookAdjustments;
    private int totalOutstandingItems;
    private BigDecimal totalOutstandingAmount;
    
    // Age Analysis
    private OutstandingItemsAgeAnalysis ageAnalysis;
    
    // Variance Analysis
    private List<ReconciliationVariance> variances;
    private BigDecimal totalVarianceAmount;
    private int varianceCount;
    
    // Status and Quality Metrics
    private String reconciliationStatus; // BALANCED, UNBALANCED, REQUIRES_REVIEW
    private String reconciliationQuality; // EXCELLENT, GOOD, NEEDS_IMPROVEMENT, POOR
    private BigDecimal balanceDifference;
    private boolean exceedsMaterialityThreshold;
    private BigDecimal materialityThreshold;
    
    // Trends and Analysis
    private String trendAnalysis;
    private boolean improvementOverPreviousPeriod;
    private Map<String, BigDecimal> monthlyTrends;
    
    // Recommendations and Actions
    private List<String> keyFindings;
    private List<String> recommendations;
    private List<String> actionItems;
    private String overallAssessment;
    
    // Approval and Review
    private boolean requiresManagerialReview;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    
    // Compliance and Controls
    private boolean complianceIssuesIdentified;
    private List<String> complianceNotes;
    private String internalControlAssessment;
    
    private String reportNotes;
    private List<String> attachedDocuments;
}