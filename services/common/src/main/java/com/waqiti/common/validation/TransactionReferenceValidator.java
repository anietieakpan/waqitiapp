package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

/**
 * Production-ready transaction reference validator with comprehensive validation rules
 * Validates transaction reference formats, checksums, and business rules
 */
@Slf4j
@Component
public class TransactionReferenceValidator implements ConstraintValidator<ValidationConstraints.ValidTransactionReference, String> {
    
    private Pattern pattern;
    private static final Set<String> RESERVED_PREFIXES = new HashSet<>();
    private static final Set<String> BLOCKED_REFERENCES = new HashSet<>();
    
    static {
        // Reserved prefixes for system use
        RESERVED_PREFIXES.add("SYS-");
        RESERVED_PREFIXES.add("ADM-");
        RESERVED_PREFIXES.add("TEST-");
        RESERVED_PREFIXES.add("DEBUG-");
        
        // Blocked references for security
        BLOCKED_REFERENCES.add("TXN-0000000000");
        BLOCKED_REFERENCES.add("TXN-1111111111");
        BLOCKED_REFERENCES.add("TXN-9999999999");
    }
    
    @Override
    public void initialize(ValidationConstraints.ValidTransactionReference constraintAnnotation) {
        String patternString = constraintAnnotation.pattern();
        if (patternString != null && !patternString.isEmpty()) {
            this.pattern = Pattern.compile(patternString);
        } else {
            // Default pattern: TXN- followed by 10-20 alphanumeric characters
            this.pattern = Pattern.compile("^TXN-[A-Z0-9]{10,20}$");
        }
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            addViolation(context, "Transaction reference cannot be null or empty");
            return false;
        }
        
        String trimmedValue = value.trim().toUpperCase();
        
        // Check basic pattern
        if (!pattern.matcher(trimmedValue).matches()) {
            addViolation(context, "Transaction reference format is invalid. Expected format: " + pattern.pattern());
            return false;
        }
        
        // Check for reserved prefixes
        if (isReservedPrefix(trimmedValue)) {
            addViolation(context, "Transaction reference uses reserved prefix");
            return false;
        }
        
        // Check blocked references
        if (BLOCKED_REFERENCES.contains(trimmedValue)) {
            addViolation(context, "Transaction reference is not allowed");
            return false;
        }
        
        // Validate checksum if present
        if (!isValidChecksum(trimmedValue)) {
            addViolation(context, "Transaction reference checksum is invalid");
            return false;
        }
        
        // Check for sequential patterns (security risk)
        if (hasSequentialPattern(trimmedValue)) {
            addViolation(context, "Transaction reference contains sequential patterns");
            return false;
        }
        
        // Check for repetitive patterns
        if (hasRepetitivePattern(trimmedValue)) {
            addViolation(context, "Transaction reference contains repetitive patterns");
            return false;
        }
        
        // Validate timestamp component if present
        if (!isValidTimestampComponent(trimmedValue)) {
            addViolation(context, "Transaction reference timestamp component is invalid");
            return false;
        }
        
        // Check entropy (randomness)
        if (!hasSufficientEntropy(trimmedValue)) {
            addViolation(context, "Transaction reference lacks sufficient entropy");
            return false;
        }
        
