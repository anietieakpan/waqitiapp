package com.waqiti.common.currency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Comprehensive audit service for currency precision operations.
 * Provides detailed logging for financial calculations, regulatory compliance, and precision tracking.
 * Essential for financial audit trails and debugging precision-related issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyPrecisionAuditService {
    
    /**
     * Log creation of precise monetary amount
     */
    public void logAmountCreation(String currencyCode, BigDecimal originalAmount, 
                                 BigDecimal preciseAmount, Instant timestamp) {
        log.info("PRECISION_AUDIT: Amount created - Currency: {}, Original: {}, Precise: {}, Timestamp: {}", 
                currencyCode, originalAmount.toPlainString(), preciseAmount.toPlainString(), timestamp);
    }
    
    /**
     * Log arithmetic operation between two amounts
     */
    public void logArithmeticOperation(String operation, PreciseMonetaryAmount amount1, 
                                     PreciseMonetaryAmount amount2, PreciseMonetaryAmount result, 
                                     Instant timestamp) {
        log.info("PRECISION_AUDIT: {} operation - Amount1: {}, Amount2: {}, Result: {}, Timestamp: {}", 
                operation, amount1.toPreciseString(), amount2.toPreciseString(), 
                result.toPreciseString(), timestamp);
    }
    
    /**
     * Log multiplication operation
     */
    public void logMultiplicationOperation(PreciseMonetaryAmount amount, BigDecimal multiplier, 
                                         PreciseMonetaryAmount result, Instant timestamp) {
        log.info("PRECISION_AUDIT: Multiplication - Amount: {}, Multiplier: {}, Result: {}, Timestamp: {}", 
                amount.toPreciseString(), multiplier.toPlainString(), result.toPreciseString(), timestamp);
    }
    
    /**
     * Log division operation
     */
    public void logDivisionOperation(PreciseMonetaryAmount amount, BigDecimal divisor, 
                                   PreciseMonetaryAmount result, Instant timestamp) {
        log.info("PRECISION_AUDIT: Division - Amount: {}, Divisor: {}, Result: {}, Timestamp: {}", 
                amount.toPreciseString(), divisor.toPlainString(), result.toPreciseString(), timestamp);
    }
    
    /**
     * Log currency conversion operation
     */
    public void logCurrencyConversion(PreciseMonetaryAmount sourceAmount, String targetCurrency, 
                                    BigDecimal exchangeRate, PreciseMonetaryAmount result, 
                                    Instant timestamp) {
        log.info("PRECISION_AUDIT: Currency conversion - Source: {}, Target: {}, Rate: {}, Result: {}, Timestamp: {}", 
                sourceAmount.toPreciseString(), targetCurrency, exchangeRate.toPlainString(), 
                result.toPreciseString(), timestamp);
    }
    
    /**
     * Log rounding operation
     */
    public void logRoundingOperation(PreciseMonetaryAmount originalAmount, 
                                   PreciseMonetaryAmount roundedAmount, Instant timestamp) {
        log.info("PRECISION_AUDIT: Rounding - Original: {}, Rounded: {}, Precision: {}, Timestamp: {}", 
                originalAmount.toPreciseString(), roundedAmount.toPreciseString(), 
                roundedAmount.getPrecision(), timestamp);
        
        // Log precision loss if significant
        BigDecimal difference = originalAmount.getAmount().subtract(roundedAmount.getAmount()).abs();
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            log.info("PRECISION_AUDIT: Precision loss detected - Difference: {}, Original precision: {}, Final precision: {}", 
                    difference.toPlainString(), originalAmount.getPrecision(), roundedAmount.getPrecision());
        }
    }
    
    /**
     * Log precision validation result
     */
    public void logPrecisionValidation(PrecisionValidationResult validationResult, Instant timestamp) {
        if (validationResult.isValid()) {
            log.info("PRECISION_AUDIT: Validation passed - {}, Timestamp: {}", 
                    validationResult.toString(), timestamp);
        } else {
            log.warn("PRECISION_AUDIT: Validation failed - {}, Timestamp: {}", 
                    validationResult.toString(), timestamp);
        }
        
        // Log detailed validation metrics
        if (validationResult.getDifference() != null) {
            log.debug("PRECISION_AUDIT: Validation metrics - Difference: {}, Tolerance: {}, Within tolerance: {}", 
                    validationResult.getDifference().toPlainString(),
                    validationResult.getTolerance() != null ? validationResult.getTolerance().toPlainString() : "N/A",
                    validationResult.isWithinTolerance());
        }
    }
    
    /**
     * Log interest calculation
     */
    public void logInterestCalculation(PreciseMonetaryAmount principal, BigDecimal annualRate, 
                                     int compoundingPeriods, int years, 
                                     PreciseMonetaryAmount result, Instant timestamp) {
        log.info("PRECISION_AUDIT: Interest calculation - Principal: {}, Rate: {}%, Periods: {}, Years: {}, Result: {}, Timestamp: {}", 
                principal.toPreciseString(), annualRate.toPlainString(), compoundingPeriods, 
                years, result.toPreciseString(), timestamp);
        
        // Calculate and log interest earned
        BigDecimal interestEarned = result.getAmount().subtract(principal.getAmount());
        log.info("PRECISION_AUDIT: Interest earned - Amount: {} {}, Effective rate: {}%", 
                interestEarned.toPlainString(), principal.getCurrencyCode(),
                interestEarned.divide(principal.getAmount(), java.math.MathContext.DECIMAL64)
                        .multiply(new BigDecimal("100")).toPlainString());
    }
    
    /**
     * Log proportional allocation
     */
    public void logProportionalAllocation(PreciseMonetaryAmount totalAmount, List<BigDecimal> proportions, 
                                        List<PreciseMonetaryAmount> allocatedAmounts, Instant timestamp) {
        log.info("PRECISION_AUDIT: Proportional allocation - Total: {}, Proportions count: {}, Timestamp: {}", 
                totalAmount.toPreciseString(), proportions.size(), timestamp);
        
        // Verify allocation accuracy
        BigDecimal allocatedSum = allocatedAmounts.stream()
                .map(PreciseMonetaryAmount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal allocationDifference = totalAmount.getAmount().subtract(allocatedSum).abs();
        
        log.info("PRECISION_AUDIT: Allocation verification - Original: {}, Allocated sum: {}, Difference: {}", 
                totalAmount.getAmount().toPlainString(), allocatedSum.toPlainString(), 
                allocationDifference.toPlainString());
        
        // Log individual allocations
        for (int i = 0; i < allocatedAmounts.size(); i++) {
            BigDecimal proportion = i < proportions.size() ? proportions.get(i) : BigDecimal.ZERO;
            log.debug("PRECISION_AUDIT: Allocation {} - Proportion: {}, Amount: {}", 
                    i, proportion.toPlainString(), allocatedAmounts.get(i).toPreciseString());
        }
        
        // Warn if allocation difference exceeds minimal threshold
        BigDecimal maxAcceptableDifference = new BigDecimal("0.01"); // 1 cent
        if (allocationDifference.compareTo(maxAcceptableDifference) > 0) {
            log.warn("PRECISION_AUDIT: Significant allocation difference detected - Difference: {} exceeds threshold: {}", 
                    allocationDifference.toPlainString(), maxAcceptableDifference.toPlainString());
        }
    }
    
    /**
     * Log sum operation
     */
    public void logSumOperation(List<PreciseMonetaryAmount> amounts, PreciseMonetaryAmount result, 
                              Instant timestamp) {
        log.info("PRECISION_AUDIT: Sum operation - Count: {}, Result: {}, Timestamp: {}", 
                amounts.size(), result.toPreciseString(), timestamp);
        
        // Log sum verification
        BigDecimal manualSum = amounts.stream()
                .map(PreciseMonetaryAmount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal sumDifference = result.getAmount().subtract(manualSum).abs();
        
        if (sumDifference.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("PRECISION_AUDIT: Sum calculation difference - Service result: {}, Manual sum: {}, Difference: {}", 
                    result.getAmount().toPlainString(), manualSum.toPlainString(), 
                    sumDifference.toPlainString());
        }
        
        // Log individual amounts in debug mode
        for (int i = 0; i < amounts.size(); i++) {
            log.debug("PRECISION_AUDIT: Sum component {} - {}", i, amounts.get(i).toPreciseString());
        }
    }
    
    /**
     * Log precision configuration usage
     */
    public void logPrecisionConfigUsage(String currencyCode, CurrencyPrecisionConfig config, 
                                      Instant timestamp) {
        log.info("PRECISION_AUDIT: Precision config used - Currency: {}, Config: {}, Timestamp: {}", 
                currencyCode, config.toString(), timestamp);
        
        if (config.requiresSpecialPrecision()) {
            log.info("PRECISION_AUDIT: Special precision currency detected - {} requires {} decimal places", 
                    currencyCode, config.getDecimalPlaces());
        }
        
        if (config.isCryptoCurrency()) {
            log.info("PRECISION_AUDIT: Cryptocurrency precision - {} using high precision scale", currencyCode);
        }
    }
    
    /**
     * Log precision error or warning
     */
    public void logPrecisionError(String operation, String currencyCode, String error, 
                                BigDecimal amount, Instant timestamp) {
        log.error("PRECISION_AUDIT: Precision error - Operation: {}, Currency: {}, Amount: {}, Error: {}, Timestamp: {}", 
                operation, currencyCode, amount != null ? amount.toPlainString() : "N/A", error, timestamp);
    }
    
    /**
     * Log precision warning
     */
    public void logPrecisionWarning(String operation, String currencyCode, String warning, 
                                  BigDecimal amount, Instant timestamp) {
        log.warn("PRECISION_AUDIT: Precision warning - Operation: {}, Currency: {}, Amount: {}, Warning: {}, Timestamp: {}", 
                operation, currencyCode, amount != null ? amount.toPlainString() : "N/A", warning, timestamp);
    }
    
    /**
     * Log high-value transaction for additional scrutiny
     */
    public void logHighValueTransaction(PreciseMonetaryAmount amount, String operation, 
                                      BigDecimal threshold, Instant timestamp) {
        log.info("PRECISION_AUDIT: High-value transaction - Operation: {}, Amount: {}, Threshold: {}, Timestamp: {}", 
                operation, amount.toPreciseString(), threshold.toPlainString(), timestamp);
        
        // Additional metadata for high-value transactions
        log.info("PRECISION_AUDIT: High-value metadata - Currency: {}, Precision: {}, Rounding: {}", 
                amount.getCurrencyCode(), amount.getPrecision(), amount.getRoundingMode());
    }
    
    /**
     * Log cross-currency operation
     */
    public void logCrossCurrencyOperation(String operation, String sourceCurrency, String targetCurrency, 
                                        BigDecimal rate, PreciseMonetaryAmount sourceAmount, 
                                        PreciseMonetaryAmount targetAmount, Instant timestamp) {
        log.info("PRECISION_AUDIT: Cross-currency operation - Operation: {}, Source: {} {}, Target: {} {}, Rate: {}, Timestamp: {}", 
                operation, sourceCurrency, sourceAmount.getAmount().toPlainString(), 
                targetCurrency, targetAmount.getAmount().toPlainString(), 
                rate.toPlainString(), timestamp);
        
        // Calculate and log effective conversion rate
        BigDecimal effectiveRate = targetAmount.getAmount().divide(sourceAmount.getAmount(), 
                java.math.MathContext.DECIMAL64);
        BigDecimal rateDifference = rate.subtract(effectiveRate).abs();
        
        log.info("PRECISION_AUDIT: Conversion rate analysis - Expected: {}, Effective: {}, Difference: {}", 
                rate.toPlainString(), effectiveRate.toPlainString(), rateDifference.toPlainString());
    }
    
    /**
     * Log batch operation summary
     */
    public void logBatchOperationSummary(String operation, int operationCount, 
                                       List<String> currencies, Instant timestamp) {
        log.info("PRECISION_AUDIT: Batch operation summary - Operation: {}, Count: {}, Currencies: {}, Timestamp: {}", 
                operation, operationCount, String.join(", ", currencies), timestamp);
    }
    
    /**
     * Log precision configuration change
     */
    public void logPrecisionConfigChange(String currencyCode, CurrencyPrecisionConfig oldConfig, 
                                       CurrencyPrecisionConfig newConfig, Instant timestamp) {
        log.info("PRECISION_AUDIT: Precision config change - Currency: {}, Old: {}, New: {}, Timestamp: {}", 
                currencyCode, oldConfig.toString(), newConfig.toString(), timestamp);
    }
}