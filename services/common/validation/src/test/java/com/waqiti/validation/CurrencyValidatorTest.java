package com.waqiti.validation;

import com.waqiti.validation.annotation.ValidCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CurrencyValidator
 */
@ExtendWith(MockitoExtension.class)
class CurrencyValidatorTest {

    private CurrencyValidator validator;
    
    @Mock
    private ValidCurrency annotation;
    
    @Mock
    private ConstraintValidatorContext context;
    
    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        validator = new CurrencyValidator();
        
        // Setup default annotation behavior
        when(annotation.supportedOnly()).thenReturn(true);
        when(annotation.allowCrypto()).thenReturn(false);
        when(annotation.allowStablecoins()).thenReturn(false);
        when(annotation.region()).thenReturn("");
        when(annotation.transactionType()).thenReturn(ValidCurrency.TransactionType.GENERAL);
        when(annotation.restrictedCurrencies()).thenReturn(new String[]{});
        when(annotation.requiredCurrencies()).thenReturn(new String[]{});
        when(annotation.checkActiveStatus()).thenReturn(true);
        
        // Setup context mocking
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
        
        validator.initialize(annotation);
    }

    @Test
    void testValidMajorCurrencies() {
        assertTrue(validator.isValid("USD", context));
        assertTrue(validator.isValid("EUR", context));
        assertTrue(validator.isValid("GBP", context));
        assertTrue(validator.isValid("JPY", context));
        assertTrue(validator.isValid("CAD", context));
        assertTrue(validator.isValid("AUD", context));
        
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testNullCurrency() {
        assertFalse(validator.isValid(null, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Currency code cannot be null or empty");
    }

    @Test
    void testEmptyCurrency() {
        assertFalse(validator.isValid("", context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Currency code cannot be null or empty");
    }

    @Test
    void testWhitespaceCurrency() {
        assertFalse(validator.isValid("   ", context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Currency code cannot be null or empty");
    }

    @Test
    void testInvalidCurrencyLength() {
        // Too short
        assertFalse(validator.isValid("US", context));
        verify(context).buildConstraintViolationWithTemplate("Currency code must be 3-4 characters long");
        
        // Too long
        assertFalse(validator.isValid("USDD", context));
    }

    @Test
    void testInvalidCurrencyFormat() {
        assertFalse(validator.isValid("US1", context));
        verify(context).buildConstraintViolationWithTemplate("Currency code must contain only uppercase letters");
    }

    @Test
    void testLowercaseCurrency() {
        // Should work as validator converts to uppercase internally
        assertTrue(validator.isValid("usd", context));
        assertTrue(validator.isValid("eur", context));
    }

    @Test
    void testRestrictedCurrencies() {
        // Iranian Rial should be restricted
        assertFalse(validator.isValid("IRR", context));
        verify(context).buildConstraintViolationWithTemplate("Currency is restricted due to sanctions: IRR");
        
        // North Korean Won should be restricted
        assertFalse(validator.isValid("KPW", context));
    }

    @Test
    void testCustomRestrictedCurrencies() {
        when(annotation.restrictedCurrencies()).thenReturn(new String[]{"USD", "EUR"});
        validator.initialize(annotation);
        
        assertFalse(validator.isValid("USD", context));
        verify(context).buildConstraintViolationWithTemplate("Currency is not allowed: USD");
    }

    @Test
    void testRequiredCurrencies() {
        when(annotation.requiredCurrencies()).thenReturn(new String[]{"USD", "EUR"});
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("USD", context));
        assertTrue(validator.isValid("EUR", context));
        
        assertFalse(validator.isValid("GBP", context));
        verify(context).buildConstraintViolationWithTemplate("Currency must be one of: USD, EUR");
    }

    @Test
    void testUnsupportedCurrency() {
        assertFalse(validator.isValid("XYZ", context));
        verify(context).buildConstraintViolationWithTemplate("Invalid ISO 4217 currency code: XYZ");
    }

    @Test
    void testCryptocurrencyNotAllowed() {
        assertFalse(validator.isValid("BTC", context));
        verify(context).buildConstraintViolationWithTemplate("Cryptocurrency not allowed: BTC");
    }

    @Test
    void testCryptocurrencyAllowed() {
        when(annotation.allowCrypto()).thenReturn(true);
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("BTC", context));
        assertTrue(validator.isValid("ETH", context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testStablecoinNotAllowed() {
        when(annotation.allowCrypto()).thenReturn(true);
        validator.initialize(annotation);
        
        assertFalse(validator.isValid("USDC", context));
        verify(context).buildConstraintViolationWithTemplate("Stablecoin not allowed: USDC");
    }

    @Test
    void testStablecoinAllowed() {
        when(annotation.allowCrypto()).thenReturn(true);
        when(annotation.allowStablecoins()).thenReturn(true);
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("USDC", context));
        assertTrue(validator.isValid("USDT", context));
    }

    @Test
    void testRegionalRestrictions() {
        when(annotation.region()).thenReturn("US");
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("USD", context));
        
        // EUR should not be allowed in US region for fiat-only transactions
        assertFalse(validator.isValid("EUR", context));
        verify(context).buildConstraintViolationWithTemplate("Currency not supported in US region: EUR");
    }

    @Test
    void testEURegionalRestrictions() {
        when(annotation.region()).thenReturn("EU");
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("EUR", context));
        
        // USD should not be allowed in EU region for fiat-only transactions
        assertFalse(validator.isValid("USD", context));
        verify(context).buildConstraintViolationWithTemplate("Currency not supported in EU region: USD");
    }

    @Test
    void testCryptoTransactionType() {
        when(annotation.transactionType()).thenReturn(ValidCurrency.TransactionType.CRYPTO);
        when(annotation.allowCrypto()).thenReturn(true);
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("BTC", context));
        assertTrue(validator.isValid("ETH", context));
        
        // Fiat currencies should not be allowed for crypto transactions
        assertFalse(validator.isValid("USD", context));
        verify(context).buildConstraintViolationWithTemplate("Only cryptocurrency allowed for crypto transactions");
    }

    @Test
    void testInternationalTransactionType() {
        when(annotation.transactionType()).thenReturn(ValidCurrency.TransactionType.INTERNATIONAL);
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("USD", context));
        assertTrue(validator.isValid("EUR", context));
        
        // Restricted currencies should not be allowed for international transfers
        assertFalse(validator.isValid("IRR", context));
        verify(context).buildConstraintViolationWithTemplate("Currency not allowed for international transfers: IRR");
    }

    @Test
    void testRemittanceTransactionType() {
        when(annotation.transactionType()).thenReturn(ValidCurrency.TransactionType.REMITTANCE);
        validator.initialize(annotation);
        
        assertTrue(validator.isValid("USD", context));
        assertTrue(validator.isValid("EUR", context));
        
        // Cryptocurrencies should not be supported for remittance
        assertFalse(validator.isValid("BTC", context));
        verify(context).buildConstraintViolationWithTemplate("Currency not supported for remittance: BTC");
    }

    @Test
    void testDiscontinuedCurrencies() {
        // Deutsche Mark (discontinued)
        assertFalse(validator.isValid("DEM", context));
        verify(context).buildConstraintViolationWithTemplate("Currency is discontinued: DEM");
        
        // French Franc (discontinued)
        assertFalse(validator.isValid("FRF", context));
    }

    @Test
    void testSupportedOnlyDisabled() {
        when(annotation.supportedOnly()).thenReturn(false);
        validator.initialize(annotation);
        
        // Should allow any valid ISO currency even if not explicitly supported
        assertTrue(validator.isValid("CHF", context)); // Swiss Franc
        assertTrue(validator.isValid("SEK", context)); // Swedish Krona
    }

    @Test
    void testStaticUtilityMethods() {
        // Test isSupportedCurrency
        assertTrue(CurrencyValidator.isSupportedCurrency("USD"));
        assertTrue(CurrencyValidator.isSupportedCurrency("BTC"));
        assertTrue(CurrencyValidator.isSupportedCurrency("USDC"));
        assertFalse(CurrencyValidator.isSupportedCurrency("XYZ"));
        assertFalse(CurrencyValidator.isSupportedCurrency(null));
        
        // Test isValidCurrencyPair
        assertTrue(CurrencyValidator.isValidCurrencyPair("USD", "EUR"));
        assertTrue(CurrencyValidator.isValidCurrencyPair("BTC", "USD"));
        assertFalse(CurrencyValidator.isValidCurrencyPair("USD", "USD")); // Same currency
        assertFalse(CurrencyValidator.isValidCurrencyPair("USD", "XYZ")); // Invalid currency
        
        // Test requiresEnhancedCompliance
        assertTrue(CurrencyValidator.requiresEnhancedCompliance("BTC"));
        assertTrue(CurrencyValidator.requiresEnhancedCompliance("USDC"));
        assertFalse(CurrencyValidator.requiresEnhancedCompliance("USD"));
        assertFalse(CurrencyValidator.requiresEnhancedCompliance(null));
    }

    @Test
    void testCaseInsensitiveValidation() {
        assertTrue(validator.isValid("usd", context));
        assertTrue(validator.isValid("Usd", context));
        assertTrue(validator.isValid("USD", context));
        assertTrue(validator.isValid("eur", context));
        assertTrue(validator.isValid("EUR", context));
    }

    @Test
    void testActiveStatusCheck() {
        when(annotation.checkActiveStatus()).thenReturn(true);
        validator.initialize(annotation);
        
        // Discontinued currencies should fail active status check
        assertFalse(validator.isValid("DEM", context));
        verify(context).buildConstraintViolationWithTemplate("Currency is no longer active: DEM");
    }

    @Test
    void testActiveStatusCheckDisabled() {
        when(annotation.checkActiveStatus()).thenReturn(false);
        validator.initialize(annotation);
        
        // Should pass even for discontinued currencies when check is disabled
        // But will still fail for being discontinued in restriction check
        assertFalse(validator.isValid("DEM", context));
        verify(context).buildConstraintViolationWithTemplate("Currency is discontinued: DEM");
    }

    @Test
    void testFourCharacterCurrency() {
        // Some crypto currencies might have 4 characters
        when(annotation.allowCrypto()).thenReturn(true);
        validator.initialize(annotation);
        
        // Should accept 4-character currency codes
        // Note: This would need to be added to supported currencies in real implementation
        String fourCharCurrency = "BTCD"; // Example 4-char code
        
        // Will fail because it's not in supported list, but should pass format validation
        assertFalse(validator.isValid(fourCharCurrency, context));
        // The error should be about support, not format
        verify(context).buildConstraintViolationWithTemplate("Invalid ISO 4217 currency code: BTCD");
    }
}