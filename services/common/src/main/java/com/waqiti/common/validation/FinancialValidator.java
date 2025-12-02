package com.waqiti.common.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Comprehensive financial validation utility.
 *
 * This validator provides production-grade validation for all financial operations
 * to prevent:
 * - Negative amount attacks
 * - Decimal overflow/underflow
 * - Currency mismatch
 * - Invalid account numbers
 * - Malformed payment data
 *
 * SECURITY: All financial inputs MUST pass through these validations.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
public class FinancialValidator {

    // Constants
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_DECIMAL_PLACES = 2;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$" // E.164 format
    );

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
        "^[0-9]{10,20}$"
    );

    private static final Pattern IBAN_PATTERN = Pattern.compile(
        "^[A-Z]{2}[0-9]{2}[A-Z0-9]+$"
    );

    private static final Pattern BIC_PATTERN = Pattern.compile(
        "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$"
    );

    private static final Pattern ROUTING_NUMBER_PATTERN = Pattern.compile(
        "^[0-9]{9}$" // US routing number
    );

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
        "USD", "EUR", "GBP", "JPY", "CNY", "CHF", "CAD", "AUD", "NZD",
        "SEK", "NOK", "DKK", "SGD", "HKD", "INR", "BRL", "MXN", "ZAR",
        "RUB", "TRY", "KRW", "THB", "IDR", "MYR", "PHP", "PLN", "AED"
    );

    /**
     * Validates a financial amount with comprehensive checks.
     *
     * @param amount The amount to validate
     * @param fieldName The field name for error messages
     * @throws ValidationException if validation fails
     */
    public void validateAmount(BigDecimal amount, String fieldName) {
        log.debug("Validating amount: {}={}", fieldName, amount);

        // Null check
        if (amount == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }

        // Negative check
        if (amount.signum() < 0) {
            throw new ValidationException(
                fieldName + " cannot be negative: " + amount +
                " (SECURITY: Potential negative amount attack)");
        }

        // Zero check
        if (amount.signum() == 0) {
            throw new ValidationException(fieldName + " cannot be zero");
        }

        // Minimum check
        if (amount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            throw new ValidationException(
                fieldName + " must be at least " + MIN_TRANSACTION_AMOUNT + ": " + amount);
        }

        // Maximum check
        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new ValidationException(
                fieldName + " exceeds maximum allowed " + MAX_TRANSACTION_AMOUNT + ": " + amount +
                " (Contact support for large transactions)");
        }

        // Precision check
        if (amount.scale() > MAX_DECIMAL_PLACES) {
            throw new ValidationException(
                fieldName + " has too many decimal places (max " + MAX_DECIMAL_PLACES + "): " +
                amount + " (scale: " + amount.scale() + ")");
        }

        // NaN and Infinity checks (defensive)
        String amountStr = amount.toString();
        if (amountStr.contains("NaN") || amountStr.contains("Infinity")) {
            throw new ValidationException(
                fieldName + " contains invalid numeric value: " + amountStr);
        }

        log.debug("✅ Amount validation passed: {}={}", fieldName, amount);
    }

    /**
     * Validates currency code (ISO 4217).
     */
    public void validateCurrency(String currency) {
        validateCurrency(currency, "currency");
    }

    /**
     * Validates currency code with custom field name.
     */
    public void validateCurrency(String currency, String fieldName) {
        if (currency == null || currency.trim().isEmpty()) {
            throw new ValidationException(fieldName + " cannot be null or empty");
        }

        if (currency.length() != 3) {
            throw new ValidationException(
                fieldName + " must be 3-character ISO 4217 code: " + currency);
        }

        String upperCurrency = currency.toUpperCase();
        if (!upperCurrency.equals(currency)) {
            throw new ValidationException(
                fieldName + " must be uppercase: " + currency +
                " (expected: " + upperCurrency + ")");
        }

        if (!SUPPORTED_CURRENCIES.contains(upperCurrency)) {
            throw new ValidationException(
                fieldName + " is not a supported currency: " + currency +
                " (Supported: " + SUPPORTED_CURRENCIES + ")");
        }
    }

    /**
     * Validates two amounts are in the same currency.
     */
    public void validateSameCurrency(String currency1, String currency2,
                                     String field1Name, String field2Name) {
        validateCurrency(currency1, field1Name);
        validateCurrency(currency2, field2Name);

        if (!currency1.equals(currency2)) {
            throw new ValidationException(
                "Currency mismatch: " + field1Name + "=" + currency1 +
                ", " + field2Name + "=" + currency2 +
                " (SECURITY: Cross-currency transaction requires explicit conversion)");
        }
    }

    /**
     * Validates UUID.
     */
    public void validateUUID(UUID id, String fieldName) {
        if (id == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }

        // Additional check: not all zeros
        if (id.equals(new UUID(0, 0))) {
            throw new ValidationException(
                fieldName + " cannot be all zeros (invalid UUID)");
        }
    }

    /**
     * Validates account number.
     */
    public void validateAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new ValidationException("Account number cannot be null or empty");
        }

        String cleaned = accountNumber.replaceAll("\\s+", "");

        if (!ACCOUNT_NUMBER_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException(
                "Invalid account number format (must be 10-20 digits): " + accountNumber);
        }
    }

    /**
     * Validates IBAN (International Bank Account Number).
     */
    public void validateIBAN(String iban) {
        if (iban == null || iban.trim().isEmpty()) {
            throw new ValidationException("IBAN cannot be null or empty");
        }

        String cleaned = iban.replaceAll("\\s+", "").toUpperCase();

        if (!IBAN_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException(
                "Invalid IBAN format (must start with 2 letters + 2 digits): " + iban);
        }

        if (cleaned.length() < 15 || cleaned.length() > 34) {
            throw new ValidationException(
                "IBAN length must be 15-34 characters: " + iban +
                " (length: " + cleaned.length() + ")");
        }

        // Mod-97 check
        if (!passesIBANMod97Check(cleaned)) {
            throw new ValidationException(
                "Invalid IBAN checksum (Mod-97 failed): " + iban +
                " (SECURITY: Potentially malformed IBAN)");
        }
    }

    /**
     * Validates BIC/SWIFT code.
     */
    public void validateBIC(String bic) {
        if (bic == null || bic.trim().isEmpty()) {
            throw new ValidationException("BIC cannot be null or empty");
        }

        String cleaned = bic.replaceAll("\\s+", "").toUpperCase();

        if (!BIC_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException(
                "Invalid BIC format (must be 8 or 11 characters): " + bic);
        }
    }

    /**
     * Validates US routing number.
     */
    public void validateRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.trim().isEmpty()) {
            throw new ValidationException("Routing number cannot be null or empty");
        }

        String cleaned = routingNumber.replaceAll("\\s+", "");

        if (!ROUTING_NUMBER_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException(
                "Invalid routing number format (must be 9 digits): " + routingNumber);
        }

        // ABA routing number checksum validation
        if (!passesABAChecksum(cleaned)) {
            throw new ValidationException(
                "Invalid routing number checksum: " + routingNumber +
                " (SECURITY: Potentially malformed routing number)");
        }
    }

    /**
     * Validates card number using Luhn algorithm.
     */
    public void validateCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new ValidationException("Card number cannot be null or empty");
        }

        String cleaned = cardNumber.replaceAll("[\\s-]", "");

        if (!cleaned.matches("^[0-9]{13,19}$")) {
            throw new ValidationException(
                "Invalid card number format (must be 13-19 digits): " +
                maskCardNumber(cardNumber));
        }

        if (!passesLuhnCheck(cleaned)) {
            throw new ValidationException(
                "Invalid card number (Luhn check failed): " +
                maskCardNumber(cardNumber) +
                " (SECURITY: Potentially fake card number)");
        }
    }

    /**
     * Validates CVV.
     */
    public void validateCVV(String cvv) {
        if (cvv == null || cvv.trim().isEmpty()) {
            throw new ValidationException("CVV cannot be null or empty");
        }

        if (!cvv.matches("^[0-9]{3,4}$")) {
            throw new ValidationException(
                "Invalid CVV format (must be 3 or 4 digits)");
        }
    }

    /**
     * Validates email address.
     */
    public void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email cannot be null or empty");
        }

        if (email.length() > 254) {
            throw new ValidationException(
                "Email too long (max 254 characters): " + email.length());
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException(
                "Invalid email format: " + maskEmail(email));
        }
    }

    /**
     * Validates phone number (E.164 format).
     */
    public void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ValidationException("Phone number cannot be null or empty");
        }

        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException(
                "Invalid phone number format (use E.164 format: +1234567890): " +
                maskPhoneNumber(phoneNumber));
        }
    }

    /**
     * Validates percentage value (0-100).
     */
    public void validatePercentage(BigDecimal percentage, String fieldName) {
        if (percentage == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }

        if (percentage.signum() < 0) {
            throw new ValidationException(fieldName + " cannot be negative: " + percentage);
        }

        if (percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new ValidationException(fieldName + " cannot exceed 100: " + percentage);
        }
    }

    /**
     * Validates string length.
     */
    public void validateLength(String value, int minLength, int maxLength, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }

        if (value.length() < minLength) {
            throw new ValidationException(
                fieldName + " too short (min " + minLength + "): " + value.length());
        }

        if (value.length() > maxLength) {
            throw new ValidationException(
                fieldName + " too long (max " + maxLength + "): " + value.length());
        }
    }

    /**
     * Validates no SQL injection patterns.
     */
    public void validateNoSQLInjection(String value, String fieldName) {
        if (value == null) {
            return;
        }

        String lower = value.toLowerCase();

        String[] sqlKeywords = {
            "select", "insert", "update", "delete", "drop", "create",
            "alter", "truncate", "exec", "execute", "union", "declare",
            "script", "javascript", "onerror", "onload"
        };

        for (String keyword : sqlKeywords) {
            if (lower.contains(keyword)) {
                throw new ValidationException(
                    fieldName + " contains potentially malicious content" +
                    " (SECURITY: Potential SQL/XSS injection detected)");
            }
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * IBAN Mod-97 checksum validation.
     */
    private boolean passesIBANMod97Check(String iban) {
        try {
            // Move first 4 characters to end
            String rearranged = iban.substring(4) + iban.substring(0, 4);

            // Replace letters with numbers (A=10, B=11, ..., Z=35)
            StringBuilder numeric = new StringBuilder();
            for (char c : rearranged.toCharArray()) {
                if (Character.isLetter(c)) {
                    numeric.append(c - 'A' + 10);
                } else {
                    numeric.append(c);
                }
            }

            // Perform Mod-97 operation
            BigDecimal num = new BigDecimal(numeric.toString());
            BigDecimal remainder = num.remainder(new BigDecimal(97));

            return remainder.intValue() == 1;

        } catch (Exception e) {
            log.warn("IBAN Mod-97 check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * ABA routing number checksum validation.
     */
    private boolean passesABAChecksum(String routingNumber) {
        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                int digit = Character.getNumericValue(routingNumber.charAt(i));
                int weight = (i % 3 == 0) ? 3 : (i % 3 == 1) ? 7 : 1;
                sum += digit * weight;
            }
            return sum % 10 == 0;

        } catch (Exception e) {
            log.warn("ABA checksum failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Luhn algorithm for card validation.
     */
    private boolean passesLuhnCheck(String cardNumber) {
        try {
            int sum = 0;
            boolean alternate = false;

            for (int i = cardNumber.length() - 1; i >= 0; i--) {
                int n = Character.getNumericValue(cardNumber.charAt(i));

                if (alternate) {
                    n *= 2;
                    if (n > 9) {
                        n = (n % 10) + 1;
                    }
                }

                sum += n;
                alternate = !alternate;
            }

            return (sum % 10 == 0);

        } catch (Exception e) {
            log.warn("Luhn check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Masks card number for logging (shows only last 4 digits).
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String cleaned = cardNumber.replaceAll("[^0-9]", "");
        return "****" + cleaned.substring(cleaned.length() - 4);
    }

    /**
     * Masks email for logging.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***.com";
        }
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }

    /**
     * Masks phone number for logging.
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        String cleaned = phoneNumber.replaceAll("[^0-9]", "");
        return "****" + cleaned.substring(cleaned.length() - 4);
    }
}
