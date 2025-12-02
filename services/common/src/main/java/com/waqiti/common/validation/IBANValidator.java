package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Production-ready IBAN validator with country-specific validation
 */
@Component
public class IBANValidator implements ConstraintValidator<ValidationConstraints.ValidIBAN, String> {
    
    private static final Map<String, IBANCountryFormat> IBAN_FORMATS = new HashMap<>();
    
    static {
        // Initialize IBAN formats for various countries
        IBAN_FORMATS.put("AD", new IBANCountryFormat(24, "AD\\d{2}\\d{4}\\d{4}[A-Z0-9]{12}"));
        IBAN_FORMATS.put("AE", new IBANCountryFormat(23, "AE\\d{2}\\d{3}\\d{16}"));
        IBAN_FORMATS.put("AT", new IBANCountryFormat(20, "AT\\d{2}\\d{5}\\d{11}"));
        IBAN_FORMATS.put("BE", new IBANCountryFormat(16, "BE\\d{2}\\d{3}\\d{7}\\d{2}"));
        IBAN_FORMATS.put("BG", new IBANCountryFormat(22, "BG\\d{2}[A-Z]{4}\\d{6}[A-Z0-9]{8}"));
        IBAN_FORMATS.put("CH", new IBANCountryFormat(21, "CH\\d{2}\\d{5}[A-Z0-9]{12}"));
        IBAN_FORMATS.put("CY", new IBANCountryFormat(28, "CY\\d{2}\\d{3}\\d{5}[A-Z0-9]{16}"));
        IBAN_FORMATS.put("CZ", new IBANCountryFormat(24, "CZ\\d{2}\\d{4}\\d{6}\\d{10}"));
        IBAN_FORMATS.put("DE", new IBANCountryFormat(22, "DE\\d{2}\\d{8}\\d{10}"));
        IBAN_FORMATS.put("DK", new IBANCountryFormat(18, "DK\\d{2}\\d{4}\\d{9}\\d{1}"));
        IBAN_FORMATS.put("EE", new IBANCountryFormat(20, "EE\\d{2}\\d{2}\\d{2}\\d{11}\\d{1}"));
        IBAN_FORMATS.put("ES", new IBANCountryFormat(24, "ES\\d{2}\\d{4}\\d{4}\\d{1}\\d{1}\\d{10}"));
        IBAN_FORMATS.put("FI", new IBANCountryFormat(18, "FI\\d{2}\\d{6}\\d{7}\\d{1}"));
        IBAN_FORMATS.put("FR", new IBANCountryFormat(27, "FR\\d{2}\\d{5}\\d{5}[A-Z0-9]{11}\\d{2}"));
        IBAN_FORMATS.put("GB", new IBANCountryFormat(22, "GB\\d{2}[A-Z]{4}\\d{6}\\d{8}"));
        IBAN_FORMATS.put("GR", new IBANCountryFormat(27, "GR\\d{2}\\d{3}\\d{4}[A-Z0-9]{16}"));
        IBAN_FORMATS.put("HR", new IBANCountryFormat(21, "HR\\d{2}\\d{7}\\d{10}"));
        IBAN_FORMATS.put("HU", new IBANCountryFormat(28, "HU\\d{2}\\d{3}\\d{4}\\d{1}\\d{15}\\d{1}"));
        IBAN_FORMATS.put("IE", new IBANCountryFormat(22, "IE\\d{2}[A-Z]{4}\\d{6}\\d{8}"));
        IBAN_FORMATS.put("IT", new IBANCountryFormat(27, "IT\\d{2}[A-Z]{1}\\d{5}\\d{5}[A-Z0-9]{12}"));
        IBAN_FORMATS.put("LI", new IBANCountryFormat(21, "LI\\d{2}\\d{5}[A-Z0-9]{12}"));
        IBAN_FORMATS.put("LT", new IBANCountryFormat(20, "LT\\d{2}\\d{5}\\d{11}"));
        IBAN_FORMATS.put("LU", new IBANCountryFormat(20, "LU\\d{2}\\d{3}[A-Z0-9]{13}"));
        IBAN_FORMATS.put("LV", new IBANCountryFormat(21, "LV\\d{2}[A-Z]{4}[A-Z0-9]{13}"));
        IBAN_FORMATS.put("MC", new IBANCountryFormat(27, "MC\\d{2}\\d{5}\\d{5}[A-Z0-9]{11}\\d{2}"));
        IBAN_FORMATS.put("MT", new IBANCountryFormat(31, "MT\\d{2}[A-Z]{4}\\d{5}[A-Z0-9]{18}"));
        IBAN_FORMATS.put("NL", new IBANCountryFormat(18, "NL\\d{2}[A-Z]{4}\\d{10}"));
        IBAN_FORMATS.put("NO", new IBANCountryFormat(15, "NO\\d{2}\\d{4}\\d{6}\\d{1}"));
        IBAN_FORMATS.put("PL", new IBANCountryFormat(28, "PL\\d{2}\\d{8}\\d{16}"));
        IBAN_FORMATS.put("PT", new IBANCountryFormat(25, "PT\\d{2}\\d{4}\\d{4}\\d{11}\\d{2}"));
        IBAN_FORMATS.put("RO", new IBANCountryFormat(24, "RO\\d{2}[A-Z]{4}[A-Z0-9]{16}"));
        IBAN_FORMATS.put("SA", new IBANCountryFormat(24, "SA\\d{2}\\d{2}[A-Z0-9]{18}"));
        IBAN_FORMATS.put("SE", new IBANCountryFormat(24, "SE\\d{2}\\d{3}\\d{16}\\d{1}"));
        IBAN_FORMATS.put("SI", new IBANCountryFormat(19, "SI\\d{2}\\d{5}\\d{8}\\d{2}"));
        IBAN_FORMATS.put("SK", new IBANCountryFormat(24, "SK\\d{2}\\d{4}\\d{6}\\d{10}"));
        IBAN_FORMATS.put("TR", new IBANCountryFormat(26, "TR\\d{2}\\d{5}[A-Z0-9]{1}[A-Z0-9]{16}"));
    }
    
