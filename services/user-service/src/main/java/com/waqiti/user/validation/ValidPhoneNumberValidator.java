package com.waqiti.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * ValidPhoneNumber Validator Implementation
 *
 * Validates phone numbers in E.164 international format
 */
@Slf4j
public class ValidPhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    // E.164 format: +[1-3 digit country code][4-14 digit number]
    // Total length: 10-15 characters (including +)
    private static final Pattern E164_PATTERN =
        Pattern.compile("^\\+[1-9]\\d{9,14}$");

    // Common country code prefixes for additional validation
    private static final Pattern[] KNOWN_COUNTRY_CODES = {
        Pattern.compile("^\\+1\\d{10}$"),        // US/Canada (11 digits total)
        Pattern.compile("^\\+44\\d{10}$"),       // UK
        Pattern.compile("^\\+86\\d{11}$"),       // China
        Pattern.compile("^\\+91\\d{10}$"),       // India
        Pattern.compile("^\\+81\\d{10}$"),       // Japan
        Pattern.compile("^\\+49\\d{10,11}$"),    // Germany
        Pattern.compile("^\\+33\\d{9}$"),        // France
        Pattern.compile("^\\+234\\d{10}$"),      // Nigeria
        Pattern.compile("^\\+971\\d{9}$"),       // UAE
        Pattern.compile("^\\+966\\d{9}$"),       // Saudi Arabia
    };

    private boolean allowNull;

    @Override
    public void initialize(ValidPhoneNumber constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        // Handle null
        if (phoneNumber == null) {
            return allowNull;
        }

        // Handle empty string
        if (phoneNumber.isEmpty()) {
            setCustomMessage(context, "Phone number cannot be empty");
            return false;
        }

        // Trim whitespace
        phoneNumber = phoneNumber.trim();

        // Check basic E.164 format
        if (!E164_PATTERN.matcher(phoneNumber).matches()) {
            setCustomMessage(context,
                "Invalid phone number format. Must be E.164 format: +[country code][number]. " +
                "Example: +14155552671");
            return false;
        }

        // Validate length
        if (phoneNumber.length() < 10 || phoneNumber.length() > 15) {
            setCustomMessage(context,
                String.format("Phone number length must be between 10-15 characters. Got: %d",
                    phoneNumber.length()));
            return false;
        }

        // Check if it starts with valid prefix
        if (!phoneNumber.startsWith("+")) {
            setCustomMessage(context, "Phone number must start with '+'");
            return false;
        }

        // Check for invalid patterns
        if (phoneNumber.equals("+0000000000") || phoneNumber.equals("+1111111111")) {
            setCustomMessage(context, "Phone number appears to be invalid or test data");
            return false;
        }

        // Check for too many repeated digits (likely invalid)
        if (hasTooManyRepeatedDigits(phoneNumber)) {
            setCustomMessage(context, "Phone number appears to be invalid (too many repeated digits)");
            log.warn("VALIDATION: Suspicious phone number with repeated digits: {}",
                maskPhoneNumber(phoneNumber));
            return false;
        }

        return true;
    }

    /**
     * Check if phone number has too many repeated digits (suspicious)
     */
    private boolean hasTooManyRepeatedDigits(String phoneNumber) {
        // Remove the '+' prefix
        String digits = phoneNumber.substring(1);

        // Count max consecutive repeated digits
        int maxRepeated = 1;
        int currentRepeated = 1;

        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) == digits.charAt(i - 1)) {
                currentRepeated++;
                maxRepeated = Math.max(maxRepeated, currentRepeated);
            } else {
                currentRepeated = 1;
            }
        }

        // If more than 5 consecutive repeated digits, it's suspicious
        return maxRepeated > 5;
    }

    /**
     * Mask phone number for logging (security)
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "***";
        }

        // Show country code and last 2 digits only
        String countryCode = phoneNumber.substring(0, Math.min(3, phoneNumber.length()));
        String lastTwo = phoneNumber.substring(phoneNumber.length() - 2);

        return countryCode + "******" + lastTwo;
    }

    /**
     * Set custom validation message
     */
    private void setCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
