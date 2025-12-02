package com.waqiti.validation;

import com.waqiti.validation.annotation.ValidMoneyAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MoneyAmountValidator
 */
@ExtendWith(MockitoExtension.class)
class MoneyAmountValidatorTest {

    private MoneyAmountValidator validator;
    
    @Mock
    private ValidMoneyAmount annotation;
    
    @Mock
    private ConstraintValidatorContext context;
    
    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        validator = new MoneyAmountValidator();
        
        // Setup default annotation behavior
        when(annotation.min()).thenReturn(0.01);
        when(annotation.max()).thenReturn(1000000.00);
        when(annotation.scale()).thenReturn(2);
        when(annotation.allowZero()).thenReturn(false);
        when(annotation.allowNegative()).thenReturn(false);
        when(annotation.currency()).thenReturn("");
        when(annotation.transactionType()).thenReturn(ValidMoneyAmount.TransactionType.GENERAL);
        when(annotation.userTier()).thenReturn(ValidMoneyAmount.UserTier.STANDARD);
        when(annotation.checkFraudLimits()).thenReturn(true);
        
        // Setup context mocking
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
        
        validator.initialize(annotation);
    }

    @Test
    void testValidAmount() {
        BigDecimal validAmount = new BigDecimal("100.50");
        assertTrue(validator.isValid(validAmount, context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testNullAmount() {
        assertFalse(validator.isValid(null, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Amount cannot be null");
    }

    @Test
    void testZeroAmountNotAllowed() {
        BigDecimal zeroAmount = BigDecimal.ZERO;
        assertFalse(validator.isValid(zeroAmount, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Amount cannot be zero");
    }

    @Test
    void testZeroAmountAllowed() {
        when(annotation.allowZero()).thenReturn(true);
        validator.initialize(annotation);
        
        BigDecimal zeroAmount = BigDecimal.ZERO;
        assertTrue(validator.isValid(zeroAmount, context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testNegativeAmountNotAllowed() {
        BigDecimal negativeAmount = new BigDecimal("-50.00");
        assertFalse(validator.isValid(negativeAmount, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Amount cannot be negative");
    }

    @Test
    void testNegativeAmountAllowed() {
        when(annotation.allowNegative()).thenReturn(true);
        validator.initialize(annotation);
        
        BigDecimal negativeAmount = new BigDecimal("-50.00");
        assertTrue(validator.isValid(negativeAmount, context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testAmountBelowMinimum() {
        BigDecimal belowMinAmount = new BigDecimal("0.005");
        assertFalse(validator.isValid(belowMinAmount, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Amount must be at least 0.01");
    }

    @Test
    void testAmountAboveMaximum() {
        BigDecimal aboveMaxAmount = new BigDecimal("2000000.00");
        assertFalse(validator.isValid(aboveMaxAmount, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Amount cannot exceed 1000000.0");
    }

    @Test
    void testExcessiveDecimalPlaces() {
        BigDecimal excessiveDecimalAmount = new BigDecimal("100.123");
        assertFalse(validator.isValid(excessiveDecimalAmount, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Amount cannot have more than 2 decimal places");
    }

    @Test
    void testValidStringAmount() {
        String validStringAmount = "123.45";
        assertTrue(validator.isValid(validStringAmount, context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testInvalidStringAmount() {
        String invalidStringAmount = "abc.def";
        assertFalse(validator.isValid(invalidStringAmount, context));
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Invalid amount format");
    }

    @Test
    void testStringAmountWithCurrencySymbols() {
        String amountWithSymbols = "$123.45";
        assertTrue(validator.isValid(amountWithSymbols, context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void testUSDCurrencyValidation() {
        when(annotation.currency()).thenReturn("USD");
        validator.initialize(annotation);
        
        BigDecimal validUSDAmount = new BigDecimal("100.50");
        assertTrue(validator.isValid(validUSDAmount, context));
        
        // Test with too many decimal places for USD
        BigDecimal invalidUSDAmount = new BigDecimal("100.123");
        assertFalse(validator.isValid(invalidUSDAmount, context));
    }

    @Test
    void testJPYCurrencyValidation() {
        when(annotation.currency()).thenReturn("JPY");
        validator.initialize(annotation);
        
        BigDecimal validJPYAmount = new BigDecimal("100");
        assertTrue(validator.isValid(validJPYAmount, context));
        
        // JPY should not have decimal places
        BigDecimal invalidJPYAmount = new BigDecimal("100.50");
        assertFalse(validator.isValid(invalidJPYAmount, context));
    }

    @Test
    void testBTCCurrencyValidation() {
        when(annotation.currency()).thenReturn("BTC");
        when(annotation.scale()).thenReturn(8);
        validator.initialize(annotation);
        
        BigDecimal validBTCAmount = new BigDecimal("0.12345678");
        assertTrue(validator.isValid(validBTCAmount, context));
        
        // Test BTC supply limit
        BigDecimal excessiveBTCAmount = new BigDecimal("25000000");
        assertFalse(validator.isValid(excessiveBTCAmount, context));
    }

    @Test
    void testPaymentTransactionType() {
        when(annotation.transactionType()).thenReturn(ValidMoneyAmount.TransactionType.PAYMENT);
        validator.initialize(annotation);
        
        // Payment amounts must be positive
        BigDecimal validPaymentAmount = new BigDecimal("50.00");
        assertTrue(validator.isValid(validPaymentAmount, context));
        
        BigDecimal zeroPaymentAmount = BigDecimal.ZERO;
        assertFalse(validator.isValid(zeroPaymentAmount, context));
    }

    @Test
    void testRefundTransactionType() {
        when(annotation.transactionType()).thenReturn(ValidMoneyAmount.TransactionType.REFUND);
        when(annotation.allowNegative()).thenReturn(true);
        validator.initialize(annotation);
        
        // Refunds can be negative (handled by allowNegative flag)
        BigDecimal validRefundAmount = new BigDecimal("-25.00");
        assertTrue(validator.isValid(validRefundAmount, context));
    }

    @Test
    void testUserTierLimits() {
        when(annotation.userTier()).thenReturn(ValidMoneyAmount.UserTier.BASIC);
        validator.initialize(annotation);
        
        // Amount within BASIC tier daily limit
        BigDecimal validAmount = new BigDecimal("500.00");
        assertTrue(validator.isValid(validAmount, context));
        
        // Amount exceeding BASIC tier daily limit
        BigDecimal excessiveAmount = new BigDecimal("2000.00");
        assertFalse(validator.isValid(excessiveAmount, context));
    }

    @Test
    void testHighValueTransaction() {
        when(annotation.highValue()).thenReturn(true);
        validator.initialize(annotation);
        
        BigDecimal highValueAmount = new BigDecimal("75000.00");
        // Should pass validation but log warning
        assertTrue(validator.isValid(highValueAmount, context));
    }

    @Test
    void testFraudLimitCheck() {
        when(annotation.checkFraudLimits()).thenReturn(true);
        validator.initialize(annotation);
        
        // Amount exceeding fraud prevention limit
        BigDecimal suspiciousAmount = new BigDecimal("150000.00");
        assertFalse(validator.isValid(suspiciousAmount, context));
        verify(context).buildConstraintViolationWithTemplate("Amount exceeds fraud prevention limits");
    }

    @Test
    void testStaticUtilityMethods() {
        // Test isSafeForProcessing
        assertTrue(MoneyAmountValidator.isSafeForProcessing(new BigDecimal("1000.00")));
        assertFalse(MoneyAmountValidator.isSafeForProcessing(null));
        
        // Test normalizeAmountForCurrency
        BigDecimal normalized = MoneyAmountValidator.normalizeAmountForCurrency(
            new BigDecimal("100.123"), "USD");
        assertEquals(0, normalized.compareTo(new BigDecimal("100.12")));
        assertEquals(2, normalized.scale());
        
        // Test BTC normalization
        BigDecimal normalizedBTC = MoneyAmountValidator.normalizeAmountForCurrency(
            new BigDecimal("1.123456789"), "BTC");
        assertEquals(8, normalizedBTC.scale());
    }

    @Test
    void testNumberType() {
        // Test Integer input
        Integer intAmount = 100;
        assertTrue(validator.isValid(intAmount, context));
        
        // Test Long input
        Long longAmount = 1000L;
        assertTrue(validator.isValid(longAmount, context));
        
        // Test Double input
        Double doubleAmount = 123.45;
        assertTrue(validator.isValid(doubleAmount, context));
    }

    @Test
    void testInvalidObjectType() {
        Object invalidObject = new Object();
        assertFalse(validator.isValid(invalidObject, context));
        verify(context).buildConstraintViolationWithTemplate("Amount must be a number, BigDecimal, or string");
    }

    @Test
    void testCryptoTransactionLimits() {
        when(annotation.transactionType()).thenReturn(ValidMoneyAmount.TransactionType.CRYPTO_BUY);
        validator.initialize(annotation);
        
        // Valid crypto transaction
        BigDecimal validCryptoAmount = new BigDecimal("1000.00");
        assertTrue(validator.isValid(validCryptoAmount, context));
        
        // Amount too small for crypto
        BigDecimal tooSmallAmount = new BigDecimal("0.000000001");
        assertFalse(validator.isValid(tooSmallAmount, context));
    }
}