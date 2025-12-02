package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.util.Map;

/**
 * Request DTO for linking a bank account through external bank integration service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkBankAccountRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{4,17}$", message = "Invalid account number format")
    private String accountNumber;
    
    @NotBlank(message = "Routing number is required")
    @Pattern(regexp = "^[0-9]{9}$", message = "Routing number must be 9 digits")
    private String routingNumber;
    
    @NotBlank(message = "Bank name is required")
    @Size(min = 2, max = 100, message = "Bank name must be between 2 and 100 characters")
    private String bankName;
    
    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "^(checking|savings|business_checking|business_savings)$", 
             message = "Account type must be checking, savings, business_checking, or business_savings")
    private String accountType;
    
    @NotBlank(message = "Account holder name is required")
    @Size(min = 2, max = 100, message = "Account holder name must be between 2 and 100 characters")
    private String accountHolderName;
    
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
    
    private String plaidPublicToken;
    
    private String plaidAccountId;
    
    private Map<String, Object> metadata;
}