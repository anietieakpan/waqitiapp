package com.waqiti.investment.tax.domain;

import com.waqiti.investment.tax.enums.CostBasisMethod;
import com.waqiti.investment.tax.enums.DividendType;
import com.waqiti.investment.tax.enums.HoldingPeriodType;
import com.waqiti.investment.tax.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Tax Transaction Entity - Detailed lot-level tracking for tax reporting
 *
 * Tracks individual securities transactions with full cost basis,
 * wash sale detection, and holding period calculations
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Entity
@Table(name = "tax_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TaxTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // =========================================================================
    // Entity References
    // =========================================================================

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "investment_account_id", nullable = false)
    private String investmentAccountId;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    // =========================================================================
    // Transaction Classification
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    // Related References
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "tax_document_id")
    private UUID taxDocumentId;

    // =========================================================================
    // Security Information
    // =========================================================================

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "security_name")
    private String securityName;

    @Column(name = "cusip", length = 9)
    private String cusip;

    @Column(name = "instrument_type", length = 30)
    private String instrumentType;

    // =========================================================================
    // Transaction Details (Sales - 1099-B)
    // =========================================================================

    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    @Column(name = "sale_date")
    private LocalDate saleDate;

    @Column(name = "quantity", precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "proceeds", precision = 19, scale = 4)
    private BigDecimal proceeds;

    @Column(name = "cost_basis", precision = 19, scale = 4)
    private BigDecimal costBasis;

    @Column(name = "sale_commission", precision = 19, scale = 4)
    private BigDecimal saleCommission;

    @Column(name = "purchase_commission", precision = 19, scale = 4)
    private BigDecimal purchaseCommission;

    @Column(name = "net_proceeds", precision = 19, scale = 4)
    private BigDecimal netProceeds;

    @Column(name = "adjusted_cost_basis", precision = 19, scale = 4)
    private BigDecimal adjustedCostBasis;

    @Column(name = "gain_loss", precision = 19, scale = 4)
    private BigDecimal gainLoss;

    // Holding Period
    @Enumerated(EnumType.STRING)
    @Column(name = "holding_period_type", length = 20)
    private HoldingPeriodType holdingPeriodType;

    @Column(name = "holding_period_days")
    private Integer holdingPeriodDays;

    // =========================================================================
    // Wash Sale Tracking (IRC Section 1091)
    // =========================================================================

    @Column(name = "is_wash_sale", nullable = false)
    @Builder.Default
    private Boolean isWashSale = false;

    @Column(name = "wash_sale_loss_disallowed", precision = 19, scale = 4)
    private BigDecimal washSaleLossDisallowed;

    @Column(name = "related_wash_sale_transaction_id")
    private UUID relatedWashSaleTransactionId;

    @Column(name = "wash_sale_adjustment", precision = 19, scale = 4)
    private BigDecimal washSaleAdjustment;

    // =========================================================================
    // Dividend Details (1099-DIV)
    // =========================================================================

    @Column(name = "dividend_payment_date")
    private LocalDate dividendPaymentDate;

    @Column(name = "dividend_ex_date")
    private LocalDate dividendExDate;

    @Column(name = "dividend_amount", precision = 19, scale = 4)
    private BigDecimal dividendAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "dividend_type", length = 30)
    private DividendType dividendType;

    @Column(name = "is_qualified_dividend", nullable = false)
    @Builder.Default
    private Boolean isQualifiedDividend = false;

    @Column(name = "return_of_capital", precision = 19, scale = 4)
    private BigDecimal returnOfCapital;

    @Column(name = "foreign_tax_paid", precision = 19, scale = 4)
    private BigDecimal foreignTaxPaid;

    @Column(name = "foreign_country", length = 100)
    private String foreignCountry;

    // =========================================================================
    // Cost Basis Calculation
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_basis_method", length = 20)
    private CostBasisMethod costBasisMethod;

    @Column(name = "is_covered_security", nullable = false)
    @Builder.Default
    private Boolean isCoveredSecurity = true;

    @Column(name = "is_noncovered_security", nullable = false)
    @Builder.Default
    private Boolean isNoncoveredSecurity = false;

    // =========================================================================
    // Reporting Status
    // =========================================================================

    @Column(name = "reported_on_1099", nullable = false)
    @Builder.Default
    private Boolean reportedOn1099 = false;

    @Column(name = "form_1099_document_number", length = 50)
    private String form1099DocumentNumber;

    @Column(name = "reporting_year")
    private Integer reportingYear;

    @Column(name = "irs_reporting_code", length = 5)
    private String irsReportingCode;

    // =========================================================================
    // Additional Tax Attributes
    // =========================================================================

    @Column(name = "is_ordinary_income", nullable = false)
    @Builder.Default
    private Boolean isOrdinaryIncome = false;

    @Column(name = "is_collectibles_gain", nullable = false)
    @Builder.Default
    private Boolean isCollectiblesGain = false;

    @Column(name = "is_section_1256", nullable = false)
    @Builder.Default
    private Boolean isSection1256 = false;

    @Column(name = "adjustment_description", columnDefinition = "TEXT")
    private String adjustmentDescription;

    @Column(name = "tax_notes", columnDefinition = "TEXT")
    private String taxNotes;

    // =========================================================================
    // Audit Trail
    // =========================================================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // =========================================================================
    // Business Logic Methods
    // =========================================================================

    /**
     * Calculate holding period in days and classify as short-term or long-term
     */
    public void calculateHoldingPeriod() {
        if (acquisitionDate != null && saleDate != null) {
            this.holdingPeriodDays = (int) ChronoUnit.DAYS.between(acquisitionDate, saleDate);
            this.holdingPeriodType = (holdingPeriodDays > 365)
                ? HoldingPeriodType.LONG_TERM
                : HoldingPeriodType.SHORT_TERM;
        }
    }

    /**
     * Calculate gain/loss from sale
     */
    public void calculateGainLoss() {
        if (netProceeds != null && adjustedCostBasis != null) {
            this.gainLoss = netProceeds.subtract(adjustedCostBasis);
        }
    }

    /**
     * Calculate net proceeds after commissions
     */
    public void calculateNetProceeds() {
        if (proceeds != null) {
            BigDecimal commission = (saleCommission != null)
                ? saleCommission
                : BigDecimal.ZERO;
            this.netProceeds = proceeds.subtract(commission);
        }
    }

    /**
     * Calculate adjusted cost basis including commissions
     */
    public void calculateAdjustedCostBasis() {
        if (costBasis != null) {
            BigDecimal commission = (purchaseCommission != null)
                ? purchaseCommission
                : BigDecimal.ZERO;
            BigDecimal washSaleAdj = (washSaleAdjustment != null)
                ? washSaleAdjustment
                : BigDecimal.ZERO;
            this.adjustedCostBasis = costBasis.add(commission).add(washSaleAdj);
        }
    }

    /**
     * Mark as wash sale and disallow loss
     */
    public void applyWashSaleRule(BigDecimal disallowedLoss, UUID relatedTransactionId) {
        this.isWashSale = true;
        this.washSaleLossDisallowed = disallowedLoss;
        this.relatedWashSaleTransactionId = relatedTransactionId;
        // Wash sale loss is added to cost basis of replacement shares
        this.washSaleAdjustment = disallowedLoss;
    }

    /**
     * Mark as reported on 1099 form
     */
    public void markAsReported(UUID taxDocumentId, String documentNumber) {
        this.reportedOn1099 = true;
        this.taxDocumentId = taxDocumentId;
        this.form1099DocumentNumber = documentNumber;
        this.reportingYear = taxYear;
    }

    /**
     * Check if this is a sale transaction
     */
    public boolean isSale() {
        return TransactionType.STOCK_SALE.equals(transactionType);
    }

    /**
     * Check if this is a purchase transaction
     */
    public boolean isPurchase() {
        return TransactionType.STOCK_PURCHASE.equals(transactionType);
    }

    /**
     * Check if this is a dividend transaction
     */
    public boolean isDividend() {
        return transactionType != null &&
               (transactionType.name().startsWith("DIVIDEND_") ||
                transactionType.equals(TransactionType.RETURN_OF_CAPITAL));
    }

    /**
     * Check if loss can be disallowed (only losses, not gains)
     */
    public boolean hasDisallowableLoss() {
        return gainLoss != null && gainLoss.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if holding period qualifies for long-term capital gains
     */
    public boolean isLongTermGain() {
        return HoldingPeriodType.LONG_TERM.equals(holdingPeriodType);
    }

    /**
     * Check if holding period qualifies for short-term capital gains
     */
    public boolean isShortTermGain() {
        return HoldingPeriodType.SHORT_TERM.equals(holdingPeriodType);
    }
}
