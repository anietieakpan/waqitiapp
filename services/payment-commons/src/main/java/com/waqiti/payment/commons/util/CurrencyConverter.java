package com.waqiti.payment.commons.util;

import com.waqiti.payment.commons.domain.Money;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for currency conversion operations
 * In production, this would integrate with real-time exchange rate APIs
 */
@Slf4j
@UtilityClass
public class CurrencyConverter {
    
    // Cache for exchange rates (in production, this would be regularly updated)
    private static final Map<String, BigDecimal> EXCHANGE_RATES = new ConcurrentHashMap<>();
    
    // Base currency for conversions (USD)
    private static final String BASE_CURRENCY = "USD";
    
    // Static initialization of mock exchange rates
    static {
        initializeMockRates();
    }
    
    /**
     * Convert money from one currency to another
     */
    public static Money convert(Money amount, String targetCurrency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        
        if (targetCurrency == null || targetCurrency.trim().isEmpty()) {
            throw new IllegalArgumentException("Target currency cannot be null or empty");
        }
        
        String sourceCurrency = amount.getCurrencyCode();
        
        // If same currency, return as-is
        if (sourceCurrency.equals(targetCurrency)) {
            return amount;
        }
        
        BigDecimal exchangeRate = getExchangeRate(sourceCurrency, targetCurrency);
        BigDecimal convertedAmount = amount.getAmount().multiply(exchangeRate);
        
        // Round to target currency's default fraction digits
        Currency targetCurrencyObj = Currency.getInstance(targetCurrency);
        convertedAmount = convertedAmount.setScale(
            targetCurrencyObj.getDefaultFractionDigits(), 
            RoundingMode.HALF_UP
        );
        
        log.debug("Converted {} {} to {} {} using rate {}", 
            amount.getAmount(), sourceCurrency, 
            convertedAmount, targetCurrency, 
            exchangeRate);
        
        return Money.of(convertedAmount, targetCurrency);
    }
    
    /**
     * Convert money to USD
     */
    public static Money convertToUSD(Money amount) {
        return convert(amount, "USD");
    }
    
    /**
     * Convert money from USD to target currency
     */
    public static Money convertFromUSD(Money usdAmount, String targetCurrency) {
        if (!"USD".equals(usdAmount.getCurrencyCode())) {
            throw new IllegalArgumentException("Amount must be in USD");
        }
        return convert(usdAmount, targetCurrency);
    }
    
    /**
     * Get exchange rate between two currencies
     */
    public static BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        String rateKey = fromCurrency + "_" + toCurrency;
        BigDecimal directRate = EXCHANGE_RATES.get(rateKey);
        
        if (directRate != null) {
            return directRate;
        }
        
        // Try reverse rate
        String reverseKey = toCurrency + "_" + fromCurrency;
        BigDecimal reverseRate = EXCHANGE_RATES.get(reverseKey);
        
        if (reverseRate != null) {
            return BigDecimal.ONE.divide(reverseRate, 6, RoundingMode.HALF_UP);
        }
        
        // Cross-currency via USD
        if (!fromCurrency.equals(BASE_CURRENCY) && !toCurrency.equals(BASE_CURRENCY)) {
            BigDecimal fromToUSD = getExchangeRate(fromCurrency, BASE_CURRENCY);
            BigDecimal usdToTarget = getExchangeRate(BASE_CURRENCY, toCurrency);
            return fromToUSD.multiply(usdToTarget);
        }
        
