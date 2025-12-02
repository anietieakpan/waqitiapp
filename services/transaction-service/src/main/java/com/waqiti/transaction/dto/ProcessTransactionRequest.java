package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionType;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class ProcessTransactionRequest {
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;
    
    @NotBlank(message = "Source account ID is required")
    private String sourceAccountId;
    
    @NotBlank(message = "Target account ID is required")
    private String targetAccountId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    private String description;
    
    @NotBlank(message = "Initiated by is required")
    private String initiatedBy;
    
    private String channel;
    private Map<String, String> metadata;
    private String deviceFingerprint;
    private String locationData;
}