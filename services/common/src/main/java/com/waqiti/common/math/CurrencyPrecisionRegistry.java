package com.waqiti.common.math;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for currency-specific precision settings.
 * Maintains precision rules for different currencies according to ISO 4217.
 *
 * This class is thread-safe and can be used across all financial services
 * to ensure consistent precision handling for different currencies.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since November 8, 2025
 */
@Slf4j
public final class CurrencyPrecisionRegistry {

    // Currency-specific precision settings (ISO 4217)
    private static final Map<String, Integer> CURRENCY_PRECISION = new ConcurrentHashMap<>();

    // Default precision for unknown currencies
    private static final int DEFAULT_PRECISION = 2;

    static {
        // Initialize common currencies according to ISO 4217
        CURRENCY_PRECISION.put("USD", 2);  // US Dollar
        CURRENCY_PRECISION.put("EUR", 2);  // Euro
        CURRENCY_PRECISION.put("GBP", 2);  // British Pound
        CURRENCY_PRECISION.put("CHF", 2);  // Swiss Franc
        CURRENCY_PRECISION.put("CAD", 2);  // Canadian Dollar
        CURRENCY_PRECISION.put("AUD", 2);  // Australian Dollar
        CURRENCY_PRECISION.put("JPY", 0);  // Japanese Yen (no decimals)
        CURRENCY_PRECISION.put("KRW", 0);  // South Korean Won (no decimals)
        CURRENCY_PRECISION.put("KWD", 3);  // Kuwaiti Dinar (3 decimals)
        CURRENCY_PRECISION.put("BHD", 3);  // Bahraini Dinar (3 decimals)
        CURRENCY_PRECISION.put("OMR", 3);  // Omani Rial (3 decimals)
        CURRENCY_PRECISION.put("TND", 3);  // Tunisian Dinar (3 decimals)
        CURRENCY_PRECISION.put("BTC", 8);  // Bitcoin
        CURRENCY_PRECISION.put("ETH", 8);  // Ethereum
        CURRENCY_PRECISION.put("XRP", 6);  // Ripple
        CURRENCY_PRECISION.put("LTC", 8);  // Litecoin

        log.info("Currency precision registry initialized with {} currencies", CURRENCY_PRECISION.size());
    }

    private CurrencyPrecisionRegistry() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Get the precision (decimal places) for a specific currency.
     *
     * @param currencyCode Currency code (e.g., "USD", "EUR", "BTC")
     * @return Number of decimal places for the currency (0-8)
     */
    public static int getPrecision(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            log.warn("Null or empty currency code provided, using default precision");
            return DEFAULT_PRECISION;
        }

        String upperCode = currencyCode.trim().toUpperCase();
        return CURRENCY_PRECISION.getOrDefault(upperCode, DEFAULT_PRECISION);
    }

    /**
     * Get display precision for a currency (typically 2 decimal places max).
     * Some currencies like JPY display with 0 decimals.
     *
     * @param currencyCode Currency code
     * @return Display precision (0-2)
     */
    public static int getDisplayPrecision(String currencyCode) {
        int actualPrecision = getPrecision(currencyCode);

        // Zero-decimal currencies stay at 0
        if (actualPrecision == 0) {
            return 0;
        }

        // Most currencies display with 2 decimals max
        return Math.min(actualPrecision, 2);
    }

    /**
     * Register a custom currency with specific precision.
     * Useful for adding new currencies or overriding defaults.
     *
     * @param currencyCode Currency code (e.g., "USDC", "DAI")
     * @param precision Decimal precision (0-18)
     * @throws IllegalArgumentException if currency code is invalid or precision is out of range
     */
    public static void registerCurrency(String currencyCode, int precision) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }
        if (precision < 0 || precision > 18) {
            throw new IllegalArgumentException("Precision must be between 0 and 18");
        }

        String upperCode = currencyCode.trim().toUpperCase();
        CURRENCY_PRECISION.put(upperCode, precision);
        log.info("Registered currency {} with precision {}", upperCode, precision);
    }

    /**
     * Check if a currency is registered.
     *
     * @param currencyCode Currency code
     * @return true if currency is registered
     */
    public static boolean isRegistered(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            return false;
        }
        return CURRENCY_PRECISION.containsKey(currencyCode.trim().toUpperCase());
    }

    /**
     * Get all registered currency codes.
     *
     * @return Set of registered currency codes
     */
    public static java.util.Set<String> getRegisteredCurrencies() {
        return new java.util.HashSet<>(CURRENCY_PRECISION.keySet());
    }

    /**
     * Check if a currency is a cryptocurrency.
     *
     * @param currencyCode Currency code
     * @return true if cryptocurrency
     */
    public static boolean isCryptocurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            return false;
        }

        String upperCode = currencyCode.trim().toUpperCase();
        return upperCode.equals("BTC") || upperCode.equals("ETH") ||
               upperCode.equals("XRP") || upperCode.equals("LTC") ||
               upperCode.equals("USDT") || upperCode.equals("USDC") ||
               upperCode.equals("DAI") || upperCode.equals("BUSD");
    }

    /**
     * Check if a currency requires zero decimal places.
     *
     * @param currencyCode Currency code
     * @return true if zero-decimal currency (e.g., JPY, KRW)
     */
    public static boolean isZeroDecimalCurrency(String currencyCode) {
        return getPrecision(currencyCode) == 0;
    }
}
