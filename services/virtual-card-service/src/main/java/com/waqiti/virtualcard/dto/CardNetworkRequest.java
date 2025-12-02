package com.waqiti.virtualcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for card network registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardNetworkRequest {
    
    @NotNull(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Card number must be 13-19 digits")
    private String cardNumber;
    
    @NotNull(message = "CVV is required")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3-4 digits")
    private String cvv;
    
    @NotNull(message = "Expiry is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Expiry must be in MM/YY format")
    private String expiry;
    
    @NotBlank(message = "Cardholder name is required")
    @Size(max = 100, message = "Cardholder name cannot exceed 100 characters")
    private String cardholderName;
    
    @Size(max = 500, message = "Billing address cannot exceed 500 characters")
    private String billingAddress;
    
    private String cardType;
    
    private String cardPurpose;
    
    private boolean threeDSecureEnabled = true;
    
    private java.util.Map<String, String> metadata;
}