package com.waqiti.common.client;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Create Wallet Request DTO for inter-service communication
 */
@Data
@Builder
public class CreateWalletRequest {
    @NotNull
    private UUID userId;
    
    @NotBlank
    private String walletType;
    
    @NotBlank
    private String currency;
    
    private String accountNumber;
    private String routingNumber;
    private String walletName;
}