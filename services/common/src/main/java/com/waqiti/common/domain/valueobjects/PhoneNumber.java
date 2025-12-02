package com.waqiti.common.domain.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PhoneNumber Value Object - Immutable representation of phone numbers
 * Encapsulates phone number validation and formatting rules for African markets
 */
@EqualsAndHashCode
public class PhoneNumber {
    
    // E.164 format pattern
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    
    // Country-specific patterns for major African markets
    private static final Pattern NIGERIA_PATTERN = Pattern.compile("^\\+234[789][01]\\d{8}$");
    private static final Pattern GHANA_PATTERN = Pattern.compile("^\\+233[235]\\d{8}$");
    private static final Pattern KENYA_PATTERN = Pattern.compile("^\\+254[17]\\d{8}$");
    private static final Pattern SOUTH_AFRICA_PATTERN = Pattern.compile("^\\+27[1-8]\\d{8}$");
    
    private final String value;
    
    @JsonCreator
    public PhoneNumber(String phoneNumber) {
        this.value = validateAndNormalize(phoneNumber);
    }
    
    public static PhoneNumber of(String phoneNumber) {
        return new PhoneNumber(phoneNumber);
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public String getCountryCode() {
        if (value.startsWith("+234")) return "NG";
        if (value.startsWith("+233")) return "GH";
        if (value.startsWith("+254")) return "KE";
        if (value.startsWith("+27")) return "ZA";
        if (value.startsWith("+1")) return "US";
        if (value.startsWith("+44")) return "GB";
        
        // Extract country code (1-3 digits after +)
        for (int i = 1; i <= 4 && i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                break;
            }
        }
        
        return "UNKNOWN";
    }
    
    public String getInternationalFormat() {
        return value;
    }
    
    public String getNationalFormat() {
        String countryCode = getCountryCode();
        
        return switch (countryCode) {
            case "NG" -> formatNigerian();
            case "GH" -> formatGhanaian();
            case "KE" -> formatKenyan();
            case "ZA" -> formatSouthAfrican();
            default -> value.substring(1); // Remove + for unknown countries
        };
    }
    
    public String getMaskedValue() {
        if (value.length() <= 7) {
            return "+***" + value.substring(value.length() - 2);
        }
        
        String countryPart = value.substring(0, 4); // +XXX
        String maskedMiddle = "*".repeat(value.length() - 7);
        String lastDigits = value.substring(value.length() - 3);
        
        return countryPart + maskedMiddle + lastDigits;
    }
    
    public PhoneNumber maskForLogging() {
        return PhoneNumber.of(getMaskedValue().replace("*", "0"));
    }
    
    public boolean isNigerian() {
        return NIGERIA_PATTERN.matcher(value).matches();
    }
    
    public boolean isGhanaian() {
        return GHANA_PATTERN.matcher(value).matches();
    }
    
    public boolean isKenyan() {
        return KENYA_PATTERN.matcher(value).matches();
    }
    
    public boolean isSouthAfrican() {
        return SOUTH_AFRICA_PATTERN.matcher(value).matches();
    }
    
    public boolean isAfricanNumber() {
        return isNigerian() || isGhanaian() || isKenyan() || isSouthAfrican();
    }
    
    public boolean isMobile() {
        String countryCode = getCountryCode();
        
        return switch (countryCode) {
            case "NG" -> value.matches("^\\+234[789][01]\\d{8}$");
            case "GH" -> value.matches("^\\+233[235]\\d{8}$");
            case "KE" -> value.matches("^\\+254[17]\\d{8}$");
            case "ZA" -> value.matches("^\\+27[6-8]\\d{8}$");
            default -> true; // Assume mobile for unknown countries
        };
    }
    
    private String formatNigerian() {
        // +234 XXX XXX XXXX -> 0XXX XXX XXXX
        String national = "0" + value.substring(4);
        return national.substring(0, 4) + " " + national.substring(4, 7) + " " + national.substring(7);
    }
    
    private String formatGhanaian() {
        // +233 XX XXX XXXX -> 0XX XXX XXXX
        String national = "0" + value.substring(4);
        return national.substring(0, 3) + " " + national.substring(3, 6) + " " + national.substring(6);
    }
    
    private String formatKenyan() {
        // +254 XXX XXX XXX -> 0XXX XXX XXX
        String national = "0" + value.substring(4);
        return national.substring(0, 4) + " " + national.substring(4, 7) + " " + national.substring(7);
    }
    
    private String formatSouthAfrican() {
        // +27 XX XXX XXXX -> 0XX XXX XXXX
        String national = "0" + value.substring(3);
        return national.substring(0, 3) + " " + national.substring(3, 6) + " " + national.substring(6);
    }
    
    private String validateAndNormalize(String phoneNumber) {
        Objects.requireNonNull(phoneNumber, "Phone number cannot be null");
        
        String cleaned = phoneNumber.trim().replaceAll("[^+0-9]", "");
        
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }
        
        // Convert national format to international
        String normalized = normalizeToInternational(cleaned);
        
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
        }
        
        return normalized;
    }
    
    private String normalizeToInternational(String phoneNumber) {
        // Already in international format
        if (phoneNumber.startsWith("+")) {
            return phoneNumber;
        }
        
        // Convert national formats to international
        if (phoneNumber.startsWith("0")) {
            // This is tricky without knowing the country context
            // For now, assume it's a Nigerian number (most common in Waqiti)
            if (phoneNumber.length() == 11 && phoneNumber.matches("^0[789][01]\\d{8}$")) {
                return "+234" + phoneNumber.substring(1);
            }
        }
        
        // If no country code, assume it needs +234 (Nigeria)
        if (phoneNumber.length() == 10 && phoneNumber.matches("^[789][01]\\d{8}$")) {
            return "+234" + phoneNumber;
        }
        
        // If it's already digits with country code but no +
        if (phoneNumber.length() >= 10 && !phoneNumber.startsWith("+")) {
            return "+" + phoneNumber;
        }
        
        throw new IllegalArgumentException("Cannot normalize phone number to international format: " + phoneNumber);
    }
    
    @Override
    public String toString() {
        return value;
    }
}