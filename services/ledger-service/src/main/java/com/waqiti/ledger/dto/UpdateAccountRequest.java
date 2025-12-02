package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountRequest {
    
    @Size(max = 255, message = "Account name must not exceed 255 characters")
    private String accountName;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    private Boolean isActive;
    
    private Boolean allowsTransactions;
}