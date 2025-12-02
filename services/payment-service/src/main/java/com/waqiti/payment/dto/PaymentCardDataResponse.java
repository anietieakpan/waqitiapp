package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payment Card Data Response DTO
 *
 * Contains payment card information with PCI DSS compliant data masking.
 * Sensitive data fields are tokenized or masked according to security requirements.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Strict card data protection and masking
 * - GDPR: Customer payment data privacy
 * - SOX: Financial data access audit trail
 *
 * SECURITY NOTE:
 * This response NEVER contains full card numbers, CVV, or unmasked sensitive data.
 * All sensitive fields are either tokenized or properly masked.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCardDataResponse {

    /**
     * User ID associated with the card
     */
    @NotNull
    private UUID userId;

    /**
     * Payment method ID
     */
    @NotNull
    private UUID paymentMethodId;

    /**
     * Card token (replaces actual card number)
     */
    @NotNull
    private String cardToken;

    /**
     * Masked card number (e.g., "****-****-****-1234")
     */
    @NotNull
    private String maskedCardNumber;

    /**
     * First 6 digits of card (BIN/IIN)
     */
    private String cardBin;

    /**
     * Last 4 digits of card number
     */
    @NotNull
    private String last4Digits;

    /**
     * Card brand
     * Values: VISA, MASTERCARD, AMEX, DISCOVER, DINERS, JCB, UNIONPAY
     */
    @NotNull
    private String cardBrand;

    /**
     * Card type
     * Values: CREDIT, DEBIT, PREPAID, CHARGE
     */
    private String cardType;

    /**
     * Card subtype
     * Values: STANDARD, REWARDS, BUSINESS, CORPORATE, PLATINUM, etc.
     */
    private String cardSubtype;

    /**
     * Cardholder name
     */
    private String cardholderName;

    /**
     * Expiration month (01-12)
     */
    private String expirationMonth;

    /**
     * Expiration year (YYYY)
     */
    private String expirationYear;

    /**
     * Card expired flag
     */
    private boolean isExpired;

    /**
     * Days until expiration
     */
    private Integer daysUntilExpiration;

    /**
     * Issuing bank name
     */
    private String issuingBank;

    /**
     * Issuing country code (ISO 3166-1 alpha-2)
     */
    private String issuingCountry;

    /**
     * Billing address (masked)
     */
    private MaskedAddress billingAddress;

    /**
     * Card verification status
     * Values: VERIFIED, UNVERIFIED, FAILED, PENDING
     */
    private String verificationStatus;

    /**
     * Verification method used
     * Values: AVS, CVV, 3DS, MANUAL
     */
    private String verificationMethod;

    /**
     * AVS (Address Verification System) result
     */
    private String avsResult;

    /**
     * CVV verification result
     */
    private String cvvResult;

    /**
     * 3D Secure enrolled flag
     */
    private boolean threeDSecureEnrolled;

    /**
     * 3D Secure status
     * Values: ENROLLED, NOT_ENROLLED, UNAVAILABLE, AUTHENTICATED
     */
    private String threeDSecureStatus;

    /**
     * Card active flag
     */
    private boolean isActive;

    /**
     * Card enabled for transactions
     */
    private boolean isEnabled;

    /**
     * Default payment method flag
     */
    private boolean isDefault;

    /**
     * Card added date
     */
    private LocalDateTime addedAt;

    /**
     * Last used date
     */
    private LocalDateTime lastUsedAt;

    /**
     * Usage count
     */
    private int usageCount;

    /**
     * Card status
     * Values: ACTIVE, INACTIVE, BLOCKED, EXPIRED, LOST, STOLEN
     */
    private String status;

    /**
     * Blocked reason if applicable
     */
    private String blockedReason;

    /**
     * Fraud flags
     */
    private List<String> fraudFlags;

    /**
     * Risk score (0-100)
     */
    private int riskScore;

    /**
     * Risk level
     * Values: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String riskLevel;

    /**
     * Spending limits configured
     */
    private SpendingLimits spendingLimits;

    /**
     * Data fields accessed (for audit)
     */
    private List<String> accessedFields;

    /**
     * Access reason (from request)
     */
    private String accessReason;

    /**
     * Accessed by (user ID)
     */
    private UUID accessedBy;

    /**
     * Access timestamp
     */
    @NotNull
    private LocalDateTime accessedAt;

    /**
     * Compliance check performed
     */
    private boolean complianceCheckPerformed;

    /**
     * Audit logged flag
     */
    private boolean auditLogged;

    /**
     * Audit trail reference
     */
    private UUID auditTrailId;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Masked Address nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaskedAddress {
        private String addressLine1Masked;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    /**
     * Spending Limits nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendingLimits {
        private java.math.BigDecimal dailyLimit;
        private java.math.BigDecimal weeklyLimit;
        private java.math.BigDecimal monthlyLimit;
        private java.math.BigDecimal singleTransactionLimit;
        private String currency;
    }
}
