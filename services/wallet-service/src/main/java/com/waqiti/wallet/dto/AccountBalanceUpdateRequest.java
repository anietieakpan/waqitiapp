package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Request DTO for updating account balances in core-banking-service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceUpdateRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    @NotBlank(message = "Operation is required")
    @Pattern(regexp = "^(CREDIT|DEBIT)$", message = "Operation must be CREDIT or DEBIT")
    private String operation;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @Size(max = 100, message = "Transaction reference must not exceed 100 characters")
    private String transactionReference;
    
    private String idempotencyKey;
    
    private boolean validateLimits;
    
    private boolean notifyUser;
}