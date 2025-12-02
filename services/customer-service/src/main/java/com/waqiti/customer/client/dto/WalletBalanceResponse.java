package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for wallet balance from wallet-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceResponse {

    /**
     * Wallet identifier
     */
    @NotBlank(message = "Wallet ID is required")
    private String walletId;

    /**
     * Current balance
     */
    @NotNull(message = "Current balance is required")
    private BigDecimal currentBalance;

    /**
     * Available balance
     */
    @NotNull(message = "Available balance is required")
    private BigDecimal availableBalance;

    /**
     * Pending amount
     */
    @NotNull(message = "Pending amount is required")
    private BigDecimal pendingAmount;

    /**
     * Currency code
     */
    @NotBlank(message = "Currency is required")
    private String currency;

    /**
     * Balance as of timestamp
     */
    @NotNull(message = "As of date is required")
    private LocalDateTime asOfDate;
}
