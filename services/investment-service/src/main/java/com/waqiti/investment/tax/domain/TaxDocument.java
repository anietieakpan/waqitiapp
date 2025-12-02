package com.waqiti.investment.tax.domain;

import com.waqiti.investment.tax.enums.DeliveryMethod;
import com.waqiti.investment.tax.enums.DocumentType;
import com.waqiti.investment.tax.enums.FilingStatus;
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
import java.util.Map;
import java.util.UUID;

/**
 * Tax Document Entity - Represents IRS Forms (1099-B, 1099-DIV, 1099-INT)
 *
 * Compliance: IRC Sections 6045, 6042, 6049
 * IRS Publication 1220 (FIRE Specifications)
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Entity
@Table(name = "tax_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TaxDocument {

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

    // =========================================================================
    // Document Classification
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    @Column(name = "document_number", nullable = false, unique = true, length = 50)
    private String documentNumber;

    // =========================================================================
    // Correction Tracking
    // =========================================================================

    @Column(name = "is_corrected", nullable = false)
    @Builder.Default
    private Boolean isCorrected = false;

    @Column(name = "original_document_id")
    private UUID originalDocumentId;

    @Column(name = "correction_number")
    private Integer correctionNumber;

    // =========================================================================
    // Taxpayer Information
    // =========================================================================

    @Column(name = "taxpayer_tin", length = 500) // Encrypted SSN/EIN
    private String taxpayerTin;

    @Column(name = "taxpayer_name", nullable = false)
    private String taxpayerName;

    @Column(name = "taxpayer_address_line1")
    private String taxpayerAddressLine1;

    @Column(name = "taxpayer_address_line2")
    private String taxpayerAddressLine2;

    @Column(name = "taxpayer_city", length = 100)
    private String taxpayerCity;

    @Column(name = "taxpayer_state", length = 2)
    private String taxpayerState;

    @Column(name = "taxpayer_zip", length = 10)
    private String taxpayerZip;

    // =========================================================================
    // Payer Information (Waqiti)
    // =========================================================================

    @Column(name = "payer_tin", nullable = false, length = 11)
    private String payerTin;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "payer_address", length = 500)
    private String payerAddress;

    // =========================================================================
    // Form 1099-B Fields (Broker Transactions)
    // =========================================================================

    @Column(name = "proceeds_from_sales", precision = 19, scale = 4)
    private BigDecimal proceedsFromSales;

    @Column(name = "cost_basis", precision = 19, scale = 4)
    private BigDecimal costBasis;

    @Column(name = "wash_sale_loss_disallowed", precision = 19, scale = 4)
    private BigDecimal washSaleLossDisallowed;

    @Column(name = "federal_tax_withheld", precision = 19, scale = 4)
    private BigDecimal federalTaxWithheld;

    @Column(name = "short_term_covered")
    private Boolean shortTermCovered;

    @Column(name = "short_term_not_covered")
    private Boolean shortTermNotCovered;

    @Column(name = "long_term_covered")
    private Boolean longTermCovered;

    @Column(name = "long_term_not_covered")
    private Boolean longTermNotCovered;

    @Column(name = "is_ordinary_income")
    private Boolean isOrdinaryIncome;

    @Column(name = "aggregate_profit_loss", precision = 19, scale = 4)
    private BigDecimal aggregateProfitLoss;

    // =========================================================================
    // Form 1099-DIV Fields (Dividends)
    // =========================================================================

    @Column(name = "total_ordinary_dividends", precision = 19, scale = 4)
    private BigDecimal totalOrdinaryDividends;

    @Column(name = "qualified_dividends", precision = 19, scale = 4)
    private BigDecimal qualifiedDividends;

    @Column(name = "total_capital_gain_distributions", precision = 19, scale = 4)
    private BigDecimal totalCapitalGainDistributions;

    @Column(name = "section_1250_gain", precision = 19, scale = 4)
    private BigDecimal section1250Gain;

    @Column(name = "section_1202_gain", precision = 19, scale = 4)
    private BigDecimal section1202Gain;

    @Column(name = "collectibles_gain", precision = 19, scale = 4)
    private BigDecimal collectiblesGain;

    @Column(name = "section_897_dividends", precision = 19, scale = 4)
    private BigDecimal section897Dividends;

    @Column(name = "section_897_capital_gain", precision = 19, scale = 4)
    private BigDecimal section897CapitalGain;

    @Column(name = "nondividend_distributions", precision = 19, scale = 4)
    private BigDecimal nondividendDistributions;

    @Column(name = "div_federal_tax_withheld", precision = 19, scale = 4)
    private BigDecimal divFederalTaxWithheld;

    @Column(name = "section_199a_dividends", precision = 19, scale = 4)
    private BigDecimal section199aDividends;

    @Column(name = "investment_expenses", precision = 19, scale = 4)
    private BigDecimal investmentExpenses;

    @Column(name = "foreign_tax_paid", precision = 19, scale = 4)
    private BigDecimal foreignTaxPaid;

    @Column(name = "foreign_country", length = 100)
    private String foreignCountry;

    @Column(name = "cash_liquidation_distributions", precision = 19, scale = 4)
    private BigDecimal cashLiquidationDistributions;

    @Column(name = "noncash_liquidation_distributions", precision = 19, scale = 4)
    private BigDecimal noncashLiquidationDistributions;

    @Column(name = "exempt_interest_dividends", precision = 19, scale = 4)
    private BigDecimal exemptInterestDividends;

    @Column(name = "private_activity_bond_dividends", precision = 19, scale = 4)
    private BigDecimal privateActivityBondDividends;

    // =========================================================================
    // State Tax Information
    // =========================================================================

    @Column(name = "state_tax_withheld", precision = 19, scale = 4)
    private BigDecimal stateTaxWithheld;

    @Column(name = "state_payer_number", length = 20)
    private String statePayerNumber;

    @Column(name = "state_distribution", precision = 19, scale = 4)
    private BigDecimal stateDistribution;

    // =========================================================================
    // Transaction Details (JSONB)
    // =========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_details", columnDefinition = "jsonb")
    private Map<String, Object> transactionDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dividend_details", columnDefinition = "jsonb")
    private Map<String, Object> dividendDetails;

    // =========================================================================
    // Filing and Delivery
    // =========================================================================

    @Column(name = "generated_at", nullable = false)
    private LocalDate generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "filing_status", nullable = false, length = 30)
    @Builder.Default
    private FilingStatus filingStatus = FilingStatus.PENDING_GENERATION;

    @Column(name = "filed_at")
    private LocalDate filedAt;

    @Column(name = "irs_confirmation_number", length = 100)
    private String irsConfirmationNumber;

    @Column(name = "delivered_to_recipient_at")
    private LocalDate deliveredToRecipientAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", length = 20)
    private DeliveryMethod deliveryMethod;

    // File paths
    @Column(name = "pdf_file_path", length = 500)
    private String pdfFilePath;

    @Column(name = "fire_xml_file_path", length = 500)
    private String fireXmlFilePath;

    @Column(name = "digital_signature", length = 500)
    private String digitalSignature;

    // =========================================================================
    // Compliance and Audit
    // =========================================================================

    @Column(name = "calculation_notes", columnDefinition = "TEXT")
    private String calculationNotes;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDate reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

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

    public void markAsGenerated() {
        this.filingStatus = FilingStatus.GENERATED;
    }

    public void markAsReviewed(UUID reviewerId, String notes) {
        this.filingStatus = FilingStatus.REVIEWED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDate.now();
        this.reviewNotes = notes;
    }

    public void markAsFiledWithIRS(String confirmationNumber) {
        this.filingStatus = FilingStatus.FILED_WITH_IRS;
        this.filedAt = LocalDate.now();
        this.irsConfirmationNumber = confirmationNumber;
    }

    public void markAsDelivered(DeliveryMethod method) {
        this.filingStatus = FilingStatus.DELIVERED_TO_RECIPIENT;
        this.deliveredToRecipientAt = LocalDate.now();
        this.deliveryMethod = method;
    }

    public void markAsCompleted() {
        this.filingStatus = FilingStatus.COMPLETED;
    }

    public void markAsFailed() {
        this.filingStatus = FilingStatus.FAILED;
    }

    public boolean isReadyForIRSFiling() {
        return FilingStatus.REVIEWED.equals(this.filingStatus);
    }

    public boolean isReadyForDelivery() {
        return FilingStatus.FILED_WITH_IRS.equals(this.filingStatus);
    }

    public boolean isForm1099B() {
        return DocumentType.FORM_1099_B.equals(this.documentType);
    }

    public boolean isForm1099DIV() {
        return DocumentType.FORM_1099_DIV.equals(this.documentType);
    }
}
