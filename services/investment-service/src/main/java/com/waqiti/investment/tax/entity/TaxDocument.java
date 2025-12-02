package com.waqiti.investment.tax.entity;

import com.waqiti.common.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for IRS Tax Documents (1099-B, 1099-DIV, 1099-INT).
 *
 * Compliance Requirements:
 * - IRC Section 6045 (Broker Reporting)
 * - IRC Section 6042 (Dividend Reporting)
 * - IRS Form 1099-B (Proceeds from Broker and Barter Exchange Transactions)
 * - IRS Form 1099-DIV (Dividends and Distributions)
 * - IRS Publication 1220 (Specifications for Electronic Filing)
 *
 * Filing Deadlines:
 * - To Recipients: January 31
 * - To IRS: February 28 (paper) or March 31 (electronic)
 *
 * Record Retention: 7 years
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Entity
@Table(name = "tax_documents", indexes = {
    @Index(name = "idx_tax_doc_user_year", columnList = "user_id, tax_year"),
    @Index(name = "idx_tax_doc_account_year", columnList = "investment_account_id, tax_year"),
    @Index(name = "idx_tax_doc_type_year", columnList = "document_type, tax_year"),
    @Index(name = "idx_tax_doc_status", columnList = "filing_status"),
    @Index(name = "idx_tax_doc_generated_at", columnList = "generated_at DESC"),
    @Index(name = "idx_tax_doc_filed_at", columnList = "filed_at DESC"),
    @Index(name = "idx_tax_doc_corrected", columnList = "is_corrected, tax_year")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxDocument extends BaseEntity {

    /**
     * User ID (taxpayer)
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Investment account ID
     */
    @Column(name = "investment_account_id", nullable = false)
    private String investmentAccountId;

    /**
     * Document type: 1099-B, 1099-DIV, 1099-INT, 1099-MISC
     */
    @Column(name = "document_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    /**
     * Tax year this document covers
     */
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    /**
     * Document number (unique identifier for this tax document)
     */
    @Column(name = "document_number", nullable = false, unique = true, length = 50)
    private String documentNumber;

    /**
     * Corrected form indicator (if this is an amended document)
     */
    @Column(name = "is_corrected", nullable = false)
    private Boolean isCorrected = false;

    /**
     * Original document ID (if this is a corrected form)
     */
    @Column(name = "original_document_id")
    private UUID originalDocumentId;

    /**
     * Correction sequence number
     */
    @Column(name = "correction_number")
    private Integer correctionNumber;

    // =============================================================================
    // Taxpayer Information
    // =============================================================================

    /**
     * Taxpayer TIN (SSN or EIN) - encrypted
     */
    @Column(name = "taxpayer_tin", length = 500)
    private String taxpayerTin;

    /**
     * Taxpayer name
     */
    @Column(name = "taxpayer_name", nullable = false, length = 255)
    private String taxpayerName;

    /**
     * Taxpayer address line 1
     */
    @Column(name = "taxpayer_address_line1", length = 255)
    private String taxpayerAddressLine1;

    /**
     * Taxpayer address line 2
     */
    @Column(name = "taxpayer_address_line2", length = 255)
    private String taxpayerAddressLine2;

    /**
     * Taxpayer city
     */
    @Column(name = "taxpayer_city", length = 100)
    private String taxpayerCity;

    /**
     * Taxpayer state
     */
    @Column(name = "taxpayer_state", length = 2)
    private String taxpayerState;

    /**
     * Taxpayer ZIP code
     */
    @Column(name = "taxpayer_zip", length = 10)
    private String taxpayerZip;

    // =============================================================================
    // Payer Information (Waqiti Platform)
     // =============================================================================

    /**
     * Payer TIN (Waqiti's EIN)
     */
    @Column(name = "payer_tin", nullable = false, length = 11)
    private String payerTin;

    /**
     * Payer name (Waqiti Inc)
     */
    @Column(name = "payer_name", nullable = false, length = 255)
    private String payerName;

    /**
     * Payer address
     */
    @Column(name = "payer_address", length = 500)
    private String payerAddress;

    // =============================================================================
    // 1099-B Specific Fields (Broker Transactions)
    // =============================================================================

    /**
     * Total proceeds from broker transactions
     */
    @Column(name = "proceeds_from_sales", precision = 19, scale = 4)
    private BigDecimal proceedsFromSales;

    /**
     * Cost or other basis
     */
    @Column(name = "cost_basis", precision = 19, scale = 4)
    private BigDecimal costBasis;

    /**
     * Wash sale loss disallowed
     */
    @Column(name = "wash_sale_loss_disallowed", precision = 19, scale = 4)
    private BigDecimal washSaleLossDisallowed;

    /**
     * Federal income tax withheld
     */
    @Column(name = "federal_tax_withheld", precision = 19, scale = 4)
    private BigDecimal federalTaxWithheld;

    /**
     * Short-term transactions covered (Box 1a checkbox)
     */
    @Column(name = "short_term_covered")
    private Boolean shortTermCovered;

    /**
     * Short-term transactions not covered (Box 1b checkbox)
     */
    @Column(name = "short_term_not_covered")
    private Boolean shortTermNotCovered;

    /**
     * Long-term transactions covered (Box 1e checkbox)
     */
    @Column(name = "long_term_covered")
    private Boolean longTermCovered;

    /**
     * Long-term transactions not covered (Box 1f checkbox)
     */
    @Column(name = "long_term_not_covered")
    private Boolean longTermNotCovered;

    /**
     * Gain or loss is ordinary (Box 2 checkbox)
     */
    @Column(name = "is_ordinary_income")
    private Boolean isOrdinaryIncome;

    /**
     * Aggregate profit or loss (Box 11)
     */
    @Column(name = "aggregate_profit_loss", precision = 19, scale = 4)
    private BigDecimal aggregateProfitLoss;

    // =============================================================================
    // 1099-DIV Specific Fields (Dividends and Distributions)
    // =============================================================================

    /**
     * Total ordinary dividends (Box 1a)
     */
    @Column(name = "total_ordinary_dividends", precision = 19, scale = 4)
    private BigDecimal totalOrdinaryDividends;

    /**
     * Qualified dividends (Box 1b)
     */
    @Column(name = "qualified_dividends", precision = 19, scale = 4)
    private BigDecimal qualifiedDividends;

    /**
     * Total capital gain distributions (Box 2a)
     */
    @Column(name = "total_capital_gain_distributions", precision = 19, scale = 4)
    private BigDecimal totalCapitalGainDistributions;

    /**
     * Unrecaptured Section 1250 gain (Box 2b)
     */
    @Column(name = "section_1250_gain", precision = 19, scale = 4)
    private BigDecimal section1250Gain;

    /**
     * Section 1202 gain (Box 2c)
     */
    @Column(name = "section_1202_gain", precision = 19, scale = 4)
    private BigDecimal section1202Gain;

    /**
     * Collectibles (28%) gain (Box 2d)
     */
    @Column(name = "collectibles_gain", precision = 19, scale = 4)
    private BigDecimal collectiblesGain;

    /**
     * Section 897 ordinary dividends (Box 2e)
     */
    @Column(name = "section_897_dividends", precision = 19, scale = 4)
    private BigDecimal section897Dividends;

    /**
     * Section 897 capital gain (Box 2f)
     */
    @Column(name = "section_897_capital_gain", precision = 19, scale = 4)
    private BigDecimal section897CapitalGain;

    /**
     * Nondividend distributions (Box 3)
     */
    @Column(name = "nondividend_distributions", precision = 19, scale = 4)
    private BigDecimal nondividendDistributions;

    /**
     * Federal income tax withheld (Box 4)
     */
    @Column(name = "div_federal_tax_withheld", precision = 19, scale = 4)
    private BigDecimal divFederalTaxWithheld;

    /**
     * Section 199A dividends (Box 5)
     */
    @Column(name = "section_199a_dividends", precision = 19, scale = 4)
    private BigDecimal section199aDividends;

    /**
     * Investment expenses (Box 6)
     */
    @Column(name = "investment_expenses", precision = 19, scale = 4)
    private BigDecimal investmentExpenses;

    /**
     * Foreign tax paid (Box 7)
     */
    @Column(name = "foreign_tax_paid", precision = 19, scale = 4)
    private BigDecimal foreignTaxPaid;

    /**
     * Foreign country or U.S. possession (Box 8)
     */
    @Column(name = "foreign_country", length = 100)
    private String foreignCountry;

    /**
     * Cash liquidation distributions (Box 9)
     */
    @Column(name = "cash_liquidation_distributions", precision = 19, scale = 4)
    private BigDecimal cashLiquidationDistributions;

    /**
     * Noncash liquidation distributions (Box 10)
     */
    @Column(name = "noncash_liquidation_distributions", precision = 19, scale = 4)
    private BigDecimal noncashLiquidationDistributions;

    /**
     * Exempt-interest dividends (Box 11)
     */
    @Column(name = "exempt_interest_dividends", precision = 19, scale = 4)
    private BigDecimal exemptInterestDividends;

    /**
     * Specified private activity bond interest dividends (Box 12)
     */
    @Column(name = "private_activity_bond_dividends", precision = 19, scale = 4)
    private BigDecimal privateActivityBondDividends;

    // =============================================================================
    // State Tax Information
    // =============================================================================

    /**
     * State tax withheld
     */
    @Column(name = "state_tax_withheld", precision = 19, scale = 4)
    private BigDecimal stateTaxWithheld;

    /**
     * State/Payer's state number
     */
    @Column(name = "state_payer_number", length = 20)
    private String statePayerNumber;

    /**
     * State distribution
     */
    @Column(name = "state_distribution", precision = 19, scale = 4)
    private BigDecimal stateDistribution;

    // =============================================================================
    // Transaction Details (JSONB)
    // =============================================================================

    /**
     * List of individual transactions for 1099-B
     * Each transaction includes: date acquired, date sold, proceeds, cost basis, wash sale, etc.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "transaction_details", columnDefinition = "jsonb")
    private List<Map<String, Object>> transactionDetails;

    /**
     * List of dividend payments for 1099-DIV
     * Each payment includes: date, amount, type, qualified status, etc.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "dividend_details", columnDefinition = "jsonb")
    private List<Map<String, Object>> dividendDetails;

    // =============================================================================
    // Filing and Delivery Information
    // =============================================================================

    /**
     * Document generation date
     */
    @Column(name = "generated_at", nullable = false)
    private LocalDate generatedAt;

    /**
     * Filing status
     */
    @Column(name = "filing_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private FilingStatus filingStatus;

    /**
     * Date filed with IRS
     */
    @Column(name = "filed_at")
    private LocalDate filedAt;

    /**
     * IRS confirmation number (FIRE TCC)
     */
    @Column(name = "irs_confirmation_number", length = 100)
    private String irsConfirmationNumber;

    /**
     * Date delivered to recipient
     */
    @Column(name = "delivered_to_recipient_at")
    private LocalDate deliveredToRecipientAt;

    /**
     * Delivery method: EMAIL, POSTAL_MAIL, ONLINE_PORTAL
     */
    @Column(name = "delivery_method", length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryMethod deliveryMethod;

    /**
     * PDF file path (encrypted storage)
     */
    @Column(name = "pdf_file_path", length = 500)
    private String pdfFilePath;

    /**
     * IRS FIRE XML file path
     */
    @Column(name = "fire_xml_file_path", length = 500)
    private String fireXmlFilePath;

    /**
     * Digital signature hash (for verification)
     */
    @Column(name = "digital_signature", length = 500)
    private String digitalSignature;

    // =============================================================================
    // Compliance and Audit
    // =============================================================================

    /**
     * Calculation notes (for audit trail)
     */
    @Column(name = "calculation_notes", columnDefinition = "TEXT")
    private String calculationNotes;

    /**
     * Reviewed by compliance officer
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * Compliance review date
     */
    @Column(name = "reviewed_at")
    private LocalDate reviewedAt;

    /**
     * Compliance review notes
     */
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    /**
     * Enums
     */
    public enum DocumentType {
        FORM_1099_B,     // Broker transactions
        FORM_1099_DIV,   // Dividends and distributions
        FORM_1099_INT,   // Interest income
        FORM_1099_MISC   // Miscellaneous income
    }

    public enum FilingStatus {
        PENDING_GENERATION,
        GENERATED,
        PENDING_REVIEW,
        REVIEWED,
        PENDING_IRS_FILING,
        FILED_WITH_IRS,
        PENDING_RECIPIENT_DELIVERY,
        DELIVERED_TO_RECIPIENT,
        COMPLETED,
        FAILED,
        CORRECTED
    }

    public enum DeliveryMethod {
        EMAIL,
        POSTAL_MAIL,
        ONLINE_PORTAL,
        SECURE_DOWNLOAD
    }
}
