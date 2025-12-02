package com.waqiti.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreationRequest {
    
    @NotBlank(message = "Account type is required")
    private String accountType; // CHECKING, SAVINGS, WALLET
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    private String accountName;
    
    @NotNull(message = "Initial balance is required")
    private BigDecimal initialBalance;
    
    private boolean isPrimary;
}