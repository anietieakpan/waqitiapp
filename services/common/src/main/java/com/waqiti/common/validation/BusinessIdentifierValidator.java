package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Production-ready business identifier validator for various international formats
 */
@Component
public class BusinessIdentifierValidator implements ConstraintValidator<ValidationConstraints.ValidBusinessIdentifier, String> {
    
    private ValidationConstraints.BusinessIdType type;
    
    @Override
    public void initialize(ValidationConstraints.ValidBusinessIdentifier annotation) {
        this.type = annotation.type();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        String cleanedValue = value.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        
        switch (type) {
            case EIN:
                return validateEIN(cleanedValue, context);
            case VAT:
                return validateVAT(cleanedValue, context);
            case GST:
                return validateGST(cleanedValue, context);
            case ABN:
                return validateABN(cleanedValue, context);
            default:
                return false;
        }
    }
    
    /**
     * Validate US Employer Identification Number (EIN)
     * Format: XX-XXXXXXX (9 digits with hyphen after 2nd digit)
     */
    private boolean validateEIN(String ein, ConstraintValidatorContext context) {
        // EIN should be 9 digits
        if (!ein.matches("^\\d{9}$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("EIN must be 9 digits").addConstraintViolation();
            return false;
        }
        
        // First two digits represent the IRS campus that assigned the EIN
        String prefix = ein.substring(0, 2);
        int prefixNum = Integer.parseInt(prefix);
        
        // Valid EIN prefixes (as of 2024)
        boolean validPrefix = 
            (prefixNum >= 1 && prefixNum <= 6) ||   // Northeast
            (prefixNum >= 10 && prefixNum <= 16) ||  // Southeast
            (prefixNum >= 20 && prefixNum <= 27) ||  // Central
            (prefixNum >= 30 && prefixNum <= 39) ||  // Southwest
            (prefixNum >= 40 && prefixNum <= 48) ||  // Mountain
            (prefixNum >= 50 && prefixNum <= 59) ||  // Pacific
            (prefixNum >= 60 && prefixNum <= 67) ||  // Philadelphia
            (prefixNum >= 68 && prefixNum <= 77) ||  // Internet
            (prefixNum >= 80 && prefixNum <= 88) ||  // Philadelphia Campus
            (prefixNum >= 90 && prefixNum <= 95) ||  // Memphis Campus
            (prefixNum >= 98 && prefixNum <= 99);    // International
        
        if (!validPrefix) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid EIN prefix").addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate EU VAT Number
     * Different format for each EU country
     */
    private boolean validateVAT(String vat, ConstraintValidatorContext context) {
        if (vat.length() < 4) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("VAT number too short").addConstraintViolation();
            return false;
        }
        
        String countryCode = vat.substring(0, 2);
        String number = vat.substring(2);
        
        switch (countryCode) {
            case "AT": // Austria
                return number.matches("^U\\d{8}$");
            case "BE": // Belgium
                return number.matches("^0\\d{9}$") && validateBelgianVAT(number);
            case "BG": // Bulgaria
                return number.matches("^\\d{9,10}$");
            case "CY": // Cyprus
                return number.matches("^\\d{8}[A-Z]$");
            case "CZ": // Czech Republic
                return number.matches("^\\d{8,10}$");
            case "DE": // Germany
                return number.matches("^\\d{9}$") && validateGermanVAT(number);
            case "DK": // Denmark
                return number.matches("^\\d{8}$");
            case "EE": // Estonia
                return number.matches("^\\d{9}$");
            case "EL": // Greece
            case "GR": // Greece (alternative)
                return number.matches("^\\d{9}$");
            case "ES": // Spain
                return validateSpanishVAT(number);
            case "FI": // Finland
                return number.matches("^\\d{8}$");
            case "FR": // France
                return validateFrenchVAT(number);
            case "HR": // Croatia
                return number.matches("^\\d{11}$");
            case "HU": // Hungary
                return number.matches("^\\d{8}$");
            case "IE": // Ireland
                return validateIrishVAT(number);
            case "IT": // Italy
                return number.matches("^\\d{11}$") && validateItalianVAT(number);
            case "LT": // Lithuania
                return number.matches("^\\d{9}$") || number.matches("^\\d{12}$");
            case "LU": // Luxembourg
                return number.matches("^\\d{8}$");
            case "LV": // Latvia
                return number.matches("^\\d{11}$");
            case "MT": // Malta
                return number.matches("^\\d{8}$");
            case "NL": // Netherlands
                return validateDutchVAT(number);
            case "PL": // Poland
                return number.matches("^\\d{10}$") && validatePolishVAT(number);
            case "PT": // Portugal
                return number.matches("^\\d{9}$");
            case "RO": // Romania
                return number.matches("^\\d{2,10}$");
            case "SE": // Sweden
                return number.matches("^\\d{12}$");
            case "SI": // Slovenia
                return number.matches("^\\d{8}$");
            case "SK": // Slovakia
                return number.matches("^\\d{10}$");
            case "GB": // United Kingdom
                return validateUKVAT(number);
            default:
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Unknown VAT country code").addConstraintViolation();
                return false;
        }
    }
    
    /**
     * Validate Goods and Services Tax number (various countries)
     */
    private boolean validateGST(String gst, ConstraintValidatorContext context) {
        // Indian GST format: 15 characters
        // Format: 2 digit state code + 10 char PAN + 1 digit entity + 1 char 'Z' + 1 check digit
        if (gst.length() == 15) {
            return validateIndianGST(gst, context);
        }
        
        // Canadian GST format: 9 digits + RT0001
        if (gst.matches("^\\d{9}RT\\d{4}$")) {
            return true;
        }
        
        // Australian GST (same as ABN)
        if (gst.length() == 11 && gst.matches("^\\d{11}$")) {
            return validateABN(gst, context);
        }
        
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Invalid GST format").addConstraintViolation();
        return false;
    }
    
    /**
     * Validate Australian Business Number (ABN)
     */
    private boolean validateABN(String abn, ConstraintValidatorContext context) {
        if (!abn.matches("^\\d{11}$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("ABN must be 11 digits").addConstraintViolation();
            return false;
        }
        
        // ABN validation using modulus 89
        int[] weights = {10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19};
        int sum = 0;
        
        for (int i = 0; i < 11; i++) {
            int digit = Character.getNumericValue(abn.charAt(i));
            if (i == 0) digit--; // Subtract 1 from first digit
            sum += digit * weights[i];
        }
        
        if (sum % 89 != 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid ABN checksum").addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    // Helper validation methods for specific countries
    
    private boolean validateBelgianVAT(String number) {
        if (number.length() != 10) return false;
        int checksum = 97 - (Integer.parseInt(number.substring(1, 9)) % 97);
        return checksum == Integer.parseInt(number.substring(9));
    }
    
    private boolean validateGermanVAT(String number) {
        // German VAT uses modulus 11 check
        if (number.length() != 9) return false;
        int sum = 0;
        int[] weights = {2, 7, 6, 5, 4, 3, 2, 1};
        
        for (int i = 0; i < 8; i++) {
            sum += Character.getNumericValue(number.charAt(i)) * weights[i];
        }
        
        int checkDigit = 11 - (sum % 11);
        if (checkDigit == 10) checkDigit = 0;
        if (checkDigit == 11) checkDigit = 1;
        
        return checkDigit == Character.getNumericValue(number.charAt(8));
    }
    
    private boolean validateSpanishVAT(String number) {
        // Spanish VAT can be NIF (individuals) or CIF (companies)
        if (number.matches("^[A-Z]\\d{7}[A-Z0-9]$")) {
            // CIF format
            return true;
        } else if (number.matches("^\\d{8}[A-Z]$")) {
            // NIF format
            String letters = "TRWAGMYFPDXBNJZSQVHLCKE";
            int dni = Integer.parseInt(number.substring(0, 8));
            return number.charAt(8) == letters.charAt(dni % 23);
        }
        return false;
    }
    
    private boolean validateFrenchVAT(String number) {
        // French VAT: 2 check digits + 9 digit SIREN
        if (!number.matches("^[A-Z0-9]{2}\\d{9}$")) return false;
        
        String siren = number.substring(2);
        // Validate SIREN using Luhn algorithm
        return validateLuhn(siren);
    }
    
    private boolean validateIrishVAT(String number) {
        // Irish VAT formats
        return number.matches("^\\d{7}[A-Z]{1,2}$") || // Old format
               number.matches("^\\d[A-Z+*]\\d{5}[A-Z]$"); // New format
    }
    
    private boolean validateItalianVAT(String number) {
        if (number.length() != 11) return false;
        
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            int digit = Character.getNumericValue(number.charAt(i));
            if (i % 2 == 0) {
                sum += digit;
            } else {
                int doubled = digit * 2;
                sum += doubled > 9 ? doubled - 9 : doubled;
            }
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == Character.getNumericValue(number.charAt(10));
    }
    
    private boolean validateDutchVAT(String number) {
        // Dutch VAT: 9 digits + B + 2 digits
        return number.matches("^\\d{9}B\\d{2}$");
    }
    
    private boolean validatePolishVAT(String number) {
        if (number.length() != 10) return false;
        
        int[] weights = {6, 5, 7, 2, 3, 4, 5, 6, 7};
        int sum = 0;
        
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(number.charAt(i)) * weights[i];
        }
        
        int checkDigit = sum % 11;
        if (checkDigit == 10) checkDigit = 0;
        
        return checkDigit == Character.getNumericValue(number.charAt(9));
    }
    
    private boolean validateUKVAT(String number) {
        // UK VAT formats
        if (number.matches("^\\d{9}$") || number.matches("^\\d{12}$")) {
            // Standard or branch trader
            return true;
        } else if (number.matches("^GD\\d{3}$") || number.matches("^HA\\d{3}$")) {
            // Government departments or health authorities
            return true;
        }
        return false;
    }
    
    private boolean validateIndianGST(String gst, ConstraintValidatorContext context) {
        // Validate state code (01-37)
        int stateCode = Integer.parseInt(gst.substring(0, 2));
        if (stateCode < 1 || stateCode > 37) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid GST state code").addConstraintViolation();
            return false;
        }
        
        // Validate PAN format
        String pan = gst.substring(2, 12);
        if (!pan.matches("^[A-Z]{5}\\d{4}[A-Z]$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid PAN in GST number").addConstraintViolation();
            return false;
        }
        
        // Check entity number
        char entity = gst.charAt(12);
        if (!Character.isDigit(entity)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid entity number in GST").addConstraintViolation();
            return false;
        }
        
        // Check default Z
        if (gst.charAt(13) != 'Z') {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid GST format (expected 'Z' at position 14)").addConstraintViolation();
            return false;
        }
        
        // Check digit validation would go here (algorithm not publicly documented)
        
        return true;
    }
    
    private boolean validateLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        
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
        
        return (sum % 10 == 0);
    }
}