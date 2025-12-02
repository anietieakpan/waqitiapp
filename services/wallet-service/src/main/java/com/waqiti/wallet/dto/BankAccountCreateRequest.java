package com.waqiti.wallet.dto;

import com.waqiti.wallet.domain.BankAccount;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating bank accounts within wallet-service.
 * 
 * This DTO is used internally within wallet-service and gets transformed
 * to the appropriate format for bank-integration-service API calls.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountCreateRequest {
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Account number is required")
    @Size(min = 8, max = 20, message = "Account number must be 8-20 characters")
    @Pattern(regexp = "^[0-9]+$", message = "Account number must contain only digits")
    private String accountNumber;
    
    @NotBlank(message = "Routing number is required")
    @Size(min = 9, max = 9, message = "Routing number must be exactly 9 digits")
    @Pattern(regexp = "^[0-9]{9}$", message = "Routing number must be exactly 9 digits")
    private String routingNumber;
    
    @NotBlank(message = "Account holder name is required")
    @Size(min = 2, max = 100, message = "Account holder name must be 2-100 characters")
    private String accountHolderName;
    
    @NotNull(message = "Account type is required")
    private BankAccount.AccountType accountType;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
    private String currency;
    
    @Size(max = 100, message = "Bank name cannot exceed 100 characters")
    private String bankName;
    
    @Size(max = 20, message = "Branch code cannot exceed 20 characters")
    private String branchCode;
    
    @Size(max = 11, message = "SWIFT code cannot exceed 11 characters")
    private String swiftCode;
    
    @DecimalMin(value = "0.01", message = "Daily limit must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Daily limit cannot exceed 1,000,000")
    private BigDecimal dailyLimit;
    
    @DecimalMin(value = "0.01", message = "Monthly limit must be at least 0.01") 
    @DecimalMax(value = "10000000.00", message = "Monthly limit cannot exceed 10,000,000")
    private BigDecimal monthlyLimit;
    
    private Boolean isPrimary = false;
    
    @Size(max = 50, message = "External bank ID cannot exceed 50 characters")
    private String externalBankId;
}