package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.util.UUID;

/**
 * Request DTO for creating bank accounts in core-banking-service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBankAccountRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Core account ID is required")
    private UUID coreAccountId;
    
    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{4,17}$", message = "Invalid account number format")
    private String accountNumber;
    
    @NotBlank(message = "Routing number is required")
    @Pattern(regexp = "^[0-9]{9}$", message = "Routing number must be 9 digits")
    private String routingNumber;
    
    @NotBlank(message = "Account holder name is required")
    @Size(min = 2, max = 100, message = "Account holder name must be between 2 and 100 characters")
    private String accountHolderName;
    
    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "^(CHECKING|SAVINGS|BUSINESS_CHECKING|BUSINESS_SAVINGS)$", 
             message = "Invalid account type")
    private String accountType;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
    
    @NotBlank(message = "Bank name is required")
    @Size(min = 2, max = 100, message = "Bank name must be between 2 and 100 characters")
    private String bankName;
    
    @Size(max = 50, message = "Branch code must not exceed 50 characters")
    private String branchCode;
    
    @Size(max = 11, message = "SWIFT code must not exceed 11 characters")
    @Pattern(regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$", 
             message = "Invalid SWIFT/BIC code format")
    private String swiftCode;
    
    @Size(max = 34, message = "IBAN must not exceed 34 characters")
    @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]+$", message = "Invalid IBAN format")
    private String iban;
    
    private String externalBankId;
    
    @NotBlank(message = "Provider account ID is required")
    private String providerAccountId;
    
    @NotBlank(message = "Provider name is required")
    private String providerName;
}