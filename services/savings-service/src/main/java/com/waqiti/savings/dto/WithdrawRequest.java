package com.waqiti.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for withdrawing money from a savings account.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to withdraw money")
public class WithdrawRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Withdrawal amount must be at least 0.01")
    @DecimalMax(value = "100000.00", message = "Withdrawal amount exceeds maximum limit")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Withdrawal amount", example = "200.00", required = true)
    private BigDecimal amount;

    @Schema(description = "Destination account for withdrawal")
    private UUID destinationAccountId;

    @Schema(description = "Destination account type", example = "BANK_ACCOUNT")
    private String destinationType;

    @NotBlank(message = "Withdrawal reason is required")
    @Schema(description = "Reason for withdrawal", example = "Emergency expense", required = true)
    @Size(max = 200, message = "Reason too long")
    private String reason;

    @Schema(description = "Optional note for the withdrawal")
    @Size(max = 500, message = "Note too long")
    private String note;

    @Schema(description = "MFA verification code if required")
    @Pattern(regexp = "\\d{6}", message = "MFA code must be 6 digits")
    private String mfaCode;

    @Schema(description = "Idempotency key to prevent duplicate withdrawals")
    @Size(max = 100, message = "Idempotency key too long")
    private String idempotencyKey;
}
