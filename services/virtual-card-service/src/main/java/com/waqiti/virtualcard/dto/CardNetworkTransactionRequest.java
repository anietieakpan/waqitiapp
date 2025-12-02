package com.waqiti.virtualcard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for card network transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardNetworkTransactionRequest {
    
    @NotBlank(message = "Network token is required")
    private String token;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Amount must be a valid currency amount")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
    private String currency;
    
    @Valid
    private MerchantInfo merchant;
    
    @Size(max = 4, message = "MCC must be 4 characters")
    private String mcc;
    
    private String terminalId;
    
    private String acquirerId;
    
    private String processingCode;
    
    private String posEntryMode;
    
    private String posConditionCode;
    
    private boolean cardPresent = false;
    
    private boolean cardholderPresent = false;
    
    private boolean isInternational = false;
    
    private boolean isOnline = false;
    
    private boolean isContactless = false;
    
    private boolean isRecurring = false;
    
    private boolean threeDSecure = false;
    
    private String authenticationData;
    
    private String cryptogram;
    
    private String cavv;
    
    private String eci;
    
    private String transactionId;
    
    private String retrievalReferenceNumber;
    
    private String systemTraceAuditNumber;
    
    private Map<String, String> additionalData;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantInfo {
        
        @NotBlank(message = "Merchant name is required")
        @Size(max = 200, message = "Merchant name cannot exceed 200 characters")
        private String name;
        
        @Size(max = 50, message = "Merchant ID cannot exceed 50 characters")
        private String id;
        
        @Size(max = 100, message = "Category cannot exceed 100 characters")
        private String category;
        
        @Size(max = 4, message = "MCC must be 4 characters")
        private String mcc;
        
        @Size(max = 3, message = "Country code must be 3 characters")
        private String country;
        
        @Size(max = 100, message = "City cannot exceed 100 characters")
        private String city;
        
        @Size(max = 20, message = "Postal code cannot exceed 20 characters")
        private String postalCode;
        
        @Size(max = 500, message = "Address cannot exceed 500 characters")
        private String address;
    }
}