package com.waqiti.investment.tax.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity for tracking individual tax-reportable transactions.
 *
 * This entity provides detailed lot-level tracking for:
 * - Stock sales (1099-B reporting)
 * - Dividend payments (1099-DIV reporting)
 * - Wash sale tracking
 * - Cost basis calculation (FIFO, LIFO, Specific Identification)
 *
 * IRS Requirements:
 * - Form 8949 (Sales and Other Dispositions of Capital Assets)
 * - Schedule D (Capital Gains and Losses)
 * - Publication 550 (Investment Income and Expenses)
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Entity
@Table(name = "tax_transactions", indexes = {
    @Index(name = "idx_tax_txn_user_year", columnList = "user_id, tax_year"),
    @Index(name = "idx_tax_txn_account_year", columnList = "investment_account_id, tax_year"),
    @Index(name = "idx_tax_txn_symbol_year", columnList = "symbol, tax_year"),
    @Index(name = "idx_tax_txn_type", columnList = "transaction_type"),
    @Index(name = "idx_tax_txn_sale_date", columnList = "sale_date"),
    @Index(name = "idx_tax_txn_reported", columnList = "reported_on_1099, tax_year"),
    @Index(name = "idx_tax_txn_wash_sale", columnList = "is_wash_sale, tax_year"),
    @Index(name = "idx_tax_txn_holding_period", columnList = "holding_period_type, tax_year")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxTransaction extends BaseEntity {

    /**
     * User ID
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Investment account ID
     */
    @Column(name = "investment_account_id", nullable = false)
    private String investmentAccountId;

    /**
     * Tax year
     */
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    /**
     * Transaction type
     */
    @Column(name = "transaction_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    /**
     * Related investment order ID (for sales)
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * Related tax document ID (once reported)
     */
    @Column(name = "tax_document_id")
    private UUID taxDocumentId;

    // =============================================================================
    // Security Information
    // =============================================================================

    /**
     * Stock symbol
     */
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    /**
     * Security name
     */
    @Column(name = "security_name", length = 255)
    private String securityName;

    /**
     * CUSIP number (Committee on Uniform Securities Identification Procedures)
     */
    @Column(name = "cusip", length = 9)
    private String cusip;

    /**
     * Instrument type: STOCK, ETF, MUTUAL_FUND, BOND, CRYPTO
     */
    @Column(name = "instrument_type", length = 30)
    private String instrumentType;

    // =============================================================================
    // Transaction Details (For Sales - 1099-B)
    // =============================================================================

    /**
     * Date acquired
     */
    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    /**
     * Date sold
     */
    @Column(name = "sale_date")
    private LocalDate saleDate;

    /**
     * Quantity sold
     */
    @Column(name = "quantity", precision = 19, scale = 8)
    private BigDecimal quantity;

    /**
     * Sale proceeds (gross amount before commissions)
     */
    @Column(name = "proceeds", precision = 19, scale = 4)
    private BigDecimal proceeds;

    /**
     * Cost basis (purchase price + commissions)
     */
    @Column(name = "cost_basis", precision = 19, scale = 4)
    private BigDecimal costBasis;

    /**
     * Commission/fees on sale
     */
    @Column(name = "sale_commission", precision = 19, scale = 4)
    private BigDecimal saleCommission;

    /**
     * Commission/fees on purchase
     */
    @Column(name = "purchase_commission", precision = 19, scale = 4)
    private BigDecimal purchaseCommission;

    /**
     * Net proceeds (proceeds - commission)
     */
    @Column(name = "net_proceeds", precision = 19, scale = 4)
    private BigDecimal netProceeds;

    /**
     * Adjusted cost basis (including adjustments)
     */
    @Column(name = "adjusted_cost_basis", precision = 19, scale = 4)
    private BigDecimal adjustedCostBasis;

    /**
     * Gain or loss
     */
    @Column(name = "gain_loss", precision = 19, scale = 4)
    private BigDecimal gainLoss;

    /**
     * Holding period type: SHORT_TERM, LONG_TERM
     */
    @Column(name = "holding_period_type", length = 20)
    @Enumerated(EnumType.STRING)
    private HoldingPeriodType holdingPeriodType;

    /**
     * Holding period in days
     */
    @Column(name = "holding_period_days")
    private Integer holdingPeriodDays;

    // =============================================================================
    // Wash Sale Tracking
    // =============================================================================

    /**
     * Wash sale flag
     */
    @Column(name = "is_wash_sale", nullable = false)
    private Boolean isWashSale = false;

    /**
     * Wash sale loss disallowed
     */
    @Column(name = "wash_sale_loss_disallowed", precision = 19, scale = 4)
    private BigDecimal washSaleLossDisallowed;

    /**
     * Related wash sale transaction ID
     */
    @Column(name = "related_wash_sale_transaction_id")
    private UUID relatedWashSaleTransactionId;

    /**
     * Wash sale adjustment to cost basis
     */
    @Column(name = "wash_sale_adjustment", precision = 19, scale = 4)
    private BigDecimal washSaleAdjustment;

    // =============================================================================
    // Dividend Details (For 1099-DIV)
    // =============================================================================

    /**
     * Dividend payment date
     */
    @Column(name = "dividend_payment_date")
    private LocalDate dividendPaymentDate;

    /**
     * Dividend ex-date
     */
    @Column(name = "dividend_ex_date")
    private LocalDate dividendExDate;

    /**
     * Dividend amount
     */
    @Column(name = "dividend_amount", precision = 19, scale = 4)
    private BigDecimal dividendAmount;

    /**
     * Dividend type: ORDINARY, QUALIFIED, CAPITAL_GAIN
     */
    @Column(name = "dividend_type", length = 30)
    @Enumerated(EnumType.STRING)
    private DividendType dividendType;

    /**
     * Qualified dividend flag (for preferential tax rate)
     */
    @Column(name = "is_qualified_dividend", nullable = false)
    private Boolean isQualifiedDividend = false;

    /**
     * Return of capital (nondividend distribution)
     */
    @Column(name = "return_of_capital", precision = 19, scale = 4)
    private BigDecimal returnOfCapital;

    /**
     * Foreign tax paid on dividend
     */
    @Column(name = "foreign_tax_paid", precision = 19, scale = 4)
    private BigDecimal foreignTaxPaid;

    /**
     * Foreign country
     */
    @Column(name = "foreign_country", length = 100)
    private String foreignCountry;

    // =============================================================================
    // Cost Basis Calculation
    // =============================================================================

    /**
     * Cost basis method: FIFO, LIFO, SPECIFIC_ID, AVERAGE_COST
     */
    @Column(name = "cost_basis_method", length = 20)
    @Enumerated(EnumType.STRING)
    private CostBasisMethod costBasisMethod;

    /**
     * Covered security flag (broker reports cost basis to IRS)
     */
    @Column(name = "is_covered_security", nullable = false)
    private Boolean isCoveredSecurity = true;

    /**
     * Noncovered security (acquired before cost basis reporting rules)
     */
    @Column(name = "is_noncovered_security", nullable = false)
    private Boolean isNoncoveredSecurity = false;

    // =============================================================================
    // Reporting Status
    // =============================================================================

    /**
     * Reported on 1099 form
     */
    @Column(name = "reported_on_1099", nullable = false)
    private Boolean reportedOn1099 = false;

    /**
     * Form 1099 document number (reference)
     */
    @Column(name = "form_1099_document_number", length = 50)
    private String form1099DocumentNumber;

    /**
     * Reporting year (may differ from tax year for late transactions)
     */
    @Column(name = "reporting_year")
    private Integer reportingYear;

    /**
     * IRS reporting code (for Form 8949)
     */
    @Column(name = "irs_reporting_code", length = 5)
    private String irsReportingCode;

    // =============================================================================
    // Additional Information
    // =============================================================================

    /**
     * Ordinary income flag (commodities, short sales, etc.)
     */
    @Column(name = "is_ordinary_income", nullable = false)
    private Boolean isOrdinaryIncome = false;

    /**
     * Collectibles gain flag (28% tax rate)
     */
    @Column(name = "is_collectibles_gain", nullable = false)
    private Boolean isCollectiblesGain = false;

    /**
     * Section 1256 contract flag (60/40 rule)
     */
    @Column(name = "is_section_1256", nullable = false)
    private Boolean isSection1256 = false;

    /**
     * Adjustment description
     */
    @Column(name = "adjustment_description", columnDefinition = "TEXT")
    private String adjustmentDescription;

    /**
     * Notes for tax reporting
     */
    @Column(name = "tax_notes", columnDefinition = "TEXT")
    private String taxNotes;

    // =============================================================================
    // Enums
    // =============================================================================

    public enum TransactionType {
        STOCK_SALE,
        STOCK_PURCHASE,
        DIVIDEND_ORDINARY,
        DIVIDEND_QUALIFIED,
        DIVIDEND_CAPITAL_GAIN,
        RETURN_OF_CAPITAL,
        INTEREST_INCOME,
        BOND_INTEREST,
        OPTION_EXERCISE,
        OPTION_ASSIGNMENT,
        STOCK_SPLIT,
        MERGER_ACQUISITION,
        SPINOFF
    }

    public enum HoldingPeriodType {
        SHORT_TERM,  // 365 days or less
        LONG_TERM    // More than 365 days
    }

    public enum DividendType {
        ORDINARY,
        QUALIFIED,
        CAPITAL_GAIN,
        RETURN_OF_CAPITAL,
        EXEMPT_INTEREST
    }

    public enum CostBasisMethod {
        FIFO,           // First In, First Out
        LIFO,           // Last In, First Out
        SPECIFIC_ID,    // Specific Share Identification
        AVERAGE_COST,   // Average Cost (for mutual funds)
        HIFO            // Highest In, First Out
    }
}
