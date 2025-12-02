/**
 * SECURITY ENHANCEMENT: Error Message Sanitizer
 * Prevents sensitive information leakage through error messages
 */
package com.waqiti.payment.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SECURITY-FOCUSED service for sanitizing error messages before exposing to clients
 * Prevents information disclosure through error messages
 */
@Component
@Slf4j
public class ErrorMessageSanitizer {
    
    // Map for storing sanitized messages with tracking IDs
    private final Map<String, String> errorTrackingMap = new ConcurrentHashMap<>();
    
    // Patterns for sensitive information that should be removed
    private static final Pattern[] SENSITIVE_PATTERNS = {
        // Database information
        Pattern.compile("(?i)password", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)secret", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)token", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)key", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)credential", Pattern.CASE_INSENSITIVE),
        
        // SQL injection patterns
        Pattern.compile("(?i)select\\s+.*\\s+from", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)insert\\s+into", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)update\\s+.*\\s+set", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)delete\\s+from", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)drop\\s+table", Pattern.CASE_INSENSITIVE),
        
        // Stack trace patterns
        Pattern.compile("at\\s+[\\w\\.]+\\([\\w\\.]+:\\d+\\)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Caused by:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("java\\.[\\w\\.]+Exception", Pattern.CASE_INSENSITIVE),
        Pattern.compile("org\\.[\\w\\.]+Exception", Pattern.CASE_INSENSITIVE),
        Pattern.compile("com\\.[\\w\\.]+Exception", Pattern.CASE_INSENSITIVE),
        
        // File system paths
        Pattern.compile("[A-Za-z]:\\\\[\\w\\\\]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/[\\w/]+/[\\w/]+", Pattern.CASE_INSENSITIVE),
        
        // Internal system information
        Pattern.compile("(?i)internal\\s+error", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)database\\s+connection", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)connection\\s+timeout", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)redis", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)kafka", Pattern.CASE_INSENSITIVE),
        
        // Account numbers and sensitive IDs
        Pattern.compile("\\b[0-9]{10,}\\b"), // Long numeric sequences that might be account numbers
        Pattern.compile("\\b[A-Za-z0-9]{20,}\\b"), // Long alphanumeric sequences that might be tokens
        
        // Credit card patterns (basic)
        Pattern.compile("\\b[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b"),
        
        // SSN patterns
        Pattern.compile("\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b"),
        
        // Email patterns in error messages (might contain sensitive info)
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b")
    };
    
    // Generic error messages based on exception types
    private static final Map<String, String> GENERIC_ERROR_MESSAGES = Map.of(
        "PaymentRequestNotFoundException", "Payment request not found",
        "ScheduledPaymentNotFoundException", "Scheduled payment not found", 
        "SplitPaymentNotFoundException", "Split payment not found",
        "PaymentFailedException", "Payment processing failed. Please try again or contact support",
        "InvalidPaymentOperationException", "Invalid payment operation",
        "InvalidPaymentStatusException", "Invalid payment status",
        "PaymentLimitExceededException", "Payment limit exceeded",
        "InsufficientFundsException", "Insufficient funds for this transaction",
        "FraudDetectedException", "Transaction blocked for security reasons",
        "ComplianceException", "Transaction cannot be processed due to compliance requirements",
        "WalletNotFoundException", "Wallet not found",
        "WalletLockedException", "Account is temporarily locked",
        "ServiceUnavailableException", "Service temporarily unavailable. Please try again later",
        "PaymentProcessingException", "Payment could not be processed. Please try again",
        "ACHTransferException", "ACH transfer failed",
        "CheckDepositException", "Check deposit failed",
        "ImageProcessingException", "Image processing failed",
        "CryptographyException", "Security processing error",
        "KYCVerificationRequiredException", "Account verification required to proceed"
    );
    
    /**
     * SECURITY FIX: Sanitize error message for client consumption
     * Removes sensitive information and provides generic user-friendly messages
     */
    public String sanitizeErrorMessage(Throwable exception, String originalMessage) {
        String trackingId = generateTrackingId();
        
        try {
            // Log the full error details for internal debugging
            log.error("SECURITY: Error occurred - Tracking ID: {} - Exception: {} - Original Message: {}", 
                    trackingId, exception.getClass().getSimpleName(), originalMessage, exception);
            
            // Store the original message with tracking ID for internal reference
            errorTrackingMap.put(trackingId, originalMessage);
            
            // Get generic message based on exception type
            String exceptionType = exception.getClass().getSimpleName();
            String sanitizedMessage = GENERIC_ERROR_MESSAGES.getOrDefault(exceptionType, 
                    "An error occurred while processing your request");
            
            // Additional sanitization for any message that might contain sensitive data
            if (originalMessage != null) {
                String checkedMessage = sanitizeMessage(originalMessage);
                
                // Only use parts of the original message if it's safe
                if (isSafeMessage(checkedMessage)) {
                    sanitizedMessage = checkedMessage;
                }
            }
            
            // Add tracking ID for support purposes
            sanitizedMessage = sanitizedMessage + " (Error ID: " + trackingId + ")";
            
            log.info("SECURITY: Error message sanitized - Tracking ID: {} - Sanitized: {}", 
                    trackingId, sanitizedMessage);
            
            return sanitizedMessage;
            
        } catch (Exception e) {
            log.error("SECURITY: Error during message sanitization - Tracking ID: {}", trackingId, e);
            return "An error occurred while processing your request (Error ID: " + trackingId + ")";
        }
    }
    
    /**
     * Sanitize a message by removing sensitive patterns
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "An error occurred";
        }
        
        String sanitized = message;
        
        // Apply all sensitive pattern filters
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]");
        }
        
        // Remove any remaining stack trace information
        if (sanitized.contains("Exception")) {
            String[] lines = sanitized.split("\n");
            StringBuilder cleanMessage = new StringBuilder();
            
            for (String line : lines) {
                if (!line.trim().startsWith("at ") && 
                    !line.trim().startsWith("Caused by:") &&
                    !line.contains("Exception") &&
                    !line.contains("Error")) {
                    cleanMessage.append(line).append(" ");
                }
            }
            
            sanitized = cleanMessage.toString().trim();
        }
        
        // Limit message length to prevent information leakage
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        
        return sanitized.trim();
    }
    
    /**
     * Check if a message is safe to expose to clients
     */
    private boolean isSafeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        // Check against sensitive patterns
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return false;
            }
        }
        
        // Additional checks for unsafe content
        String lowerMessage = message.toLowerCase();
        String[] unsafeKeywords = {
            "exception", "error", "failed", "timeout", "connection", 
            "internal", "system", "server", "database", "null",
            "violation", "constraint", "foreign key", "primary key",
            "duplicate", "syntax", "permission", "access denied"
        };
        
        for (String keyword : unsafeKeywords) {
            if (lowerMessage.contains(keyword)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Generate a unique tracking ID for error correlation
     */
    private String generateTrackingId() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Get the original error message using tracking ID (for internal support use)
     */
    public String getOriginalErrorMessage(String trackingId) {
        return errorTrackingMap.get(trackingId);
    }
    
    /**
     * SECURITY FIX: Sanitize specific payment-related error messages
     */
    public String sanitizePaymentError(String operation, Throwable exception, String originalMessage) {
        String trackingId = generateTrackingId();
        
        log.error("SECURITY: Payment error in operation {} - Tracking ID: {} - Exception: {} - Message: {}", 
                operation, trackingId, exception.getClass().getSimpleName(), originalMessage, exception);
        
        // Store for internal reference
        errorTrackingMap.put(trackingId, operation + ": " + originalMessage);
        
        // Provide operation-specific sanitized messages
        String sanitizedMessage = switch (operation.toLowerCase()) {
            case "payment_process" -> "Payment could not be processed. Please verify your information and try again";
            case "payment_authorize" -> "Payment authorization failed. Please check your payment method";
            case "payment_capture" -> "Payment capture failed. Please contact support";
            case "payment_refund" -> "Refund could not be processed. Please contact support";
            case "payment_cancel" -> "Payment cancellation failed. Please try again";
            case "webhook_process" -> "Payment status update failed. Status will be refreshed automatically";
            case "fraud_check" -> "Transaction blocked for security verification";
            case "compliance_check" -> "Transaction requires additional verification";
            default -> "Payment operation failed. Please try again or contact support";
        };
        
        return sanitizedMessage + " (Error ID: " + trackingId + ")";
    }
    
    /**
     * Get error statistics for monitoring
     */
    public Map<String, Object> getErrorStatistics() {
        return Map.of(
            "totalErrorsTracked", errorTrackingMap.size(),
            "sanitizationEnabled", true,
            "sensitivePatternCount", SENSITIVE_PATTERNS.length,
            "genericMessageCount", GENERIC_ERROR_MESSAGES.size()
        );
    }
    
    /**
     * Clear old tracking entries (for memory management)
     * Should be called periodically
     */
    public void cleanupOldEntries() {
        if (errorTrackingMap.size() > 10000) {
            log.info("SECURITY: Cleaning up old error tracking entries, current size: {}", errorTrackingMap.size());
            errorTrackingMap.clear();
            log.info("SECURITY: Error tracking entries cleaned up");
        }
    }
}