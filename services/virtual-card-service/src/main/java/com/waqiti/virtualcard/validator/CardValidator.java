package com.waqiti.virtualcard.validator;

import com.waqiti.virtualcard.domain.CardNetwork;
import com.waqiti.virtualcard.domain.VirtualCard;
import com.waqiti.virtualcard.dto.CardTransactionRequest;
import com.waqiti.common.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * Service for validating card data and transactions
 */
@Service
@Slf4j
public class CardValidator {
    
    // Card number patterns for different networks
    private static final Pattern VISA_PATTERN = Pattern.compile("^4[0-9]{12,18}$");
    private static final Pattern MASTERCARD_PATTERN = Pattern.compile("^5[1-5][0-9]{14}$|^2[2-7][0-9]{14}$");
    private static final Pattern AMEX_PATTERN = Pattern.compile("^3[47][0-9]{13}$");
    private static final Pattern DISCOVER_PATTERN = Pattern.compile("^6(?:011|5[0-9]{2})[0-9]{12}$");
    private static final Pattern DINERS_PATTERN = Pattern.compile("^3[0689][0-9]{11,16}$");
    private static final Pattern JCB_PATTERN = Pattern.compile("^35[0-9]{14}$");
    
    // CVV patterns
    private static final Pattern CVV3_PATTERN = Pattern.compile("^[0-9]{3}$");
    private static final Pattern CVV4_PATTERN = Pattern.compile("^[0-9]{4}$");
    
    // PIN pattern
    private static final Pattern PIN_PATTERN = Pattern.compile("^[0-9]{4,8}$");
    
