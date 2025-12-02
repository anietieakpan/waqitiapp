package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.waqiti.common.validation.PaymentValidation;
import com.waqiti.common.validation.PaymentValidation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced Payment Request DTO with comprehensive validation
 * Includes all security checks and business rule validations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidPaymentRequest
@GroupSequence({EnhancedPaymentRequest.BasicValidation.class, EnhancedPaymentRequest.class})
@Schema(description = "Enhanced payment request with comprehensive validation")
public class EnhancedPaymentRequest {

    // Validation groups
    public interface BasicValidation {}
    public interface AdvancedValidation {}

    @NotNull(message = "Sender ID is required", groups = BasicValidation.class)
    @Schema(description = "Unique identifier of the payment sender", required = true)
    private UUID senderId;

    @NotNull(message = "Recipient identifier is required", groups = BasicValidation.class)
    @Schema(description = "Recipient identifier (can be user ID, email, phone, etc.)")
    private String recipientIdentifier;

    @NotNull(message = "Recipient type is required", groups = BasicValidation.class)
    @Pattern(regexp = "^(USER|MERCHANT|BANK_ACCOUNT|CARD|EMAIL|PHONE|CRYPTO_WALLET)$", 
             message = "Invalid recipient type", groups = BasicValidation.class)
    @Schema(description = "Type of recipient", allowableValues = {"USER", "MERCHANT", "BANK_ACCOUNT", "CARD", "EMAIL", "PHONE", "CRYPTO_WALLET"})
    private String recipientType;

