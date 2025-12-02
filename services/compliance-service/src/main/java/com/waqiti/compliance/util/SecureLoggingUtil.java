package com.waqiti.compliance.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

import java.util.regex.Pattern;

/**
 * Utility class for secure logging that masks sensitive data
 * Ensures PII and sensitive financial information is not logged in plain text
 */
@UtilityClass
@Slf4j
public class SecureLoggingUtil {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?1[-\\s]?)?\\(?([0-9]{3})\\)?[-\\s]?([0-9]{3})[-\\s]?([0-9]{4})");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-?\\d{2}-?\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\b\\d{10,20}\\b");
    
    /**
     * Mask transaction ID - show first 4 and last 4 characters
     */
    public static String maskTransactionId(String transactionId) {
        if (transactionId == null || transactionId.length() <= 8) {
            return "****";
        }
        return transactionId.substring(0, 4) + "****" + transactionId.substring(transactionId.length() - 4);
    }
    
    /**
     * Mask customer ID with hash for consistency in logs
     */
    public static String maskCustomerId(String customerId) {
        if (customerId == null) {
            return "NULL";
        }
        return "CUST_" + DigestUtils.md5DigestAsHex(customerId.getBytes()).substring(0, 8).toUpperCase();
    }
    
    /**
     * Mask account ID - show first 2 and last 2 characters
     */
    public static String maskAccountId(String accountId) {
        if (accountId == null || accountId.length() <= 4) {
            return "****";
        }
        return accountId.substring(0, 2) + "****" + accountId.substring(accountId.length() - 2);
    }
    
    /**
     * Mask amount - round to nearest hundred for privacy
     */
    public static String maskAmount(java.math.BigDecimal amount, String currency) {
        if (amount == null) {
            return "N/A";
        }
        
        // For large amounts, show rounded value
        if (amount.compareTo(java.math.BigDecimal.valueOf(10000)) >= 0) {
            java.math.BigDecimal rounded = amount.divide(java.math.BigDecimal.valueOf(1000), 0, java.math.RoundingMode.DOWN)
                .multiply(java.math.BigDecimal.valueOf(1000));
            return "~" + rounded + " " + (currency != null ? currency : "");
        }
        
        return amount + " " + (currency != null ? currency : "");
    }
    
    /**
     * Mask IP address - show first two octets only
     */
    public static String maskIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return "0.0.0.0";
        }
        
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.* ";
        }
        
        return "*.*.*.* ";
    }
    
    /**
     * Hash email for consistent tracking without exposing PII
     */
    public static String hashEmail(String email) {
        if (email == null) {
            return "NULL";
        }
        return "EMAIL_" + DigestUtils.md5DigestAsHex(email.toLowerCase().getBytes()).substring(0, 8).toUpperCase();
    }
    
    /**
     * Mask phone number - show country code and last 4 digits
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return "+X-XXX-XXX-" + digits.substring(digits.length() - 4);
        } else if (digits.length() >= 4) {
            return "XXX-" + digits.substring(digits.length() - 4);
        }
        
        return "****";
    }
    
    /**
     * Remove or mask any potential PII from free text
     */
    public static String sanitizeText(String text) {
        if (text == null) {
            return null;
        }
        
        String sanitized = text;
        
        // Replace email addresses
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("$1@***MASKED***");
        
        // Replace phone numbers
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("***-***-$4");
        
        // Replace SSNs
        sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("***-**-****");
        
        // Replace credit card numbers
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("****-****-****-****");
        
        // Replace potential account numbers (10-20 digits)
        sanitized = ACCOUNT_NUMBER_PATTERN.matcher(sanitized).replaceAll("**********");
        
        return sanitized;
    }
    
    /**
     * Create a loggable representation of a transaction for audit purposes
     */
    public static String createTransactionLogEntry(String transactionId, String customerId, 
                                                  java.math.BigDecimal amount, String currency, String type) {
        return String.format("TXN[ID=%s, CUST=%s, AMT=%s, TYPE=%s]",
            maskTransactionId(transactionId),
            maskCustomerId(customerId),
            maskAmount(amount, currency),
            type);
    }
    
    /**
     * Create a compliance action log entry
     */
    public static String createComplianceLogEntry(String action, String transactionId, 
                                                 String riskLevel, Integer riskScore, String decision) {
        return String.format("COMPLIANCE[ACTION=%s, TXN=%s, RISK=%s(%s), DECISION=%s]",
            action,
            maskTransactionId(transactionId),
            riskLevel,
            riskScore != null ? riskScore : "N/A",
            decision);
    }
    
    /**
     * Create a user action log entry for audit trail
     */
    public static String createUserActionLogEntry(String userId, String action, String resourceType, 
                                                 String resourceId, String result) {
        return String.format("USER_ACTION[USER=%s, ACTION=%s, RESOURCE=%s:%s, RESULT=%s]",
            maskCustomerId(userId), // Reuse masking logic for user IDs
            action,
            resourceType,
            maskTransactionId(resourceId), // Generic ID masking
            result);
    }
    
    /**
     * Mask sensitive data in JSON-like structures
     */
    public static String maskJsonSensitiveData(String json) {
        if (json == null) {
            return null;
        }
        
        String masked = json;
        
        // Mask common sensitive field patterns
        masked = masked.replaceAll("(\"(?:customerId|customer_id|userId|user_id)\"\\s*:\\s*\")([^\"]+)(\")", "$1" + "****" + "$3");
        masked = masked.replaceAll("(\"(?:accountId|account_id|accountNumber|account_number)\"\\s*:\\s*\")([^\"]+)(\")", "$1" + "****" + "$3");
        masked = masked.replaceAll("(\"(?:transactionId|transaction_id|txnId|txn_id)\"\\s*:\\s*\")([^\"]+)(\")", "$1" + "****" + "$3");
        masked = masked.replaceAll("(\"(?:email|emailAddress|email_address)\"\\s*:\\s*\")([^\"]+)(\")", "$1" + "****@****.***" + "$3");
        masked = masked.replaceAll("(\"(?:phone|phoneNumber|phone_number)\"\\s*:\\s*\")([^\"]+)(\")", "$1" + "***-***-****" + "$3");
        masked = masked.replaceAll("(\"(?:ssn|socialSecurityNumber|social_security_number)\"\\s*:\\s*\")([^\"]+)(\")", "$1" + "***-**-****" + "$3");
        
        return masked;
    }
    
    /**
     * Check if a log message contains potential sensitive data
     */
    public static boolean containsSensitiveData(String message) {
        if (message == null) {
            return false;
        }
        
        return EMAIL_PATTERN.matcher(message).find() ||
               PHONE_PATTERN.matcher(message).find() ||
               SSN_PATTERN.matcher(message).find() ||
               CREDIT_CARD_PATTERN.matcher(message).find() ||
               ACCOUNT_NUMBER_PATTERN.matcher(message).find();
    }
    
    /**
     * Log a compliance event with proper data masking
     */
    public static void logComplianceEvent(org.slf4j.Logger logger, String level, String transactionId, 
                                         String customerId, String event, Object... additionalParams) {
        String maskedLogEntry = createTransactionLogEntry(transactionId, customerId, null, null, event);
        
        switch (level.toUpperCase()) {
            case "ERROR":
                logger.error("{} - {}", maskedLogEntry, formatAdditionalParams(additionalParams));
                break;
            case "WARN":
                logger.warn("{} - {}", maskedLogEntry, formatAdditionalParams(additionalParams));
                break;
            case "INFO":
                logger.info("{} - {}", maskedLogEntry, formatAdditionalParams(additionalParams));
                break;
            case "DEBUG":
                logger.debug("{} - {}", maskedLogEntry, formatAdditionalParams(additionalParams));
                break;
            default:
                logger.info("{} - {}", maskedLogEntry, formatAdditionalParams(additionalParams));
        }
    }
    
    /**
     * Format additional parameters safely
     */
    private static String formatAdditionalParams(Object... params) {
        if (params == null || params.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i += 2) {
            if (i + 1 < params.length) {
                String key = String.valueOf(params[i]);
                String value = sanitizeText(String.valueOf(params[i + 1]));
                sb.append(key).append("=").append(value);
                if (i + 2 < params.length) {
                    sb.append(", ");
                }
            }
        }
        
        return sb.toString();
    }
}