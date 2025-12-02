package com.waqiti.wallet.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Refund Request DTO
 *
 * Contains details for initiating a refund for a wallet closure
 * or other refund scenarios.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Wallet ID is required")
    private UUID walletId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    private String currency;

    @NotBlank(message = "Refund reason is required")
    @Size(max = 500, message = "Refund reason must not exceed 500 characters")
    private String refundReason;

    @NotBlank(message = "Refund method is required")
    private String refundMethod; // BANK_TRANSFER, ORIGINAL_PAYMENT_METHOD, WALLET_CREDIT, CHECK

    private String correlationId;

    // Optional fields for specific refund methods
    private String bankAccountNumber;
    private String bankRoutingNumber;
    private String bankAccountHolderName;
    private UUID destinationWalletId; // For WALLET_CREDIT
    private String checkMailingAddress; // For CHECK
}
