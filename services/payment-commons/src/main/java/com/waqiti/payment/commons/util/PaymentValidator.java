package com.waqiti.payment.commons.util;

import com.waqiti.payment.commons.domain.Money;
import com.waqiti.payment.commons.domain.PaymentMethod;
import com.waqiti.payment.commons.dto.PaymentRequest;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for payment validation logic
 */
@UtilityClass
public class PaymentValidator {
    
    // Currency patterns
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$");
    private static final Pattern SWIFT_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    
    // Email and phone patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );
    
    // Limits
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("0.01");
    
    /**
     * Validate a payment request comprehensively
     */
    public static ValidationResult validatePaymentRequest(PaymentRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (request == null) {
            errors.add("Payment request cannot be null");
            return ValidationResult.invalid(errors, warnings);
        }
        
        // Validate basic fields
        validateBasicFields(request, errors);
        
        // Validate amount
        validateAmount(request.getAmount(), errors, warnings);
        
        // Validate payment method compatibility
        validatePaymentMethodCompatibility(request, errors, warnings);
        
        // Validate geographic restrictions
        validateGeographicRestrictions(request, errors, warnings);
        
        // Validate timing constraints
        validateTimingConstraints(request, errors, warnings);
        
        // Validate compliance requirements
        validateComplianceRequirements(request, errors, warnings);
        
        // Validate business rules
        validateBusinessRules(request, errors, warnings);
        
        return errors.isEmpty() ? 
            ValidationResult.valid(warnings) : 
            ValidationResult.invalid(errors, warnings);
    }
    
    /**
     * Validate currency code format
     */
    public static boolean isValidCurrencyCode(String currencyCode) {
        return currencyCode != null && CURRENCY_CODE_PATTERN.matcher(currencyCode).matches();
    }
    
    /**
     * Validate IBAN format
     */
    public static boolean isValidIBAN(String iban) {
        return iban != null && IBAN_PATTERN.matcher(iban.toUpperCase().replaceAll("\\s", "")).matches();
    }
    
