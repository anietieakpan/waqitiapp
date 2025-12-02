package com.waqiti.account.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for crediting an account
 *
 * Credit operations add funds to an account. Common use cases:
 * - Customer deposits
 * - Refunds and reversals
 * - Interest payments
 * - Incoming transfers
 * - Cashback/rewards
 * - Payment settlements
 *
 * Critical Requirements:
 * - Idempotency: Same idempotency key = same credit (prevent duplicates)
 * - Audit trail: Complete transaction metadata
 * - Compliance: Source of funds verification (AML/KYC)
 * - Validation: Amount, currency, account status checks
 * - Atomicity: All-or-nothing operation
 *
 * Business Rules:
 * - Amount must be positive and non-zero
 * - Account must be ACTIVE (not FROZEN, CLOSED, SUSPENDED)
 * - Currency must match account currency (or conversion required)
 * - Daily/monthly limits may apply (checked by service)
 * - Large credits may trigger compliance review
 * - Idempotency key required for financial operations
 *
 * @author Production Readiness Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRequest {

    /**
     * ID of the account to credit
     * Required for identifying the target account
     */
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    /**
     * Credit amount
     * Must be positive and greater than zero
     * Precision: 19 digits, scale: 4 (matches BigDecimal(19,4) in entity)
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", inclusive = true, message = "Amount must be greater than zero")
    @DecimalMax(value = "999999999.9999", inclusive = true, message = "Amount exceeds maximum allowed")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     * Must match account currency or conversion rate required
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase 3-letter code (e.g., USD, EUR, GBP)")
    private String currency;

    /**
     * Transaction reference/ID from external system
     * Required for tracking and reconciliation
     */
    @NotBlank(message = "Transaction reference is required")
    @Size(max = 100, message = "Transaction reference cannot exceed 100 characters")
    private String transactionReference;

    /**
     * Idempotency key for duplicate prevention
     * CRITICAL: Same key = same transaction (returns existing result)
     * Format: UUID or unique string
     */
    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
    private String idempotencyKey;

    /**
     * Credit type/category for classification
     * Values: DEPOSIT, REFUND, TRANSFER_IN, INTEREST, CASHBACK, REWARD, SETTLEMENT, OTHER
     */
    @NotBlank(message = "Credit type is required")
    private String creditType;

    /**
     * Source of funds description
     * Required for AML/compliance tracking
     */
    @NotBlank(message = "Source of funds is required")
    @Size(min = 5, max = 200, message = "Source description must be between 5 and 200 characters")
    private String sourceOfFunds;

    /**
     * Description/narration for the credit
     * Appears on account statement
     */
    @NotBlank(message = "Description is required")
    @Size(min = 5, max = 500, message = "Description must be between 5 and 500 characters")
    private String description;

    /**
     * Source account ID (for internal transfers)
     * Optional: Only required if creditType = TRANSFER_IN
     */
    private UUID sourceAccountId;

    /**
     * External system ID (e.g., payment processor transaction ID)
     * Optional: For reconciliation with external systems
     */
    private String externalSystemId;

    /**
     * External system name (e.g., "Stripe", "PayPal", "Wire Transfer")
     * Optional: Identifies the source system
     */
    private String externalSystemName;

    /**
     * Effective date/time for the credit
     * Optional: If null, uses current timestamp
     * For backdated or scheduled credits
     */
    private LocalDateTime effectiveDate;

    /**
     * User ID initiating the credit (for audit trail)
     * Optional: System-initiated credits may not have user ID
     */
    private UUID initiatedBy;

    /**
     * IP address of the user/system initiating credit
     * For security and fraud detection
     */
    @Size(max = 45, message = "IP address cannot exceed 45 characters")
    private String ipAddress;

    /**
     * Additional metadata (JSON format)
     * Optional: For storing extra transaction details
     * Examples: processor response, compliance flags, etc.
     */
    private String metadata;

    /**
     * Flag indicating if compliance review is required
     * True for large amounts or high-risk sources
     */
    @Builder.Default
    private Boolean requiresComplianceReview = false;

    /**
     * Flag indicating if this is a reversal of a previous transaction
     */
    @Builder.Default
    private Boolean isReversal = false;

    /**
     * Original transaction ID (if this is a reversal/refund)
     */
    private UUID originalTransactionId;

    /**
     * Validates if this is an internal transfer
     */
    public boolean isInternalTransfer() {
        return "TRANSFER_IN".equals(creditType) && sourceAccountId != null;
    }

    /**
     * Validates if this is from external source
     */
    public boolean isExternalCredit() {
        return externalSystemId != null && externalSystemName != null;
    }

    /**
     * Validates if this is a reversal operation
     */
    public boolean isReversalOperation() {
        return Boolean.TRUE.equals(isReversal) && originalTransactionId != null;
    }

    /**
     * Checks if amount exceeds threshold for compliance review
     * Threshold: $10,000 USD equivalent
     */
    public boolean exceedsComplianceThreshold() {
        if (amount == null) return false;

        // Convert to USD equivalent for threshold check (simplified)
        // In production, use actual exchange rates
        BigDecimal threshold = new BigDecimal("10000.00");
        return amount.compareTo(threshold) > 0;
    }

    /**
     * Validates if credit type is valid
     */
    private boolean isValidCreditType(String type) {
        return type != null && type.matches(
            "DEPOSIT|REFUND|TRANSFER_IN|INTEREST|CASHBACK|REWARD|SETTLEMENT|ADJUSTMENT|OTHER"
        );
    }

    /**
     * Comprehensive validation of the credit request
     *
     * @return validation error message, or null if valid
     */
    public String getValidationError() {
        if (accountId == null) {
            return "Account ID is required";
        }

        if (amount == null) {
            return "Amount is required";
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Amount must be greater than zero";
        }

        if (amount.compareTo(new BigDecimal("999999999.9999")) > 0) {
            return "Amount exceeds maximum allowed";
        }

        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            return "Valid 3-letter currency code required";
        }

        if (transactionReference == null || transactionReference.trim().isEmpty()) {
            return "Transaction reference is required";
        }

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return "Idempotency key is required";
        }

        if (creditType == null || !isValidCreditType(creditType)) {
            return "Invalid credit type: " + creditType;
        }

        if (sourceOfFunds == null || sourceOfFunds.trim().length() < 5) {
            return "Source of funds description required (min 5 characters)";
        }

        if (description == null || description.trim().length() < 5) {
            return "Description required (min 5 characters)";
        }

        // Internal transfers must have source account
        if ("TRANSFER_IN".equals(creditType) && sourceAccountId == null) {
            return "Source account ID required for internal transfers";
        }

        // Reversals must reference original transaction
        if (Boolean.TRUE.equals(isReversal) && originalTransactionId == null) {
            return "Original transaction ID required for reversals";
        }

        return null; // Valid
    }

    /**
     * Validates business rules for the credit request
     *
     * @return true if request is valid, false otherwise
     */
    public boolean isValid() {
        return getValidationError() == null;
    }

    /**
     * Gets a sanitized description for logging (removes sensitive data)
     */
    public String getSanitizedDescription() {
        if (description == null) return null;

        // Remove potential account numbers, card numbers, etc.
        return description.replaceAll("\\d{4,}", "****");
    }
}
