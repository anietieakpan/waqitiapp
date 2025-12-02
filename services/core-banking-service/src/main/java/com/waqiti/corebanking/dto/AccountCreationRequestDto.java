package com.waqiti.corebanking.dto;

import com.waqiti.corebanking.validation.CurrencyCode;
import com.waqiti.corebanking.validation.ValidUUID;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new banking account")
public class AccountCreationRequestDto {

    @NotBlank(message = "User ID is required")
    @ValidUUID(allowPrefix = true)
    @Schema(description = "ID of the user who will own this account", example = "user-550e8400-e29b-41d4-a716-446655440000", required = true)
    private String userId;

    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "USER_WALLET|USER_SAVINGS|USER_CREDIT|BUSINESS_OPERATING|SAVINGS|CHECKING|MONEY_MARKET",
             message = "Invalid account type")
    @Schema(description = "Type of account to create",
            example = "USER_WALLET",
            allowableValues = {"USER_WALLET", "USER_SAVINGS", "USER_CREDIT", "BUSINESS_OPERATING"},
            required = true)
    private String accountType;

    @NotBlank(message = "Currency is required")
    @CurrencyCode
    @Schema(description = "ISO 4217 currency code for the account", example = "USD", required = true)
    private String currency;

    @Schema(description = "Initial balance for the account", example = "0.00")
    @DecimalMin(value = "0.0", message = "Initial balance cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid balance format")
    private BigDecimal initialBalance;

    @Schema(description = "Credit limit for credit accounts", example = "1000.00")
    @DecimalMin(value = "0.0", message = "Credit limit cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid credit limit format")
    private BigDecimal creditLimit;

    @Schema(description = "Daily transaction limit", example = "5000.00")
    @DecimalMin(value = "0.0", message = "Daily limit cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid daily limit format")
    private BigDecimal dailyLimit;

    @Schema(description = "Monthly transaction limit", example = "50000.00")
    @DecimalMin(value = "0.0", message = "Monthly limit cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid monthly limit format")
    private BigDecimal monthlyLimit;

    @Schema(description = "Account description or purpose", example = "Primary checking account")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Schema(description = "Parent account ID for hierarchical accounts", example = "parent-account-123")
    private String parentAccountId;

    @Schema(description = "Additional metadata for the account")
    private Map<String, Object> metadata;

    @Schema(description = "Whether to auto-activate the account", example = "true")
    private Boolean autoActivate = true;

    @Schema(description = "Compliance level required", 
            example = "STANDARD",
            allowableValues = {"BASIC", "STANDARD", "ENHANCED", "PREMIUM"})
    private String complianceLevel = "STANDARD";
}