package com.waqiti.payment.businessprofile.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BusinessValidator {

    // US Tax ID patterns (EIN)
    private static final Pattern US_EIN_PATTERN = Pattern.compile("^\\d{2}-\\d{7}$");
    private static final Pattern US_SSN_PATTERN = Pattern.compile("^\\d{3}-\\d{2}-\\d{4}$");
    
    // UK patterns
    private static final Pattern UK_VAT_PATTERN = Pattern.compile("^GB\\d{9}$|^GB\\d{12}$");
    private static final Pattern UK_COMPANY_PATTERN = Pattern.compile("^\\d{8}$");
    
    // Canada patterns
    private static final Pattern CA_BN_PATTERN = Pattern.compile("^\\d{9}RT\\d{4}$");
    
    // EU VAT patterns
    private static final Map<String, Pattern> EU_VAT_PATTERNS = Map.of(
        "DE", Pattern.compile("^DE\\d{9}$"),
        "FR", Pattern.compile("^FR[A-Z0-9]{2}\\d{9}$"),
        "IT", Pattern.compile("^IT\\d{11}$"),
        "ES", Pattern.compile("^ES[A-Z]\\d{7}[A-Z]$|^ES\\d{8}[A-Z]$"),
        "NL", Pattern.compile("^NL\\d{9}B\\d{2}$")
    );

    private static final Set<String> VALID_BUSINESS_TYPES = Set.of(
        "SOLE_PROPRIETORSHIP",
        "PARTNERSHIP", 
        "LIMITED_LIABILITY_COMPANY",
        "CORPORATION",
        "S_CORPORATION",
        "NON_PROFIT",
        "COOPERATIVE",
        "LIMITED_PARTNERSHIP",
        "PUBLIC_LIMITED_COMPANY",
        "PRIVATE_LIMITED_COMPANY"
    );

    public boolean isValidTaxId(String taxId, String country) {
        if (taxId == null || taxId.trim().isEmpty()) {
            return false;
        }
        
        taxId = taxId.trim().replace(" ", "");
        
        try {
            switch (country.toUpperCase()) {
                case "US":
                case "USA":
                    return isValidUSTaxId(taxId);
                case "UK":
                case "GB":
                    return isValidUKVatNumber(taxId);
                case "CA":
                case "CANADA":
                    return isValidCanadaBusinessNumber(taxId);
                default:
                    // Check if it's an EU country
                    if (EU_VAT_PATTERNS.containsKey(country.toUpperCase())) {
                        return EU_VAT_PATTERNS.get(country.toUpperCase()).matcher(taxId).matches();
                    }
                    // For other countries, basic validation
                    return taxId.length() >= 6 && taxId.length() <= 20 && taxId.matches("^[A-Z0-9-]+$");
            }
        } catch (Exception e) {
            log.error("Error validating tax ID: {}", taxId, e);
            return false;
        }
    }

    public boolean isValidRegistrationNumber(String registrationNumber, String country) {
        if (registrationNumber == null || registrationNumber.trim().isEmpty()) {
            return false;
        }
        
        registrationNumber = registrationNumber.trim().replace(" ", "");
        
        try {
            switch (country.toUpperCase()) {
                case "US":
                case "USA":
                    // State registration numbers vary, basic validation
                    return registrationNumber.matches("^[A-Z0-9-]{6,20}$");
                case "UK":
                case "GB":
                    return UK_COMPANY_PATTERN.matcher(registrationNumber).matches();
                case "CA":
                case "CANADA":
                    // Canadian corporation numbers
                    return registrationNumber.matches("^\\d{6,9}$");
                default:
                    // General validation for other countries
                    return registrationNumber.length() >= 4 && 
                           registrationNumber.length() <= 25 && 
                           registrationNumber.matches("^[A-Z0-9-/]+$");
            }
        } catch (Exception e) {
            log.error("Error validating registration number: {}", registrationNumber, e);
            return false;
        }
    }

    public boolean isValidBusinessType(String businessType) {
        if (businessType == null || businessType.trim().isEmpty()) {
            return false;
        }
        return VALID_BUSINESS_TYPES.contains(businessType.toUpperCase());
    }

    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }

    public boolean isValidPhoneNumber(String phoneNumber, String country) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        // Remove all non-digits
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        
        switch (country.toUpperCase()) {
            case "US":
            case "USA":
            case "CA":
            case "CANADA":
                // North American Numbering Plan (10 digits)
                return digits.length() == 10 || (digits.length() == 11 && digits.startsWith("1"));
            case "UK":
            case "GB":
                // UK phone numbers (10-11 digits)
                return digits.length() >= 10 && digits.length() <= 11;
            default:
                // International validation (7-15 digits according to ITU-T E.164)
                return digits.length() >= 7 && digits.length() <= 15;
        }
    }

    public boolean isValidWebsite(String website) {
        if (website == null || website.trim().isEmpty()) {
            return true; // Website is optional
        }
        
        String urlPattern = "^(https?://)?(www\\.)?[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]*\\.[a-zA-Z]{2,}(/.*)?$";
        return website.matches(urlPattern);
    }

    public boolean isValidIndustry(String industry) {
        if (industry == null || industry.trim().isEmpty()) {
            return false;
        }
        
        // Using NAICS industry codes validation
        Set<String> validIndustries = Set.of(
            "AGRICULTURE", "MINING", "UTILITIES", "CONSTRUCTION", "MANUFACTURING",
            "WHOLESALE_TRADE", "RETAIL_TRADE", "TRANSPORTATION", "INFORMATION",
            "FINANCE", "REAL_ESTATE", "PROFESSIONAL_SERVICES", "MANAGEMENT",
            "ADMINISTRATIVE", "EDUCATIONAL", "HEALTHCARE", "ARTS", "ACCOMMODATION",
            "OTHER_SERVICES", "PUBLIC_ADMINISTRATION", "TECHNOLOGY", "E_COMMERCE"
        );
        
        return validIndustries.contains(industry.toUpperCase());
    }

    private boolean isValidUSTaxId(String taxId) {
        // EIN format: XX-XXXXXXX
        if (US_EIN_PATTERN.matcher(taxId).matches()) {
            return true;
        }
        // SSN format for sole proprietorships: XXX-XX-XXXX
        if (US_SSN_PATTERN.matcher(taxId).matches()) {
            return true;
        }
        // ITIN format: 9XX-XX-XXXX
        if (taxId.matches("^9\\d{2}-\\d{2}-\\d{4}$")) {
            return true;
        }
        return false;
    }

    private boolean isValidUKVatNumber(String vatNumber) {
        return UK_VAT_PATTERN.matcher(vatNumber).matches();
    }

    private boolean isValidCanadaBusinessNumber(String businessNumber) {
        return CA_BN_PATTERN.matcher(businessNumber).matches();
    }

    public ValidationResult validateComprehensive(String field, Object value, String country) {
        ValidationResult.ValidationResultBuilder builder = ValidationResult.builder();
        
        switch (field.toLowerCase()) {
            case "taxid":
                boolean validTaxId = isValidTaxId((String) value, country);
                builder.valid(validTaxId);
                if (!validTaxId) {
                    builder.errorMessage("Invalid tax ID format for country: " + country);
                    builder.suggestion("Please provide a valid tax ID (e.g., XX-XXXXXXX for US EIN)");
                }
                break;
                
            case "registrationnumber":
                boolean validRegNum = isValidRegistrationNumber((String) value, country);
                builder.valid(validRegNum);
                if (!validRegNum) {
                    builder.errorMessage("Invalid registration number format");
                    builder.suggestion("Please provide a valid business registration number");
                }
                break;
                
            case "email":
                boolean validEmail = isValidEmail((String) value);
                builder.valid(validEmail);
                if (!validEmail) {
                    builder.errorMessage("Invalid email format");
                    builder.suggestion("Please provide a valid email address");
                }
                break;
                
            case "phone":
                boolean validPhone = isValidPhoneNumber((String) value, country);
                builder.valid(validPhone);
                if (!validPhone) {
                    builder.errorMessage("Invalid phone number format");
                    builder.suggestion("Please provide a valid phone number for " + country);
                }
                break;
                
            case "website":
                boolean validWebsite = isValidWebsite((String) value);
                builder.valid(validWebsite);
                if (!validWebsite) {
                    builder.errorMessage("Invalid website URL");
                    builder.suggestion("Please provide a valid website URL (e.g., https://example.com)");
                }
                break;
                
            default:
                builder.valid(false).errorMessage("Unknown field: " + field);
        }
        
        return builder.build();
    }
}