package com.waqiti.tax.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request object for tax calculations.
 * Contains all necessary information to calculate taxes for financial transactions
 * across different jurisdictions and transaction types.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationRequest {

    /**
     * Unique identifier for the transaction requiring tax calculation
     */
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    /**
     * User ID for whom the tax is being calculated
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Transaction amount subject to taxation
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be positive")
    @Digits(integer = 12, fraction = 2, message = "Amount must have max 12 integer digits and 2 decimal places")
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    /**
     * Tax jurisdiction code (e.g., "US-CA", "US-NY", "UK", "DE")
     */
    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    /**
     * Type of transaction for tax classification
     */
    @NotBlank(message = "Transaction type is required")
    private String transactionType;

    /**
     * Date when the transaction occurred
     */
    @NotNull(message = "Transaction date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;

    /**
     * Category of the transaction (e.g., "BUSINESS", "PERSONAL", "CHARITY")
     */
    private String transactionCategory;

    /**
     * Type of recipient (e.g., "INDIVIDUAL", "BUSINESS", "GOVERNMENT", "CHARITY")
     */
    private String recipientType;

    /**
     * Transaction fees that may be deductible
     */
    @DecimalMin(value = "0.0", message = "Transaction fee must be non-negative")
    private BigDecimal transactionFee;

    /**
     * List of tax exemption codes that may apply
     */
    private List<String> taxExemptionCodes;

    /**
     * Country code where the transaction originates
     */
    private String sourceCountry;

    /**
     * Country code where the transaction is destined
     */
    private String destinationCountry;

    /**
     * State/province code for state-level taxation
     */
    private String stateCode;

    /**
     * Whether this is a cross-border transaction
     */
    @Builder.Default
    private Boolean crossBorder = false;

    /**
     * Business type for business-specific tax rules
     */
    private String businessType;

    /**
     * Tax year for which calculation is being performed
     */
    private Integer taxYear;

    /**
     * Customer tax residence status
     */
    private String taxResidenceStatus;

    /**
     * Additional metadata for tax calculation
     */
    private String metadata;

    /**
     * Whether this transaction should be included in tax reporting
     */
    @Builder.Default
    private Boolean includeTaxReporting = true;

    /**
     * Payment method used (may affect tax treatment)
     */
    private String paymentMethod;

    /**
     * Reference to the original transaction if this is a refund
     */
    private String originalTransactionId;

    /**
     * Validates the request for completeness and consistency
     */
    public void validate() {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (transactionFee != null && transactionFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction fee cannot be negative");
        }
        
        if (crossBorder && (sourceCountry == null || destinationCountry == null)) {
            throw new IllegalArgumentException("Source and destination countries required for cross-border transactions");
        }
        
        if (taxYear != null && (taxYear < 2000 || taxYear > LocalDate.now().getYear() + 1)) {
            throw new IllegalArgumentException("Invalid tax year");
        }
    }

    /**
     * Checks if this is a domestic transaction
     */
    public boolean isDomestic() {
        return !Boolean.TRUE.equals(crossBorder) && 
               sourceCountry != null && destinationCountry != null && 
               sourceCountry.equals(destinationCountry);
    }

    /**
     * Gets the effective tax year (current year if not specified)
     */
    public int getEffectiveTaxYear() {
        return taxYear != null ? taxYear : LocalDate.now().getYear();
    }

    /**
     * Checks if any tax exemptions are claimed
     */
    public boolean hasTaxExemptions() {
        return taxExemptionCodes != null && !taxExemptionCodes.isEmpty();
    }

    /**
     * Gets the transaction amount for display (formatted)
     */
    public String getFormattedAmount() {
        return amount != null ? String.format("%.2f %s", amount, currency) : "N/A";
    }
}