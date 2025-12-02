package com.waqiti.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for closing an account
 *
 * Account closure is a critical operation that requires:
 * - Zero balance verification (handled by service layer)
 * - Reason documentation for audit trail
 * - User confirmation
 * - Regulatory compliance checks
 * - Pending transaction verification
 *
 * Business Rules:
 * - Account balance must be zero
 * - No pending transactions
 * - No active holds or reserves
 * - Cooling-off period may apply for certain account types
 * - Closure reason required for audit/compliance
 * - Cannot close if account has dependent sub-accounts
 * - Soft delete (archived for regulatory retention period)
 *
 * @author Production Readiness Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseAccountRequest {

    /**
     * ID of the account to close
     * Required for idempotency and audit trail
     */
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    /**
     * Reason for account closure
     * Required for regulatory compliance and audit trail
     *
     * Common reasons:
     * - USER_REQUEST: Customer-initiated closure
     * - FRAUD_DETECTED: Fraudulent activity detected
     * - COMPLIANCE_VIOLATION: KYC/AML violation
     * - INACTIVITY: Long-term inactivity (per policy)
     * - DUPLICATE_ACCOUNT: User has duplicate accounts
     * - REGULATORY_REQUIREMENT: Required by regulator
     * - BUSINESS_CLOSURE: For business accounts
     */
    @NotBlank(message = "Closure reason is required")
    @Size(min = 10, max = 500, message = "Closure reason must be between 10 and 500 characters")
    private String closureReason;

    /**
     * Closure type/category for classification
     * Values: USER_REQUEST, FRAUD, COMPLIANCE, INACTIVITY, DUPLICATE, OTHER
     */
    @NotBlank(message = "Closure type is required")
    private String closureType;

    /**
     * User confirmation flag
     * Must be true for user-initiated closures
     * Prevents accidental closures
     */
    @Builder.Default
    private Boolean userConfirmed = false;

    /**
     * Transfer remaining funds to this account (if balance > 0)
     * Optional: If provided and balance exists, transfer before closure
     */
    private UUID transferToAccountId;

    /**
     * Admin override flag (for forced closures)
     * Only applicable for admin/system closures
     * Bypasses certain validations (with proper authorization)
     */
    @Builder.Default
    private Boolean adminOverride = false;

    /**
     * Admin user ID performing the closure (if admin-initiated)
     * Required when adminOverride = true
     */
    private UUID adminUserId;

    /**
     * Additional notes for internal use
     * Optional: Compliance officer notes, investigation references, etc.
     */
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String internalNotes;

    /**
     * Validates if this is a user-initiated closure request
     */
    public boolean isUserInitiated() {
        return "USER_REQUEST".equals(closureType) && Boolean.TRUE.equals(userConfirmed);
    }

    /**
     * Validates if this is an admin-forced closure
     */
    public boolean isAdminForced() {
        return Boolean.TRUE.equals(adminOverride) && adminUserId != null;
    }

    /**
     * Validates if fund transfer is requested
     */
    public boolean requiresFundTransfer() {
        return transferToAccountId != null;
    }

    /**
     * Validates business rules for closure request
     *
     * @return true if request is valid, false otherwise
     */
    public boolean isValid() {
        // User-initiated closures must have confirmation
        if ("USER_REQUEST".equals(closureType) && !Boolean.TRUE.equals(userConfirmed)) {
            return false;
        }

        // Admin overrides must have admin user ID
        if (Boolean.TRUE.equals(adminOverride) && adminUserId == null) {
            return false;
        }

        // Closure type must be valid
        if (closureType != null && !isValidClosureType(closureType)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the closure type is valid
     */
    private boolean isValidClosureType(String type) {
        return type.matches("USER_REQUEST|FRAUD|COMPLIANCE|INACTIVITY|DUPLICATE|REGULATORY|OTHER");
    }

    /**
     * Validates if account can be safely closed
     * Note: Balance and transaction checks happen in service layer
     *
     * @return validation error message, or null if valid
     */
    public String getValidationError() {
        if (accountId == null) {
            return "Account ID is required";
        }

        if (closureReason == null || closureReason.trim().length() < 10) {
            return "Closure reason must be at least 10 characters";
        }

        if (closureType == null || closureType.trim().isEmpty()) {
            return "Closure type is required";
        }

        if (!isValidClosureType(closureType)) {
            return "Invalid closure type: " + closureType;
        }

        if ("USER_REQUEST".equals(closureType) && !Boolean.TRUE.equals(userConfirmed)) {
            return "User confirmation required for user-initiated closures";
        }

        if (Boolean.TRUE.equals(adminOverride) && adminUserId == null) {
            return "Admin user ID required when using admin override";
        }

        return null; // Valid
    }
}
