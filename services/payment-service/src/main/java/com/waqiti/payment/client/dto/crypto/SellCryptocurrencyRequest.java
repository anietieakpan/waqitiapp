package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for selling cryptocurrency to fiat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellCryptocurrencyRequest {
    private UUID userId;
    private String currency;
    private BigDecimal amount;
    private String fiatCurrency;
    private String bankAccountId;
}
