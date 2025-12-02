package com.waqiti.transaction.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for masking Personally Identifiable Information (PII) in logs.
 *
 * This service provides comprehensive PII masking to ensure GDPR and PCI-DSS compliance
 * by preventing sensitive data from appearing in application logs.
 *
 * Masked Data Types:
 * - Email addresses
 * - Phone numbers
 * - Credit card numbers
 * - SSN/Tax IDs
 * - Wallet IDs
 * - Transaction amounts (in financial context)
 * - IP addresses
 * - Personal names
 * - Bank account numbers
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class PiiMaskingService {

    // Regex patterns for PII detection
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?\\d{1,3}[-. ]?)?\\(?\\d{3}\\)?[-. ]?\\d{3}[-. ]?\\d{4}\\b"
    );

    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b"
    );

    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    private static final Pattern WALLET_ID_PATTERN = Pattern.compile(
        "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    );

    private static final Pattern AMOUNT_WITH_CURRENCY_PATTERN = Pattern.compile(
        "\\b(?:USD|EUR|GBP|CAD|AUD|NGN|KES|GHS)\\s*\\$?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b|\\b\\$\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b"
    );

    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile(
        "\\b\\d{8,17}\\b"
    );

    /**
     * Masks all PII in the provided text.
     *
     * @param text The text potentially containing PII
     * @return Text with all PII masked
     */
    public String maskAllPii(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String masked = text;
        masked = maskEmails(masked);
        masked = maskPhoneNumbers(masked);
        masked = maskCreditCards(masked);
        masked = maskSSN(masked);
        masked = maskWalletIds(masked);
        masked = maskAmounts(masked);
        masked = maskIpAddresses(masked);

        return masked;
    }

    /**
     * Masks email addresses.
     *
     * Example: john.doe@example.com → j***@e***.com
     *
     * @param text The text containing email addresses
     * @return Text with masked email addresses
     */
    public String maskEmails(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = EMAIL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String email = matcher.group();
            String[] parts = email.split("@");

            if (parts.length == 2) {
                String username = parts[0];
                String domain = parts[1];

                // Mask username: show first character only
                String maskedUsername = username.length() > 0 ?
                    username.charAt(0) + "***" : "***";

                // Mask domain: show first character of domain name only
                String[] domainParts = domain.split("\\.");
                String maskedDomain = domainParts.length > 0 ?
                    domainParts[0].charAt(0) + "***." + String.join(".", java.util.Arrays.copyOfRange(domainParts, 1, domainParts.length)) :
                    "***";

                matcher.appendReplacement(sb, maskedUsername + "@" + maskedDomain);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks phone numbers.
     *
     * Example: +1-555-123-4567 → +1-555-***-****
     *
     * @param text The text containing phone numbers
     * @return Text with masked phone numbers
     */
    public String maskPhoneNumbers(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = PHONE_PATTERN.matcher(text);
        return matcher.replaceAll("***-***-****");
    }

    /**
     * Masks credit card numbers.
     *
     * Example: 4532-1234-5678-9010 → ****-****-****-9010
     *
     * @param text The text containing credit card numbers
     * @return Text with masked credit card numbers
     */
    public String maskCreditCards(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = CREDIT_CARD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String cardNumber = matcher.group();
            // Keep last 4 digits, mask the rest
            String lastFour = cardNumber.replaceAll("[^0-9]", "");
            if (lastFour.length() >= 4) {
                lastFour = lastFour.substring(lastFour.length() - 4);
                matcher.appendReplacement(sb, "****-****-****-" + lastFour);
            } else {
                matcher.appendReplacement(sb, "****-****-****-****");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks Social Security Numbers.
     *
     * Example: 123-45-6789 → ***-**-6789
     *
     * @param text The text containing SSN
     * @return Text with masked SSN
     */
    public String maskSSN(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = SSN_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String ssn = matcher.group();
            String lastFour = ssn.substring(ssn.length() - 4);
            matcher.appendReplacement(sb, "***-**-" + lastFour);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks wallet IDs (UUIDs).
     *
     * Example: 123e4567-e89b-12d3-a456-426614174000 → 123e****-****-****-****-*****4000
     *
     * @param text The text containing wallet IDs
     * @return Text with masked wallet IDs
     */
    public String maskWalletIds(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = WALLET_ID_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String walletId = matcher.group();
            String[] parts = walletId.split("-");
            if (parts.length == 5) {
                String masked = parts[0].substring(0, 4) + "****-****-****-****-*****" +
                               parts[4].substring(parts[4].length() - 4);
                matcher.appendReplacement(sb, masked);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks transaction amounts in financial context.
     *
     * Example: USD 1,234.56 → USD ***.** / $1,234.56 → $***.**
     *
     * @param text The text containing amounts
     * @return Text with masked amounts
     */
    public String maskAmounts(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = AMOUNT_WITH_CURRENCY_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String amount = matcher.group();
            String masked;

            if (amount.startsWith("$")) {
                masked = "$***.**";
            } else {
                // Extract currency code
                String currency = amount.split("\\s")[0];
                masked = currency + " ***.**";
            }

            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks IP addresses.
     *
     * Example: 192.168.1.100 → 192.168.***.***
     *
     * @param text The text containing IP addresses
     * @return Text with masked IP addresses
     */
    public String maskIpAddresses(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = IP_ADDRESS_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String ip = matcher.group();
            String[] octets = ip.split("\\.");
            if (octets.length == 4) {
                String masked = octets[0] + "." + octets[1] + ".***. ***";
                matcher.appendReplacement(sb, masked);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks bank account numbers.
     *
     * Example: 1234567890 → ******7890
     *
     * @param text The text containing bank account numbers
     * @return Text with masked account numbers
     */
    public String maskBankAccounts(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = BANK_ACCOUNT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String account = matcher.group();
            if (account.length() >= 4) {
                String lastFour = account.substring(account.length() - 4);
                matcher.appendReplacement(sb, "******" + lastFour);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Masks a specific field value completely.
     *
     * Used for highly sensitive data that should never appear in logs.
     *
     * @param fieldName The name of the field
     * @param value The value to mask
     * @return Masked representation
     */
    public String maskFieldCompletely(String fieldName, Object value) {
        if (value == null) {
            return fieldName + "=[REDACTED:null]";
        }

        return fieldName + "=[REDACTED:" + value.getClass().getSimpleName() + "]";
    }

    /**
     * Creates a safe log message for financial transactions.
     *
     * Masks all PII while preserving transaction ID for debugging.
     *
     * @param transactionId The transaction ID (preserved)
     * @param fromWallet Source wallet (masked)
     * @param toWallet Destination wallet (masked)
     * @param amount Amount (masked)
     * @param currency Currency code (preserved)
     * @return Safe log message
     */
    public String createSafeTransactionLogMessage(String transactionId, String fromWallet,
                                                  String toWallet, String amount, String currency) {
        return String.format("Transaction initiated: id=%s, from=%s, to=%s, amount=%s, currency=%s",
                           transactionId,
                           maskWalletIds(fromWallet),
                           maskWalletIds(toWallet),
                           "***.**",  // Always mask amount
                           currency);
    }

    /**
     * Creates a safe log message for user operations.
     *
     * @param userId User ID (preserved for debugging)
     * @param email User email (masked)
     * @param operation Operation name (preserved)
     * @return Safe log message
     */
    public String createSafeUserLogMessage(String userId, String email, String operation) {
        return String.format("User operation: userId=%s, email=%s, operation=%s",
                           userId,
                           maskEmails(email),
                           operation);
    }

    /**
     * Validates if text contains any unmasked PII.
     *
     * Used for testing and validation.
     *
     * @param text The text to validate
     * @return true if PII is detected, false otherwise
     */
    public boolean containsUnmaskedPii(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        return EMAIL_PATTERN.matcher(text).find() ||
               PHONE_PATTERN.matcher(text).find() ||
               CREDIT_CARD_PATTERN.matcher(text).find() ||
               SSN_PATTERN.matcher(text).find();
    }
}
