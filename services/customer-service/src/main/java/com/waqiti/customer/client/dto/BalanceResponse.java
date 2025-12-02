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
 * Response DTO for account balance from account-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    /**
     * Account identifier
     */
    @NotBlank(message = "Account ID is required")
    private String accountId;

    /**
     * Current balance
     */
    @NotNull(message = "Current balance is required")
    private BigDecimal currentBalance;

    /**
     * Available balance (after pending transactions)
     */
    @NotNull(message = "Available balance is required")
    private BigDecimal availableBalance;

    /**
     * Amount on hold
     */
    @NotNull(message = "Held amount is required")
    private BigDecimal heldAmount;

    /**
     * Pending credits
     */
    @NotNull(message = "Pending credits is required")
    private BigDecimal pendingCredits;

    /**
     * Pending debits
     */
    @NotNull(message = "Pending debits is required")
    private BigDecimal pendingDebits;

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
