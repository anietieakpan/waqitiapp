package com.waqiti.account.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.waqiti.common.validation.ValidationUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for account creation with comprehensive validation
 * 
 * This class encapsulates all required and optional parameters for creating
 * a new financial account with proper validation, sanitization, and security measures.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Getter
@Builder
@Schema(description = "Request payload for creating a new account")
public class CreateAccountRequestDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "User ID who owns the account", required = true)
    @NotNull(message = "User ID is required")
    private final UUID userId;
    
    @Schema(description = "Account type", required = true, allowableValues = {"SAVINGS", "CHECKING", "INVESTMENT", "CREDIT"})
    @NotNull(message = "Account type is required")
    @Pattern(regexp = "^(SAVINGS|CHECKING|INVESTMENT|CREDIT)$", message = "Invalid account type")
    private final String accountType;
    
    @Schema(description = "Account currency ISO code", required = true, example = "USD")
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid currency code")
    private final String currency;
    
    @Schema(description = "Account name/alias", required = true, example = "Primary Savings")
    @NotBlank(message = "Account name is required")
    @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
    private final String accountName;
    
    @Schema(description = "Initial deposit amount", example = "1000.00")
    @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
    @DecimalMax(value = "1000000.00", message = "Initial deposit exceeds maximum allowed")
    @Digits(integer = 10, fraction = 2, message = "Invalid amount format")
    private final BigDecimal initialDeposit;
    
    @Schema(description = "Account category", example = "PERSONAL")
    @Pattern(regexp = "^(PERSONAL|BUSINESS|JOINT)$", message = "Invalid account category")
    private final String accountCategory;
    
    @Schema(description = "Parent account ID for sub-accounts")
    private final UUID parentAccountId;
    
    @Schema(description = "Enable overdraft protection", defaultValue = "false")
    private final Boolean overdraftProtection;
    
    @Schema(description = "Daily transaction limit")
    @DecimalMin(value = "0.00", message = "Transaction limit cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Invalid limit format")
    private final BigDecimal dailyTransactionLimit;
    
    @Schema(description = "Monthly transaction limit")
    @DecimalMin(value = "0.00", message = "Monthly limit cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Invalid limit format")
    private final BigDecimal monthlyTransactionLimit;
    
    @Schema(description = "Account tier level", allowableValues = {"BASIC", "STANDARD", "PREMIUM", "VIP"})
    @Pattern(regexp = "^(BASIC|STANDARD|PREMIUM|VIP)$", message = "Invalid tier level")
    private final String tierLevel;
    
    @Schema(description = "KYC verification level required", allowableValues = {"LEVEL_1", "LEVEL_2", "LEVEL_3"})
    @Pattern(regexp = "^(LEVEL_1|LEVEL_2|LEVEL_3)$", message = "Invalid KYC level")
    private final String kycLevel;
    
    @Schema(description = "Enable international transactions", defaultValue = "false")
    private final Boolean internationalEnabled;
    
    @Schema(description = "Enable virtual card issuance", defaultValue = "false")
    private final Boolean virtualCardEnabled;
    
    @Schema(description = "Notification preferences")
    private final NotificationPreferences notificationPreferences;
    
    @Schema(description = "Additional metadata")
    @Size(max = 20, message = "Too many metadata entries")
    private final Map<String, Object> metadata;
    
    /**
     * Notification preferences
     */
    @Getter
    @Builder
    public static class NotificationPreferences implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Email notifications enabled")
        private final Boolean emailEnabled;
        
        @Schema(description = "SMS notifications enabled")
        private final Boolean smsEnabled;
        
        @Schema(description = "Push notifications enabled")
        private final Boolean pushEnabled;
        
        @Schema(description = "Transaction alerts enabled")
        private final Boolean transactionAlerts;
        
        @Schema(description = "Low balance alerts enabled")
        private final Boolean lowBalanceAlerts;
        
        @Schema(description = "Low balance threshold")
        @DecimalMin(value = "0.00")
        private final BigDecimal lowBalanceThreshold;
        
        @JsonCreator
        public NotificationPreferences(
                @JsonProperty("emailEnabled") Boolean emailEnabled,
                @JsonProperty("smsEnabled") Boolean smsEnabled,
                @JsonProperty("pushEnabled") Boolean pushEnabled,
                @JsonProperty("transactionAlerts") Boolean transactionAlerts,
                @JsonProperty("lowBalanceAlerts") Boolean lowBalanceAlerts,
                @JsonProperty("lowBalanceThreshold") BigDecimal lowBalanceThreshold) {
            
            this.emailEnabled = emailEnabled != null ? emailEnabled : true;
            this.smsEnabled = smsEnabled != null ? smsEnabled : false;
            this.pushEnabled = pushEnabled != null ? pushEnabled : true;
            this.transactionAlerts = transactionAlerts != null ? transactionAlerts : true;
            this.lowBalanceAlerts = lowBalanceAlerts != null ? lowBalanceAlerts : true;
            this.lowBalanceThreshold = lowBalanceThreshold != null ? lowBalanceThreshold : new BigDecimal("100.00");
        }
    }
    
    /**
     * JSON Creator for proper deserialization with validation
     */
    @JsonCreator
    public CreateAccountRequestDTO(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("accountType") String accountType,
            @JsonProperty("currency") String currency,
            @JsonProperty("accountName") String accountName,
            @JsonProperty("initialDeposit") BigDecimal initialDeposit,
            @JsonProperty("accountCategory") String accountCategory,
            @JsonProperty("parentAccountId") UUID parentAccountId,
            @JsonProperty("overdraftProtection") Boolean overdraftProtection,
            @JsonProperty("dailyTransactionLimit") BigDecimal dailyTransactionLimit,
            @JsonProperty("monthlyTransactionLimit") BigDecimal monthlyTransactionLimit,
            @JsonProperty("tierLevel") String tierLevel,
            @JsonProperty("kycLevel") String kycLevel,
            @JsonProperty("internationalEnabled") Boolean internationalEnabled,
            @JsonProperty("virtualCardEnabled") Boolean virtualCardEnabled,
            @JsonProperty("notificationPreferences") NotificationPreferences notificationPreferences,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        
        // Required fields validation
        this.userId = ValidationUtils.requireNonNull(userId, "User ID is required");
        this.accountType = ValidationUtils.requireNonBlank(accountType, "Account type is required").toUpperCase();
        this.currency = ValidationUtils.requireNonBlank(currency, "Currency is required").toUpperCase();
        this.accountName = ValidationUtils.sanitizeInput(
            ValidationUtils.requireNonBlank(accountName, "Account name is required")
        );
        
        // Optional fields with defaults
        this.initialDeposit = initialDeposit != null ? 
            ValidationUtils.requireNonNegative(initialDeposit, "Initial deposit cannot be negative") : 
            BigDecimal.ZERO;
        
        this.accountCategory = accountCategory != null ? accountCategory.toUpperCase() : "PERSONAL";
        this.parentAccountId = parentAccountId;
        this.overdraftProtection = overdraftProtection != null ? overdraftProtection : false;
        
        // Limits validation
        this.dailyTransactionLimit = dailyTransactionLimit != null ?
            ValidationUtils.requireNonNegative(dailyTransactionLimit, "Daily limit cannot be negative") :
            new BigDecimal("5000.00");
        
        this.monthlyTransactionLimit = monthlyTransactionLimit != null ?
            ValidationUtils.requireNonNegative(monthlyTransactionLimit, "Monthly limit cannot be negative") :
            new BigDecimal("50000.00");
        
        // Feature flags
        this.tierLevel = tierLevel != null ? tierLevel.toUpperCase() : "STANDARD";
        this.kycLevel = kycLevel != null ? kycLevel.toUpperCase() : "LEVEL_1";
        this.internationalEnabled = internationalEnabled != null ? internationalEnabled : false;
        this.virtualCardEnabled = virtualCardEnabled != null ? virtualCardEnabled : false;
        
        // Preferences
        this.notificationPreferences = notificationPreferences != null ? 
            notificationPreferences : 
            NotificationPreferences.builder().build();
        
        // Metadata
        this.metadata = metadata;
        
        // Validate consistency
        validateRequest();
    }
    
    /**
     * Validates the entire request for consistency
     */
    private void validateRequest() {
        List<String> errors = new ArrayList<>();
        
        // Validate account type
        if (!isValidAccountType(accountType)) {
            errors.add("Invalid account type: " + accountType);
        }
        
        // Validate currency
        if (!isValidCurrency(currency)) {
            errors.add("Invalid currency code: " + currency);
        }
        
        // Validate limits
        if (dailyTransactionLimit != null && monthlyTransactionLimit != null) {
            if (dailyTransactionLimit.compareTo(monthlyTransactionLimit) > 0) {
                errors.add("Daily limit cannot exceed monthly limit");
            }
        }
        
        // Validate tier requirements
        if ("VIP".equals(tierLevel) && initialDeposit.compareTo(new BigDecimal("10000")) < 0) {
            errors.add("VIP tier requires minimum initial deposit of 10000");
        }
        
        // Validate KYC requirements
        if (internationalEnabled && !"LEVEL_3".equals(kycLevel)) {
            errors.add("International transactions require KYC Level 3");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }
    
    /**
     * Validates account type
     */
    private boolean isValidAccountType(String type) {
        return type != null && type.matches("^(SAVINGS|CHECKING|INVESTMENT|CREDIT)$");
    }
    
    /**
     * Validates currency code
     */
    private boolean isValidCurrency(String currency) {
        return currency != null && currency.matches("^[A-Z]{3}$");
    }
    
    /**
     * Checks if this is a business account
     */
    public boolean isBusinessAccount() {
        return "BUSINESS".equals(accountCategory);
    }
    
    /**
     * Checks if this is a premium account
     */
    public boolean isPremiumAccount() {
        return "PREMIUM".equals(tierLevel) || "VIP".equals(tierLevel);
    }
    
    /**
     * Gets effective daily limit based on tier
     */
    public BigDecimal getEffectiveDailyLimit() {
        if (dailyTransactionLimit != null) {
            return dailyTransactionLimit;
        }
        
        return switch (tierLevel) {
            case "VIP" -> new BigDecimal("100000.00");
            case "PREMIUM" -> new BigDecimal("50000.00");
            case "STANDARD" -> new BigDecimal("10000.00");
            default -> new BigDecimal("5000.00");
        };
    }
}