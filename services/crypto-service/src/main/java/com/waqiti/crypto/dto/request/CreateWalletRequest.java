/**
 * Create Wallet Request DTO
 * Request to create a new cryptocurrency wallet
 */
package com.waqiti.crypto.dto.request;

import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.WalletType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Currency is required")
    private CryptoCurrency currency;
    
    @NotNull(message = "Wallet type is required")
    private WalletType walletType;
    
    private String walletName;
    
    private boolean enableMultiSig;
    
    private Integer requiredSignatures;
    
    private boolean enableWhitelist;
    
    private boolean enableAutoSweep;
}