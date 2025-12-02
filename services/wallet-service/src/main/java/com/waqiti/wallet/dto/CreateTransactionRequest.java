package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for creating transactions in core-banking-service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {
    
    @NotBlank(message = "From account ID is required")
    private String fromAccountId;
    
    @NotBlank(message = "To account ID is required")
    private String toAccountId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "^(TRANSFER|DEPOSIT|WITHDRAWAL|PAYMENT|REFUND|FEE|INTEREST)$", 
             message = "Invalid transaction type")
    private String transactionType;
    
    @Size(max = 100, message = "Reference must not exceed 100 characters")
    private String reference;
    
    private String idempotencyKey;
    
    private boolean validateLimits;
    
    private boolean requireApproval;
    
    private Map<String, Object> metadata;
}