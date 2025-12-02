package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating financial accounts in core-banking-service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreBankingAccountRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Account name is required")
    @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
    private String accountName;
    
    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "^(CHECKING|SAVINGS|BUSINESS_CHECKING|BUSINESS_SAVINGS|CREDIT|LOAN|INVESTMENT)$", 
             message = "Invalid account type")
    private String accountType;
    
    @NotBlank(message = "Account category is required")
    @Pattern(regexp = "^(ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE)$", 
             message = "Invalid account category")
    private String accountCategory;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
    
    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Invalid balance format")
    private BigDecimal initialBalance;
    
    @DecimalMin(value = "0.00", message = "Credit limit cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Invalid credit limit format")
    private BigDecimal creditLimit;
    
    @NotNull(message = "Daily transaction limit is required")
    @DecimalMin(value = "0.01", message = "Daily limit must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Daily limit exceeds maximum allowed")
    @Digits(integer = 10, fraction = 2, message = "Invalid daily limit format")
    private BigDecimal dailyTransactionLimit;
    
    @NotNull(message = "Monthly transaction limit is required")
    @DecimalMin(value = "0.01", message = "Monthly limit must be greater than 0")
    @DecimalMax(value = "10000000.00", message = "Monthly limit exceeds maximum allowed")
    @Digits(integer = 15, fraction = 2, message = "Invalid monthly limit format")
    private BigDecimal monthlyTransactionLimit;
}