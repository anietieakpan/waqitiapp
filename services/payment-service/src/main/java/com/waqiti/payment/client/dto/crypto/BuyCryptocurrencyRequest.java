package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for buying cryptocurrency with fiat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyCryptocurrencyRequest {
    private UUID userId;
    private String currency;
    private BigDecimal amount;
    private String fiatCurrency;
    private BigDecimal fiatAmount;
    private String paymentMethodId;
}
