package com.waqiti.common.financial;

import com.waqiti.common.domain.Money;
import com.waqiti.common.exception.FinancialCalculationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Financial Calculation Validator
 * 
 * Prevents monetary losses by validating all financial calculations
 * and ensuring precision, atomicity, and correctness of money operations.
 */
@Component
@Slf4j
public class FinancialCalculationValidator {

    // Currency-specific precision cache
    private static final Map<String, Integer> CURRENCY_PRECISION = new ConcurrentHashMap<>();
    
    // Crypto currency precision overrides
    private static final Map<String, Integer> CRYPTO_PRECISION = Map.of(
        "BTC", 8,
        "ETH", 18,
        "USDC", 6,
        "USDT", 6
    );
    
    // Maximum precision for internal calculations
    private static final int MAX_INTERNAL_PRECISION = 12;
    
    // Minimum transaction amounts to prevent dust attacks
    private static final Map<String, BigDecimal> MIN_TRANSACTION_AMOUNTS = Map.of(
        "USD", new BigDecimal("0.01"),
        "EUR", new BigDecimal("0.01"),
        "GBP", new BigDecimal("0.01"),
        "JPY", new BigDecimal("1.00"),
        "BTC", new BigDecimal("0.00000001"), // 1 satoshi
        "ETH", new BigDecimal("0.000000000000000001") // 1 wei
    );

    /**
     * Get precision for a specific currency
     */
    public int getCurrencyPrecision(String currencyCode) {
        return CURRENCY_PRECISION.computeIfAbsent(currencyCode, code -> {
            // Check if it's a cryptocurrency first
            if (CRYPTO_PRECISION.containsKey(code)) {
                return CRYPTO_PRECISION.get(code);
            }
            
            try {
                Currency currency = Currency.getInstance(code);
                return currency.getDefaultFractionDigits();
            } catch (IllegalArgumentException e) {
                log.warn("Unknown currency code: {}, defaulting to 2 decimal places", code);
                return 2;
            }
        });
    }

    /**
     * Validate and fix double-entry accounting calculations
     */
    public void validateDoubleEntry(BigDecimal debits, BigDecimal credits, String context) {
        if (debits == null || credits == null) {
            throw new FinancialCalculationException(
                "Null amounts in double-entry validation: " + context);
        }
        
        // Check if debits equal credits (fundamental accounting principle)
        if (debits.compareTo(credits) != 0) {
            log.error("CRITICAL: Double-entry imbalance detected in {}: debits={}, credits={}", 
                context, debits, credits);
            throw new FinancialCalculationException(
                String.format("Double-entry imbalance: debits=%s, credits=%s in %s", 
                    debits, credits, context));
        }
        
        log.debug("Double-entry validation passed: {} - debits={}, credits={}", 
            context, debits, credits);
    }

    /**
     * Calculate account balance with proper normal balance logic
     */
    public BigDecimal calculateAccountBalance(String accountType,
            BigDecimal debits, BigDecimal credits, String currencyCode) {

        validateAmounts(debits, credits);
        int precision = getCurrencyPrecision(currencyCode);
        
        // Apply normal balance logic based on account type
        BigDecimal balance = switch (accountType.toUpperCase()) {
            case "ASSET", "EXPENSE" -> 
                // Assets and expenses have normal debit balances
                debits.subtract(credits);
            case "LIABILITY", "EQUITY", "REVENUE" -> 
                // Liabilities, equity, and revenue have normal credit balances
                credits.subtract(debits);
            default -> throw new FinancialCalculationException(
                "Unknown account type for balance calculation: " + accountType);
        };
        
        return balance.setScale(precision, RoundingMode.HALF_UP);
    }

