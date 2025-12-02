package com.waqiti.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance Update Request DTO
 *
 * Used for admin/reconciliation balance updates
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateRequest {

    @NotNull(message = "Wallet ID is required")
    private UUID walletId;

    @NotNull(message = "New balance is required")
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Balance must have at most 15 digits and 4 decimal places")
    private BigDecimal newBalance;

    @NotNull(message = "Reference is required")
    @Size(max = 255, message = "Reference cannot exceed 255 characters")
    private String reference;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
