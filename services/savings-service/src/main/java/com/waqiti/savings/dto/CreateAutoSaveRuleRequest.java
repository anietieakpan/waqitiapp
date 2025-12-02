package com.waqiti.savings.dto;

import com.waqiti.savings.domain.AutoSaveRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating an auto-save rule.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create an auto-save rule")
public class CreateAutoSaveRuleRequest {

    @NotNull(message = "Savings account ID is required")
    @Schema(description = "Savings account ID", required = true)
    private UUID savingsAccountId;

    @Schema(description = "Savings goal ID (optional, for goal-specific rules)")
    private UUID goalId;

    @NotBlank(message = "Rule name is required")
    @Size(min = 3, max = 100, message = "Rule name must be between 3 and 100 characters")
    @Schema(description = "Rule name", example = "Monthly Salary Save", required = true)
    private String ruleName;

    @NotNull(message = "Rule type is required")
    @Schema(description = "Type of auto-save rule", example = "PERCENTAGE_OF_INCOME", required = true)
    private AutoSaveRule.RuleType ruleType;

    @Schema(description = "Fixed amount for FIXED_AMOUNT rule type", example = "100.00")
    @DecimalMin(value = "1.00", message = "Amount must be at least $1")
    @DecimalMax(value = "10000.00", message = "Amount exceeds maximum")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @Schema(description = "Percentage for PERCENTAGE_OF_INCOME type", example = "10.00")
    @DecimalMin(value = "1.0", message = "Percentage must be at least 1%")
    @DecimalMax(value = "50.0", message = "Percentage cannot exceed 50%")
    private BigDecimal percentage;

    @Schema(description = "Round up to nearest amount for ROUND_UP type", example = "1.00")
    @DecimalMin(value = "1.00", message = "Round up amount must be at least $1")
    private BigDecimal roundUpTo;

    @Schema(description = "Maximum amount per execution", example = "500.00")
    @DecimalMin(value = "1.00", message = "Max amount must be positive")
    private BigDecimal maxAmount;

    @Schema(description = "Minimum amount per execution", example = "5.00")
    @DecimalMin(value = "0.01", message = "Min amount must be positive")
    private BigDecimal minAmount;

    @NotNull(message = "Frequency is required")
    @Schema(description = "Execution frequency", example = "MONTHLY", required = true)
    private AutoSaveRule.Frequency frequency;

    @Schema(description = "Day of week for weekly rules", example = "FRIDAY")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Day of month for monthly rules (1-31)", example = "15")
    @Min(value = 1, message = "Day of month must be between 1 and 31")
    @Max(value = 31, message = "Day of month must be between 1 and 31")
    private Integer dayOfMonth;

    @Schema(description = "Trigger type", example = "INCOME_BASED")
    private AutoSaveRule.TriggerType triggerType;

    @Schema(description = "Trigger conditions (JSON)")
    private Map<String, Object> triggerConditions;

    @Schema(description = "Payment method", example = "BANK_ACCOUNT")
    private AutoSaveRule.PaymentMethod paymentMethod = AutoSaveRule.PaymentMethod.BANK_ACCOUNT;

    @Schema(description = "Funding source ID")
    private UUID fundingSourceId;

    @Schema(description = "Rule priority (1-10, higher = more important)", example = "5")
    @Min(value = 1, message = "Priority must be between 1 and 10")
    @Max(value = 10, message = "Priority must be between 1 and 10")
    private Integer priority = 5;

    @Schema(description = "Rule start date")
    @Future(message = "Start date must be in the future")
    private LocalDateTime startDate;

    @Schema(description = "Rule end date")
    @Future(message = "End date must be in the future")
    private LocalDateTime endDate;

    @Schema(description = "Enable notifications on execution")
    private Boolean notifyOnExecution = true;

    @Schema(description = "Enable notifications on failure")
    private Boolean notifyOnFailure = true;
}
