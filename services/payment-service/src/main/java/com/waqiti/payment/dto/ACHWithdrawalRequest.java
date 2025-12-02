package com.waqiti.payment.dto;

import com.waqiti.payment.entity.BankAccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * ACH Withdrawal Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ACHWithdrawalRequest implements ACHRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least $0.01")
    @DecimalMax(value = "10000.00", message = "Amount cannot exceed $10,000")
    private BigDecimal amount;
    
    @NotBlank(message = "Routing number is required")
    @Pattern(regexp = "^[0-9]{9}$", message = "Routing number must be 9 digits")
    private String routingNumber;
    
    @NotBlank(message = "Account number is required")
    @Size(min = 4, max = 17, message = "Account number must be between 4 and 17 digits")
    private String accountNumber;
    
    @NotBlank(message = "Account holder name is required")
    @Size(max = 100, message = "Account holder name cannot exceed 100 characters")
    private String accountHolderName;
    
    @NotNull(message = "Account type is required")
    private BankAccountType accountType;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;
    
    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
    private String idempotencyKey;
}