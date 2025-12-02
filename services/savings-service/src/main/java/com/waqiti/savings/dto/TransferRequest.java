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
 * Request DTO for transferring money between savings accounts.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to transfer money between accounts")
public class TransferRequest {

    @NotNull(message = "Destination account is required")
    @Schema(description = "Destination account ID", required = true)
    private UUID toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
    @DecimalMax(value = "100000.00", message = "Transfer amount exceeds maximum limit")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Transfer amount", example = "1000.00", required = true)
    private BigDecimal amount;

    @Schema(description = "Transfer description", example = "Moving to high-yield account")
    @Size(max = 200, message = "Description too long")
    private String description;

    @Schema(description = "Optional note for the transfer")
    @Size(max = 500, message = "Note too long")
    private String note;

    @Schema(description = "MFA verification code if required")
    @Pattern(regexp = "\\d{6}", message = "MFA code must be 6 digits")
    private String mfaCode;

    @Schema(description = "Idempotency key to prevent duplicate transfers")
    @Size(max = 100, message = "Idempotency key too long")
    private String idempotencyKey;
}
