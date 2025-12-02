package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

/**
 * Request to verify a debit card for instant deposits
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDebitCardRequest {
    
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Invalid card number")
    private String cardNumber;
    
    @NotBlank(message = "Expiry month is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Invalid expiry month")
    private String expiryMonth;
    
    @NotBlank(message = "Expiry year is required")
    @Pattern(regexp = "^[0-9]{2,4}$", message = "Invalid expiry year")
    private String expiryYear;
    
    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "Invalid CVV")
    private String cvv;
    
    @NotBlank(message = "Cardholder name is required")
    @Size(min = 2, max = 100, message = "Cardholder name must be between 2 and 100 characters")
    private String cardholderName;
    
    @NotBlank(message = "Billing zip code is required")
    @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$", message = "Invalid zip code")
    private String billingZipCode;
    
    // Save card for future use
    private boolean saveCard;
    
    private String nickname;
}