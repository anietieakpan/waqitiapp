package com.waqiti.savings.dto;

import com.waqiti.savings.domain.SavingsAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating a new savings account.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new savings account")
public class CreateSavingsAccountRequest {

    @Schema(description = "User ID (will be overridden with authenticated user)")
    private UUID userId;

    @Schema(description = "Account name/nickname", example = "My Emergency Fund")
    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name must not exceed 100 characters")
    private String accountName;

    @NotNull(message = "Account type is required")
    @Schema(description = "Type of savings account", example = "HIGH_YIELD_SAVINGS")
    private SavingsAccount.AccountType accountType;

    @Schema(description = "Initial deposit amount", example = "500.00")
    @DecimalMin(value = "0.0", message = "Initial deposit must be positive")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    private BigDecimal initialDeposit;

    @Schema(description = "Account currency", example = "USD")
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be uppercase ISO code")
    private String currency = "USD";

    @Schema(description = "Interest calculation type", example = "COMPOUND_DAILY")
    private SavingsAccount.InterestCalculationType interestCalculationType;

    @Schema(description = "Enable overdraft protection")
    private Boolean overdraftEnabled = false;

    @Schema(description = "Overdraft limit if enabled", example = "500.00")
    @DecimalMin(value = "0.0", message = "Overdraft limit must be positive")
    private BigDecimal overdraftLimit;

    @Schema(description = "Enable auto-sweep")
    private Boolean autoSweepEnabled = false;

    @Schema(description = "Auto-sweep threshold", example = "10000.00")
    @DecimalMin(value = "0.0", message = "Auto-sweep threshold must be positive")
    private BigDecimal autoSweepThreshold;

    @Schema(description = "Target account for auto-sweep")
    private UUID autoSweepTargetAccountId;

    @Schema(description = "Account settings")
    private Map<String, Object> settings;

    @Schema(description = "Account metadata")
    private Map<String, Object> metadata;
}
