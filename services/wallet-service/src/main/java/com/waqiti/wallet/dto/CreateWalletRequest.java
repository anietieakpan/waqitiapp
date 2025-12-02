package com.waqiti.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request to create a new wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {
    @NotNull
    private UUID userId;

    @NotBlank
    private String walletType; // "INTERNAL" (legacy field for backwards compatibility)

    @NotBlank
    private String accountType; // "SAVINGS", "CHECKING", etc.

    @NotBlank
    private String currency;
}