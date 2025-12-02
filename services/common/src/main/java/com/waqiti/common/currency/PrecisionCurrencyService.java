package com.waqiti.common.currency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade currency precision service for financial calculations.
 * Implements rigorous decimal precision handling to prevent rounding errors and ensure financial accuracy.
 * 
 * Features:
 * - Currency-specific precision rules (ISO 4217 compliant)
 * - Configurable rounding modes per currency and operation type
 * - Intermediate calculation precision preservation
 * - Multi-currency conversion with precision tracking
 * - Financial calculation validation and verification
 * - Comprehensive audit logging for regulatory compliance
 * - Performance-optimized currency operations
 * - Support for cryptocurrencies and exotic currencies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrecisionCurrencyService {
    
    private final CurrencyPrecisionAuditService auditService;
    
    @Value("${currency.precision.default.scale:4}")
    private int defaultPrecisionScale;
    
    @Value("${currency.precision.intermediate.scale:8}")
    private int intermediatePrecisionScale;
    
    @Value("${currency.precision.validation.enabled:true}")
    private boolean precisionValidationEnabled;
    
    @Value("${currency.precision.audit.enabled:true}")
    private boolean precisionAuditEnabled;
    
    @Value("${currency.precision.crypto.scale:18}")
    private int cryptoCurrencyScale;
    
    // Currency-specific precision configurations
    private static final Map<String, CurrencyPrecisionConfig> CURRENCY_CONFIGS = new ConcurrentHashMap<>();
    
    // Financial calculation contexts for different operation types
    private static final Map<CalculationType, MathContext> CALCULATION_CONTEXTS = new ConcurrentHashMap<>();
    
    static {
        initializeCurrencyConfigs();
        initializeCalculationContexts();
    }
    
    /**
     * Initialize currency-specific precision configurations
     */
    private static void initializeCurrencyConfigs() {
        // Major currencies with standard precision
        CURRENCY_CONFIGS.put("USD", new CurrencyPrecisionConfig("USD", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("EUR", new CurrencyPrecisionConfig("EUR", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("GBP", new CurrencyPrecisionConfig("GBP", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("JPY", new CurrencyPrecisionConfig("JPY", 0, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("CHF", new CurrencyPrecisionConfig("CHF", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("CAD", new CurrencyPrecisionConfig("CAD", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("AUD", new CurrencyPrecisionConfig("AUD", 2, RoundingMode.HALF_EVEN, true));
        
        // Emerging market currencies
        CURRENCY_CONFIGS.put("CNY", new CurrencyPrecisionConfig("CNY", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("INR", new CurrencyPrecisionConfig("INR", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("BRL", new CurrencyPrecisionConfig("BRL", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("RUB", new CurrencyPrecisionConfig("RUB", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("ZAR", new CurrencyPrecisionConfig("ZAR", 2, RoundingMode.HALF_EVEN, true));
        
        // Middle Eastern currencies
        CURRENCY_CONFIGS.put("AED", new CurrencyPrecisionConfig("AED", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("SAR", new CurrencyPrecisionConfig("SAR", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("QAR", new CurrencyPrecisionConfig("QAR", 2, RoundingMode.HALF_EVEN, true));
        
        // Special precision currencies
        CURRENCY_CONFIGS.put("KWD", new CurrencyPrecisionConfig("KWD", 3, RoundingMode.HALF_EVEN, true)); // Kuwaiti Dinar
        CURRENCY_CONFIGS.put("BHD", new CurrencyPrecisionConfig("BHD", 3, RoundingMode.HALF_EVEN, true)); // Bahraini Dinar
        CURRENCY_CONFIGS.put("OMR", new CurrencyPrecisionConfig("OMR", 3, RoundingMode.HALF_EVEN, true)); // Omani Rial
        CURRENCY_CONFIGS.put("JOD", new CurrencyPrecisionConfig("JOD", 3, RoundingMode.HALF_EVEN, true)); // Jordanian Dinar
        
        // High inflation currencies (may require special handling)
        CURRENCY_CONFIGS.put("TRY", new CurrencyPrecisionConfig("TRY", 2, RoundingMode.HALF_EVEN, true));
        CURRENCY_CONFIGS.put("ARS", new CurrencyPrecisionConfig("ARS", 2, RoundingMode.HALF_EVEN, true));
        
        // Cryptocurrencies with high precision
        CURRENCY_CONFIGS.put("BTC", new CurrencyPrecisionConfig("BTC", 8, RoundingMode.HALF_EVEN, false));
        CURRENCY_CONFIGS.put("ETH", new CurrencyPrecisionConfig("ETH", 18, RoundingMode.HALF_EVEN, false));
        CURRENCY_CONFIGS.put("USDC", new CurrencyPrecisionConfig("USDC", 6, RoundingMode.HALF_EVEN, false));
        CURRENCY_CONFIGS.put("USDT", new CurrencyPrecisionConfig("USDT", 6, RoundingMode.HALF_EVEN, false));
    }
    
    /**
     * Initialize calculation contexts for different operation types
     */
    private static void initializeCalculationContexts() {
        // Precise calculations with maximum precision
        CALCULATION_CONTEXTS.put(CalculationType.INTERMEDIATE, new MathContext(34, RoundingMode.HALF_EVEN));
        
        // Standard financial calculations
        CALCULATION_CONTEXTS.put(CalculationType.FINANCIAL, new MathContext(15, RoundingMode.HALF_EVEN));
        
        // Exchange rate calculations with high precision
        CALCULATION_CONTEXTS.put(CalculationType.EXCHANGE_RATE, new MathContext(20, RoundingMode.HALF_EVEN));
        
        // Interest rate calculations
        CALCULATION_CONTEXTS.put(CalculationType.INTEREST, new MathContext(16, RoundingMode.HALF_EVEN));
        
        // Final display rounding
        CALCULATION_CONTEXTS.put(CalculationType.DISPLAY, new MathContext(10, RoundingMode.HALF_EVEN));
    }
    
    /**
     * Create a precise monetary amount with proper currency precision
     */
    public PreciseMonetaryAmount createPreciseAmount(BigDecimal amount, String currencyCode) {
        validateInputs(amount, currencyCode);
        
        CurrencyPrecisionConfig config = getCurrencyConfig(currencyCode);
        
        // Perform intermediate calculation with high precision
        BigDecimal preciseAmount = amount.setScale(intermediatePrecisionScale, RoundingMode.HALF_EVEN);
        
        // Create precise monetary amount
        PreciseMonetaryAmount monetaryAmount = PreciseMonetaryAmount.builder()
                .amount(preciseAmount)
                .currencyCode(currencyCode)
                .precision(config.getDecimalPlaces())
                .roundingMode(config.getRoundingMode())
                .createdAt(Instant.now())
                .build();
        
        if (precisionAuditEnabled) {
            auditService.logAmountCreation(currencyCode, amount, preciseAmount, Instant.now());
        }
        
        return monetaryAmount;
    }
    
    /**
     * Add two monetary amounts with precision preservation
     */
    public PreciseMonetaryAmount add(PreciseMonetaryAmount amount1, PreciseMonetaryAmount amount2) {
        validateSameCurrency(amount1, amount2);
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE);
        
        // Perform addition with intermediate precision
        BigDecimal result = amount1.getAmount().add(amount2.getAmount(), context);
        
        // Create result with proper precision
        PreciseMonetaryAmount resultAmount = createPreciseAmount(result, amount1.getCurrencyCode());
        
        if (precisionAuditEnabled) {
            auditService.logArithmeticOperation("ADD", amount1, amount2, resultAmount, Instant.now());
        }
        
        return resultAmount;
    }
    
    /**
     * Subtract two monetary amounts with precision preservation
     */
    public PreciseMonetaryAmount subtract(PreciseMonetaryAmount amount1, PreciseMonetaryAmount amount2) {
        validateSameCurrency(amount1, amount2);
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE);
        
        // Perform subtraction with intermediate precision
        BigDecimal result = amount1.getAmount().subtract(amount2.getAmount(), context);
        
        // Create result with proper precision
        PreciseMonetaryAmount resultAmount = createPreciseAmount(result, amount1.getCurrencyCode());
        
        if (precisionAuditEnabled) {
            auditService.logArithmeticOperation("SUBTRACT", amount1, amount2, resultAmount, Instant.now());
        }
        
        return resultAmount;
    }
    
    /**
     * Multiply monetary amount by a factor with precision preservation
     */
    public PreciseMonetaryAmount multiply(PreciseMonetaryAmount amount, BigDecimal multiplier) {
        validateInputs(amount.getAmount(), amount.getCurrencyCode());
        validateMultiplier(multiplier);
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE);
        
        // Perform multiplication with intermediate precision
        BigDecimal result = amount.getAmount().multiply(multiplier, context);
        
        // Create result with proper precision
        PreciseMonetaryAmount resultAmount = createPreciseAmount(result, amount.getCurrencyCode());
        
        if (precisionAuditEnabled) {
            auditService.logMultiplicationOperation(amount, multiplier, resultAmount, Instant.now());
        }
        
        return resultAmount;
    }
    
    /**
     * Divide monetary amount by a divisor with precision preservation
     */
    public PreciseMonetaryAmount divide(PreciseMonetaryAmount amount, BigDecimal divisor) {
        validateInputs(amount.getAmount(), amount.getCurrencyCode());
        validateDivisor(divisor);
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE);
        
        // Perform division with intermediate precision
        BigDecimal result = amount.getAmount().divide(divisor, context);
        
        // Create result with proper precision
        PreciseMonetaryAmount resultAmount = createPreciseAmount(result, amount.getCurrencyCode());
        
        if (precisionAuditEnabled) {
            auditService.logDivisionOperation(amount, divisor, resultAmount, Instant.now());
        }
        
        return resultAmount;
    }
    
    /**
     * Convert currency with exchange rate and precision preservation
     */
    public PreciseMonetaryAmount convertCurrency(PreciseMonetaryAmount amount, String targetCurrency, 
                                                BigDecimal exchangeRate) {
        validateInputs(amount.getAmount(), amount.getCurrencyCode());
        validateInputs(exchangeRate, targetCurrency);
        
        if (amount.getCurrencyCode().equals(targetCurrency)) {
            log.warn("Converting currency to same currency: {}", targetCurrency);
            return amount;
        }
        
        MathContext exchangeContext = CALCULATION_CONTEXTS.get(CalculationType.EXCHANGE_RATE);
        
        // Perform currency conversion with high precision
        BigDecimal convertedAmount = amount.getAmount().multiply(exchangeRate, exchangeContext);
        
        // Create result in target currency
        PreciseMonetaryAmount resultAmount = createPreciseAmount(convertedAmount, targetCurrency);
        
        if (precisionAuditEnabled) {
            auditService.logCurrencyConversion(amount, targetCurrency, exchangeRate, 
                    resultAmount, Instant.now());
        }
        
        return resultAmount;
    }
    
    /**
     * Calculate percentage of an amount with precision preservation
     */
    public PreciseMonetaryAmount calculatePercentage(PreciseMonetaryAmount amount, BigDecimal percentage) {
        validateInputs(amount.getAmount(), amount.getCurrencyCode());
        validatePercentage(percentage);
        
        // Convert percentage to decimal (e.g., 5% -> 0.05)
        BigDecimal percentageDecimal = percentage.divide(new BigDecimal("100"), 
                CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE));
        
        // Calculate percentage amount
        return multiply(amount, percentageDecimal);
    }
    
    /**
     * Round amount to final display precision
     */
    public PreciseMonetaryAmount roundToDisplayPrecision(PreciseMonetaryAmount amount) {
        CurrencyPrecisionConfig config = getCurrencyConfig(amount.getCurrencyCode());
        
        // Round to currency-specific decimal places
        BigDecimal rounded = amount.getAmount().setScale(config.getDecimalPlaces(), config.getRoundingMode());
        
        PreciseMonetaryAmount roundedAmount = PreciseMonetaryAmount.builder()
                .amount(rounded)
                .currencyCode(amount.getCurrencyCode())
                .precision(config.getDecimalPlaces())
                .roundingMode(config.getRoundingMode())
                .createdAt(Instant.now())
                .build();
        
        if (precisionAuditEnabled) {
            auditService.logRoundingOperation(amount, roundedAmount, Instant.now());
        }
        
        return roundedAmount;
    }
    
    /**
     * Validate precision accuracy of financial calculation
     */
    public PrecisionValidationResult validatePrecision(PreciseMonetaryAmount amount, 
                                                      PreciseMonetaryAmount expectedAmount, 
                                                      BigDecimal tolerancePercentage) {
        if (!precisionValidationEnabled) {
            return PrecisionValidationResult.valid("Validation disabled");
        }
        
        try {
            validateSameCurrency(amount, expectedAmount);
            
            // Calculate absolute difference
            BigDecimal difference = amount.getAmount().subtract(expectedAmount.getAmount()).abs();
            
            // Calculate tolerance amount
            BigDecimal tolerance = expectedAmount.getAmount().multiply(tolerancePercentage)
                    .divide(new BigDecimal("100"), CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE));
            
            boolean isWithinTolerance = difference.compareTo(tolerance) <= 0;
            
            PrecisionValidationResult result = PrecisionValidationResult.builder()
                    .valid(isWithinTolerance)
                    .actualAmount(amount)
                    .expectedAmount(expectedAmount)
                    .difference(difference)
                    .tolerance(tolerance)
                    .tolerancePercentage(tolerancePercentage)
                    .validatedAt(Instant.now())
                    .build();
            
            if (precisionAuditEnabled) {
                auditService.logPrecisionValidation(result, Instant.now());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Precision validation failed: {}", e.getMessage(), e);
            return PrecisionValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }
    
    /**
     * Perform compound interest calculation with precision preservation
     */
    public PreciseMonetaryAmount calculateCompoundInterest(PreciseMonetaryAmount principal, 
                                                          BigDecimal annualRate, 
                                                          int compoundingPeriodsPerYear, 
                                                          int years) {
        validateInputs(principal.getAmount(), principal.getCurrencyCode());
        validateInterestRate(annualRate);
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTEREST);
        
        // A = P(1 + r/n)^(nt)
        BigDecimal periodicRate = annualRate.divide(new BigDecimal(compoundingPeriodsPerYear), context);
        BigDecimal onePlusPeriodicRate = BigDecimal.ONE.add(periodicRate);
        
        int totalPeriods = compoundingPeriodsPerYear * years;
        
        // Calculate (1 + r/n)^(nt) using precise exponentiation
        BigDecimal compoundFactor = preciseExponentiation(onePlusPeriodicRate, totalPeriods, context);
        
        // Calculate final amount
        BigDecimal finalAmount = principal.getAmount().multiply(compoundFactor, context);
        
        PreciseMonetaryAmount result = createPreciseAmount(finalAmount, principal.getCurrencyCode());
        
        if (precisionAuditEnabled) {
            auditService.logInterestCalculation(principal, annualRate, compoundingPeriodsPerYear, 
                    years, result, Instant.now());
        }
        
        return result;
    }
    
    /**
     * Allocate amount proportionally with precision preservation
     */
    public List<PreciseMonetaryAmount> allocateProportionally(PreciseMonetaryAmount totalAmount, 
                                                             List<BigDecimal> proportions) {
        validateInputs(totalAmount.getAmount(), totalAmount.getCurrencyCode());
        validateProportions(proportions);
        
        List<PreciseMonetaryAmount> allocatedAmounts = new ArrayList<>();
        BigDecimal remainingAmount = totalAmount.getAmount();
        
        // Calculate sum of proportions for normalization
        BigDecimal totalProportion = proportions.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE);
        
        // Allocate based on proportions (keep highest precision until the end)
        for (int i = 0; i < proportions.size() - 1; i++) {
            BigDecimal proportion = proportions.get(i);
            BigDecimal normalizedProportion = proportion.divide(totalProportion, context);
            
            BigDecimal allocatedAmount = totalAmount.getAmount().multiply(normalizedProportion, context);
            
            PreciseMonetaryAmount allocated = createPreciseAmount(allocatedAmount, totalAmount.getCurrencyCode());
            allocatedAmounts.add(allocated);
            
            remainingAmount = remainingAmount.subtract(allocatedAmount);
        }
        
        // Allocate remaining to last proportion (ensures exact total)
        PreciseMonetaryAmount lastAllocated = createPreciseAmount(remainingAmount, totalAmount.getCurrencyCode());
        allocatedAmounts.add(lastAllocated);
        
        if (precisionAuditEnabled) {
            auditService.logProportionalAllocation(totalAmount, proportions, allocatedAmounts, Instant.now());
        }
        
        return allocatedAmounts;
    }
    
    /**
     * Sum multiple amounts with precision preservation
     */
    public PreciseMonetaryAmount sum(List<PreciseMonetaryAmount> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            throw new IllegalArgumentException("Cannot sum empty list of amounts");
        }
        
        String currencyCode = amounts.get(0).getCurrencyCode();
        
        // Validate all amounts have same currency
        for (PreciseMonetaryAmount amount : amounts) {
            if (!currencyCode.equals(amount.getCurrencyCode())) {
                throw new IllegalArgumentException(
                    String.format("Mixed currencies in sum: %s vs %s", currencyCode, amount.getCurrencyCode()));
            }
        }
        
        MathContext context = CALCULATION_CONTEXTS.get(CalculationType.INTERMEDIATE);
        
        // Sum with intermediate precision
        BigDecimal total = amounts.stream()
                .map(PreciseMonetaryAmount::getAmount)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, context));
        
        PreciseMonetaryAmount result = createPreciseAmount(total, currencyCode);
        
        if (precisionAuditEnabled) {
            auditService.logSumOperation(amounts, result, Instant.now());
        }
        
        return result;
    }
    
    // Private helper methods
    
    private void validateInputs(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }
        if (currencyCode.length() != 3) {
            throw new IllegalArgumentException("Currency code must be 3 characters (ISO 4217)");
        }
    }
    
    private void validateSameCurrency(PreciseMonetaryAmount amount1, PreciseMonetaryAmount amount2) {
        if (!amount1.getCurrencyCode().equals(amount2.getCurrencyCode())) {
            throw new IllegalArgumentException(
                String.format("Currency mismatch: %s vs %s", 
                        amount1.getCurrencyCode(), amount2.getCurrencyCode()));
        }
    }
    
    private void validateMultiplier(BigDecimal multiplier) {
        if (multiplier == null) {
            throw new IllegalArgumentException("Multiplier cannot be null");
        }
    }
    
    private void validateDivisor(BigDecimal divisor) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Divisor cannot be null or zero");
        }
    }
    
    private void validatePercentage(BigDecimal percentage) {
        if (percentage == null) {
            throw new IllegalArgumentException("Percentage cannot be null");
        }
        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Percentage cannot be negative");
        }
    }
    
    private void validateInterestRate(BigDecimal rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Interest rate cannot be null");
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
    }
    
    private void validateProportions(List<BigDecimal> proportions) {
        if (proportions == null || proportions.isEmpty()) {
            throw new IllegalArgumentException("Proportions cannot be null or empty");
        }
        for (BigDecimal proportion : proportions) {
            if (proportion == null || proportion.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("All proportions must be non-negative");
            }
        }
    }
    
    private CurrencyPrecisionConfig getCurrencyConfig(String currencyCode) {
        CurrencyPrecisionConfig config = CURRENCY_CONFIGS.get(currencyCode);
        if (config == null) {
            log.warn("Unknown currency code: {}. Using default configuration.", currencyCode);
            return new CurrencyPrecisionConfig(currencyCode, defaultPrecisionScale, 
                    RoundingMode.HALF_EVEN, true);
        }
        return config;
    }
    
    private BigDecimal preciseExponentiation(BigDecimal base, int exponent, MathContext context) {
        if (exponent == 0) {
            return BigDecimal.ONE;
        }
        
        if (exponent == 1) {
            return base;
        }
        
        // Use repeated squaring for efficient exponentiation
        BigDecimal result = BigDecimal.ONE;
        BigDecimal power = base;
        int exp = exponent;
        
        while (exp > 0) {
            if (exp % 2 == 1) {
                result = result.multiply(power, context);
            }
            power = power.multiply(power, context);
            exp /= 2;
        }
        
        return result;
    }
    
    // Supporting classes and enums
    
    public enum CalculationType {
        INTERMEDIATE,
        FINANCIAL, 
        EXCHANGE_RATE,
        INTEREST,
        DISPLAY
    }
}