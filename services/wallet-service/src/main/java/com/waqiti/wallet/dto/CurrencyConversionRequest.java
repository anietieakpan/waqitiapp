package com.waqiti.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyConversionRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Source currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    private String sourceCurrency;

    @NotNull(message = "Target currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    private String targetCurrency;
}