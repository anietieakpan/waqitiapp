package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for cryptocurrency address
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoAddressResponse {
    private UUID addressId;
    private UUID walletId;
    private String address;
    private String currency;
    private String addressType; // RECEIVING, CHANGE
    private String derivationPath;
    private boolean isUsed;
    private LocalDateTime createdAt;
    private boolean available;
    private String errorMessage;

    public static CryptoAddressResponse unavailable(String message) {
        return CryptoAddressResponse.builder()
                .available(false)
                .errorMessage(message)
                .build();
    }
}
