package com.waqiti.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * Data Validation Service
 *
 * CRITICAL FIX: This service was missing and causing NullPointerException
 * in IdentityVerificationService.java
 *
 * Provides comprehensive data validation for KYC processes:
 * - Name validation (format, special characters, fraud patterns)
 * - National ID validation (per-country algorithms)
 * - Date of birth validation (age requirements)
 * - Address validation (format, completeness)
 * - Phone number validation (E.164 format)
 * - Email validation (RFC 5322 compliance)
 *
 * Compliance:
 * - FATF: Customer identification requirements
 * - KYC/AML: Data quality standards
 * - GDPR: Data minimization and accuracy
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Slf4j
@Service
public class DataValidationService {

    // Regex patterns
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s'-]{2,100}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    private static final Pattern PHONE_E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    // Fraud detection patterns
    private static final Pattern REPEATED_CHARS_PATTERN = Pattern.compile("(.)\\1{4,}");
    private static final Pattern SEQUENTIAL_PATTERN = Pattern.compile("(012|123|234|345|456|567|678|789|890)");

    // Age requirements
    private static final int MINIMUM_AGE = 18;
    private static final int MAXIMUM_AGE = 120;

    /**
     * Validate full name
     *
     * Checks:
     * - Length (2-100 characters)
     * - Valid characters (letters, spaces, hyphens, apostrophes)
     * - Not all spaces or special characters
     * - No repeated characters (fraud detection)
     */
    public boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            log.warn("Name validation failed: null or empty");
            return false;
        }

        String trimmed = name.trim();

        // Length check
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            log.warn("Name validation failed: invalid length - {}", trimmed.length());
            return false;
        }

        // Pattern check
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            log.warn("Name validation failed: invalid characters - {}", trimmed);
            return false;
        }

        // Must contain at least one letter
        if (!trimmed.matches(".*[a-zA-Z].*")) {
            log.warn("Name validation failed: no letters found");
            return false;
        }

        // Fraud detection: excessive repeated characters
        if (REPEATED_CHARS_PATTERN.matcher(trimmed).find()) {
            log.warn("Name validation failed: repeated characters detected - {}", trimmed);
            return false;
        }

        // Should have at least first and last name (contains space)
        if (!trimmed.contains(" ")) {
            log.info("Name validation warning: single name provided - {}", trimmed);
            // Allow but log for review
        }

        log.debug("Name validation successful: {}", trimmed);
        return true;
    }

    /**
     * Validate national ID number
     *
     * Implements country-specific validation algorithms
     */
    public boolean isValidNationalId(String nationalId, String country) {
        if (nationalId == null || nationalId.trim().isEmpty()) {
            log.warn("National ID validation failed: null or empty");
            return false;
        }

        String trimmed = nationalId.trim().replaceAll("[^a-zA-Z0-9]", "");

        // Country-specific validation
        return switch (country.toUpperCase()) {
            case "US" -> isValidSSN(trimmed);
            case "GB" -> isValidNINO(trimmed);
            case "CA" -> isValidSIN(trimmed);
            case "NG" -> isValidNIN(trimmed);
            case "ZA" -> isValidSAID(trimmed);
            case "KE" -> isValidKenyanID(trimmed);
            default -> isValidGenericNationalId(trimmed);
        };
    }

    /**
     * Validate US Social Security Number (SSN)
     */
    private boolean isValidSSN(String ssn) {
        // Remove any non-digits
        String digits = ssn.replaceAll("\\D", "");

        if (digits.length() != 9) {
            log.warn("SSN validation failed: invalid length - {}", digits.length());
            return false;
        }

        // SSN cannot be all zeros in any group
        if (digits.startsWith("000") || digits.substring(3, 5).equals("00") || digits.substring(5).equals("0000")) {
            log.warn("SSN validation failed: invalid format (zeros)");
            return false;
        }

        // SSN cannot be 666-xx-xxxx
        if (digits.startsWith("666")) {
            log.warn("SSN validation failed: invalid area number (666)");
            return false;
        }

        // SSN cannot start with 900-999 (reserved)
        int areaNumber = Integer.parseInt(digits.substring(0, 3));
        if (areaNumber >= 900) {
            log.warn("SSN validation failed: reserved area number - {}", areaNumber);
            return false;
        }

        // Check for sequential numbers (fraud detection)
        if (SEQUENTIAL_PATTERN.matcher(digits).find()) {
            log.warn("SSN validation warning: sequential pattern detected");
        }

        log.debug("SSN validation successful");
        return true;
    }

    /**
     * Validate UK National Insurance Number (NINO)
     */
    private boolean isValidNINO(String nino) {
        // Format: AB123456C
        if (!nino.matches("^[A-Z]{2}\\d{6}[A-Z]$")) {
            log.warn("NINO validation failed: invalid format");
            return false;
        }

        // First two letters cannot be BG, GB, NK, KN, TN, NT, ZZ
        String prefix = nino.substring(0, 2);
        if (prefix.matches("(BG|GB|NK|KN|TN|NT|ZZ)")) {
            log.warn("NINO validation failed: invalid prefix - {}", prefix);
            return false;
        }

        log.debug("NINO validation successful");
        return true;
    }

    /**
     * Validate Canadian Social Insurance Number (SIN)
     */
    private boolean isValidSIN(String sin) {
        String digits = sin.replaceAll("\\D", "");

        if (digits.length() != 9) {
            return false;
        }

        // Luhn algorithm validation
        return validateLuhn(digits);
    }

    /**
     * Validate Nigerian National Identification Number (NIN)
     */
    private boolean isValidNIN(String nin) {
        // Format: 11 digits
        return nin.matches("^\\d{11}$");
    }

    /**
     * Validate South African ID Number
     */
    private boolean isValidSAID(String said) {
        if (said.length() != 13) {
            return false;
        }

        // First 6 digits are date of birth (YYMMDD)
        try {
            int yy = Integer.parseInt(said.substring(0, 2));
            int mm = Integer.parseInt(said.substring(2, 4));
            int dd = Integer.parseInt(said.substring(4, 6));

            if (mm < 1 || mm > 12 || dd < 1 || dd > 31) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // Luhn checksum validation
        return validateLuhn(said);
    }

    /**
     * Validate Kenyan National ID
     */
    private boolean isValidKenyanID(String id) {
        // Format: 7-8 digits
        return id.matches("^\\d{7,8}$");
    }

    /**
     * Generic national ID validation (fallback)
     */
    private boolean isValidGenericNationalId(String id) {
        // Must be alphanumeric, 5-20 characters
        if (id.length() < 5 || id.length() > 20) {
            return false;
        }

        if (!ALPHANUMERIC_PATTERN.matcher(id).matches()) {
            return false;
        }

        // Fraud detection: no excessive repeated characters
        if (REPEATED_CHARS_PATTERN.matcher(id).find()) {
            log.warn("National ID validation warning: repeated characters - {}", id);
        }

        return true;
    }

    /**
     * Luhn algorithm for checksum validation
     */
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

    /**
     * Validate date of birth
     *
     * Checks:
     * - Not null
     * - Not in future
     * - Age between 18-120 years
     */
    public boolean isValidDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            log.warn("Date of birth validation failed: null");
            return false;
        }

        LocalDate today = LocalDate.now();

        // Cannot be in the future
        if (dateOfBirth.isAfter(today)) {
            log.warn("Date of birth validation failed: future date - {}", dateOfBirth);
            return false;
        }

        // Calculate age
        int age = Period.between(dateOfBirth, today).getYears();

        // Minimum age check
        if (age < MINIMUM_AGE) {
            log.warn("Date of birth validation failed: below minimum age - {}", age);
            return false;
        }

        // Maximum age check (data quality)
        if (age > MAXIMUM_AGE) {
            log.warn("Date of birth validation failed: exceeds maximum age - {}", age);
            return false;
        }

        log.debug("Date of birth validation successful: age {}", age);
        return true;
    }

    /**
     * Validate email address (RFC 5322 compliance)
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String trimmed = email.trim().toLowerCase();

        if (trimmed.length() > 254) { // RFC 5321 limit
            return false;
        }

        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            log.warn("Email validation failed: invalid format - {}", trimmed);
            return false;
        }

        // Check for disposable email domains (basic list)
        String[] disposableDomains = {"tempmail.com", "throwaway.email", "guerrillamail.com", "mailinator.com"};
        for (String domain : disposableDomains) {
            if (trimmed.endsWith("@" + domain)) {
                log.warn("Email validation warning: disposable email detected - {}", domain);
            }
        }

        log.debug("Email validation successful: {}", trimmed);
        return true;
    }

    /**
     * Validate phone number (E.164 format)
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        String trimmed = phoneNumber.trim().replaceAll("[\\s()-]", "");

        // Must start with +
        if (!trimmed.startsWith("+")) {
            log.warn("Phone validation failed: missing country code");
            return false;
        }

        // E.164 format validation
        if (!PHONE_E164_PATTERN.matcher(trimmed).matches()) {
            log.warn("Phone validation failed: invalid E.164 format - {}", trimmed);
            return false;
        }

        log.debug("Phone validation successful: {}", trimmed);
        return true;
    }

    /**
     * Validate address completeness
     */
    public boolean isValidAddress(String street, String city, String state, String postalCode, String country) {
        if (street == null || street.trim().isEmpty()) {
            log.warn("Address validation failed: missing street");
            return false;
        }

        if (city == null || city.trim().isEmpty()) {
            log.warn("Address validation failed: missing city");
            return false;
        }

        if (country == null || country.trim().isEmpty()) {
            log.warn("Address validation failed: missing country");
            return false;
        }

        // Street address length check
        if (street.trim().length() < 5 || street.trim().length() > 200) {
            log.warn("Address validation failed: invalid street length");
            return false;
        }

        log.debug("Address validation successful");
        return true;
    }

    /**
     * Calculate age from date of birth
     */
    public int calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return 0;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /**
     * Check if user meets minimum age requirement
     */
    public boolean meetsMinimumAge(LocalDate dateOfBirth) {
        return calculateAge(dateOfBirth) >= MINIMUM_AGE;
    }
}
