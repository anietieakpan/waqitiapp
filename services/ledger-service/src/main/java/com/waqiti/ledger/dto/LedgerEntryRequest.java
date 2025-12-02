package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryRequest {
    
    @NotNull(message = "Account ID is required")
    private UUID accountId;
    
    @NotBlank(message = "Entry type is required")
    private String entryType; // DEBIT, CREDIT, RESERVATION, RELEASE, PENDING
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
    
    private String referenceNumber;
    
    @NotBlank(message = "Description is required")
    private String description;
    
    private String narrative;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;
    
    @NotNull(message = "Value date is required")
    private LocalDateTime valueDate;
    
    private UUID contraAccountId;
    private String metadata;
}