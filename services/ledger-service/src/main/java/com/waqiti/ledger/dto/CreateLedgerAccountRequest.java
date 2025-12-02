package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLedgerAccountRequest {
    
    @NotBlank(message = "Account code is required")
    @Size(max = 20, message = "Account code must not exceed 20 characters")
    private String accountCode;
    
    @NotBlank(message = "Account name is required")
    @Size(max = 255, message = "Account name must not exceed 255 characters")
    private String accountName;
    
    @NotBlank(message = "Account type is required")
    private String accountType;
    
    private UUID parentAccountId;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @Builder.Default
    private boolean allowsTransactions = true;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    @NotBlank(message = "Normal balance is required")
    private String normalBalance;
    
    private String createdBy;
}