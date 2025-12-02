package com.waqiti.common.domain.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;

import java.util.Objects;
import java.util.regex.Pattern;
import java.security.SecureRandom;

/**
 * AccountNumber Value Object - Immutable representation of account numbers
 * Encapsulates account number validation and formatting rules
 */
@EqualsAndHashCode
public class AccountNumber {
    
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{10}$");
    private static final int ACCOUNT_NUMBER_LENGTH = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final String value;
    
    @JsonCreator
    public AccountNumber(String accountNumber) {
        this.value = validateAndNormalize(accountNumber);
    }
    
    public static AccountNumber of(String accountNumber) {
        return new AccountNumber(accountNumber);
    }
    
    public static AccountNumber generate() {
        // Generate a valid 10-digit account number
        // First digit cannot be 0, remaining 9 digits are random
        StringBuilder sb = new StringBuilder();
        sb.append(SECURE_RANDOM.nextInt(9) + 1); // First digit 1-9
        
        for (int i = 0; i < 9; i++) {
            sb.append(SECURE_RANDOM.nextInt(10)); // Remaining digits 0-9
        }
        
        return new AccountNumber(sb.toString());
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public String getFormattedValue() {
        // Format as XXXX-XXXX-XX for display
        return value.substring(0, 4) + "-" + value.substring(4, 8) + "-" + value.substring(8);
    }
    
    public String getMaskedValue() {
        // Mask middle digits for security: XXXX****XX
        return value.substring(0, 4) + "****" + value.substring(8);
    }
    
    public AccountNumber maskForLogging() {
        return new AccountNumber(getMaskedValue().replace("*", "0"));
    }
    
    public boolean isValid() {
        return ACCOUNT_NUMBER_PATTERN.matcher(value).matches() && passesLuhnCheck();
    }
    
    public String getBankCode() {
        // First 3 digits represent bank code in this implementation
        return value.substring(0, 3);
    }
    
    public String getBranchCode() {
        // Next 3 digits represent branch code
        return value.substring(3, 6);
    }
    
    public String getAccountSequence() {
        // Last 4 digits represent account sequence
        return value.substring(6);
    }
    
    private String validateAndNormalize(String accountNumber) {
        Objects.requireNonNull(accountNumber, "Account number cannot be null");
        
        String trimmed = accountNumber.trim().replaceAll("[^0-9]", ""); // Remove non-digits
        
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        
        if (trimmed.length() != ACCOUNT_NUMBER_LENGTH) {
            throw new IllegalArgumentException("Account number must be exactly " + ACCOUNT_NUMBER_LENGTH + " digits");
        }
        
        if (!ACCOUNT_NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Account number must contain only digits");
        }
        
        return trimmed;
    }
    
    private boolean passesLuhnCheck() {
        // Simplified Luhn algorithm check for account number validation
        int sum = 0;
        boolean alternate = false;
        
        for (int i = value.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(value.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return sum % 10 == 0;
    }
    
    @Override
    public String toString() {
        return value;
    }
}