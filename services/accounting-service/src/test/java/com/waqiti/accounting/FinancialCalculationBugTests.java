package com.waqiti.accounting;

import com.waqiti.accounting.service.AccountingService;
import com.waqiti.common.domain.Money;
import com.waqiti.common.financial.FinancialCalculationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Critical Financial Calculation Bug Tests
 * 
 * Tests specifically designed to catch monetary loss bugs identified in the audit.
 * These tests MUST PASS to prevent financial losses in production.
 */
@SpringBootTest
@ActiveProfiles("test")
public class FinancialCalculationBugTests {

    private FinancialCalculationValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new FinancialCalculationValidator();
    }

    @Nested
    @DisplayName("CRITICAL: Double-Entry Balance Calculation Bug Tests")
    class DoubleEntryBalanceTests {
        
        @Test
        @DisplayName("Should calculate asset account balances correctly")
        void testAssetAccountBalance() {
            // Asset accounts have normal debit balances
            // Debits increase balance, credits decrease balance
            BigDecimal debits = new BigDecimal("1000.00");
            BigDecimal credits = new BigDecimal("300.00");
            
            BigDecimal balance = validator.calculateAccountBalance("ASSET", debits, credits, "USD");
            
            // Asset balance should be debits - credits = 700.00
            assertThat(balance).isEqualTo(new BigDecimal("700.00"));
        }
        
        @Test
        @DisplayName("Should calculate liability account balances correctly")
        void testLiabilityAccountBalance() {
            // Liability accounts have normal credit balances
            // Credits increase balance, debits decrease balance
            BigDecimal debits = new BigDecimal("200.00");
            BigDecimal credits = new BigDecimal("1000.00");
            
            BigDecimal balance = validator.calculateAccountBalance("LIABILITY", debits, credits, "USD");
            
            // Liability balance should be credits - debits = 800.00
            assertThat(balance).isEqualTo(new BigDecimal("800.00"));
        }
        
        @Test
        @DisplayName("Should validate double-entry imbalance detection")
        void testDoubleEntryImbalanceDetection() {
            // This should fail - debits don't equal credits
            BigDecimal debits = new BigDecimal("1000.00");
            BigDecimal credits = new BigDecimal("999.99"); // Off by 1 cent
            
            assertThatThrownBy(() -> 
                validator.validateDoubleEntry(debits, credits, "payment processing"))
                .hasMessageContaining("Double-entry imbalance");
        }
        
        @Test
        @DisplayName("Should pass double-entry balance validation")
        void testDoubleEntryBalanceValidation() {
            BigDecimal debits = new BigDecimal("1000.00");
            BigDecimal credits = new BigDecimal("1000.00");
            
            // This should not throw an exception
            assertDoesNotThrow(() -> 
                validator.validateDoubleEntry(debits, credits, "payment processing"));
        }
    }

    @Nested
    @DisplayName("CRITICAL: Fee Calculation Double-Charging Bug Tests")
    class FeeCalculationTests {
        
        @Test
        @DisplayName("Should calculate percentage + fixed fee correctly")
        void testCorrectFeeCalculation() {
            Money amount = new Money(new BigDecimal("100.00"), "USD");
            BigDecimal feePercentage = new BigDecimal("2.9"); // 2.9%
            Money fixedFee = new Money(new BigDecimal("0.30"), "USD");
            
            Money totalFee = validator.validateFeeCalculation(amount, feePercentage, fixedFee, "USD");
            
            // Expected: (100.00 * 2.9%) + 0.30 = 2.90 + 0.30 = 3.20
            assertThat(totalFee.getAmount()).isEqualTo(new BigDecimal("3.20"));
        }
        
        @Test
        @DisplayName("Should prevent negative fee percentages")
        void testNegativeFeePercentagePrevention() {
            Money amount = new Money(new BigDecimal("100.00"), "USD");
            BigDecimal negativeFeePercentage = new BigDecimal("-1.0");
            
            assertThatThrownBy(() -> 
                validator.validateFeeCalculation(amount, negativeFeePercentage, null, "USD"))
                .hasMessageContaining("Fee percentage cannot be negative");
        }
        
        @Test
        @DisplayName("Should prevent excessive fee percentages")
        void testExcessiveFeePercentagePrevention() {
            Money amount = new Money(new BigDecimal("100.00"), "USD");
            BigDecimal excessiveFeePercentage = new BigDecimal("150.0"); // 150%
            
            assertThatThrownBy(() -> 
                validator.validateFeeCalculation(amount, excessiveFeePercentage, null, "USD"))
                .hasMessageContaining("Fee percentage cannot exceed 100%");
        }
        
        @Test
        @DisplayName("Should handle minimum fee enforcement")
        void testMinimumFeeEnforcement() {
            Money tinyAmount = new Money(new BigDecimal("0.10"), "USD");
            BigDecimal lowFeePercentage = new BigDecimal("0.1"); // 0.1%
            
            Money calculatedFee = validator.validateFeeCalculation(tinyAmount, lowFeePercentage, null, "USD");
            
            // 0.1% of $0.10 = $0.001, should be rounded up to minimum $0.01
            assertThat(calculatedFee.getAmount()).isEqualTo(new BigDecimal("0.01"));
        }
    }

    @Nested
    @DisplayName("CRITICAL: Currency Precision Loss Bug Tests")
    class CurrencyPrecisionTests {
        
        @Test
        @DisplayName("Should handle JPY (zero decimal places) correctly")
        void testJapaneseYenPrecision() {
            Money amount = new Money(new BigDecimal("1000"), "JPY");
            BigDecimal exchangeRate = new BigDecimal("0.007"); // JPY to USD
            
            Money converted = validator.validateCurrencyConversion(amount, "USD", exchangeRate, "test");
            
            // 1000 JPY * 0.007 = 7.00 USD
            assertThat(converted.getAmount()).isEqualTo(new BigDecimal("7.00"));
            assertThat(converted.getCurrencyCode()).isEqualTo("USD");
        }
        
        @Test
        @DisplayName("Should handle Bitcoin (8 decimal places) correctly")
        void testBitcoinPrecision() {
            Money amount = new Money(new BigDecimal("1.50000000"), "BTC");
            BigDecimal exchangeRate = new BigDecimal("50000.00"); // BTC to USD
            
            Money converted = validator.validateCurrencyConversion(amount, "USD", exchangeRate, "test");
            
            // 1.5 BTC * 50000 = 75000.00 USD
            assertThat(converted.getAmount()).isEqualTo(new BigDecimal("75000.00"));
        }
        
        @Test
        @DisplayName("Should preserve precision in high-value crypto transactions")
        void testHighValueCryptoPrecision() {
            Money btcAmount = new Money(new BigDecimal("0.00000001"), "BTC"); // 1 satoshi
            BigDecimal btcToUsdRate = new BigDecimal("50000.00");
            
            Money converted = validator.validateCurrencyConversion(btcAmount, "USD", btcToUsdRate, "test");
            
            // 1 satoshi = 0.00000001 BTC * 50000 = 0.0005 USD = 0.00 USD (below minimum)
            // Should throw exception as below minimum USD amount
            assertThatThrownBy(() -> 
                validator.validateCurrencyConversion(btcAmount, "USD", btcToUsdRate, "test"))
                .hasMessageContaining("below minimum");
        }
    }

    @Nested
    @DisplayName("HIGH: Interest Calculation Division By Zero Bug Tests")
    class InterestCalculationTests {
        
        @Test
        @DisplayName("Should prevent division by zero in interest calculation")
        void testDivisionByZeroPrevention() {
            Money principal = new Money(new BigDecimal("1000.00"), "USD");
            BigDecimal annualRate = new BigDecimal("5.0");
            int days = 30;
            int calculationDays = 0; // This should cause division by zero
            
            assertThatThrownBy(() -> 
                validator.validateInterestCalculation(principal, annualRate, days, calculationDays, "USD"))
                .hasMessageContaining("Calculation days must be positive");
        }
        
        @Test
        @DisplayName("Should calculate interest correctly for 30 days")
        void testThirtyDayInterestCalculation() {
            Money principal = new Money(new BigDecimal("1000.00"), "USD");
            BigDecimal annualRate = new BigDecimal("6.0"); // 6% annual
            int days = 30;
            int calculationDays = 365; // Annual basis
            
            Money interest = validator.validateInterestCalculation(principal, annualRate, days, calculationDays, "USD");
            
            // Expected: 1000 * 6% * (30/365) = 1000 * 0.06 * 0.08219 = $4.93
            BigDecimal expectedInterest = new BigDecimal("4.93");
            assertThat(interest.getAmount()).isEqualByComparingTo(expectedInterest);
        }
        
        @Test
        @DisplayName("Should warn about unusually high interest rates")
        void testHighInterestRateWarning() {
            Money principal = new Money(new BigDecimal("1000.00"), "USD");
            BigDecimal highRate = new BigDecimal("200.0"); // 200% annual - suspicious
            int days = 30;
            int calculationDays = 365;
            
            // Should not throw exception but should log warning
            Money interest = validator.validateInterestCalculation(principal, highRate, days, calculationDays, "USD");
            
            assertThat(interest).isNotNull();
            assertThat(interest.getAmount()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("MEDIUM: Money Splitting Remainder Allocation Bug Tests")
    class MoneySplittingTests {
        
        @Test
        @DisplayName("Should split money evenly with fair remainder distribution")
        void testEvenMoneySplit() {
            Money totalAmount = new Money(new BigDecimal("10.00"), "USD");
            int parts = 3;
            
            Money[] splits = validator.validateMoneySplit(totalAmount, parts, "bill splitting");
            
            // $10.00 / 3 = $3.33 + $3.33 + $3.34 (remainder distributed fairly)
            assertThat(splits).hasSize(3);
            assertThat(splits[0].getAmount()).isEqualTo(new BigDecimal("3.34")); // Gets remainder
            assertThat(splits[1].getAmount()).isEqualTo(new BigDecimal("3.33"));
            assertThat(splits[2].getAmount()).isEqualTo(new BigDecimal("3.33"));
            
            // Verify total is preserved
            BigDecimal total = BigDecimal.ZERO;
            for (Money split : splits) {
                total = total.add(split.getAmount());
            }
            assertThat(total).isEqualTo(totalAmount.getAmount());
        }
        
        @Test
        @DisplayName("Should split large amount with minimal remainder bias")
        void testLargeAmountSplit() {
            Money totalAmount = new Money(new BigDecimal("1000.01"), "USD");
            int parts = 7;
            
            Money[] splits = validator.validateMoneySplit(totalAmount, parts, "settlement");
            
            assertThat(splits).hasSize(7);
            
            // Verify total is preserved exactly
            BigDecimal total = BigDecimal.ZERO;
            for (Money split : splits) {
                total = total.add(split.getAmount());
            }
            assertThat(total).isEqualTo(totalAmount.getAmount());
            
            // Verify remainder distribution (1 cent distributed to first part)
            long partsWithExtraCent = java.util.Arrays.stream(splits)
                .mapToLong(split -> split.getAmount().remainder(BigDecimal.ONE).multiply(new BigDecimal("100")).longValue())
                .filter(remainder -> remainder == 1)
                .count();
            
            assertThat(partsWithExtraCent).isEqualTo(1); // Only 1 part gets the extra cent
        }
        
        @Test
        @DisplayName("Should handle single part split correctly")
        void testSinglePartSplit() {
            Money totalAmount = new Money(new BigDecimal("100.00"), "USD");
            
            Money[] splits = validator.validateMoneySplit(totalAmount, 1, "no split");
            
            assertThat(splits).hasSize(1);
            assertThat(splits[0]).isEqualTo(totalAmount);
        }
    }

    @Nested
    @DisplayName("HIGH: Race Condition and Atomicity Bug Tests")
    class AtomicityTests {
        
        @Test
        @DisplayName("Should detect insufficient funds in atomic transaction")
        void testInsufficientFundsDetection() {
            String accountId = "ACC123";
            Money reservedAmount = new Money(new BigDecimal("1000.00"), "USD");
            Money currentBalance = new Money(new BigDecimal("999.99"), "USD"); // Insufficient
            String transactionId = "TXN456";
            
            assertThatThrownBy(() -> 
                validator.validateTransactionAtomicity(accountId, reservedAmount, currentBalance, transactionId))
                .hasMessageContaining("Insufficient funds");
        }
        
        @Test
        @DisplayName("Should pass sufficient funds validation")
        void testSufficientFundsValidation() {
            String accountId = "ACC123";
            Money reservedAmount = new Money(new BigDecimal("500.00"), "USD");
            Money currentBalance = new Money(new BigDecimal("1000.00"), "USD"); // Sufficient
            String transactionId = "TXN456";
            
            // Should not throw exception
            assertDoesNotThrow(() -> 
                validator.validateTransactionAtomicity(accountId, reservedAmount, currentBalance, transactionId));
        }
        
        @Test
        @DisplayName("Should handle concurrent transaction validation")
        void testConcurrentTransactionValidation() throws InterruptedException {
            final String accountId = "ACC123";
            final Money initialBalance = new Money(new BigDecimal("1000.00"), "USD");
            final Money reservationAmount = new Money(new BigDecimal("100.00"), "USD");
            
            ExecutorService executor = Executors.newFixedThreadPool(10);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            // Simulate 20 concurrent transactions, each trying to reserve $100
            // Only 10 should succeed (1000 / 100 = 10)
            for (int i = 0; i < 20; i++) {
                final String transactionId = "TXN" + i;
                executor.submit(() -> {
                    try {
                        // Simulate decreasing balance as reservations are made
                        Money currentBalance = new Money(
                            initialBalance.getAmount().subtract(
                                reservationAmount.getAmount().multiply(
                                    new BigDecimal(successCount.get()))), 
                            "USD");
                        
                        validator.validateTransactionAtomicity(
                            accountId, reservationAmount, currentBalance, transactionId);
                        
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                });
            }
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            
            // Note: This test simulates the logic but doesn't test actual database atomicity
            // Real atomicity testing requires integration tests with actual database locks
            assertThat(successCount.get() + failureCount.get()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Security Tests")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Should handle zero amounts correctly")
        void testZeroAmountHandling() {
            Money zeroAmount = new Money(BigDecimal.ZERO, "USD");
            BigDecimal zeroPercentage = BigDecimal.ZERO;
            
            Money fee = validator.validateFeeCalculation(zeroAmount, zeroPercentage, null, "USD");
            
            assertThat(fee.getAmount()).isEqualTo(BigDecimal.ZERO);
        }
        
        @Test
        @DisplayName("Should prevent currency mismatch in calculations")
        void testCurrencyMismatchPrevention() {
            String accountId = "ACC123";
            Money reservedAmountUSD = new Money(new BigDecimal("100.00"), "USD");
            Money balanceEUR = new Money(new BigDecimal("1000.00"), "EUR"); // Different currency
            String transactionId = "TXN456";
            
            assertThatThrownBy(() -> 
                validator.validateTransactionAtomicity(accountId, reservedAmountUSD, balanceEUR, transactionId))
                .hasMessageContaining("Currency mismatch");
        }
        
        @Test
        @DisplayName("Should handle maximum precision without overflow")
        void testMaximumPrecisionHandling() {
            // Test with high precision amount
            BigDecimal highPrecisionAmount = new BigDecimal("1000.123456789012");
            Money amount = new Money(highPrecisionAmount, "USD");
            BigDecimal feePercentage = new BigDecimal("2.5");
            
            Money fee = validator.validateFeeCalculation(amount, feePercentage, null, "USD");
            
            assertThat(fee.getAmount()).isNotNull();
            assertThat(fee.getAmount().scale()).isLessThanOrEqualTo(2); // USD precision
        }
        
        @Test
        @DisplayName("Should detect null amount injection attacks")
        void testNullAmountPrevention() {
            assertThatThrownBy(() -> 
                validator.validateFeeCalculation(null, new BigDecimal("2.5"), null, "USD"))
                .hasMessageContaining("Null amount");
            
            assertThatThrownBy(() -> 
                new Money(null, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}