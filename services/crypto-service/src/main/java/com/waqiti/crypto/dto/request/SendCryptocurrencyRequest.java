/**
 * Send Cryptocurrency Request DTO
 * Request to send cryptocurrency to another address
 */
package com.waqiti.crypto.dto.request;

import com.waqiti.crypto.entity.CryptoCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class SendCryptocurrencyRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "From wallet ID is required")
    private UUID fromWalletId;
    
    @NotBlank(message = "To address is required")
    private String toAddress;
    
    @NotNull(message = "Currency is required")
    private CryptoCurrency currency;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00000001", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    private BigDecimal customFee;
    
    private String memo;
    
    private boolean expedited;
    
    @Pattern(regexp = "^[0-9]{6}$", message = "Transaction PIN must be 6 digits")
    private String transactionPin;
}