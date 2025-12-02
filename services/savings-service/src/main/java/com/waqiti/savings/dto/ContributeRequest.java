package com.waqiti.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for contributing to a savings goal.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to contribute to a savings goal")
public class ContributeRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Contribution must be at least 0.01")
    @DecimalMax(value = "100000.00", message = "Contribution exceeds maximum limit")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Contribution amount", example = "250.00", required = true)
    private BigDecimal amount;

    @Schema(description = "Contribution source", example = "SALARY")
    @Size(max = 50, message = "Source description too long")
    private String source;

    @Schema(description = "Optional note for this contribution")
    @Size(max = 500, message = "Note too long")
    private String note;

    @Schema(description = "Is this an auto-save contribution")
    private Boolean isAutoSave = false;

    @Schema(description = "Auto-save rule ID if applicable")
    private java.util.UUID autoSaveRuleId;

    @Schema(description = "Payment method", example = "BANK_ACCOUNT")
    private String paymentMethod;

    @Schema(description = "Idempotency key to prevent duplicate contributions")
    @Size(max = 100, message = "Idempotency key too long")
    private String idempotencyKey;
}
