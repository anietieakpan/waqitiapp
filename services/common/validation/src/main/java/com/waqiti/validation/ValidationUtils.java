package com.waqiti.validation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validation Utilities
 * 
 * Comprehensive utility class for financial validation including:
 * - Money amount validation and normalization
 * - Currency validation and conversion
 * - Cross-field validation
 * - Business rule validation
 * - Fraud detection helpers
 * - Validation result processing
 * 
 * SECURITY: Centralized validation logic ensures consistent security
 * controls across all financial operations
 */
@Slf4j
@UtilityClass
public class ValidationUtils {
    
    // Regular expressions for validation
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3,4}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{6,20}$");
    private static final Pattern ROUTING_NUMBER_PATTERN = Pattern.compile("^[0-9]{9}$");
    
    // Financial constants
    private static final BigDecimal MAX_SAFE_AMOUNT = new BigDecimal("999999999999.99999999");
    private static final BigDecimal MIN_MICRO_AMOUNT = new BigDecimal("0.00000001");
    private static final int MAX_PRECISION = 20;
    
    // Fraud detection thresholds
    private static final BigDecimal SUSPICIOUS_AMOUNT_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal HIGH_RISK_AMOUNT_THRESHOLD = new BigDecimal("50000.00");
    
    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public Map<String, Object> getMetadata() {
            return new HashMap<>(metadata);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public String getErrorSummary() {
            return String.join("; ", errors);
        }
        
        public String getWarningSummary() {
            return String.join("; ", warnings);
        }
    }
    
    /**
     * Money amount validation
     */
    public static ValidationResult validateAmount(BigDecimal amount, String currency, 
                                                String transactionType) {
        ValidationResult result = new ValidationResult();
        
        if (amount == null) {
            result.addError("Amount cannot be null");
            return result;
        }
        
        // Basic amount validation
        if (!isValidAmountFormat(amount)) {
            result.addError("Invalid amount format");
        }
        
        // Range validation
        if (!isWithinSafeRange(amount)) {
            result.addError("Amount outside safe processing range");
        }
        
        // Currency-specific validation
        if (currency != null && !currency.isEmpty()) {
            if (!validateAmountForCurrency(amount, currency, result)) {
                // Errors already added in method
            }
        }
        
        // Transaction type validation
        if (transactionType != null) {
            validateAmountForTransactionType(amount, transactionType, result);
        }
        
        // Fraud detection
        performFraudDetection(amount, result);
        
        return result;
    }
    
    /**
     * Currency validation
     */
    public static ValidationResult validateCurrency(String currency, String region, 
                                                  boolean allowCrypto) {
        ValidationResult result = new ValidationResult();
        
        if (currency == null || currency.trim().isEmpty()) {
            result.addError("Currency cannot be null or empty");
            return result;
        }
        
        String upperCurrency = currency.toUpperCase();
        
        // Format validation
        if (!CURRENCY_PATTERN.matcher(upperCurrency).matches()) {
            result.addError("Invalid currency format");
        }
        
        // Support validation
        if (!CurrencyValidator.isSupportedCurrency(upperCurrency)) {
            result.addError("Unsupported currency: " + currency);
        }
        
        // Crypto validation
        if (!allowCrypto && CurrencyValidator.requiresEnhancedCompliance(upperCurrency)) {
            result.addError("Cryptocurrency not allowed");
        }
        
        return result;
    }
    
    /**
     * Cross-field validation for amount and currency
     */
    public static ValidationResult validateAmountCurrencyPair(BigDecimal amount, String currency) {
        ValidationResult result = new ValidationResult();
        
        // Individual validation first
        ValidationResult amountResult = validateAmount(amount, currency, null);
        ValidationResult currencyResult = validateCurrency(currency, null, true);
        
        result.getErrors().addAll(amountResult.getErrors());
        result.getErrors().addAll(currencyResult.getErrors());
        result.getWarnings().addAll(amountResult.getWarnings());
        result.getWarnings().addAll(currencyResult.getWarnings());
        
        if (result.isValid()) {
            // Cross-validation rules
            validateCrossFieldRules(amount, currency, result);
        }
        
        return result;
    }
    
    /**
     * Validate transfer request with source and destination
     */
    public static ValidationResult validateTransfer(BigDecimal amount, String sourceCurrency, 
                                                  String destinationCurrency, String accountFrom, 
                                                  String accountTo) {
        ValidationResult result = new ValidationResult();
        
        // Amount validation
        ValidationResult amountResult = validateAmount(amount, sourceCurrency, "TRANSFER");
        result.getErrors().addAll(amountResult.getErrors());
        result.getWarnings().addAll(amountResult.getWarnings());
        
        // Currency validation
        if (!sourceCurrency.equals(destinationCurrency)) {
            if (!CurrencyValidator.isValidCurrencyPair(sourceCurrency, destinationCurrency)) {
                result.addError("Invalid currency pair for transfer");
            }
        }
        
        // Account validation
        if (!isValidAccountNumber(accountFrom)) {
            result.addError("Invalid source account number");
        }
        
        if (!isValidAccountNumber(accountTo)) {
            result.addError("Invalid destination account number");
        }
        
        if (accountFrom.equals(accountTo)) {
            result.addError("Source and destination accounts cannot be the same");
        }
        
        return result;
    }
    
    /**
     * Validate payment request
     */
    public static ValidationResult validatePayment(BigDecimal amount, String currency, 
                                                 String paymentMethod, String merchantId) {
        ValidationResult result = new ValidationResult();
        
        // Amount validation for payments
        ValidationResult amountResult = validateAmount(amount, currency, "PAYMENT");
        result.getErrors().addAll(amountResult.getErrors());
        result.getWarnings().addAll(amountResult.getWarnings());
        
        // Payment must be positive
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Payment amount must be positive");
        }
        
        // Payment method validation
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            result.addError("Payment method is required");
        }
        
        // Merchant validation
        if (merchantId == null || merchantId.trim().isEmpty()) {
            result.addError("Merchant ID is required");
        }
        
        return result;
    }
    
    /**
     * Format validation helpers
     */
    public static boolean isValidAmountFormat(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        
        try {
            // Check precision and scale limits
            return amount.precision() <= MAX_PRECISION && 
                   amount.scale() <= 18; // Max scale for any currency
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public static boolean isWithinSafeRange(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        
        BigDecimal absAmount = amount.abs();
        return absAmount.compareTo(MAX_SAFE_AMOUNT) <= 0 && 
               absAmount.compareTo(MIN_MICRO_AMOUNT) >= 0;
    }
    
    public static boolean isValidAccountNumber(String accountNumber) {
        return accountNumber != null && 
               ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches();
    }
    
    public static boolean isValidRoutingNumber(String routingNumber) {
        return routingNumber != null && 
               ROUTING_NUMBER_PATTERN.matcher(routingNumber).matches();
    }
    
    /**
     * Normalization utilities
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        
        int scale = getScaleForCurrency(currency);
        return amount.setScale(scale, RoundingMode.HALF_EVEN);
    }
    
    public static String normalizeCurrency(String currency) {
        if (currency == null) {
            return null;
        }
        
        return currency.trim().toUpperCase();
    }
    
    private static int getScaleForCurrency(String currency) {
        if (currency == null) {
            return 2;
        }
        
        String upperCurrency = currency.toUpperCase();
        
        return switch (upperCurrency) {
            case "JPY", "KRW", "VND", "CLP", "ISK", "PYG" -> 0;
            case "BTC" -> 8;
            case "ETH", "USDC", "USDT" -> 18;
            default -> 2; // Standard fiat currency scale
        };
    }
    
    /**
     * Business rule validation
     */
    private static boolean validateAmountForCurrency(BigDecimal amount, String currency, 
                                                   ValidationResult result) {
        String upperCurrency = currency.toUpperCase();
        int expectedScale = getScaleForCurrency(upperCurrency);
        
        if (amount.scale() > expectedScale) {
            result.addError(String.format("Amount has too many decimal places for %s", currency));
            return false;
        }
        
        // Currency-specific range checks
        switch (upperCurrency) {
            case "BTC" -> {
                if (amount.compareTo(new BigDecimal("21000000")) > 0) {
                    result.addError("BTC amount exceeds total supply");
                    return false;
                }
            }
            case "JPY", "KRW" -> {
                if (amount.scale() > 0) {
                    result.addError(String.format("%s should not have decimal places", currency));
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private static void validateAmountForTransactionType(BigDecimal amount, String transactionType, 
                                                       ValidationResult result) {
        switch (transactionType.toUpperCase()) {
            case "PAYMENT", "TRANSFER", "WITHDRAWAL" -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    result.addError(transactionType + " amount must be positive");
                }
            }
            case "REFUND", "REVERSAL", "CHARGEBACK" -> {
                // These can be negative, no additional validation needed
            }
            case "FEE", "INTEREST" -> {
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError(transactionType + " amount cannot be negative");
                }
            }
        }
    }
    
    private static void validateCrossFieldRules(BigDecimal amount, String currency, 
                                              ValidationResult result) {
        // Check if amount is reasonable for the currency
        if ("BTC".equals(currency) && amount.compareTo(new BigDecimal("1000")) > 0) {
            result.addWarning("Large BTC amount - verify transaction");
        }
        
        if ("USD".equals(currency) && amount.compareTo(new BigDecimal("100000")) > 0) {
            result.addWarning("Large USD amount - may require compliance review");
        }
    }
    
    private static void performFraudDetection(BigDecimal amount, ValidationResult result) {
        if (amount == null) {
            return;
        }
        
        BigDecimal absAmount = amount.abs();
        
        if (absAmount.compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            result.addWarning("Amount exceeds suspicious transaction threshold");
            result.addMetadata("fraud_risk", "MEDIUM");
        }
        
        if (absAmount.compareTo(HIGH_RISK_AMOUNT_THRESHOLD) > 0) {
            result.addWarning("Amount exceeds high-risk transaction threshold");
            result.addMetadata("fraud_risk", "HIGH");
        }
        
        // Unusual precision patterns
        if (absAmount.scale() > 4 && absAmount.compareTo(new BigDecimal("1.0")) < 0) {
            result.addWarning("Unusual precision pattern detected");
        }
    }
    
    /**
     * Bean validation integration
     */
    public static <T> ValidationResult validateBean(T object, Validator validator) {
        ValidationResult result = new ValidationResult();
        
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        for (ConstraintViolation<T> violation : violations) {
            result.addError(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        
        return result;
    }
    
    public static <T> Map<String, List<String>> groupValidationErrors(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
            .collect(Collectors.groupingBy(
                violation -> violation.getPropertyPath().toString(),
                Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
            ));
    }
    
    /**
     * Security validation helpers
     */
    public static boolean isPotentiallyMalicious(String input) {
        if (input == null) {
            return false;
        }
        
        String lower = input.toLowerCase();
        
        // Check for SQL injection patterns
        if (lower.contains("'") || lower.contains("--") || lower.contains("/*") || 
            lower.contains("*/") || lower.contains("xp_") || lower.contains("sp_")) {
            return true;
        }
        
        // Check for script injection patterns
        if (lower.contains("<script") || lower.contains("javascript:") || 
            lower.contains("vbscript:") || lower.contains("onload=") ||
            lower.contains("onerror=")) {
            return true;
        }
        
        return false;
    }
    
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        return input.trim()
            .replaceAll("[<>\"'%;()&+]", "")
            .replaceAll("\\s+", " ");
    }
    
    /**
     * Utility methods for validation logging
     */
    public static void logValidationFailure(String operation, String details, ValidationResult result) {
        log.warn("SECURITY: Validation failed for operation: {}, details: {}, errors: {}", 
            operation, details, result.getErrorSummary());
    }
    
    public static void logSuspiciousActivity(String operation, BigDecimal amount, String currency) {
        log.warn("SECURITY: Suspicious financial activity detected - operation: {}, amount: {}, currency: {}", 
            operation, amount, currency);
    }
}