package com.waqiti.payment.dto;

import com.waqiti.common.validation.ValidationConstraints.*;
import com.waqiti.security.encryption.annotation.Encrypted;
import com.waqiti.security.encryption.annotation.Tokenized;
import com.waqiti.security.encryption.PCIFieldEncryptionService;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment request DTO with comprehensive validation and PCI DSS compliance
 * 
 * CRITICAL SECURITY: This DTO implements PCI DSS v4.0 compliant field-level encryption
 * and tokenization for sensitive payment data:
 * 
 * PROTECTED FIELDS:
 * - cardNumber: Tokenized with format preservation for PCI scope reduction
 * - cvv: Encrypted with dedicated CVV encryption key (should not be stored long-term)
 * - recipientName: Encrypted to protect cardholder/recipient identity
 * - recipientAccount: Tokenized for secure account number handling
 * - recipientIBAN: Encrypted international banking details
 * 
 * COMPLIANCE FEATURES:
 * - Automatic encryption/decryption via JPA converters
 * - Comprehensive audit trails for all sensitive data operations
 * - Separate encryption keys for different data types
 * - Format-preserving tokenization maintains system compatibility
 * - Secure key management and automatic rotation
 * 
 * USAGE NOTES:
 * - CVV should only be used during payment processing and not stored long-term
 * - Tokens are format-preserving to maintain compatibility with existing systems
 * - All encrypted/tokenized operations are automatically audited
 * - Field validation occurs before encryption/tokenization
 * 
 * NON-COMPLIANCE PENALTIES:
 * - Unprotected cardholder data: $5,000 - $500,000 per month
 * - Data breach with unencrypted PAN/CVV: $50 - $90 per record
 * - Loss of payment processing certification
 * - Criminal liability for data protection violations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange(startDateField = "scheduledDate", endDateField = "expiryDate", maxDays = 365)
public class PaymentRequest {

    @NotBlank(message = "Payment ID is required")
    @Pattern(regexp = "^PAY-[A-Z0-9]{10,20}$", message = "Invalid payment ID format")
    @NoSQLInjection
    private String paymentId;

    @NotBlank(message = "User ID is required")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$", 
             message = "Invalid UUID format")
    private String userId;

    @NotNull(message = "Amount is required")
    @ValidAmount(min = "0.01", max = "1000000.00", maxDecimalPlaces = 2)
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @ValidCurrency(allowCrypto = true)
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @NotBlank(message = "Recipient account is required")
    @ValidAccountNumber(minLength = 8, maxLength = 20)
    @NoSQLInjection
    @Tokenized(formatPreserving = true, contextId = "recipient_account", expirationDays = 365)
    private String recipientAccount;

    @ValidIBAN
    @Encrypted(keyType = PCIFieldEncryptionService.KeyType.CARDHOLDER_NAME, auditContext = "recipient_iban")
    private String recipientIBAN;

    @ValidSwiftCode
    private String recipientSwiftCode;

    @NotBlank(message = "Recipient name is required")
    @Size(min = 2, max = 100, message = "Recipient name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-\\']+$", message = "Recipient name contains invalid characters")
    @NoXSS
    @NoSQLInjection
    @Encrypted(keyType = PCIFieldEncryptionService.KeyType.CARDHOLDER_NAME, auditContext = "recipient_name")
    private String recipientName;

    @Email(message = "Invalid email format")
    @ValidEmail(checkDNS = false, allowDisposable = false)
    private String recipientEmail;

    @ValidPhoneNumber(requireInternationalFormat = true)
    private String recipientPhone;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(BANK_TRANSFER|CARD|WALLET|CRYPTO)$", 
             message = "Invalid payment method")
    private String paymentMethod;

    @ValidCardNumber(acceptedTypes = {CardType.VISA, CardType.MASTERCARD, CardType.AMEX})
    @Tokenized(formatPreserving = true, contextId = "payment_card_number", expirationDays = 90)
    private String cardNumber;

    @ValidCVV
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    @Encrypted(keyType = PCIFieldEncryptionService.KeyType.CVV_ENCRYPTION, auditContext = "payment_cvv")
    private String cvv;

    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Card expiry must be in MM/YY format")
    private String cardExpiry;

    @ValidCryptoAddress(supportedTypes = {CryptoType.BITCOIN, CryptoType.ETHEREUM})
    private String cryptoAddress;

    @Size(max = 500, message = "Payment description cannot exceed 500 characters")
    @NoXSS
    @NoSQLInjection
    private String description;

    @ValidTransactionReference
    private String reference;

    @NotBlank(message = "Source country is required")
    @ValidCountryCode
    private String sourceCountry;

    @NotBlank(message = "Destination country is required")
    @ValidCountryCode
    private String destinationCountry;

    @ValidPostalCode
    private String billingZipCode;

    @Pattern(regexp = "^(GOODS|SERVICES|FAMILY_SUPPORT|EDUCATION|MEDICAL|OTHER)$",
             message = "Invalid purpose code")
    private String purposeCode;

    @Future(message = "Scheduled date must be in the future")
    private LocalDateTime scheduledDate;

    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiryDate;

    @ValidIPAddress(allowPrivate = false, allowLoopback = false)
    private String clientIpAddress;

    @Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Invalid device fingerprint format")
    @Size(max = 500)
    private String deviceFingerprint;

    @ValidURL(requireSSL = true)
    private String webhookUrl;

    @Pattern(regexp = "^[a-zA-Z0-9]{32,64}$", message = "Invalid idempotency key format")
    private String idempotencyKey;

    @Min(value = 1, message = "Retry count must be at least 1")
    @Max(value = 5, message = "Retry count cannot exceed 5")
    private Integer retryCount;

    @ValidBusinessNumber(type = BusinessNumberType.AUTO)
    private String merchantTaxId;

    @Size(max = 100)
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Invalid session ID format")
    private String sessionId;

    @NotNull(message = "2FA verification required for payments")
    private Boolean twoFactorVerified;

    @Pattern(regexp = "^[0-9]{6}$", message = "2FA code must be 6 digits")
    private String twoFactorCode;

    @ValidJSON(maxDepth = 5, maxKeys = 20)
    private String metadata;

    @Size(max = 50)
    private Map<@NotBlank @Size(max = 50) String, 
                @NotBlank @Size(max = 200) @NoXSS String> customFields;

    @AssertTrue(message = "Card details required for CARD payment method")
    private boolean isCardDetailsValid() {
        if ("CARD".equals(paymentMethod)) {
            return cardNumber != null && cvv != null && cardExpiry != null;
        }
        return true;
    }

    @AssertTrue(message = "Crypto address required for CRYPTO payment method")
    private boolean isCryptoDetailsValid() {
        if ("CRYPTO".equals(paymentMethod)) {
            return cryptoAddress != null;
        }
        return true;
    }

    @AssertTrue(message = "IBAN or SWIFT code required for international transfers")
    private boolean isInternationalTransferValid() {
        if (!sourceCountry.equals(destinationCountry)) {
            return recipientIBAN != null || recipientSwiftCode != null;
        }
        return true;
    }
}