    /**
     * Validate fee calculation with proper rounding
     */
    public Money validateFeeCalculation(Money amount, BigDecimal feePercentage, 
            Money fixedFee, String currencyCode) {
        
        validateAmount(amount.getAmount(), "fee calculation base amount");
        validateAmount(feePercentage, "fee percentage");
        
        if (feePercentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new FinancialCalculationException("Fee percentage cannot be negative");
        }
        
        if (feePercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new FinancialCalculationException("Fee percentage cannot exceed 100%");
        }
        
        int precision = getCurrencyPrecision(currencyCode);
        
        // Calculate percentage fee with high internal precision
        BigDecimal percentageFee = amount.getAmount()
            .multiply(feePercentage)
            .divide(new BigDecimal("100"), MAX_INTERNAL_PRECISION, RoundingMode.HALF_UP);
        
        // Add fixed fee if provided
        if (fixedFee != null && fixedFee.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            validateAmount(fixedFee.getAmount(), "fixed fee amount");
            percentageFee = percentageFee.add(fixedFee.getAmount());
        }
        
        // Round to currency precision
        BigDecimal finalFee = percentageFee.setScale(precision, RoundingMode.HALF_UP);
        
        // Validate minimum fee amount
        BigDecimal minAmount = MIN_TRANSACTION_AMOUNTS.getOrDefault(currencyCode, 
            new BigDecimal("0.01"));
        
        if (finalFee.compareTo(BigDecimal.ZERO) > 0 && finalFee.compareTo(minAmount) < 0) {
            log.debug("Fee amount {} below minimum {}, rounding up to minimum",
                finalFee, minAmount);
            finalFee = minAmount;
        }

        return Money.of(finalFee, currencyCode);
    }

    /**
     * Validate currency conversion with proper precision
     */
    public Money validateCurrencyConversion(Money sourceAmount, String targetCurrency, 
            BigDecimal exchangeRate, String context) {
        
        validateAmount(sourceAmount.getAmount(), "conversion source amount");
        validateAmount(exchangeRate, "exchange rate");
        
        if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FinancialCalculationException("Exchange rate must be positive");
        }
        
        int targetPrecision = getCurrencyPrecision(targetCurrency);
        
        // Perform conversion with high internal precision
        BigDecimal convertedAmount = sourceAmount.getAmount()
            .multiply(exchangeRate)
            .setScale(MAX_INTERNAL_PRECISION, RoundingMode.HALF_UP);
        
        // Round to target currency precision
        BigDecimal finalAmount = convertedAmount.setScale(targetPrecision, RoundingMode.HALF_UP);
        
        // Validate minimum amount for target currency
        BigDecimal minAmount = MIN_TRANSACTION_AMOUNTS.getOrDefault(targetCurrency, 
            new BigDecimal("0.01"));
        
        if (finalAmount.compareTo(minAmount) < 0) {
            throw new FinancialCalculationException(
                String.format("Converted amount %s below minimum %s for currency %s in %s",
                    finalAmount, minAmount, targetCurrency, context));
        }
        
        log.debug("Currency conversion validated: {} {} -> {} {} (rate: {}) in {}",
            sourceAmount.getAmount(), sourceAmount.getCurrencyCode(),
            finalAmount, targetCurrency, exchangeRate, context);

