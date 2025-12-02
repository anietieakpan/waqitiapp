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
 * Collection of financial statements generated as part of the period close process.
 * This includes Balance Sheet, Income Statement, Cash Flow Statement, and 
 * Statement of Retained Earnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialStatements {
    
    /**
     * Unique identifier for this financial statements package
     */
    private UUID financialStatementsId;
    
    /**
     * Accounting period these statements represent
     */
    private UUID periodId;
    
    /**
     * Period start date
     */
    private LocalDate periodStartDate;
    
    /**
     * Period end date (as-of date for balance sheet)
     */
    private LocalDate periodEndDate;
    
    /**
     * Company or entity information
     */
    private CompanyInfo companyInfo;
    
    /**
     * Balance Sheet
     */
    private BalanceSheetResponse balanceSheet;
    
    /**
     * Income Statement (Profit & Loss)
     */
    private IncomeStatementResponse incomeStatement;
    
    /**
     * Cash Flow Statement
     */
    private CashFlowStatementResponse cashFlowStatement;
    
    /**
     * Statement of Retained Earnings
     */
    private RetainedEarningsStatement retainedEarningsStatement;
    
    /**
     * Statement of Equity (if applicable)
     */
    private EquityStatement equityStatement;
    
    /**
     * Trial Balance
     */
    private TrialBalanceResponse trialBalance;
    
    /**
     * Comparative statements (if available)
     */
    private ComparativeStatements comparativeStatements;
    
    /**
     * Notes to financial statements
     */
    private List<FinancialStatementNote> notes;
    
    /**
     * Key financial ratios and metrics
     */
    private FinancialRatios financialRatios;
    
    /**
     * Consolidation information (if applicable)
     */
    private ConsolidationInfo consolidationInfo;
    
    /**
     * Currency used for the statements
     */
    private String baseCurrency;
    
    /**
     * Exchange rates used for multi-currency entities
     */
    private List<ExchangeRateInfo> exchangeRates;
    
    /**
     * Statement preparation method (GAAP, IFRS, etc.)
     */
    private String accountingStandard;
    
    /**
     * When the statements were generated
     */
    private LocalDateTime statementsGeneratedAt;
    
    /**
     * User who generated the statements
     */
    private String statementsGeneratedBy;
    
    /**
     * Version of the financial statements
     */
    private String statementsVersion;
    
    /**
     * Status of the financial statements
     */
    private String statementsStatus;
    
    /**
     * Whether the statements are audited
     */
    private boolean isAudited;
    
    /**
     * Auditor information (if audited)
     */
    private AuditorInfo auditorInfo;
    
    /**
     * Whether the statements are interim or final
     */
    private boolean isInterimStatements;
    
    /**
     * Whether the statements are consolidated
     */
    private boolean isConsolidated;
    
    /**
     * List of subsidiaries included (if consolidated)
     */
    private List<SubsidiaryInfo> subsidiariesIncluded;
    
    /**
     * Report generation parameters
     */
    private ReportParameters reportParameters;
    
    /**
     * Custom metadata for the statements
     */
    private String metadata;
    
    /**
     * Whether the statements are approved for release
     */
    private boolean isApprovedForRelease;
    
    /**
     * When the statements were approved
     */
    private LocalDateTime approvedAt;
    
    /**
     * User who approved the statements
     */
    private String approvedBy;
    
    /**
     * Approval comments
     */
    private String approvalComments;
}

/**
 * Company information for financial statements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CompanyInfo {
    
    /**
     * Company legal name
     */
    private String companyName;
    
    /**
     * Company registration number
     */
    private String registrationNumber;
    
    /**
     * Tax identification number
     */
    private String taxId;
    
    /**
     * Company address
     */
    private String address;
    
    /**
     * Industry classification
     */
    private String industryCode;
    
    /**
     * Fiscal year end date
     */
    private LocalDate fiscalYearEnd;
    
    /**
     * Company logo (if applicable)
     */
    private String logoUrl;
}

