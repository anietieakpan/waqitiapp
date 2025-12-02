package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to create a wallet in the external system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {
    private UUID userId;
    private String walletType; // "INTERNAL" (legacy field for backwards compatibility)
    private String accountType; // "SAVINGS", "CHECKING", etc.
    private String currency;
}

