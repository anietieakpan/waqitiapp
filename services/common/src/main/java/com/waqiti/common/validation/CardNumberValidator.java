package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Production-ready card number validator with Luhn algorithm and card type detection
 */
@Component
public class CardNumberValidator implements ConstraintValidator<ValidationConstraints.ValidCardNumber, String> {
    
    private ValidationConstraints.CardType[] supportedTypes;
    
    private static final Map<ValidationConstraints.CardType, CardPattern> CARD_PATTERNS = new HashMap<>();
    
    static {
        // Define card patterns for various card types
        CARD_PATTERNS.put(ValidationConstraints.CardType.VISA, 
            new CardPattern("^4[0-9]{12}(?:[0-9]{3})?$", 13, 16, 19));
        
        CARD_PATTERNS.put(ValidationConstraints.CardType.MASTERCARD, 
            new CardPattern("^(?:5[1-5][0-9]{2}|222[1-9]|22[3-9][0-9]|2[3-6][0-9]{2}|27[01][0-9]|2720)[0-9]{12}$", 16));
        
        CARD_PATTERNS.put(ValidationConstraints.CardType.AMEX, 
            new CardPattern("^3[47][0-9]{13}$", 15));
        
        CARD_PATTERNS.put(ValidationConstraints.CardType.DISCOVER, 
            new CardPattern("^(?:6011|65[0-9]{2}|64[4-9][0-9])[0-9]{12,15}$", 16, 19));
        
        CARD_PATTERNS.put(ValidationConstraints.CardType.DINERS, 
            new CardPattern("^3(?:0[0-5]|[68][0-9])[0-9]{11}$", 14));
        
        CARD_PATTERNS.put(ValidationConstraints.CardType.JCB, 
            new CardPattern("^(?:2131|1800|35\\d{3})\\d{11}$", 16, 19));
    }
    
    @Override
    public void initialize(ValidationConstraints.ValidCardNumber annotation) {
        this.supportedTypes = annotation.supportedTypes();
    }
    
    @Override
    public boolean isValid(String cardNumber, ConstraintValidatorContext context) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Remove all non-digit characters (spaces, hyphens, etc.)
        String cleanedNumber = cardNumber.replaceAll("[^0-9]", "");
        
        // Basic length check
        if (cleanedNumber.length() < 13 || cleanedNumber.length() > 19) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Card number must be between 13 and 19 digits")
                .addConstraintViolation();
            return false;
        }
        
        // Identify card type
        ValidationConstraints.CardType detectedType = detectCardType(cleanedNumber);
        if (detectedType == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unrecognized card type")
                .addConstraintViolation();
            return false;
        }
        
        // Check if card type is supported
        if (!Arrays.asList(supportedTypes).contains(detectedType)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("%s cards are not supported", detectedType.name())
            ).addConstraintViolation();
            return false;
        }
        
        // Validate against specific card pattern
        CardPattern pattern = CARD_PATTERNS.get(detectedType);
        if (!Pattern.matches(pattern.regex, cleanedNumber)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Invalid %s card number format", detectedType.name())
            ).addConstraintViolation();
            return false;
        }
        
        // Check valid lengths for this card type
        boolean validLength = false;
        for (int length : pattern.validLengths) {
            if (cleanedNumber.length() == length) {
                validLength = true;
                break;
            }
        }
        
        if (!validLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("%s card numbers must be %s digits", 
                    detectedType.name(), 
                    Arrays.toString(pattern.validLengths))
            ).addConstraintViolation();
            return false;
        }
        
        // Validate using Luhn algorithm
        if (!validateLuhn(cleanedNumber)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid card number (failed checksum validation)")
                .addConstraintViolation();
            return false;
        }
        
        // Additional security checks
        if (isTestCardNumber(cleanedNumber)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Test card numbers are not allowed in production")
                .addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private ValidationConstraints.CardType detectCardType(String cardNumber) {
        for (Map.Entry<ValidationConstraints.CardType, CardPattern> entry : CARD_PATTERNS.entrySet()) {
            if (Pattern.matches(entry.getValue().regex, cardNumber)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private boolean validateLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        
        // Process from right to left
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
        
        return (sum % 10 == 0);
    }
    
    private boolean isTestCardNumber(String cardNumber) {
        // Common test card numbers that should be rejected in production
        String[] testCards = {
            "4111111111111111", // Visa test
            "4012888888881881", // Visa test
            "5105105105105100", // Mastercard test
            "5555555555554444", // Mastercard test
            "378282246310005",  // Amex test
            "371449635398431",  // Amex test
            "6011111111111117", // Discover test
            "6011000990139424", // Discover test
            "30569309025904",   // Diners test
            "38520000023237",   // Diners test
            "3530111333300000", // JCB test
            "3566002020360505"  // JCB test
        };
        
        for (String testCard : testCards) {
            if (cardNumber.equals(testCard)) {
                return true;
            }
        }
        
        // Check for obvious patterns
        if (cardNumber.matches("^(\\d)\\1+$")) { // All same digits
            return true;
        }
        
        if (cardNumber.matches("^1234567890123456$|^0123456789012345$")) { // Sequential
            return true;
        }
        
        return false;
    }
    
    private static class CardPattern {
        final String regex;
        final int[] validLengths;
        
        CardPattern(String regex, int... validLengths) {
            this.regex = regex;
            this.validLengths = validLengths;
        }
    }
}