/**
 * Statement of Retained Earnings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RetainedEarningsStatement {
    
    /**
     * Beginning retained earnings balance
     */
    private BigDecimal beginningBalance;
    
    /**
     * Net income for the period
     */
    private BigDecimal netIncome;
    
    /**
     * Dividends declared during the period
     */
    private BigDecimal dividends;
    
    /**
     * Other comprehensive income
     */
    private BigDecimal otherComprehensiveIncome;
    
    /**
     * Prior period adjustments
     */
    private BigDecimal priorPeriodAdjustments;
    
    /**
     * Other adjustments to retained earnings
     */
    private BigDecimal otherAdjustments;
    
    /**
     * Ending retained earnings balance
     */
    private BigDecimal endingBalance;
    
    /**
     * Detailed breakdown of changes
     */
    private List<RetainedEarningsLineItem> lineItems;
}

/**
 * Statement of Equity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class EquityStatement {
    
    /**
     * Common stock information
     */
    private EquityComponent commonStock;
    
    /**
     * Preferred stock information
     */
    private EquityComponent preferredStock;
    
    /**
     * Additional paid-in capital
     */
    private EquityComponent additionalPaidInCapital;
    
    /**
     * Retained earnings
     */
    private EquityComponent retainedEarnings;
    
    /**
     * Accumulated other comprehensive income
     */
    private EquityComponent accumulatedOCI;
    
    /**
     * Treasury stock
     */
    private EquityComponent treasuryStock;
    
    /**
     * Total equity
     */
    private BigDecimal totalEquity;
    
    /**
     * Equity transactions during the period
     */
    private List<EquityTransaction> equityTransactions;
}

/**
 * Comparative statements information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ComparativeStatements {
    
    /**
     * Previous period information
     */
    private PeriodInfo previousPeriod;
    
    /**
     * Year-over-year comparison
     */
    private YearOverYearComparison yearOverYearComparison;
    
    /**
     * Quarter-over-quarter comparison
     */
    private QuarterOverQuarterComparison quarterOverQuarterComparison;
    
    /**
     * Variance analysis
     */
    private VarianceAnalysis varianceAnalysis;
    
    /**
     * Trend analysis
     */
    private TrendAnalysis trendAnalysis;
}

/**
 * Financial statement note
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class FinancialStatementNote {
    
    /**
     * Note number or identifier
     */
    private String noteNumber;
    
    /**
     * Note title
     */
    private String noteTitle;
    
    /**
     * Note content
     */
    private String noteContent;
    
    /**
     * Note category
     */
    private String noteCategory;
    
    /**
     * Related account or line item
     */
    private String relatedItem;
    
    /**
     * Whether this note is mandatory
     */
    private boolean isMandatory;
    
    /**
     * Note order for presentation
     */
    private Integer displayOrder;
}

/**
 * Consolidation information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ConsolidationInfo {
    
    /**
     * Parent company information
     */
    private CompanyInfo parentCompany;
    
    /**
     * List of consolidated entities
     */
    private List<ConsolidatedEntity> consolidatedEntities;
    
    /**
     * Consolidation method used
     */
    private String consolidationMethod;
    
    /**
     * Elimination entries made
     */
    private List<EliminationEntry> eliminationEntries;
    
    /**
     * Non-controlling interests
     */
    private BigDecimal nonControllingInterests;
    
    /**
     * Intercompany eliminations
     */
    private BigDecimal intercompanyEliminations;
}

/**
 * Exchange rate information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ExchangeRateInfo {
    
    /**
     * Currency code
     */
    private String currencyCode;
    
    /**
     * Exchange rate to base currency
     */
    private BigDecimal exchangeRate;
    
    /**
     * Rate type (CLOSING, AVERAGE, HISTORICAL)
     */
    private String rateType;
    
    /**
     * Rate date
     */
    private LocalDate rateDate;
    
    /**
     * Rate source
     */
    private String rateSource;
}

/**
 * Auditor information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AuditorInfo {
    
    /**
     * Audit firm name
     */
    private String auditFirm;
    
    /**
     * Lead auditor name
     */
    private String leadAuditor;
    
    /**
     * Audit opinion
     */
    private String auditOpinion;
    
    /**
     * Audit completion date
     */
    private LocalDate auditCompletionDate;
    
    /**
     * Audit report date
     */
    private LocalDate auditReportDate;
}

/**
 * Subsidiary information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SubsidiaryInfo {
    
    /**
     * Subsidiary name
     */
    private String subsidiaryName;
    
    /**
     * Subsidiary ID
     */
    private UUID subsidiaryId;
    
    /**
     * Ownership percentage
     */
    private BigDecimal ownershipPercentage;
    
    /**
     * Country of incorporation
     */
    private String countryOfIncorporation;
    
    /**
     * Functional currency
     */
    private String functionalCurrency;
    
    /**
     * Whether fully consolidated
     */
    private boolean isFullyConsolidated;
}

