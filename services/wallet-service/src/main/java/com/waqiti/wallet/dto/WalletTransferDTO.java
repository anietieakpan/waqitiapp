package com.waqiti.wallet.dto;

import com.waqiti.common.validation.ValidationConstraints.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Wallet transfer DTO with comprehensive validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange(startDateField = "scheduledTime", endDateField = "expiryTime", maxDays = 30)
public class WalletTransferDTO {

    @NotBlank(message = "Source wallet ID is required")
    @Pattern(regexp = "^WAL-[A-Z0-9]{8}-[A-Z0-9]{4}$", message = "Invalid wallet ID format")
    @NoSQLInjection
    private String sourceWalletId;

    @NotBlank(message = "Destination wallet ID is required")
    @Pattern(regexp = "^WAL-[A-Z0-9]{8}-[A-Z0-9]{4}$", message = "Invalid wallet ID format")
    @NoSQLInjection
    private String destinationWalletId;

    @NotNull(message = "Transfer amount is required")
    @ValidAmount(min = "0.01", max = "100000.00", maxDecimalPlaces = 2, allowZero = false)
    @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be at least 0.01")
    @DecimalMax(value = "100000.00", inclusive = true, message = "Amount cannot exceed 100,000")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @ValidCurrency(allowCrypto = true)
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @Pattern(regexp = "^(INSTANT|STANDARD|SCHEDULED)$", message = "Invalid transfer type")
    @NotBlank(message = "Transfer type is required")
    private String transferType;

    @Pattern(regexp = "^(P2P|PAYMENT|REFUND|TOPUP|WITHDRAWAL|FEE|CASHBACK|REWARD)$",
             message = "Invalid transfer category")
    private String transferCategory;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @NoXSS
    @NoSQLInjection
    private String description;

    @Size(max = 200, message = "Reference cannot exceed 200 characters")
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Reference contains invalid characters")
    private String reference;

    @ValidTransactionReference(pattern = "^TXN-[0-9]{8}-[A-Z0-9]{8}$")
    private String transactionReference;

    @Future(message = "Scheduled time must be in the future")
    private LocalDateTime scheduledTime;

    @Future(message = "Expiry time must be in the future")
    private LocalDateTime expiryTime;

    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 10, message = "Priority cannot exceed 10")
    private Integer priority;

    @DecimalMin(value = "0", message = "Fee cannot be negative")
    @DecimalMax(value = "1000", message = "Fee cannot exceed 1000")
    private BigDecimal transactionFee;

    @Pattern(regexp = "^(SENDER|RECEIVER|SPLIT)$", message = "Invalid fee bearer")
    private String feeBearer;

    @ValidIPAddress(allowPrivate = false, allowLoopback = false)
    private String clientIpAddress;

    @Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Invalid device ID format")
    @Size(max = 200)
    private String deviceId;

    @ValidCoordinates
    private Double latitude;
    
    @ValidCoordinates
    private Double longitude;

    @Pattern(regexp = "^[a-zA-Z0-9]{32,64}$", message = "Invalid idempotency key")
    private String idempotencyKey;

    @NotNull(message = "2FA verification is required for transfers")
    private Boolean requiresTwoFactor;

    @Pattern(regexp = "^[0-9]{6}$", message = "2FA code must be 6 digits")
    private String twoFactorCode;

    @Pattern(regexp = "^(SMS|EMAIL|AUTHENTICATOR|BIOMETRIC)$", 
             message = "Invalid 2FA method")
    private String twoFactorMethod;

    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Invalid session token")
    private String sessionToken;

    @ValidEmail(checkDNS = false, allowDisposable = false)
    private String notificationEmail;

    @ValidPhoneNumber(requireInternationalFormat = true)
    private String notificationPhone;

    @Pattern(regexp = "^(NONE|EMAIL|SMS|PUSH|ALL)$", message = "Invalid notification preference")
    private String notificationPreference;

    @Size(max = 10)
    private List<@NotBlank @Size(max = 50) @NoXSS String> tags;

    @ValidJSON(maxDepth = 3, maxKeys = 20)
    private String metadata;

    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|CRITICAL)$", message = "Invalid risk level")
    private String riskLevel;

    @Size(max = 100)
    @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "Invalid merchant ID")
    private String merchantId;

    @Size(max = 100)
    @NoXSS
    private String merchantName;

    @ValidURL(requireSSL = true)
    private String merchantWebsite;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid MCC code")
    private String merchantCategoryCode;

    @ValidCountryCode
    private String merchantCountry;

    @Pattern(regexp = "^(PENDING|PROCESSING|COMPLETED|FAILED|CANCELLED|REVERSED)$",
             message = "Invalid status")
    private String expectedStatus;

    @Min(value = 0, message = "Retry count cannot be negative")
    @Max(value = 3, message = "Maximum 3 retries allowed")
    private Integer retryCount;

    @ValidBusinessNumber(type = BusinessNumberType.AUTO)
    private String businessTaxId;

    @Pattern(regexp = "^(PERSONAL|BUSINESS|GOVERNMENT|NON_PROFIT)$",
             message = "Invalid account type")
    private String accountType;

    @Size(max = 500)
    @NoXSS
    private String complianceNotes;

    @Pattern(regexp = "^[A-Z]{2}-[A-Z]{2,3}-[0-9]{6}$", 
             message = "Invalid compliance reference")
    private String complianceReference;

    @NotNull(message = "AML check required")
    private Boolean amlCheckPassed;

    @DecimalMin(value = "0", message = "Risk score cannot be negative")
    @DecimalMax(value = "100", message = "Risk score cannot exceed 100")
    private BigDecimal riskScore;

    @Size(max = 20)
    private List<@Pattern(regexp = "^[A-Z_]+$") String> requiredApprovals;

    @AssertTrue(message = "Source and destination wallets cannot be the same")
    private boolean isDifferentWallets() {
        return sourceWalletId == null || destinationWalletId == null ||
               !sourceWalletId.equals(destinationWalletId);
    }

    @AssertTrue(message = "Scheduled time required for SCHEDULED transfer type")
    private boolean isScheduledTimeValid() {
        if ("SCHEDULED".equals(transferType)) {
            return scheduledTime != null && scheduledTime.isAfter(LocalDateTime.now());
        }
        return true;
    }

    @AssertTrue(message = "High-value transfers require additional verification")
    private boolean isHighValueTransferValid() {
        if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            return requiresTwoFactor != null && requiresTwoFactor && 
                   twoFactorCode != null && amlCheckPassed != null && amlCheckPassed;
        }
        return true;
    }

    @AssertTrue(message = "Business tax ID required for BUSINESS account type")
    private boolean isBusinessAccountValid() {
        if ("BUSINESS".equals(accountType)) {
            return businessTaxId != null && !businessTaxId.isEmpty();
        }
        return true;
    }
}