    /**
     * Validate card number using Luhn algorithm and format checks
     */
    public boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return false;
        }
        
        // Remove any non-digit characters
        String cleanCardNumber = cardNumber.replaceAll("\\D", "");
        
        // Check length
        if (cleanCardNumber.length() < 13 || cleanCardNumber.length() > 19) {
            return false;
        }
        
        // Validate using Luhn algorithm
        return isValidLuhn(cleanCardNumber);
    }
    
    /**
     * Validate card number against specific network
     */
    public boolean isValidForNetwork(String cardNumber, CardNetwork network) {
        if (!isValidCardNumber(cardNumber)) {
            return false;
        }
        
        String cleanCardNumber = cardNumber.replaceAll("\\D", "");
        
        return switch (network) {
            case VISA -> VISA_PATTERN.matcher(cleanCardNumber).matches();
            case MASTERCARD -> MASTERCARD_PATTERN.matcher(cleanCardNumber).matches();
            case AMERICAN_EXPRESS -> AMEX_PATTERN.matcher(cleanCardNumber).matches();
            case DISCOVER -> DISCOVER_PATTERN.matcher(cleanCardNumber).matches();
            case DINERS_CLUB -> DINERS_PATTERN.matcher(cleanCardNumber).matches();
            case JCB -> JCB_PATTERN.matcher(cleanCardNumber).matches();
            default -> false;
        };
    }
    
    /**
     * Detect card network from card number
     */
    public CardNetwork detectCardNetwork(String cardNumber) {
        if (!isValidCardNumber(cardNumber)) {
            throw new ValidationException("Invalid card number");
        }
        
        String cleanCardNumber = cardNumber.replaceAll("\\D", "");
        
        if (VISA_PATTERN.matcher(cleanCardNumber).matches()) {
            return CardNetwork.VISA;
        } else if (MASTERCARD_PATTERN.matcher(cleanCardNumber).matches()) {
            return CardNetwork.MASTERCARD;
        } else if (AMEX_PATTERN.matcher(cleanCardNumber).matches()) {
            return CardNetwork.AMERICAN_EXPRESS;
        } else if (DISCOVER_PATTERN.matcher(cleanCardNumber).matches()) {
            return CardNetwork.DISCOVER;
        } else if (DINERS_PATTERN.matcher(cleanCardNumber).matches()) {
            return CardNetwork.DINERS_CLUB;
        } else if (JCB_PATTERN.matcher(cleanCardNumber).matches()) {
            return CardNetwork.JCB;
        }
        
        throw new ValidationException("Unable to detect card network");
    }
    
    /**
     * Validate CVV format
     */
    public boolean isValidCVV(String cvv, CardNetwork network) {
        if (cvv == null || cvv.trim().isEmpty()) {
            return false;
        }
        
        // American Express uses 4-digit CVV, others use 3-digit
        if (network == CardNetwork.AMERICAN_EXPRESS) {
            return CVV4_PATTERN.matcher(cvv).matches();
        } else {
            return CVV3_PATTERN.matcher(cvv).matches();
        }
    }
    
    /**
     * Validate expiry date
     */
    public boolean isValidExpiryDate(int expiryMonth, int expiryYear) {
        if (expiryMonth < 1 || expiryMonth > 12) {
            return false;
        }
        
        // Convert 2-digit year to 4-digit if necessary
        int fullYear = expiryYear < 100 ? 2000 + expiryYear : expiryYear;
        
        if (fullYear < 2000 || fullYear > 2099) {
            return false;
        }
        
        YearMonth expiry = YearMonth.of(fullYear, expiryMonth);
        YearMonth current = YearMonth.now();
        
        return !expiry.isBefore(current);
    }
    
    /**
     * Validate PIN format
     */
    public boolean isValidPIN(String pin) {
        return pin != null && PIN_PATTERN.matcher(pin).matches();
    }
    
    /**
     * Validate cardholder name
     */
    public boolean isValidCardholderName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = name.trim();
        
        // Check length
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            return false;
        }
        
        // Check for valid characters (letters, spaces, hyphens, apostrophes)
        return trimmed.matches("^[a-zA-Z\\s\\-'.]+$");
    }
    
    /**
     * Validate virtual card for activation
     */
    public void validateCardForActivation(VirtualCard card) {
        if (card == null) {
            throw new ValidationException("Card cannot be null");
        }
        
        // Check if card is already active
        if (card.canProcessTransaction()) {
            throw new ValidationException("Card is already active");
        }
        
        // Check if card is expired
        if (card.isExpired()) {
            throw new ValidationException("Cannot activate expired card");
        }
        
        // Validate cardholder name
        if (!isValidCardholderName(card.getCardholderName())) {
            throw new ValidationException("Invalid cardholder name");
        }
        
        // Validate expiry date
        if (!isValidExpiryDate(card.getExpiryMonth(), card.getExpiryYear())) {
            throw new ValidationException("Invalid expiry date");
        }
        
        log.debug("Card validation passed for activation: {}", card.getId());
    }
    
    /**
     * Validate transaction request
     */
    public void validateTransactionRequest(CardTransactionRequest request) {
        if (request == null) {
            throw new ValidationException("Transaction request cannot be null");
        }
        
        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Transaction amount must be greater than zero");
        }
        
        // Validate currency
        if (request.getCurrency() == null || !isValidCurrency(request.getCurrency())) {
            throw new ValidationException("Invalid currency code");
        }
        
        // Validate merchant information
        if (request.getMerchant() == null) {
            throw new ValidationException("Merchant information is required");
        }
        
        validateMerchantInfo(request.getMerchant());
        
        // Validate transaction type
        if (request.getTransactionType() == null) {
            throw new ValidationException("Transaction type is required");
        }
        
        log.debug("Transaction request validation passed for amount: {} {}", 
                 request.getAmount(), request.getCurrency());
    }
    
    /**
     * Validate card for transaction processing
     */
    public void validateCardForTransaction(VirtualCard card, BigDecimal amount) {
        if (card == null) {
            throw new ValidationException("Card cannot be null");
        }
        
        // Check if card can process transactions
        if (!card.canProcessTransaction()) {
            throw new ValidationException("Card cannot process transactions");
        }
        
        // Check if card is locked
        if (card.isLocked()) {
            throw new ValidationException("Card is locked: " + card.getLockReason());
        }
        
        // Check if card is expired
        if (card.isExpired()) {
            throw new ValidationException("Card is expired");
        }
        
        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Transaction amount must be greater than zero");
        }
        
        log.debug("Card transaction validation passed: {} for amount: {}", 
                 card.getId(), amount);
    }
    
    /**
     * Validate billing address format
     */
    public boolean isValidBillingAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return true; // Optional field
        }
        
        String trimmed = address.trim();
        return trimmed.length() <= 500 && trimmed.matches("^[a-zA-Z0-9\\s\\-,.#/]+$");
    }
    
    // Private helper methods
    
    private boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10) == 0;
    }
    
    private boolean isValidCurrency(String currency) {
        // Basic currency code validation (ISO 4217)
        return currency != null && 
               currency.length() == 3 && 
               currency.matches("^[A-Z]{3}$");
    }
    
    private void validateMerchantInfo(CardTransactionRequest.MerchantInfo merchant) {
        if (merchant.getName() == null || merchant.getName().trim().isEmpty()) {
            throw new ValidationException("Merchant name is required");
        }
        
        if (merchant.getName().length() > 200) {
            throw new ValidationException("Merchant name too long");
        }
        
        // Validate MCC if provided
        if (merchant.getMcc() != null && !merchant.getMcc().matches("^[0-9]{4}$")) {
            throw new ValidationException("Invalid merchant category code");
        }
        
        // Validate country code if provided
        if (merchant.getCountry() != null && !merchant.getCountry().matches("^[A-Z]{2,3}$")) {
            throw new ValidationException("Invalid country code");
        }
    }
}