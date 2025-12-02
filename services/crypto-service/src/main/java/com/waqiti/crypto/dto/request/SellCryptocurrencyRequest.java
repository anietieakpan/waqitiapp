/**
 * Sell Cryptocurrency Request DTO
 * Request to sell cryptocurrency for fiat
 */
package com.waqiti.crypto.dto.request;

import com.waqiti.crypto.entity.CryptoCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellCryptocurrencyRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotNull(message = "Currency is required")
    private CryptoCurrency currency;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00000001", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Bank account ID is required")
    private String bankAccountId;
    
    private boolean acceptPriceSlippage;
    
    private BigDecimal maxSlippagePercent;
    
    @Pattern(regexp = "^[0-9]{6}$", message = "Transaction PIN must be 6 digits")
    private String transactionPin;
}