        return Money.of(finalAmount, targetCurrency);
    }

    /**
     * Validate interest calculation
     */
    public Money validateInterestCalculation(Money principal, BigDecimal annualRate, 
            int days, int calculationDays, String currencyCode) {
        
        validateAmount(principal.getAmount(), "principal amount");
        validateAmount(annualRate, "annual interest rate");
        
        if (days < 0) {
            throw new FinancialCalculationException("Interest calculation days cannot be negative");
        }
        
        if (calculationDays <= 0) {
            throw new FinancialCalculationException("Calculation days must be positive (got: " + calculationDays + ")");
        }
        
        if (annualRate.compareTo(new BigDecimal("100")) > 0) {
            log.warn("Interest rate {}% seems unusually high", annualRate);
        }
        
        int precision = getCurrencyPrecision(currencyCode);
        
        // Calculate interest with high precision
        BigDecimal dailyRate = annualRate.divide(new BigDecimal("100"), MAX_INTERNAL_PRECISION, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(calculationDays), MAX_INTERNAL_PRECISION, RoundingMode.HALF_UP);
        
        BigDecimal interestAmount = principal.getAmount()
            .multiply(dailyRate)
            .multiply(BigDecimal.valueOf(days))
            .setScale(precision, RoundingMode.HALF_UP);
        
        log.debug("Interest calculation: principal={}, rate={}%, days={}, interest={}",
            principal.getAmount(), annualRate, days, interestAmount);

        return Money.of(interestAmount, currencyCode);
    }

    /**
     * Validate money splitting to prevent remainder allocation errors
     */
    public Money[] validateMoneySplit(Money totalAmount, int parts, String context) {
        validateAmount(totalAmount.getAmount(), "total amount for splitting");
        
        if (parts <= 0) {
            throw new FinancialCalculationException("Cannot split money into " + parts + " parts");
        }
        
        if (parts == 1) {
            return new Money[]{totalAmount};
        }
        
        int precision = getCurrencyPrecision(totalAmount.getCurrencyCode());
        String currency = totalAmount.getCurrencyCode();
        
        // Calculate base amount per part
        BigDecimal partAmount = totalAmount.getAmount()
            .divide(BigDecimal.valueOf(parts), MAX_INTERNAL_PRECISION, RoundingMode.DOWN);
        
        // Round down to currency precision for each part
        BigDecimal roundedPartAmount = partAmount.setScale(precision, RoundingMode.DOWN);
        
        // Calculate remainder
        BigDecimal totalAllocated = roundedPartAmount.multiply(BigDecimal.valueOf(parts));
        BigDecimal remainder = totalAmount.getAmount().subtract(totalAllocated);
        
        Money[] results = new Money[parts];
        
        // Distribute remainder evenly across parts (not just the last one)
        BigDecimal smallestUnit = BigDecimal.ONE.movePointLeft(precision);
        int remainderParts = remainder.divide(smallestUnit, 0, RoundingMode.DOWN).intValue();
        
        for (int i = 0; i < parts; i++) {
            BigDecimal amount = roundedPartAmount;
            
            // Add one smallest unit to first 'remainderParts' parts
            if (i < remainderParts) {
                amount = amount.add(smallestUnit);
            }

            results[i] = Money.of(amount, currency);
        }
        
        // Verify total matches
        BigDecimal totalCheck = java.util.Arrays.stream(results)
            .map(Money::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalCheck.compareTo(totalAmount.getAmount()) != 0) {
            throw new FinancialCalculationException(
                String.format("Money split validation failed in %s: original=%s, split total=%s",
                    context, totalAmount.getAmount(), totalCheck));
        }
        
        log.debug("Money split validated: {} into {} parts in {}", 
            totalAmount, parts, context);
        
        return results;
    }

    /**
     * Validate transaction atomicity (to be called before balance updates)
     */
    public void validateTransactionAtomicity(String accountId, Money reservedAmount, 
            Money currentBalance, String transactionId) {
        
        validateAmount(reservedAmount.getAmount(), "reserved amount");
        validateAmount(currentBalance.getAmount(), "current balance");
        
        if (!reservedAmount.getCurrencyCode().equals(currentBalance.getCurrencyCode())) {
            throw new FinancialCalculationException(
                "Currency mismatch in transaction atomicity check");
        }
        
        BigDecimal availableBalance = currentBalance.getAmount().subtract(reservedAmount.getAmount());
        
        if (availableBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.error("CRITICAL: Insufficient funds detected for transaction {}: account={}, balance={}, reserved={}", 
                transactionId, accountId, currentBalance.getAmount(), reservedAmount.getAmount());
            
            throw new FinancialCalculationException(
                String.format("Insufficient funds: available=%s, required=%s for transaction %s",
                    availableBalance, reservedAmount.getAmount(), transactionId));
        }
        
        log.debug("Transaction atomicity validated: transaction={}, account={}, available={}",
            transactionId, accountId, availableBalance);
    }

    // Private helper methods
    
    private void validateAmount(BigDecimal amount, String context) {
        if (amount == null) {
            throw new FinancialCalculationException("Null amount in " + context);
        }
        
        if (amount.scale() > MAX_INTERNAL_PRECISION) {
            throw new FinancialCalculationException(
                String.format("Amount precision too high in %s: %d (max: %d)", 
                    context, amount.scale(), MAX_INTERNAL_PRECISION));
        }
    }
    
    private void validateAmounts(BigDecimal... amounts) {
        for (int i = 0; i < amounts.length; i++) {
            validateAmount(amounts[i], "amount " + i);
        }
    }
}