    @Override
    public void initialize(ValidationConstraints.ValidIBAN annotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String iban, ConstraintValidatorContext context) {
        if (iban == null || iban.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Remove spaces and convert to uppercase
        String cleanIBAN = iban.replaceAll("\\s", "").toUpperCase();
        
        // Basic length check (min 15, max 34 characters)
        if (cleanIBAN.length() < 15 || cleanIBAN.length() > 34) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("IBAN length must be between 15 and 34 characters")
                .addConstraintViolation();
            return false;
        }
        
        // Check if country code exists
        String countryCode = cleanIBAN.substring(0, 2);
        if (!countryCode.matches("[A-Z]{2}")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid country code in IBAN")
                .addConstraintViolation();
            return false;
        }
        
        // Check country-specific format if known
        IBANCountryFormat format = IBAN_FORMATS.get(countryCode);
        if (format != null) {
            if (cleanIBAN.length() != format.length) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    String.format("IBAN for %s must be %d characters long", countryCode, format.length)
                ).addConstraintViolation();
                return false;
            }
            
            if (!Pattern.matches(format.pattern, cleanIBAN)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    String.format("Invalid IBAN format for %s", countryCode)
                ).addConstraintViolation();
                return false;
            }
        }
        
        // Validate check digits using mod-97 algorithm
        if (!validateCheckDigits(cleanIBAN)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid IBAN check digits")
                .addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean validateCheckDigits(String iban) {
        // Move first 4 characters to the end
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        
        // Replace letters with numbers (A=10, B=11, ..., Z=35)
        StringBuilder numericIBAN = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numericIBAN.append(c);
            } else {
                numericIBAN.append(c - 'A' + 10);
            }
        }
        
        // Calculate mod 97
        BigInteger ibanNumber = new BigInteger(numericIBAN.toString());
        return ibanNumber.mod(BigInteger.valueOf(97)).equals(BigInteger.ONE);
    }
    
    private static class IBANCountryFormat {
        final int length;
        final String pattern;
        
        IBANCountryFormat(int length, String pattern) {
            this.length = length;
            this.pattern = pattern;
        }
    }
}