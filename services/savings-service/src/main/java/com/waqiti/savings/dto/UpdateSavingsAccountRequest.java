package com.waqiti.savings.dto;

import com.waqiti.savings.domain.SavingsAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for updating an existing savings account.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update savings account settings")
public class UpdateSavingsAccountRequest {

    @Schema(description = "Account name/nickname", example = "My Updated Emergency Fund")
    @Size(max = 100, message = "Account name must not exceed 100 characters")
    private String accountName;

    @Schema(description = "Interest calculation type", example = "COMPOUND_MONTHLY")
    private SavingsAccount.InterestCalculationType interestCalculationType;

    @Schema(description = "Enable overdraft protection")
    private Boolean overdraftEnabled;

    @Schema(description = "Overdraft limit", example = "1000.00")
    @DecimalMin(value = "0.0", message = "Overdraft limit must be positive")
    private BigDecimal overdraftLimit;

    @Schema(description = "Enable auto-sweep")
    private Boolean autoSweepEnabled;

    @Schema(description = "Auto-sweep threshold", example = "15000.00")
    @DecimalMin(value = "0.0", message = "Auto-sweep threshold must be positive")
    private BigDecimal autoSweepThreshold;

    @Schema(description = "Target account for auto-sweep")
    private UUID autoSweepTargetAccountId;

    @Schema(description = "Daily deposit limit", example = "10000.00")
    @DecimalMin(value = "0.0", message = "Deposit limit must be positive")
    private BigDecimal dailyDepositLimit;

    @Schema(description = "Daily withdrawal limit", example = "5000.00")
    @DecimalMin(value = "0.0", message = "Withdrawal limit must be positive")
    private BigDecimal dailyWithdrawalLimit;

    @Schema(description = "Monthly withdrawal count limit", example = "6")
    private Integer monthlyWithdrawalCountLimit;

    @Schema(description = "Account settings")
    private Map<String, Object> settings;

    @Schema(description = "Account metadata")
    private Map<String, Object> metadata;
}
