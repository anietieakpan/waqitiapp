package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for cryptocurrency wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoWalletResponse {
    private UUID walletId;
    private UUID userId;
    private String currency;
    private String walletType;
    private String status;
    private String address;
    private String publicKey;
    private CryptoBalanceResponse balance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean available;
    private String errorMessage;

    public static CryptoWalletResponse unavailable(String message) {
        return CryptoWalletResponse.builder()
                .available(false)
                .errorMessage(message)
                .build();
    }
}