        log.debug("Transaction reference validation successful: {}", value);
        return true;
    }
    
    /**
     * Check if reference uses reserved prefix
     */
    private boolean isReservedPrefix(String value) {
        return RESERVED_PREFIXES.stream().anyMatch(value::startsWith);
    }
    
    /**
     * Validate checksum using Luhn algorithm (if applicable)
     */
    private boolean isValidChecksum(String value) {
        // Extract numeric part after TXN- prefix
        if (!value.startsWith("TXN-")) {
            return true; // Not applicable for non-standard formats
        }
        
        String numericPart = value.substring(4).replaceAll("[^0-9]", "");
        if (numericPart.length() < 2) {
            return true; // No checksum digit
        }
        
        // If last character is a digit, treat it as checksum
        if (Character.isDigit(numericPart.charAt(numericPart.length() - 1))) {
            return validateLuhnChecksum(numericPart);
        }
        
        return true; // No checksum validation needed
    }
    
    /**
     * Validate using Luhn algorithm
     */
    private boolean validateLuhnChecksum(String number) {
        int sum = 0;
        boolean alternate = false;
        
        // Process from right to left
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            
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
    
    /**
     * Check for sequential patterns like 123456 or ABCDEF
     */
    private boolean hasSequentialPattern(String value) {
        String alphaNumPart = value.replaceAll("[^A-Z0-9]", "");
        
        if (alphaNumPart.length() < 4) {
            return false;
        }
        
        // Check for ascending sequences
        int sequentialCount = 0;
        for (int i = 1; i < alphaNumPart.length(); i++) {
            char current = alphaNumPart.charAt(i);
            char previous = alphaNumPart.charAt(i - 1);
            
            if (current == previous + 1) {
                sequentialCount++;
                if (sequentialCount >= 3) { // 4 or more sequential characters
                    return true;
                }
            } else {
                sequentialCount = 0;
            }
        }
        
        // Check for descending sequences
        sequentialCount = 0;
        for (int i = 1; i < alphaNumPart.length(); i++) {
            char current = alphaNumPart.charAt(i);
            char previous = alphaNumPart.charAt(i - 1);
            
            if (current == previous - 1) {
                sequentialCount++;
                if (sequentialCount >= 3) {
                    return true;
                }
            } else {
                sequentialCount = 0;
            }
        }
        
        return false;
    }
    
    /**
     * Check for repetitive patterns like AAAA or 1111
     */
    private boolean hasRepetitivePattern(String value) {
        String alphaNumPart = value.replaceAll("[^A-Z0-9]", "");
        
        if (alphaNumPart.length() < 4) {
            return false;
        }
        
        // Check for same character repeated 4 or more times
        for (int i = 0; i <= alphaNumPart.length() - 4; i++) {
            char c = alphaNumPart.charAt(i);
            boolean isRepetitive = true;
            
            for (int j = 1; j < 4; j++) {
                if (alphaNumPart.charAt(i + j) != c) {
                    isRepetitive = false;
                    break;
                }
            }
            
            if (isRepetitive) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validate timestamp component if present in reference
     */
    private boolean isValidTimestampComponent(String value) {
        // Look for timestamp patterns (YYYYMMDD, YYMMDD, etc.)
        String numericPart = value.replaceAll("[^0-9]", "");
        
        // Check for date patterns that might be invalid
        if (numericPart.length() >= 8) {
            String dateCandidate = numericPart.substring(0, 8);
            try {
                int year = Integer.parseInt(dateCandidate.substring(0, 4));
                int month = Integer.parseInt(dateCandidate.substring(4, 6));
                int day = Integer.parseInt(dateCandidate.substring(6, 8));
                
                // Basic date validation
                if (year < 2020 || year > 2030) return false;
                if (month < 1 || month > 12) return false;
                if (day < 1 || day > 31) return false;
                
                // February validation
                if (month == 2 && day > 29) return false;
                if (month == 2 && day == 29 && !isLeapYear(year)) return false;
                
                // April, June, September, November have 30 days
                if ((month == 4 || month == 6 || month == 9 || month == 11) && day > 30) {
                    return false;
                }
                
            } catch (NumberFormatException e) {
                // If it's not a valid number, that's fine - might not be a date
            }
        }
        
        return true;
    }
    
    /**
     * Check if year is leap year
     */
    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
    
    /**
     * Check if reference has sufficient entropy (randomness)
     */
    private boolean hasSufficientEntropy(String value) {
        String alphaNumPart = value.replaceAll("[^A-Z0-9]", "");
        
        if (alphaNumPart.length() < 8) {
            return true; // Too short to measure entropy effectively
        }
        
        // Count unique characters
        Set<Character> uniqueChars = new HashSet<>();
        for (char c : alphaNumPart.toCharArray()) {
            uniqueChars.add(c);
        }
        
        // Calculate entropy ratio
        double entropyRatio = (double) uniqueChars.size() / alphaNumPart.length();
        
        // Require at least 40% unique characters for sufficient entropy
        return entropyRatio >= 0.4;
    }
    
    /**
     * Add custom violation message
     */
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}