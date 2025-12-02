package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for card transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTransactionRequest {
    
    @NotNull(message = "Card ID is required")
    @NotBlank(message = "Card ID cannot be blank")
    private String cardId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Amount must be a valid currency amount")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
    private String currency;
    
    private BigDecimal originalAmount;
    
    @Size(min = 3, max = 3, message = "Original currency must be 3 characters")
    private String originalCurrency;
    
    @Valid
    private MerchantInfo merchant;
    
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;
    
    @Size(max = 4, message = "Merchant category code must be 4 characters")
    private String merchantCategoryCode;
    
    @Size(max = 50, message = "Terminal ID cannot exceed 50 characters")
    private String terminalId;
    
    @Size(max = 30, message = "Entry mode cannot exceed 30 characters")
    private String entryMode;
    
    @Size(max = 30, message = "Processing method cannot exceed 30 characters")
    private String processingMethod;
    
    private boolean cardPresent = false;
    
    private boolean cardholderPresent = false;
    
    private boolean isInternational = false;
    
    private boolean isOnline = false;
    
    private boolean isContactless = false;
    
    private boolean isRecurring = false;
    
    private boolean threeDSecure = false;
    
    @Size(max = 10, message = "3D Secure version cannot exceed 10 characters")
    private String threeDSecureVersion;
    
    @Size(max = 20, message = "POS data cannot exceed 20 characters")
    private String posData;
    
    @Size(max = 50, message = "Acquirer reference cannot exceed 50 characters")
    private String acquirerReference;
    
    @Size(max = 20, message = "Trace number cannot exceed 20 characters")
    private String traceNumber;
    
    @Size(max = 500, message = "Transaction description cannot exceed 500 characters")
    private String transactionDescription;
    
    private boolean mfaVerified = false;
    
    @Size(max = 100, message = "MFA method cannot exceed 100 characters")
    private String mfaMethod;
    
    private Map<String, String> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantInfo {
        
        @NotNull(message = "Merchant name is required")
        @Size(max = 200, message = "Merchant name cannot exceed 200 characters")
        private String name;
        
        @Size(max = 50, message = "Merchant ID cannot exceed 50 characters")
        private String id;
        
        @Size(max = 100, message = "Merchant category cannot exceed 100 characters")
        private String category;
        
        @Size(max = 4, message = "MCC must be 4 characters")
        private String mcc;
        
        @Size(max = 3, message = "Country code must be 3 characters")
        private String country;
        
        @Size(max = 100, message = "City cannot exceed 100 characters")
        private String city;
        
        @Size(max = 500, message = "Address cannot exceed 500 characters")
        private String address;
        
        @Size(max = 20, message = "Postal code cannot exceed 20 characters")
        private String postalCode;
        
        @Size(max = 50, message = "Phone cannot exceed 50 characters")
        private String phone;
        
        @Size(max = 100, message = "Website cannot exceed 100 characters")
        @Pattern(regexp = "^(https?://)?[\\w\\-\\.]+\\.[a-zA-Z]{2,}(/.*)?$", 
                message = "Website must be a valid URL")
        private String website;
    }
}