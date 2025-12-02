/**
 * Buy Cryptocurrency Request DTO
 * Request to purchase cryptocurrency with fiat - Enhanced with comprehensive validation
 */
package com.waqiti.crypto.dto.request;

import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.validation.annotation.ValidMoneyAmount;
import com.waqiti.validation.annotation.ValidCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
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
public class BuyCryptocurrencyRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotNull(message = "Currency is required")
    private CryptoCurrency currency;
    
    @NotNull(message = "Fiat amount is required")
    @ValidMoneyAmount(
        min = 1.00,
        max = 100000.00,
        scale = 2,
        transactionType = ValidMoneyAmount.TransactionType.CRYPTO_BUY,
        checkFraudLimits = true,
        userTier = ValidMoneyAmount.UserTier.STANDARD,
        message = "Invalid fiat amount for cryptocurrency purchase"
    )
    private BigDecimal fiatAmount;
    
    @NotBlank(message = "Fiat currency is required")
    @ValidCurrency(
        supportedOnly = true,
        allowCrypto = false,
        transactionType = ValidCurrency.TransactionType.CRYPTO,
        message = "Invalid fiat currency for crypto purchase"
    )
    private String fiatCurrency;
    
    @NotNull(message = "Payment method is required")
    private String paymentMethodId;
    
    private boolean acceptPriceSlippage;
    
    private BigDecimal maxSlippagePercent;
    
    @Pattern(regexp = "^[0-9]{6}$", message = "Transaction PIN must be 6 digits")
    private String transactionPin;
}