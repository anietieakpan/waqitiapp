package com.waqiti.validation;

import com.waqiti.validation.annotation.ValidCurrency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.Currency;
import java.util.Locale;

/**
 * Currency Code Validator
 * 
 * Comprehensive validation for currency codes including:
 * - Valid ISO 4217 currency codes
 * - Supported currency verification
 * - Regional compliance checks
 * - Cryptocurrency validation
 * - Sanctions and restrictions compliance
 * - Cross-field currency matching
 * 
 * SECURITY: Prevents invalid currency processing, regulatory violations,
 * and potential fraud through currency manipulation
 */
@Slf4j
@Component
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
    
    // Major fiat currencies supported by the platform
    private static final Set<String> SUPPORTED_FIAT_CURRENCIES = Set.of(
        "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "SEK", "NZD",
        "NOK", "DKK", "PLN", "CZK", "HUF", "SGD", "HKD", "KRW", "INR", "BRL",
        "MXN", "ZAR", "RUB", "TRY", "THB", "MYR", "PHP", "IDR", "VND", "EGP"
    );
    
    // Supported cryptocurrencies
    private static final Set<String> SUPPORTED_CRYPTOCURRENCIES = Set.of(
        "BTC", "ETH", "LTC", "XRP", "ADA", "DOT", "LINK", "BCH", "XLM", "DOGE",
        "MATIC", "AVAX", "SOL", "ATOM", "XTZ", "ALGO", "VET", "FIL", "THETA"
    );
    
    // Supported stablecoins
    private static final Set<String> SUPPORTED_STABLECOINS = Set.of(
        "USDC", "USDT", "BUSD", "DAI", "TUSD", "USDP", "GUSD", "HUSD", "USDN"
    );
    
    // Sanctioned or restricted currencies (compliance)
    private static final Set<String> RESTRICTED_CURRENCIES = Set.of(
        "IRR", // Iranian Rial
        "KPW", // North Korean Won
        "SYP", // Syrian Pound
        "VES"  // Venezuelan Bolívar
    );
    
    // Discontinued currencies
    private static final Set<String> DISCONTINUED_CURRENCIES = Set.of(
        "DEM", "FRF", "ITL", "ESP", "NLG", "BEF", "FIM", "IEP", "ATS", "PTE"
    );
    
    // Regional currency mappings
    private static final Set<String> US_CURRENCIES = Set.of("USD");
    private static final Set<String> EU_CURRENCIES = Set.of("EUR");
    private static final Set<String> UK_CURRENCIES = Set.of("GBP");
    private static final Set<String> ASIA_PACIFIC_CURRENCIES = Set.of(
        "JPY", "CNY", "KRW", "SGD", "HKD", "INR", "THB", "MYR", "PHP", "IDR", "VND"
    );
    
    private ValidCurrency annotation;
    
    @Override
    public void initialize(ValidCurrency annotation) {
        this.annotation = annotation;
    }
    
    @Override
    public boolean isValid(String currency, ConstraintValidatorContext context) {
        if (currency == null || currency.trim().isEmpty()) {
            addViolation(context, "Currency code cannot be null or empty");
            return false;
        }
        
        String upperCurrency = currency.trim().toUpperCase();
        
        return performComprehensiveValidation(upperCurrency, context);
    }
    
    /**
     * Comprehensive currency validation
     */
    private boolean performComprehensiveValidation(String currency, ConstraintValidatorContext context) {
        boolean isValid;
        
        // Basic format validation
        if (!validateBasicFormat(currency, context)) {
            isValid = false;
        }
        
        // Restriction checks
        if (!validateRestrictions(currency, context)) {
            isValid = false;
        }
        
        // ISO 4217 validation
        if (!validateISO4217(currency, context)) {
            isValid = false;
        }
        
        // Support validation
        if (!validateSupport(currency, context)) {
            isValid = false;
        }
        
        // Cryptocurrency validation
        if (!validateCryptocurrency(currency, context)) {
            isValid = false;
        }
        
        // Regional compliance validation
        if (!validateRegionalCompliance(currency, context)) {
            isValid = false;
        }
        
        // Transaction type compatibility
        if (!validateTransactionTypeCompatibility(currency, context)) {
            isValid = false;
        }
        
        // Required currencies validation
        if (!validateRequiredCurrencies(currency, context)) {
            isValid = false;
        }
        
        // Active status validation
        if (!validateActiveStatus(currency, context)) {
            isValid = false;
        }
        
        // Log validation attempts for security monitoring
        if (!isValid) {
            log.warn("SECURITY: Currency validation failed for: {}, type: {}, region: {}", 
                currency, annotation.transactionType(), annotation.region());
        }
        
        return isValid;
    }
    
    /**
     * Basic format validation
     */
    private boolean validateBasicFormat(String currency, ConstraintValidatorContext context) {
        // Currency code should be 3-4 characters
        if (currency.length() < 3 || currency.length() > 4) {
            addViolation(context, "Currency code must be 3-4 characters long");
            return false;
        }
        
        // Should contain only letters
        if (!currency.matches("^[A-Z]+$")) {
            addViolation(context, "Currency code must contain only uppercase letters");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate restrictions and sanctions compliance
     */
    private boolean validateRestrictions(String currency, ConstraintValidatorContext context) {
        // Check against restricted currencies
        if (RESTRICTED_CURRENCIES.contains(currency)) {
            addViolation(context, "Currency is restricted due to sanctions: " + currency);
            return false;
        }
        
        // Check against specific restricted currencies from annotation
        for (String restricted : annotation.restrictedCurrencies()) {
            if (restricted.equalsIgnoreCase(currency)) {
                addViolation(context, "Currency is not allowed: " + currency);
                return false;
            }
        }
        
        // Check against discontinued currencies
        if (DISCONTINUED_CURRENCIES.contains(currency)) {
            addViolation(context, "Currency is discontinued: " + currency);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate ISO 4217 compliance for fiat currencies
     */
    private boolean validateISO4217(String currency, ConstraintValidatorContext context) {
        // Skip ISO validation for crypto currencies
        if (isCryptoCurrency(currency)) {
            return true;
        }
        
        try {
            // Validate against ISO 4217 standard
            Currency.getInstance(currency);
            return true;
        } catch (IllegalArgumentException e) {
            addViolation(context, "Invalid ISO 4217 currency code: " + currency);
            return false;
        }
    }
    
    /**
     * Validate currency support
     */
    private boolean validateSupport(String currency, ConstraintValidatorContext context) {
        if (!annotation.supportedOnly()) {
            return true; // Skip support validation if not required
        }
        
        boolean isSupported = SUPPORTED_FIAT_CURRENCIES.contains(currency) ||
                             (annotation.allowCrypto() && SUPPORTED_CRYPTOCURRENCIES.contains(currency)) ||
                             (annotation.allowStablecoins() && SUPPORTED_STABLECOINS.contains(currency));
        
        if (!isSupported) {
            addViolation(context, "Currency is not supported: " + currency);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate cryptocurrency
     */
    private boolean validateCryptocurrency(String currency, ConstraintValidatorContext context) {
        boolean isCrypto = isCryptoCurrency(currency);
        boolean isStablecoin = SUPPORTED_STABLECOINS.contains(currency);
        
        if (isCrypto && !annotation.allowCrypto()) {
            addViolation(context, "Cryptocurrency not allowed: " + currency);
            return false;
        }
        
        if (isStablecoin && !annotation.allowStablecoins()) {
            addViolation(context, "Stablecoin not allowed: " + currency);
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if currency is a cryptocurrency
     */
    private boolean isCryptoCurrency(String currency) {
        return SUPPORTED_CRYPTOCURRENCIES.contains(currency) || 
               SUPPORTED_STABLECOINS.contains(currency);
    }
    
    /**
     * Validate regional compliance
     */
    private boolean validateRegionalCompliance(String currency, ConstraintValidatorContext context) {
        if (annotation.region().isEmpty()) {
            return true; // No regional restrictions
        }
        
        String region = annotation.region().toUpperCase();
        
        return switch (region) {
            case "US" -> {
                if (!US_CURRENCIES.contains(currency) && !isCryptoCurrency(currency)) {
                    addViolation(context, "Currency not supported in US region: " + currency);
                    yield false;
                }
                yield true;
            }
            case "EU" -> {
                if (!EU_CURRENCIES.contains(currency) && !isCryptoCurrency(currency)) {
                    addViolation(context, "Currency not supported in EU region: " + currency);
                    yield false;
                }
                yield true;
            }
            case "UK" -> {
                if (!UK_CURRENCIES.contains(currency) && !isCryptoCurrency(currency)) {
                    addViolation(context, "Currency not supported in UK region: " + currency);
                    yield false;
                }
                yield true;
            }
            case "ASIA_PACIFIC" -> {
                if (!ASIA_PACIFIC_CURRENCIES.contains(currency) && !isCryptoCurrency(currency)) {
                    addViolation(context, "Currency not supported in Asia-Pacific region: " + currency);
                    yield false;
                }
                yield true;
            }
            default -> true; // No specific validation for other regions
        };
    }
    
    /**
     * Validate transaction type compatibility
     */
    private boolean validateTransactionTypeCompatibility(String currency, 
                                                       ConstraintValidatorContext context) {
        ValidCurrency.TransactionType type = annotation.transactionType();
        
        return switch (type) {
            case CRYPTO -> {
                if (!isCryptoCurrency(currency)) {
                    addViolation(context, "Only cryptocurrency allowed for crypto transactions");
                    yield false;
                }
                yield true;
            }
            case INTERNATIONAL -> {
                // International transfers may have additional currency restrictions
                if (RESTRICTED_CURRENCIES.contains(currency)) {
                    addViolation(context, "Currency not allowed for international transfers: " + currency);
                    yield false;
                }
                yield true;
            }
            case REMITTANCE -> {
                // Remittance services may have specific currency corridors
                if (!SUPPORTED_FIAT_CURRENCIES.contains(currency)) {
                    addViolation(context, "Currency not supported for remittance: " + currency);
                    yield false;
                }
                yield true;
            }
            default -> true; // No specific restrictions for other types
        };
    }
    
    /**
     * Validate required currencies
     */
    private boolean validateRequiredCurrencies(String currency, ConstraintValidatorContext context) {
        String[] required = annotation.requiredCurrencies();
        
        if (required.length == 0) {
            return true; // No required currencies specified
        }
        
        for (String requiredCurrency : required) {
            if (requiredCurrency.equalsIgnoreCase(currency)) {
                return true;
            }
        }
        
        addViolation(context, "Currency must be one of: " + String.join(", ", required));
        return false;
    }
    
    /**
     * Validate currency is active/trading
     */
    private boolean validateActiveStatus(String currency, ConstraintValidatorContext context) {
        if (!annotation.checkActiveStatus()) {
            return true;
        }
        
        // Check if currency is discontinued
        if (DISCONTINUED_CURRENCIES.contains(currency)) {
            addViolation(context, "Currency is no longer active: " + currency);
            return false;
        }
        
        // In a real implementation, this would check against a live currency service
        // to verify the currency is currently being traded and active
        
        return true;
    }
    
    /**
     * Add validation violation message
     */
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    /**
     * Utility method to check if currency is supported
     */
    public static boolean isSupportedCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return false;
        }
        
        String upperCurrency = currency.toUpperCase();
        return SUPPORTED_FIAT_CURRENCIES.contains(upperCurrency) ||
               SUPPORTED_CRYPTOCURRENCIES.contains(upperCurrency) ||
               SUPPORTED_STABLECOINS.contains(upperCurrency);
    }
    
    /**
     * Get all supported currencies
     */
    public static Set<String> getAllSupportedCurrencies() {
        return Set.of(
            SUPPORTED_FIAT_CURRENCIES.toArray(new String[0]),
            SUPPORTED_CRYPTOCURRENCIES.toArray(new String[0]),
            SUPPORTED_STABLECOINS.toArray(new String[0])
        );
    }
    
    /**
     * Utility method to check if currency pair is valid for exchange
     */
    public static boolean isValidCurrencyPair(String fromCurrency, String toCurrency) {
        return isSupportedCurrency(fromCurrency) && 
               isSupportedCurrency(toCurrency) &&
               !fromCurrency.equalsIgnoreCase(toCurrency);
    }
    
    /**
     * Check if currency requires enhanced compliance
     */
    public static boolean requiresEnhancedCompliance(String currency) {
        if (currency == null) {
            return false;
        }
        
        String upperCurrency = currency.toUpperCase();
        
        // Cryptocurrencies generally require enhanced compliance
        return SUPPORTED_CRYPTOCURRENCIES.contains(upperCurrency) ||
               SUPPORTED_STABLECOINS.contains(upperCurrency);
    }
}