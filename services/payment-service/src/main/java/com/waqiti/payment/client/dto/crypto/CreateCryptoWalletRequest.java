package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a cryptocurrency wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCryptoWalletRequest {
    private UUID userId;
    private String currency; // BTC, ETH, USDC, etc.
    private String walletType; // HD, MULTISIG, etc.
}