        throw new IllegalArgumentException(
            String.format("Exchange rate not available for %s to %s", fromCurrency, toCurrency)
        );
    }
    
    /**
     * Check if conversion is supported between two currencies
     */
    public static boolean isConversionSupported(String fromCurrency, String toCurrency) {
        try {
            getExchangeRate(fromCurrency, toCurrency);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get all supported currencies
     */
    public static String[] getSupportedCurrencies() {
        return new String[]{
            "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "BRL", 
            "MXN", "KRW", "SGD", "HKD", "NZD", "SEK", "NOK", "DKK", "PLN", "CZK"
        };
    }
    
    /**
     * Update exchange rate (for real-time rate updates)
     */
    public static void updateExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive");
        }
        
        String rateKey = fromCurrency + "_" + toCurrency;
        EXCHANGE_RATES.put(rateKey, rate);
        
        log.info("Updated exchange rate: {} = {}", rateKey, rate);
    }
    
    /**
     * Calculate conversion fees (typically 0.5% - 3% depending on currencies and provider)
     */
    public static Money calculateConversionFee(Money amount, String targetCurrency, 
                                              BigDecimal feePercentage) {
        Money convertedAmount = convert(amount, targetCurrency);
        BigDecimal feeAmount = convertedAmount.getAmount()
            .multiply(feePercentage.divide(BigDecimal.valueOf(100)));
        
        return Money.of(feeAmount, targetCurrency);
    }
    
    /**
     * Convert with fees included
     */
    public static ConversionResult convertWithFees(Money amount, String targetCurrency, 
                                                  BigDecimal feePercentage) {
        Money convertedAmount = convert(amount, targetCurrency);
        Money fees = calculateConversionFee(amount, targetCurrency, feePercentage);
        Money netAmount = convertedAmount.subtract(fees);
        
        return ConversionResult.builder()
            .originalAmount(amount)
            .convertedAmount(convertedAmount)
            .fees(fees)
            .netAmount(netAmount)
            .exchangeRate(getExchangeRate(amount.getCurrencyCode(), targetCurrency))
            .feePercentage(feePercentage)
            .build();
    }
    
    /**
     * Get conversion preview without executing
     */
    public static ConversionPreview getConversionPreview(Money amount, String targetCurrency) {
        BigDecimal exchangeRate = getExchangeRate(amount.getCurrencyCode(), targetCurrency);
        Money convertedAmount = convert(amount, targetCurrency);
        
        // Estimate fees (varies by currency pair)
        BigDecimal estimatedFeePercentage = getEstimatedFeePercentage(
            amount.getCurrencyCode(), targetCurrency
        );
        Money estimatedFees = calculateConversionFee(amount, targetCurrency, estimatedFeePercentage);
        
        return ConversionPreview.builder()
            .originalAmount(amount)
            .targetCurrency(targetCurrency)
            .exchangeRate(exchangeRate)
            .convertedAmount(convertedAmount)
            .estimatedFees(estimatedFees)
            .estimatedFeePercentage(estimatedFeePercentage)
            .rateTimestamp(java.time.Instant.now())
            .build();
    }
    
    /**
     * Batch conversion for multiple amounts
     */
    public static Map<Money, Money> convertBatch(Map<Money, String> conversions) {
        Map<Money, Money> results = new HashMap<>();
        
        for (Map.Entry<Money, String> entry : conversions.entrySet()) {
            try {
                Money converted = convert(entry.getKey(), entry.getValue());
                results.put(entry.getKey(), converted);
            } catch (Exception e) {
                log.error("Failed to convert {} to {}", 
                    entry.getKey(), entry.getValue(), e);
                // Continue with other conversions
            }
        }
        
        return results;
    }
    
    // Helper methods
    private static void initializeMockRates() {
        // Major currency pairs (mock rates - in production, fetch from real API)
        EXCHANGE_RATES.put("USD_EUR", new BigDecimal("0.85"));
        EXCHANGE_RATES.put("USD_GBP", new BigDecimal("0.75"));
        EXCHANGE_RATES.put("USD_CAD", new BigDecimal("1.25"));
        EXCHANGE_RATES.put("USD_AUD", new BigDecimal("1.35"));
        EXCHANGE_RATES.put("USD_JPY", new BigDecimal("110.0"));
        EXCHANGE_RATES.put("USD_CHF", new BigDecimal("0.92"));
        EXCHANGE_RATES.put("USD_CNY", new BigDecimal("6.45"));
        EXCHANGE_RATES.put("USD_INR", new BigDecimal("74.5"));
        EXCHANGE_RATES.put("USD_BRL", new BigDecimal("5.2"));
        EXCHANGE_RATES.put("USD_MXN", new BigDecimal("20.0"));
        EXCHANGE_RATES.put("USD_KRW", new BigDecimal("1180.0"));
        EXCHANGE_RATES.put("USD_SGD", new BigDecimal("1.35"));
        EXCHANGE_RATES.put("USD_HKD", new BigDecimal("7.8"));
        EXCHANGE_RATES.put("USD_NZD", new BigDecimal("1.42"));
        EXCHANGE_RATES.put("USD_SEK", new BigDecimal("8.6"));
        EXCHANGE_RATES.put("USD_NOK", new BigDecimal("8.9"));
        EXCHANGE_RATES.put("USD_DKK", new BigDecimal("6.3"));
        EXCHANGE_RATES.put("USD_PLN", new BigDecimal("3.9"));
        EXCHANGE_RATES.put("USD_CZK", new BigDecimal("22.0"));
        
        // Cross rates (some examples)
        EXCHANGE_RATES.put("EUR_GBP", new BigDecimal("0.88"));
        EXCHANGE_RATES.put("EUR_CHF", new BigDecimal("1.08"));
        EXCHANGE_RATES.put("GBP_EUR", new BigDecimal("1.14"));
        
        log.info("Initialized {} mock exchange rates", EXCHANGE_RATES.size());
    }
    
    private static BigDecimal getEstimatedFeePercentage(String fromCurrency, String toCurrency) {
        // Major currency pairs have lower fees
        if (isMajorCurrencyPair(fromCurrency, toCurrency)) {
            return new BigDecimal("0.5"); // 0.5%
        }
        
        // Exotic currency pairs have higher fees
        if (isExoticCurrencyPair(fromCurrency, toCurrency)) {
            return new BigDecimal("2.5"); // 2.5%
        }
        
        // Standard fee for most pairs
        return new BigDecimal("1.0"); // 1.0%
    }
    
    private static boolean isMajorCurrencyPair(String currency1, String currency2) {
        String[] majorCurrencies = {"USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD"};
        
        boolean currency1Major = false;
        boolean currency2Major = false;
        
        for (String major : majorCurrencies) {
            if (major.equals(currency1)) currency1Major = true;
            if (major.equals(currency2)) currency2Major = true;
        }
        
        return currency1Major && currency2Major;
    }
    
    private static boolean isExoticCurrencyPair(String currency1, String currency2) {
        String[] exoticCurrencies = {"THB", "ZAR", "TRY", "MYR", "IDR", "PHP"};
        
        for (String exotic : exoticCurrencies) {
            if (exotic.equals(currency1) || exotic.equals(currency2)) {
                return true;
            }
        }
        
        return false;
    }
    
    // Result classes
    @lombok.Data
    @lombok.Builder
    public static class ConversionResult {
        private Money originalAmount;
        private Money convertedAmount;
        private Money fees;
        private Money netAmount;
        private BigDecimal exchangeRate;
        private BigDecimal feePercentage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ConversionPreview {
        private Money originalAmount;
        private String targetCurrency;
        private BigDecimal exchangeRate;
        private Money convertedAmount;
        private Money estimatedFees;
        private BigDecimal estimatedFeePercentage;
        private java.time.Instant rateTimestamp;
    }
}