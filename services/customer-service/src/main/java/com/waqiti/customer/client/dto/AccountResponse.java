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
 * Response DTO for account information from account-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    /**
     * Unique account identifier
     */
    @NotBlank(message = "Account ID is required")
    private String accountId;

    /**
     * Customer ID who owns the account
     */
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    /**
     * Account number
     */
    @NotBlank(message = "Account number is required")
    private String accountNumber;

    /**
     * Type of account (CHECKING, SAVINGS, etc.)
     */
    @NotBlank(message = "Account type is required")
    private String accountType;

    /**
     * Current account status (ACTIVE, FROZEN, CLOSED, etc.)
     */
    @NotBlank(message = "Account status is required")
    private String status;

    /**
     * Current account balance
     */
    @NotNull(message = "Balance is required")
    private BigDecimal balance;

    /**
     * Available balance (after holds)
     */
    @NotNull(message = "Available balance is required")
    private BigDecimal availableBalance;

    /**
     * Currency code (USD, EUR, etc.)
     */
    @NotBlank(message = "Currency is required")
    private String currency;

    /**
     * Account creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Whether account is frozen
     */
    private Boolean frozen;

    /**
     * Whether account is dormant
     */
    private Boolean dormant;

    /**
     * Branch or institution identifier
     */
    private String branchId;
}