/**
 * Report generation parameters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReportParameters {
    
    /**
     * Reporting level of detail
     */
    private String detailLevel;
    
    /**
     * Whether to include comparative data
     */
    private boolean includeComparatives;
    
    /**
     * Whether to include notes
     */
    private boolean includeNotes;
    
    /**
     * Whether to include ratios
     */
    private boolean includeRatios;
    
    /**
     * Report format
     */
    private String reportFormat;
    
    /**
     * Rounding precision
     */
    private Integer roundingPrecision;
    
    /**
     * Whether to show zero balances
     */
    private boolean showZeroBalances;
}

/**
 * Retained earnings line item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RetainedEarningsLineItem {
    
    /**
     * Description of the line item
     */
    private String description;
    
    /**
     * Amount of the line item
     */
    private BigDecimal amount;
    
    /**
     * Line item type
     */
    private String lineItemType;
    
    /**
     * Related account
     */
    private String relatedAccount;
}

/**
 * Equity component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class EquityComponent {
    
    /**
     * Beginning balance
     */
    private BigDecimal beginningBalance;
    
    /**
     * Changes during the period
     */
    private BigDecimal periodChanges;
    
    /**
     * Ending balance
     */
    private BigDecimal endingBalance;
    
    /**
     * Number of shares (if applicable)
     */
    private Long numberOfShares;
    
    /**
     * Par value per share (if applicable)
     */
    private BigDecimal parValuePerShare;
}

/**
 * Equity transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class EquityTransaction {
    
    /**
     * Transaction description
     */
    private String description;
    
    /**
     * Transaction date
     */
    private LocalDate transactionDate;
    
    /**
     * Transaction amount
     */
    private BigDecimal amount;
    
    /**
     * Transaction type
     */
    private String transactionType;
    
    /**
     * Number of shares affected
     */
    private Long sharesAffected;
}

/**
 * Year-over-year comparison
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class YearOverYearComparison {
    
    /**
     * Current year net income
     */
    private BigDecimal currentYearNetIncome;
    
    /**
     * Previous year net income
     */
    private BigDecimal previousYearNetIncome;
    
    /**
     * Net income variance
     */
    private BigDecimal netIncomeVariance;
    
    /**
     * Net income variance percentage
     */
    private BigDecimal netIncomeVariancePercent;
    
    /**
     * Revenue growth rate
     */
    private BigDecimal revenueGrowthRate;
    
    /**
     * Asset growth rate
     */
    private BigDecimal assetGrowthRate;
}

/**
 * Quarter-over-quarter comparison
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class QuarterOverQuarterComparison {
    
    /**
     * Current quarter net income
     */
    private BigDecimal currentQuarterNetIncome;
    
    /**
     * Previous quarter net income
     */
    private BigDecimal previousQuarterNetIncome;
    
    /**
     * Quarterly variance
     */
    private BigDecimal quarterlyVariance;
    
    /**
     * Quarterly variance percentage
     */
    private BigDecimal quarterlyVariancePercent;
    
    /**
     * Seasonal adjustment factor
     */
    private BigDecimal seasonalAdjustment;
}

/**
 * Trend analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TrendAnalysis {
    
    /**
     * Revenue trend (INCREASING, DECREASING, STABLE)
     */
    private String revenueTrend;
    
    /**
     * Expense trend
     */
    private String expenseTrend;
    
    /**
     * Profitability trend
     */
    private String profitabilityTrend;
    
    /**
     * Liquidity trend
     */
    private String liquidityTrend;
    
    /**
     * Growth trajectory
     */
    private String growthTrajectory;
}

/**
 * Consolidated entity information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ConsolidatedEntity {
    
    /**
     * Entity name
     */
    private String entityName;
    
    /**
     * Entity ID
     */
    private UUID entityId;
    
    /**
     * Consolidation percentage
     */
    private BigDecimal consolidationPercentage;
    
    /**
     * Consolidation method
     */
    private String consolidationMethod;
    
    /**
     * Acquisition date
     */
    private LocalDate acquisitionDate;
}