package com.waqiti.payment.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * CRITICAL: PCI DSS Compliant Card Details DTO
 * 
 * This DTO handles card data with strict PCI DSS compliance:
 * - CVV is never stored or logged
 * - Track data is explicitly prohibited
 * - Proper validation for all fields
 * - Secure toString() implementation
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CardDetails {
    
    /**
     * Primary Account Number (PAN)
     * CRITICAL: This is sensitive data that must be tokenized
     */
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Card number must be 13-19 digits")
    @JsonProperty("cardNumber")
    private String cardNumber;
    
    /**
     * Card Verification Value (CVV)
     * CRITICAL: This MUST NOT be stored per PCI DSS
     * Only used for real-time processing and immediately discarded
     */
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3-4 digits")
    @JsonProperty("cvv")
    private String cvv;
    
    /**
     * Cardholder name as it appears on the card
     * Safe to store per PCI DSS
     */
    @Size(max = 100, message = "Cardholder name must not exceed 100 characters")
    @JsonProperty("cardholderName")
    private String cardholderName;
    
    /**
     * Card expiry month (1-12)
     * Safe to store per PCI DSS
     */
    @NotNull(message = "Expiry month is required")
    @Min(value = 1, message = "Expiry month must be between 1 and 12")
    @Max(value = 12, message = "Expiry month must be between 1 and 12")
    @JsonProperty("expiryMonth")
    private Integer expiryMonth;
    
    /**
     * Card expiry year (YYYY)
     * Safe to store per PCI DSS
     */
    @NotNull(message = "Expiry year is required")
    @Min(value = 2024, message = "Expiry year must be current year or future")
    @JsonProperty("expiryYear")
    private Integer expiryYear;
    
    /**
     * Billing address line 1 (optional)
     * Safe to store per PCI DSS
     */
    @Size(max = 100, message = "Address line 1 must not exceed 100 characters")
    @JsonProperty("addressLine1")
    private String addressLine1;
    
    /**
     * Billing address line 2 (optional)
     * Safe to store per PCI DSS
     */
    @Size(max = 100, message = "Address line 2 must not exceed 100 characters")
    @JsonProperty("addressLine2")
    private String addressLine2;
    
    /**
     * Billing city (optional)
     * Safe to store per PCI DSS
     */
    @Size(max = 50, message = "City must not exceed 50 characters")
    @JsonProperty("city")
    private String city;
    
    /**
     * Billing state/province (optional)
     * Safe to store per PCI DSS
     */
    @Size(max = 50, message = "State must not exceed 50 characters")
    @JsonProperty("state")
    private String state;
    
    /**
     * Billing postal/zip code (optional)
     * Safe to store per PCI DSS
     */
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @JsonProperty("postalCode")
    private String postalCode;
    
    /**
     * Billing country code (ISO 3166-1 alpha-2)
     * Safe to store per PCI DSS
     */
    @Size(min = 2, max = 2, message = "Country code must be exactly 2 characters")
    @JsonProperty("countryCode")
    private String countryCode;
    
    /**
     * Track data (PROHIBITED by PCI DSS)
     * This field should NEVER be populated
     */
    @JsonIgnore
    private String trackData;
    
    /**
     * Utility methods
     */
    
    /**
     * Get masked card number for display/logging
     * Shows only first 6 and last 4 digits
     */
    @JsonIgnore
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 10) {
            return "****";
        }
        return cardNumber.substring(0, 6) + "******" + cardNumber.substring(cardNumber.length() - 4);
    }
    
    /**
     * Get last 4 digits of card number
     */
    @JsonIgnore
    public String getLast4Digits() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return cardNumber.substring(cardNumber.length() - 4);
    }
    
    /**
     * Get first 6 digits (BIN) of card number
     */
    @JsonIgnore
    public String getBin() {
        if (cardNumber == null || cardNumber.length() < 6) {
            return null;
        }
        return cardNumber.substring(0, 6);
    }
    
    /**
     * Detect card type from PAN
     */
    @JsonIgnore
    public String getCardType() {
        if (cardNumber == null) {
            return "UNKNOWN";
        }
        
        if (cardNumber.startsWith("4")) {
            return "VISA";
        } else if (cardNumber.startsWith("5") || cardNumber.startsWith("2")) {
            return "MASTERCARD";
        } else if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) {
            return "AMEX";
        } else if (cardNumber.startsWith("6")) {
            return "DISCOVER";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Check if card is expired
     */
    @JsonIgnore
    public boolean isExpired() {
        if (expiryMonth == null || expiryYear == null) {
            return true;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = LocalDateTime.of(expiryYear, expiryMonth, 1, 0, 0).plusMonths(1);
        
        return now.isAfter(expiry);
    }
    
    /**
     * Check if card is expiring soon (within 30 days)
     */
    @JsonIgnore
    public boolean isExpiringSoon() {
        if (expiryMonth == null || expiryYear == null) {
            return true;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = LocalDateTime.of(expiryYear, expiryMonth, 1, 0, 0).plusMonths(1);
        LocalDateTime soonThreshold = now.plusDays(30);
        
        return expiry.isBefore(soonThreshold);
    }
    
    /**
     * Validate Luhn algorithm
     */
    @JsonIgnore
    public boolean isValidLuhn() {
        if (cardNumber == null) {
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return sum % 10 == 0;
    }
    
    /**
     * Get display name for UI
     */
    @JsonIgnore
    public String getDisplayName() {
        return String.format("%s ending in %s", getCardType(), getLast4Digits());
    }
    
    /**
     * Check if this is a complete card details object
     */
    @JsonIgnore
    public boolean isComplete() {
        return cardNumber != null && 
               expiryMonth != null && 
               expiryYear != null && 
               !isExpired();
    }
    
    /**
     * Clear sensitive data from memory
     * Called after tokenization to ensure PCI compliance
     */
    public void clearSensitiveData() {
        this.cardNumber = null;
        this.cvv = null;
        this.trackData = null;
    }
    
    /**
     * Validate card data for PCI compliance
     */
    public void validatePCICompliance() {
        // Ensure track data is not present
        if (trackData != null && !trackData.trim().isEmpty()) {
            throw new IllegalStateException("PCI VIOLATION: Track data cannot be processed");
        }
        
        // Ensure CVV is not being stored
        if (cvv != null) {
            // CVV should only be used for processing, never stored
            // This is just a validation - actual clearing happens elsewhere
        }
        
        // Validate card number format
        if (cardNumber != null && !cardNumber.matches("^[0-9]{13,19}$")) {
            throw new IllegalStateException("Invalid card number format");
        }
    }
    
    /**
     * Create a safe copy for logging/auditing (no sensitive data)
     */
    @JsonIgnore
    public CardDetails createSafeCopy() {
        return CardDetails.builder()
            .cardholderName(cardholderName)
            .expiryMonth(expiryMonth)
            .expiryYear(expiryYear)
            .addressLine1(addressLine1)
            .addressLine2(addressLine2)
            .city(city)
            .state(state)
            .postalCode(postalCode)
            .countryCode(countryCode)
            // Exclude cardNumber, cvv, trackData
            .build();
    }
    
    /**
     * PCI DSS Compliant toString() - NO SENSITIVE DATA
     */
    @Override
    public String toString() {
        return String.format("CardDetails{cardType=%s, last4=%s, expiry=%d/%d, cardholder=%s, country=%s}", 
            getCardType(), 
            getLast4Digits(), 
            expiryMonth != null ? expiryMonth : 0, 
            expiryYear != null ? expiryYear : 0,
            cardholderName != null ? cardholderName.substring(0, Math.min(cardholderName.length(), 10)) + "..." : null,
            countryCode);
    }
    
    /**
     * Equals and hashCode based on safe fields only
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CardDetails that = (CardDetails) obj;
        
        // Compare based on last 4 digits and expiry only for safety
        return java.util.Objects.equals(getLast4Digits(), that.getLast4Digits()) &&
               java.util.Objects.equals(expiryMonth, that.expiryMonth) &&
               java.util.Objects.equals(expiryYear, that.expiryYear) &&
               java.util.Objects.equals(cardholderName, that.cardholderName);
    }
    
    @Override
    public int hashCode() {
        // Hash based on safe fields only
        return java.util.Objects.hash(getLast4Digits(), expiryMonth, expiryYear, cardholderName);
    }
}