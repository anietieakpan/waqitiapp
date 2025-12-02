package com.waqiti.payment.core.validation;

import com.waqiti.payment.core.integration.PaymentProcessingRequest;
import com.waqiti.payment.core.model.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Comprehensive payment validation service
 * Industrial-grade validation with multiple rule sets
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentValidationService {
    
    private final Validator validator;
    
    private static final Map<String, CurrencyRules> CURRENCY_RULES = initializeCurrencyRules();
    private static final Map<PaymentType, PaymentTypeRules> PAYMENT_TYPE_RULES = initializePaymentTypeRules();
    
    // Regex patterns for validation
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]+$");
    private static final Pattern SWIFT_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^[0-9]{13,19}$");
    
    /**
     * Validate payment processing request
     */
    public ValidationResult validatePaymentRequest(PaymentProcessingRequest request) {
        log.debug("Validating payment request: {}", request.getRequestId());
        
        ValidationResult result = new ValidationResult();
        result.setRequestId(request.getRequestId());
        
        // Basic validation
        validateBasicFields(request, result);
        
        // Bean validation
        validateBeanConstraints(request, result);
        
        // Amount validation
        validateAmount(request, result);
        
        // Currency validation
        validateCurrency(request, result);
        
        // Payment type specific validation
        validatePaymentTypeSpecific(request, result);
        
        // Time-based validation
        validateTimings(request, result);
        
        // Security validation
        validateSecurity(request, result);
        
        // Business rules validation
        validateBusinessRules(request, result);
        
        // Set overall status
        result.setValid(result.getErrors().isEmpty());
        result.setValidatedAt(LocalDateTime.now());
        
        log.info("Validation result for {}: valid={}, errors={}, warnings={}", 
            request.getRequestId(), result.isValid(), 
            result.getErrors().size(), result.getWarnings().size());
        
        return result;
    }
    
    private void validateBasicFields(PaymentProcessingRequest request, ValidationResult result) {
        if (request.getRequestId() == null) {
            result.addError("REQUEST_ID_MISSING", "Request ID is required");
        }
        
        if (request.getSenderId() == null) {
            result.addError("SENDER_ID_MISSING", "Sender ID is required");
        }
        
        if (request.getRecipientId() == null) {
            result.addError("RECIPIENT_ID_MISSING", "Recipient ID is required");
        }
        
        if (request.getPaymentType() == null) {
            result.addError("PAYMENT_TYPE_MISSING", "Payment type is required");
        }
        
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isEmpty()) {
            result.addError("IDEMPOTENCY_KEY_MISSING", "Idempotency key is required");
        }
    }
    
    private void validateBeanConstraints(PaymentProcessingRequest request, ValidationResult result) {
        Set<ConstraintViolation<PaymentProcessingRequest>> violations = validator.validate(request);
        
        for (ConstraintViolation<PaymentProcessingRequest> violation : violations) {
            result.addError(
                "CONSTRAINT_VIOLATION",
                violation.getPropertyPath() + ": " + violation.getMessage()
            );
        }
    }
    
    private void validateAmount(PaymentProcessingRequest request, ValidationResult result) {
        BigDecimal amount = request.getAmount();
        
        if (amount == null) {
            result.addError("AMOUNT_MISSING", "Amount is required");
            return;
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("AMOUNT_INVALID", "Amount must be greater than zero");
        }
        
        // Check decimal places for currency
        CurrencyRules currencyRules = CURRENCY_RULES.get(request.getCurrency());
        if (currencyRules != null) {
            if (amount.scale() > currencyRules.decimalPlaces) {
                result.addError("AMOUNT_PRECISION", 
                    "Amount has too many decimal places for " + request.getCurrency());
            }
            
            if (amount.compareTo(currencyRules.minAmount) < 0) {
                result.addError("AMOUNT_TOO_LOW", 
                    "Amount is below minimum for " + request.getCurrency());
            }
            
            if (amount.compareTo(currencyRules.maxAmount) > 0) {
                result.addError("AMOUNT_TOO_HIGH", 
                    "Amount exceeds maximum for " + request.getCurrency());
            }
        }
        
        // Check max acceptable fee if specified
        if (request.getMaxAcceptableFee() != null && 
            request.getMaxAcceptableFee().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("MAX_FEE_INVALID", "Max acceptable fee cannot be negative");
        }
    }
    
    private void validateCurrency(PaymentProcessingRequest request, ValidationResult result) {
        String currency = request.getCurrency();
        
        if (currency == null || currency.isEmpty()) {
            result.addError("CURRENCY_MISSING", "Currency is required");
            return;
        }
        
        if (currency.length() != 3) {
            result.addError("CURRENCY_INVALID", "Currency must be 3-letter ISO code");
        }
        
        if (!CURRENCY_RULES.containsKey(currency)) {
            result.addWarning("CURRENCY_UNKNOWN", "Currency " + currency + " is not recognized");
        }
    }
    
    private void validatePaymentTypeSpecific(PaymentProcessingRequest request, ValidationResult result) {
        PaymentType paymentType = request.getPaymentType();
        if (paymentType == null) {
            return;
        }
        
        PaymentTypeRules rules = PAYMENT_TYPE_RULES.get(paymentType);
        if (rules == null) {
            result.addWarning("PAYMENT_TYPE_RULES", "No specific rules for payment type: " + paymentType);
            return;
        }
        
        // Check required fields
        if (rules.requiredFields != null) {
            for (String field : rules.requiredFields) {
                if (!hasField(request, field)) {
                    result.addError("REQUIRED_FIELD_MISSING", 
                        "Required field missing for " + paymentType + ": " + field);
                }
            }
        }
        
        // Check amount limits
        BigDecimal amount = request.getAmount();
        if (amount != null) {
            if (rules.minAmount != null && amount.compareTo(rules.minAmount) < 0) {
                result.addError("PAYMENT_TYPE_MIN_AMOUNT", 
                    paymentType + " minimum amount is " + rules.minAmount);
            }
            
            if (rules.maxAmount != null && amount.compareTo(rules.maxAmount) > 0) {
                result.addError("PAYMENT_TYPE_MAX_AMOUNT", 
                    paymentType + " maximum amount is " + rules.maxAmount);
            }
        }
    }
    
    private void validateTimings(PaymentProcessingRequest request, ValidationResult result) {
        // Check if request is expired
        if (request.isExpired()) {
            result.addError("REQUEST_EXPIRED", "Payment request has expired");
        }
        
        // Validate execution time
        if (request.getRequestedExecutionTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime executionTime = request.getRequestedExecutionTime();
            
            if (executionTime.isBefore(now)) {
                result.addError("EXECUTION_TIME_PAST", "Requested execution time is in the past");
            }
            
            // Check if too far in future (e.g., more than 1 year)
            if (executionTime.isAfter(now.plusYears(1))) {
                result.addWarning("EXECUTION_TIME_FAR", "Execution time is more than 1 year in future");
            }
        }
        
        // Validate expiry time
        if (request.getExpiryTime() != null && request.getRequestedExecutionTime() != null) {
            if (request.getExpiryTime().isBefore(request.getRequestedExecutionTime())) {
                result.addError("EXPIRY_BEFORE_EXECUTION", 
                    "Expiry time cannot be before execution time");
            }
        }
    }
    
    private void validateSecurity(PaymentProcessingRequest request, ValidationResult result) {
        // Validate security context if present
        if (request.getSecurityContext() != null) {
            PaymentProcessingRequest.SecurityContext security = request.getSecurityContext();
            
            if (security.getUserId() == null || security.getUserId().isEmpty()) {
                result.addWarning("SECURITY_USER_MISSING", "Security context missing user ID");
            }
            
            if (security.getIpAddress() != null && !isValidIpAddress(security.getIpAddress())) {
                result.addWarning("SECURITY_IP_INVALID", "Invalid IP address in security context");
            }
            
            if (security.isAuthenticationTime() != null && 
                security.getAuthenticationTime().isBefore(LocalDateTime.now().minusHours(24))) {
                result.addWarning("SECURITY_AUTH_OLD", "Authentication is more than 24 hours old");
            }
        }
    }
    
    private void validateBusinessRules(PaymentProcessingRequest request, ValidationResult result) {
        // Validate retry attempts
        if (request.getMaxRetryAttempts() < 0) {
            result.addError("RETRY_ATTEMPTS_INVALID", "Max retry attempts cannot be negative");
        }
        
        if (request.getMaxRetryAttempts() > 10) {
            result.addWarning("RETRY_ATTEMPTS_HIGH", "Max retry attempts is unusually high");
        }
        
        // Validate provider preferences
        if (!request.getPreferredProviders().isEmpty() && !request.getExcludedProviders().isEmpty()) {
            Set<String> overlap = new HashSet<>(request.getPreferredProviders());
            overlap.retainAll(request.getExcludedProviders());
            
            if (!overlap.isEmpty()) {
                result.addError("PROVIDER_CONFLICT", 
                    "Providers cannot be both preferred and excluded: " + overlap);
            }
        }
        
        // Add custom business rules based on payment type
        applyCustomBusinessRules(request, result);
    }
    
    private void applyCustomBusinessRules(PaymentProcessingRequest request, ValidationResult result) {
        // High-value transaction rules
        if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            if (!request.isRequireComplianceCheck()) {
                result.addWarning("HIGH_VALUE_COMPLIANCE", 
                    "High-value transaction should require compliance check");
            }
            
            if (request.getPriority() != PaymentProcessingRequest.ProcessingPriority.HIGH &&
                request.getPriority() != PaymentProcessingRequest.ProcessingPriority.CRITICAL) {
                result.addWarning("HIGH_VALUE_PRIORITY", 
                    "High-value transaction should have high priority");
            }
        }
        
        // Real-time payment rules
        if (request.getProcessingMode() == PaymentProcessingRequest.ProcessingMode.REAL_TIME) {
            if (request.getMaxRetryAttempts() > 1) {
                result.addWarning("REALTIME_RETRY", 
                    "Real-time payments should have limited retry attempts");
            }
        }
    }
    
    private boolean hasField(PaymentProcessingRequest request, String fieldName) {
        // Check if required field exists in payment data
        if (request.getPaymentData() != null && request.getPaymentData().containsKey(fieldName)) {
            return true;
        }
        
        // Check metadata
        if (request.getMetadata() != null && request.getMetadata().containsKey(fieldName)) {
            return true;
        }
        
        return false;
    }
    
    private boolean isValidIpAddress(String ip) {
        // Simple IP validation (supports both IPv4 and IPv6)
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                // IPv4
                for (String part : parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                }
                return true;
            }
            // Could be IPv6 - simplified check
            return ip.contains(":");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate account number based on type
     */
    public boolean validateAccountNumber(String accountNumber, String accountType) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return false;
        }
        
        switch (accountType.toUpperCase()) {
            case "IBAN":
                return IBAN_PATTERN.matcher(accountNumber).matches();
            case "SWIFT":
                return SWIFT_PATTERN.matcher(accountNumber).matches();
            case "EMAIL":
                return EMAIL_PATTERN.matcher(accountNumber).matches();
            case "PHONE":
                return PHONE_PATTERN.matcher(accountNumber).matches();
            case "CARD":
                return CARD_NUMBER_PATTERN.matcher(accountNumber.replaceAll("\\s", "")).matches();
            default:
                // Basic validation for unknown types
                return accountNumber.length() >= 5 && accountNumber.length() <= 50;
        }
    }
    
    /**
     * Initialize currency rules
     */
    private static Map<String, CurrencyRules> initializeCurrencyRules() {
        Map<String, CurrencyRules> rules = new HashMap<>();
        
        rules.put("USD", new CurrencyRules(2, new BigDecimal("0.01"), new BigDecimal("1000000")));
        rules.put("EUR", new CurrencyRules(2, new BigDecimal("0.01"), new BigDecimal("1000000")));
        rules.put("GBP", new CurrencyRules(2, new BigDecimal("0.01"), new BigDecimal("1000000")));
        rules.put("JPY", new CurrencyRules(0, new BigDecimal("1"), new BigDecimal("100000000")));
        rules.put("BTC", new CurrencyRules(8, new BigDecimal("0.00000001"), new BigDecimal("21000000")));
        
        return rules;
    }
    
    /**
     * Initialize payment type rules
     */
    private static Map<PaymentType, PaymentTypeRules> initializePaymentTypeRules() {
        Map<PaymentType, PaymentTypeRules> rules = new HashMap<>();
        
        rules.put(PaymentType.CARD, PaymentTypeRules.builder()
            .requiredFields(List.of("cardNumber", "expiryDate", "cvv"))
            .minAmount(new BigDecimal("0.50"))
            .maxAmount(new BigDecimal("50000"))
            .build());
        
        rules.put(PaymentType.BANK_TRANSFER, PaymentTypeRules.builder()
            .requiredFields(List.of("accountNumber", "routingNumber"))
            .minAmount(new BigDecimal("1.00"))
            .maxAmount(new BigDecimal("1000000"))
            .build());
        
        rules.put(PaymentType.CRYPTO, PaymentTypeRules.builder()
            .requiredFields(List.of("walletAddress", "network"))
            .minAmount(new BigDecimal("0.00000001"))
            .maxAmount(new BigDecimal("1000000"))
            .build());
        
        return rules;
    }
    
    /**
     * Currency validation rules
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CurrencyRules {
        private int decimalPlaces;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
    }
    
    /**
     * Payment type validation rules
     */
    @lombok.Data
    @lombok.Builder
    private static class PaymentTypeRules {
        private List<String> requiredFields;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private List<String> supportedCurrencies;
    }
}