    /**
     * Validate SWIFT/BIC format
     */
    public static boolean isValidSWIFT(String swift) {
        return swift != null && SWIFT_PATTERN.matcher(swift.toUpperCase()).matches();
    }
    
    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Validate phone number format
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && PHONE_PATTERN.matcher(phoneNumber).matches();
    }
    
    /**
     * Check if amount is within acceptable limits
     */
    public static boolean isValidAmount(Money amount) {
        if (amount == null) return false;
        
        BigDecimal value = amount.getAmount();
        return value.compareTo(MIN_PAYMENT_AMOUNT) >= 0 && 
               value.compareTo(MAX_PAYMENT_AMOUNT) <= 0;
    }
    
    /**
     * Check if payment method supports the given currency
     */
    public static boolean isPaymentMethodCompatibleWithCurrency(PaymentMethod method, String currency) {
        if (method == null || currency == null) return false;
        
        // Cryptocurrency methods only support their respective currencies
        if (method.isCryptocurrency()) {
            switch (method) {
                case BITCOIN:
                    return "BTC".equals(currency);
                case ETHEREUM:
                    return "ETH".equals(currency);
                case LITECOIN:
                    return "LTC".equals(currency);
                default:
                    return false;
            }
        }
        
        // Most other methods support major fiat currencies
        return isValidCurrencyCode(currency);
    }
    
    /**
     * Check if payment method is available in the given country
     */
    public static boolean isPaymentMethodAvailableInCountry(PaymentMethod method, String country) {
        if (method == null || country == null) return true; // Default to available
        
        // Country-specific restrictions
        switch (method) {
            case SEPA:
                return isSEPACountry(country);
            case ACH:
                return "US".equals(country);
            case INTERAC:
                return "CA".equals(country);
            default:
                return true; // Most methods are globally available
        }
    }
    
    private static void validateBasicFields(PaymentRequest request, List<String> errors) {
        if (request.getRequestId() == null) {
            errors.add("Request ID is required");
        }
        
        if (request.getSenderId() == null) {
            errors.add("Sender ID is required");
        }
        
        if (request.getRecipientId() == null) {
            errors.add("Recipient ID is required");
        }
        
        if (request.getRecipientType() == null || request.getRecipientType().trim().isEmpty()) {
            errors.add("Recipient type is required");
        }
        
        if (request.getPaymentMethod() == null) {
            errors.add("Payment method is required");
        }
        
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            errors.add("Description cannot exceed 500 characters");
        }
    }
    
    private static void validateAmount(Money amount, List<String> errors, List<String> warnings) {
        if (amount == null) {
            errors.add("Payment amount is required");
            return;
        }
        
        try {
            amount.validatePositive();
        } catch (IllegalArgumentException e) {
            errors.add("Payment amount must be positive");
        }
        
        if (!isValidAmount(amount)) {
            errors.add(String.format("Payment amount must be between %s and %s %s",
                MIN_PAYMENT_AMOUNT, MAX_PAYMENT_AMOUNT, amount.getCurrencyCode()));
        }
        
        if (!isValidCurrencyCode(amount.getCurrencyCode())) {
            errors.add("Invalid currency code: " + amount.getCurrencyCode());
        }
        
        // Warning for unusual amounts
        if (amount.getAmount().scale() > amount.getCurrency().getDefaultFractionDigits()) {
            warnings.add("Amount has more decimal places than typical for " + amount.getCurrencyCode());
        }
    }
    
    private static void validatePaymentMethodCompatibility(PaymentRequest request, 
                                                          List<String> errors, 
                                                          List<String> warnings) {
        if (request.getPaymentMethod() == null || request.getAmount() == null) {
            return;
        }
        
        PaymentMethod method = request.getPaymentMethod();
        String currency = request.getAmount().getCurrencyCode();
        
        if (!isPaymentMethodCompatibleWithCurrency(method, currency)) {
            errors.add(String.format("Payment method %s does not support currency %s", 
                method.getDisplayName(), currency));
        }
        
        // Check country availability
        if (request.getSenderCountry() != null && 
            !isPaymentMethodAvailableInCountry(method, request.getSenderCountry())) {
            errors.add(String.format("Payment method %s is not available in %s", 
                method.getDisplayName(), request.getSenderCountry()));
        }
        
        // Warnings for suboptimal combinations
        if (method.isInternational() && !request.isInternational()) {
            warnings.add("Using international payment method for domestic transaction");
        }
        
        if (!method.isInstantSettlement() && request.getScheduledAt() == null) {
            warnings.add(String.format("Payment method %s requires clearing time (%d minutes)", 
                method.getDisplayName(), method.getTypicalProcessingTimeMinutes()));
        }
    }
    
    private static void validateGeographicRestrictions(PaymentRequest request, 
                                                      List<String> errors, 
                                                      List<String> warnings) {
        String senderCountry = request.getSenderCountry();
        String recipientCountry = request.getRecipientCountry();
        
        if (senderCountry != null && recipientCountry != null) {
            // Check for sanctioned countries (simplified example)
            if (isSanctionedCountry(senderCountry) || isSanctionedCountry(recipientCountry)) {
                errors.add("Payments to/from sanctioned countries are not permitted");
            }
            
            // International payment warnings
            if (!senderCountry.equals(recipientCountry)) {
                warnings.add("International payment may require additional compliance checks");
                
                if (request.getAmount() != null && request.getAmount().getAmount().doubleValue() > 1000) {
                    warnings.add("High-value international payment may require enhanced KYC verification");
                }
            }
        }
    }
    
    private static void validateTimingConstraints(PaymentRequest request, 
                                                 List<String> errors, 
                                                 List<String> warnings) {
        Instant now = Instant.now();
        
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(now)) {
            errors.add("Payment request has already expired");
        }
        
        if (request.getScheduledAt() != null) {
            if (request.getScheduledAt().isBefore(now)) {
                errors.add("Scheduled payment time cannot be in the past");
            } else if (request.getScheduledAt().isAfter(now.plusSeconds(86400 * 365))) {
                warnings.add("Scheduled payment is more than one year in the future");
            }
        }
        
        // Check business hours for certain payment methods
        if (request.getPaymentMethod() != null && 
            request.getPaymentMethod().isBankTransfer() && 
            !isBusinessHours(now)) {
            warnings.add("Bank transfer requested outside business hours may be delayed");
        }
    }
    
    private static void validateComplianceRequirements(PaymentRequest request, 
                                                      List<String> errors, 
                                                      List<String> warnings) {
        // KYC requirements
        if (Boolean.TRUE.equals(request.getRequiresKYC()) && 
            request.getPaymentMethod() != null && 
            !request.getPaymentMethod().requiresKYC()) {
            warnings.add("KYC verification requested for payment method that typically doesn't require it");
        }
        
        // High-value transaction requirements
        if (request.isHighValue()) {
            warnings.add("High-value transaction requires enhanced monitoring and reporting");
            
            if (request.getComplianceLevel() == null || 
                "STANDARD".equals(request.getComplianceLevel())) {
                warnings.add("High-value transaction should use ENHANCED or PREMIUM compliance level");
            }
        }
        
        // Cryptocurrency compliance
        if (request.getPaymentMethod() != null && 
            request.getPaymentMethod().isCryptocurrency()) {
            warnings.add("Cryptocurrency transactions require additional compliance verification");
        }
    }
    
    private static void validateBusinessRules(PaymentRequest request, 
                                            List<String> errors, 
                                            List<String> warnings) {
        // Self-payment check
        if (request.getSenderId() != null && 
            request.getSenderId().equals(request.getRecipientId())) {
            errors.add("Cannot send payment to yourself");
        }
        
        // Split payment validation
        if (Boolean.TRUE.equals(request.getIsSplitPayment()) && 
            request.getSplitPaymentId() == null) {
            errors.add("Split payment ID is required for split payments");
        }
        
        // Partial payment validation
        if (Boolean.TRUE.equals(request.getAllowPartialPayment())) {
            if (request.getMinimumAmount() == null) {
                warnings.add("Minimum amount should be specified for partial payments");
            } else if (request.getAmount() != null && 
                      request.getMinimumAmount().isGreaterThan(request.getAmount())) {
                errors.add("Minimum amount cannot be greater than requested amount");
            }
        }
        
        // Fee structure validation
        if ("RECIPIENT_PAYS".equals(request.getFeeStructure()) && 
            request.getPaymentMethod() != null && 
            !request.getPaymentMethod().supportsRefunds()) {
            warnings.add("Recipient-pays fee structure may cause issues with non-refundable payment methods");
        }
    }
    
    // Utility methods
    private static boolean isSEPACountry(String country) {
        // Simplified SEPA country list
        String[] sepaCountries = {
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU",
            "IS", "IE", "IT", "LV", "LI", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO",
            "SK", "SI", "ES", "SE", "CH", "GB"
        };
        
        for (String sepaCountry : sepaCountries) {
            if (sepaCountry.equals(country)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isSanctionedCountry(String country) {
        // Simplified sanctions list - in production, this would be dynamic
        String[] sanctionedCountries = {"IR", "KP", "SY"}; // Iran, North Korea, Syria
        
        for (String sanctioned : sanctionedCountries) {
            if (sanctioned.equals(country)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isBusinessHours(Instant time) {
        // Simplified business hours check (9 AM - 5 PM UTC)
        int hour = time.atZone(java.time.ZoneOffset.UTC).getHour();
        return hour >= 9 && hour < 17;
    }
    
    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        private ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }
        
        public static ValidationResult valid(List<String> warnings) {
            return new ValidationResult(true, null, warnings);
        }
        
        public static ValidationResult invalid(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public String getErrorMessage() {
            return String.join("; ", errors);
        }
        
        public String getWarningMessage() {
            return String.join("; ", warnings);
        }
    }
}