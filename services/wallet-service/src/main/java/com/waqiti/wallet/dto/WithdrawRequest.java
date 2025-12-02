package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for atomic withdrawal operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest {
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;
    
    private String description;
    
    private String idempotencyKey;
    
    // Optional fields for external withdrawal
    private String destinationAccount;
    private String destinationBank;
    private String withdrawalMethod; // BANK_TRANSFER, CARD, etc.
}