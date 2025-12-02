package com.waqiti.wallet.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.waqiti.validation.annotation.ValidMoneyAmount;
import com.waqiti.validation.annotation.ValidCurrency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transfer request DTO
 * Used to initiate wallet-to-wallet transfers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    
    @NotNull(message = "Source wallet ID is required")
    private UUID fromWalletId;
    
    @NotNull(message = "Destination wallet ID is required")
    private UUID toWalletId;
    
    @NotNull(message = "Transfer amount is required")
    @ValidMoneyAmount(
        min = 0.01,
        max = 1000000.00,
        scale = 2,
        transactionType = ValidMoneyAmount.TransactionType.TRANSFER,
        checkFraudLimits = true,
        userTier = ValidMoneyAmount.UserTier.STANDARD,
        message = "Invalid transfer amount"
    )
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @ValidCurrency(
        supportedOnly = true,
        allowCrypto = false,
        transactionType = ValidCurrency.TransactionType.TRANSFER,
        checkActiveStatus = true,
        message = "Invalid or unsupported currency for transfer"
    )
    private String currency;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @Size(max = 100, message = "Reference cannot exceed 100 characters")
    private String reference;
    
    // Transfer type and options
    private String transferType; // INSTANT, STANDARD, SCHEDULED
    private boolean isUrgent;
    private boolean requiresConfirmation;
    private LocalDateTime scheduledDate;
    
    // Security and compliance
    private String purpose; // PERSONAL, BUSINESS, INVESTMENT, etc.
    private String sourceOfFunds;
    private boolean isHighValue; // Automatically determined based on amount
    private String complianceNotes;
    
    // Fee handling
    private String feeBearer; // SENDER, RECEIVER, SHARED
    private BigDecimal expectedFee;
    private boolean acceptFeeVariation;
    
    // Multi-currency handling
    private String targetCurrency;
    private BigDecimal exchangeRate;
    private boolean acceptExchangeRateVariation;
    
    // Notification preferences
    private boolean notifySender;
    private boolean notifyReceiver;
    private String notificationMethod; // EMAIL, SMS, PUSH, ALL
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String clientRequestId; // For idempotency
    private String userAgent;
    private String ipAddress;
    private String deviceId;
    
    // Recurring transfer options
    private boolean isRecurring;
    private String recurringFrequency; // DAILY, WEEKLY, MONTHLY, QUARTERLY
    private LocalDateTime recurringEndDate;
    private Integer maxRecurrences;
    
    // Authorization and verification
    private String twoFactorToken;
    private String biometricSignature;
    private String authorizationCode;
    private boolean requiresManagerApproval;
    
    // Business logic validation
    @AssertTrue(message = "Scheduled date must be in the future")
    public boolean isScheduledDateValid() {
        if (scheduledDate == null) {
            return true; // Not a scheduled transfer
        }
        return scheduledDate.isAfter(LocalDateTime.now());
    }
    
    @AssertTrue(message = "Source and destination wallets cannot be the same")
    public boolean areWalletsValid() {
        if (fromWalletId == null || toWalletId == null) {
            return true; // Let @NotNull handle null validation
        }
        return !fromWalletId.equals(toWalletId);
    }
    
    @AssertTrue(message = "Recurring end date must be specified for recurring transfers")
    public boolean isRecurringConfigValid() {
        if (!isRecurring) {
            return true;
        }
        return recurringEndDate != null || maxRecurrences != null;
    }
    
    // Helper methods
    public boolean isInstantTransfer() {
        return "INSTANT".equals(transferType);
    }
    
    public boolean isScheduledTransfer() {
        return scheduledDate != null && scheduledDate.isAfter(LocalDateTime.now());
    }
    
    public boolean requiresTwoFactorAuth() {
        // High value transfers require 2FA
        return amount != null && amount.compareTo(new BigDecimal("1000.00")) > 0;
    }
    
    public boolean isInternationalTransfer() {
        return targetCurrency != null && !currency.equals(targetCurrency);
    }
    
    public BigDecimal getTotalAmount() {
        BigDecimal total = amount != null ? amount : BigDecimal.ZERO;
        if ("SENDER".equals(feeBearer) && expectedFee != null) {
            total = total.add(expectedFee);
        }
        return total;
    }
}