package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for cryptocurrency balance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoBalanceResponse {
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal stakedBalance;
    private BigDecimal totalBalance;
    private String currency;
    private BigDecimal usdValue;
    private boolean available;
    private String errorMessage;

    public static CryptoBalanceResponse unavailable(String message) {
        return CryptoBalanceResponse.builder()
                .available(false)
                .errorMessage(message)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .stakedBalance(BigDecimal.ZERO)
                .totalBalance(BigDecimal.ZERO)
                .build();
    }
}
