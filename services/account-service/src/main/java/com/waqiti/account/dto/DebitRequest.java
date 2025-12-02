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
 * Request DTO for debiting an account
 *
 * Debit operations subtract funds from an account. Common use cases:
 * - Customer withdrawals
 * - Payment processing
 * - Fee charges
 * - Outgoing transfers
 * - Purchase transactions
 * - Bill payments
 *
 * Critical Requirements:
 * - Idempotency: Same idempotency key = same debit (prevent duplicates)
 * - Sufficient funds: Balance check before debit
 * - Audit trail: Complete transaction metadata
 * - Security: Enhanced validation for fund removal
 * - Compliance: Transaction monitoring (fraud detection, AML)
 * - Atomicity: All-or-nothing operation
 *
 * Business Rules:
 * - Amount must be positive and non-zero
 * - Account must have sufficient available balance
 * - Account must be ACTIVE (not FROZEN, CLOSED, SUSPENDED)
 * - Currency must match account currency
 * - Daily/monthly withdrawal limits enforced
 * - Large debits may require additional authorization
 * - Idempotency key required for financial operations
 * - Cannot debit if account has pending compliance review
 *
 * Security Considerations:
 * - IP address logging for fraud detection
 * - Device fingerprinting support
 * - Velocity checks (multiple debits in short time)
 * - Geolocation validation
 * - 2FA/MFA may be required for large amounts
 *
 * @author Production Readiness Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitRequest {

    /**
     * ID of the account to debit
     * Required for identifying the source account
     */
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    /**
     * Debit amount
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
     * Must match account currency
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
     * Debit type/category for classification
     * Values: WITHDRAWAL, PAYMENT, FEE, TRANSFER_OUT, PURCHASE, BILL_PAYMENT, CHARGE, OTHER
     */
    @NotBlank(message = "Debit type is required")
    private String debitType;

    /**
     * Purpose/destination of funds
     * Required for transaction monitoring and compliance
     */
    @NotBlank(message = "Purpose is required")
    @Size(min = 5, max = 200, message = "Purpose must be between 5 and 200 characters")
    private String purpose;

    /**
     * Description/narration for the debit
     * Appears on account statement
     */
    @NotBlank(message = "Description is required")
    @Size(min = 5, max = 500, message = "Description must be between 5 and 500 characters")
    private String description;

    /**
     * Destination account ID (for internal transfers)
     * Optional: Only required if debitType = TRANSFER_OUT
     */
    private UUID destinationAccountId;

    /**
     * Beneficiary name (for external transfers/payments)
     * Required for certain transaction types
     */
    @Size(max = 200, message = "Beneficiary name cannot exceed 200 characters")
    private String beneficiaryName;

    /**
     * Beneficiary account number (for external transfers)
     * Optional: For external payment processing
     */
    @Size(max = 50, message = "Beneficiary account cannot exceed 50 characters")
    private String beneficiaryAccount;

    /**
     * External system ID (e.g., payment processor transaction ID)
     * Optional: For reconciliation with external systems
     */
    private String externalSystemId;

    /**
     * External system name (e.g., "ACH", "Wire", "Card Network")
     * Optional: Identifies the destination system
     */
    private String externalSystemName;

    /**
     * Effective date/time for the debit
     * Optional: If null, uses current timestamp
     * For scheduled debits
     */
    private LocalDateTime effectiveDate;

    /**
     * User ID initiating the debit (for audit trail)
     * Required for user-initiated debits
     */
    private UUID initiatedBy;

    /**
     * IP address of the user/system initiating debit
     * CRITICAL for fraud detection and security
     */
    @Size(max = 45, message = "IP address cannot exceed 45 characters")
    private String ipAddress;

    /**
     * Device fingerprint for fraud detection
     * Optional: Browser/device identifier
     */
    @Size(max = 200, message = "Device fingerprint cannot exceed 200 characters")
    private String deviceFingerprint;

    /**
     * Geolocation of transaction (latitude,longitude)
     * Optional: For fraud detection
     */
    @Size(max = 50, message = "Geolocation cannot exceed 50 characters")
    private String geolocation;

    /**
     * User agent string (browser/app identifier)
     * Optional: For security analysis
     */
    @Size(max = 500, message = "User agent cannot exceed 500 characters")
    private String userAgent;

    /**
     * Additional metadata (JSON format)
     * Optional: For storing extra transaction details
     * Examples: merchant info, card details (last 4), etc.
     */
    private String metadata;

    /**
     * Flag indicating if this transaction requires additional authorization
     * True for large amounts or high-risk transactions
     */
    @Builder.Default
    private Boolean requiresAuthorization = false;

    /**
     * Authorization code (if pre-authorized)
     * Required when requiresAuthorization = true
     */
    private String authorizationCode;

    /**
     * Flag indicating if overdraft is allowed
     * Default: false (reject if insufficient funds)
     */
    @Builder.Default
    private Boolean allowOverdraft = false;

    /**
     * Flag indicating if this is a reversal of a previous transaction
     */
    @Builder.Default
    private Boolean isReversal = false;

    /**
     * Original transaction ID (if this is a reversal)
     */
    private UUID originalTransactionId;

    /**
     * Flag to bypass daily/monthly limits (admin override)
     * Requires admin authorization
     */
    @Builder.Default
    private Boolean bypassLimits = false;

    /**
     * Admin user ID (if bypassLimits = true)
     * Required when bypassing limits
     */
    private UUID adminAuthorizerId;

    /**
     * Validates if this is an internal transfer
     */
    public boolean isInternalTransfer() {
        return "TRANSFER_OUT".equals(debitType) && destinationAccountId != null;
    }

    /**
     * Validates if this is an external payment
     */
    public boolean isExternalPayment() {
        return externalSystemId != null && externalSystemName != null;
    }

    /**
     * Validates if this is a reversal operation
     */
    public boolean isReversalOperation() {
        return Boolean.TRUE.equals(isReversal) && originalTransactionId != null;
    }

    /**
     * Checks if amount exceeds threshold requiring authorization
     * Threshold: $5,000 USD equivalent
     */
    public boolean exceedsAuthorizationThreshold() {
        if (amount == null) return false;

        BigDecimal threshold = new BigDecimal("5000.00");
        return amount.compareTo(threshold) > 0;
    }

    /**
     * Checks if this is a high-risk transaction
     * Based on amount, type, and flags
     */
    public boolean isHighRisk() {
        if (amount == null) return false;

        // High risk if:
        // - Amount > $10,000
        // - External payment without proper beneficiary info
        // - Bypass limits requested
        // - Missing security context (IP, device)

        BigDecimal highRiskThreshold = new BigDecimal("10000.00");
        boolean largeAmount = amount.compareTo(highRiskThreshold) > 0;
        boolean externalWithoutBeneficiary = isExternalPayment() &&
            (beneficiaryName == null || beneficiaryAccount == null);
        boolean lacksSecurity = ipAddress == null || deviceFingerprint == null;

        return largeAmount || externalWithoutBeneficiary ||
               Boolean.TRUE.equals(bypassLimits) || lacksSecurity;
    }

    /**
     * Validates if debit type is valid
     */
    private boolean isValidDebitType(String type) {
        return type != null && type.matches(
            "WITHDRAWAL|PAYMENT|FEE|TRANSFER_OUT|PURCHASE|BILL_PAYMENT|CHARGE|ADJUSTMENT|OTHER"
        );
    }

    /**
     * Comprehensive validation of the debit request
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

        if (debitType == null || !isValidDebitType(debitType)) {
            return "Invalid debit type: " + debitType;
        }

        if (purpose == null || purpose.trim().length() < 5) {
            return "Purpose description required (min 5 characters)";
        }

        if (description == null || description.trim().length() < 5) {
            return "Description required (min 5 characters)";
        }

        // Internal transfers must have destination account
        if ("TRANSFER_OUT".equals(debitType) && destinationAccountId == null) {
            return "Destination account ID required for internal transfers";
        }

        // Reversals must reference original transaction
        if (Boolean.TRUE.equals(isReversal) && originalTransactionId == null) {
            return "Original transaction ID required for reversals";
        }

        // Authorization required for large amounts
        if (Boolean.TRUE.equals(requiresAuthorization) &&
            (authorizationCode == null || authorizationCode.trim().isEmpty())) {
            return "Authorization code required";
        }

        // Bypass limits requires admin authorization
        if (Boolean.TRUE.equals(bypassLimits) && adminAuthorizerId == null) {
            return "Admin authorizer ID required when bypassing limits";
        }

        // Security validation for non-system transactions
        if (initiatedBy != null && ipAddress == null) {
            return "IP address required for user-initiated transactions";
        }

        return null; // Valid
    }

    /**
     * Validates business rules for the debit request
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

    /**
     * Gets sanitized beneficiary account for logging
     */
    public String getSanitizedBeneficiaryAccount() {
        if (beneficiaryAccount == null || beneficiaryAccount.length() <= 4) {
            return beneficiaryAccount;
        }

        // Show only last 4 digits
        int length = beneficiaryAccount.length();
        return "****" + beneficiaryAccount.substring(length - 4);
    }
}