    @NotNull(message = "Amount is required", groups = BasicValidation.class)
    @ValidPaymentAmount(min = 0.01, max = 1000000.00, paymentType = PaymentType.STANDARD)
    @Schema(description = "Payment amount", example = "100.00", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required", groups = BasicValidation.class)
    @ValidCurrency(supported = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF"})
    @Schema(description = "ISO 4217 currency code", example = "USD", required = true)
    private String currency;

    @ValidPaymentDescription(maxLength = 500, checkProfanity = true, checkSuspiciousPatterns = true)
    @Schema(description = "Payment description or memo", maxLength = 500)
    private String description;

    @NotNull(message = "Payment method is required", groups = BasicValidation.class)
    @Pattern(regexp = "^(WALLET|BANK_ACCOUNT|DEBIT_CARD|CREDIT_CARD|CRYPTO|WIRE)$", 
             message = "Invalid payment method", groups = BasicValidation.class)
    @Schema(description = "Payment method", required = true)
    private String paymentMethod;

    @Schema(description = "Specific payment method ID (e.g., saved card ID)")
    private String paymentMethodId;

    // Bank account details (conditional validation)
    @ValidIBAN
    @Schema(description = "IBAN for international transfers")
    private String iban;

    @ValidSwiftCode
    @Schema(description = "SWIFT/BIC code for international transfers")
    private String swiftCode;

    @ValidRoutingNumber
    @Schema(description = "Routing number for US bank transfers")
    private String routingNumber;

    @ValidAccountNumber(minLength = 4, maxLength = 17)
    @Schema(description = "Bank account number")
    private String accountNumber;

    // Card details (for card payments)
    @ValidCardNumber(acceptedTypes = {CardType.VISA, CardType.MASTERCARD, CardType.AMEX})
    @Schema(description = "Card number (will be tokenized)")
    private String cardNumber;

    @ValidCVV
    @Schema(description = "Card CVV/CVC code")
    private String cvv;

    @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Invalid expiry month")
    @Schema(description = "Card expiry month (MM)", example = "12")
    private String expiryMonth;

    @Pattern(regexp = "^20[2-9][0-9]$", message = "Invalid expiry year")
    @Schema(description = "Card expiry year (YYYY)", example = "2025")
    private String expiryYear;

    // Crypto payment details
    @ValidCryptoAddress(cryptoType = CryptoType.BITCOIN)
    @Schema(description = "Cryptocurrency wallet address")
    private String cryptoAddress;

    @Schema(description = "Cryptocurrency network (e.g., mainnet, testnet)")
    private String cryptoNetwork;

    // Scheduled payment details
    @ValidScheduledDate(minDaysInFuture = 0, maxDaysInFuture = 365)
    @Schema(description = "Scheduled payment date/time")
    private LocalDateTime scheduledDate;

    @ValidRecurringFrequency
    @Schema(description = "Recurring payment frequency")
    private String recurringFrequency;

    @Min(value = 1, message = "Number of payments must be at least 1")
    @Max(value = 999, message = "Number of payments cannot exceed 999")
    @Schema(description = "Total number of recurring payments")
    private Integer recurringCount;

    // Security and compliance
    @NotNull(message = "Device ID is required for security", groups = BasicValidation.class)
    @Schema(description = "Device identifier for fraud prevention", required = true)
    private String deviceId;

    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$", message = "Invalid IP address format")
    @Schema(description = "Client IP address")
    private String clientIp;

    @Schema(description = "User's location (latitude,longitude)")
    private String userLocation;

    @AssertTrue(message = "User consent is required", groups = BasicValidation.class)
    @Schema(description = "User consent for the transaction", required = true)
    private Boolean userConsent;

    @ValidPaymentReference(pattern = "^[A-Za-z0-9\\-\\/\\s]{1,35}$")
    @Schema(description = "Payment reference number")
    private String paymentReference;

    // Additional validation fields
    @Schema(description = "Purpose of payment for compliance")
    private String purposeOfPayment;

    @Schema(description = "Source of funds for high-value payments")
    private String sourceOfFunds;

    @Schema(description = "Beneficiary name for verification")
    private String beneficiaryName;

    @Schema(description = "Beneficiary address for international transfers")
    private String beneficiaryAddress;

    // Metadata
    @Schema(description = "Additional metadata as JSON")
    private JsonNode metadata;

    @Schema(description = "Custom tags for categorization")
    private Map<String, String> tags;

    // Fee configuration
    @Schema(description = "Who pays the fee: SENDER, RECIPIENT, SHARED")
    @Pattern(regexp = "^(SENDER|RECIPIENT|SHARED)$", message = "Invalid fee payer")
    private String feePayer = "SENDER";

    @Schema(description = "Use express processing")
    private Boolean expressProcessing = false;

    // Anti-fraud fields
    @Schema(description = "Session ID for tracking")
    private String sessionId;

    @Schema(description = "User agent string")
    private String userAgent;

    @Schema(description = "Risk score from fraud detection")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double riskScore;

    // Validation methods for cross-field validation
    @AssertTrue(message = "Card details required for card payments")
    private boolean isCardDetailsValid() {
        if ("CREDIT_CARD".equals(paymentMethod) || "DEBIT_CARD".equals(paymentMethod)) {
            return cardNumber != null && cvv != null && expiryMonth != null && expiryYear != null;
        }
        return true;
    }

    @AssertTrue(message = "Bank details required for bank transfers")
    private boolean isBankDetailsValid() {
        if ("BANK_ACCOUNT".equals(paymentMethod) || "WIRE".equals(paymentMethod)) {
            // For international transfers
            if (iban != null) {
                return swiftCode != null;
            }
            // For US transfers
            if (routingNumber != null) {
                return accountNumber != null;
            }
            return false;
        }
        return true;
    }

    @AssertTrue(message = "Crypto details required for crypto payments")
    private boolean isCryptoDetailsValid() {
        if ("CRYPTO".equals(paymentMethod)) {
            return cryptoAddress != null && cryptoNetwork != null;
        }
        return true;
    }

    @AssertTrue(message = "Beneficiary details required for international transfers")
    private boolean isBeneficiaryDetailsValid() {
        if (iban != null || "WIRE".equals(paymentMethod)) {
            return beneficiaryName != null && beneficiaryAddress != null;
        }
        return true;
    }

    @AssertTrue(message = "Source of funds required for high-value payments")
    private boolean isSourceOfFundsValid() {
        if (amount != null && amount.compareTo(new BigDecimal("10000")) >= 0) {
            return sourceOfFunds != null && !sourceOfFunds.trim().isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "Recurring details required for recurring payments")
    private boolean isRecurringDetailsValid() {
        if (recurringFrequency != null) {
            return recurringCount != null && recurringCount > 0;
        }
        return true;
    }
}