package com.waqiti.common.domain.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Email Value Object - Immutable representation of email addresses
 * Encapsulates email validation and normalization rules
 */
@EqualsAndHashCode
public class Email {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final int MAX_LENGTH = 320; // RFC 5321 limit
    private static final int MAX_LOCAL_LENGTH = 64; // RFC 5321 limit
    private static final int MAX_DOMAIN_LENGTH = 253; // RFC 5321 limit
    
    private final String value;
    
    @JsonCreator
    public Email(String email) {
        this.value = validateAndNormalize(email);
    }
    
    public static Email of(String email) {
        return new Email(email);
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public String getLocalPart() {
        return value.substring(0, value.indexOf('@'));
    }
    
    public String getDomainPart() {
        return value.substring(value.indexOf('@') + 1);
    }
    
    public boolean isDomainMatch(String domain) {
        return getDomainPart().equalsIgnoreCase(domain);
    }
    
    public boolean isFromDomain(String domain) {
        return getDomainPart().endsWith(domain.toLowerCase());
    }
    
    public Email maskForLogging() {
        String local = getLocalPart();
        String domain = getDomainPart();
        
        if (local.length() <= 2) {
            return new Email("**@" + domain);
        }
        
        String maskedLocal = local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1);
        return new Email(maskedLocal + "@" + domain);
    }
    
    private String validateAndNormalize(String email) {
        Objects.requireNonNull(email, "Email cannot be null");
        
        String trimmed = email.trim();
        
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Email exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + trimmed);
        }
        
        // Additional validation
        validateParts(trimmed);
        
        // Normalize to lowercase
        return trimmed.toLowerCase();
    }
    
    private void validateParts(String email) {
        int atIndex = email.indexOf('@');
        
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            throw new IllegalArgumentException("Invalid email format: missing local or domain part");
        }
        
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);
        
        if (localPart.length() > MAX_LOCAL_LENGTH) {
            throw new IllegalArgumentException("Email local part exceeds maximum length of " + MAX_LOCAL_LENGTH);
        }
        
        if (domainPart.length() > MAX_DOMAIN_LENGTH) {
            throw new IllegalArgumentException("Email domain part exceeds maximum length of " + MAX_DOMAIN_LENGTH);
        }
        
        // Check for consecutive dots
        if (localPart.contains("..") || domainPart.contains("..")) {
            throw new IllegalArgumentException("Email cannot contain consecutive dots");
        }
        
        // Check for leading/trailing dots in local part
        if (localPart.startsWith(".") || localPart.endsWith(".")) {
            throw new IllegalArgumentException("Email local part cannot start or end with a dot");
        }
    }
    
    @Override
    public String toString() {
        return value;
    }
}