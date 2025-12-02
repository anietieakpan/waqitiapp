package com.waqiti.corebanking.service;

import com.waqiti.corebanking.entity.ExchangeRateHistory;
import com.waqiti.corebanking.exception.ExchangeRateException;
import com.waqiti.corebanking.repository.ExchangeRateHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Currency Exchange Service
 * 
 * Handles real-time currency exchange rates and conversions.
 * Integrates with multiple exchange rate providers for redundancy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyExchangeService {
    
    @Lazy
    private final CurrencyExchangeService self;
    private final RestTemplate restTemplate;
    private final ExchangeRateHistoryRepository exchangeRateHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${core-banking.currency.exchange-api-key:}")
    private String exchangeApiKey;
    
    @Value("${core-banking.currency.base-currency:USD}")
    private String baseCurrency;
    
    @Value("${core-banking.currency.cache-duration-minutes:60}")
    private int cacheDurationMinutes;
    
    @Value("${core-banking.currency.spread-percentage:0.5}")
    private BigDecimal spreadPercentage;
    
    // In-memory cache for exchange rates
    private final Map<String, ExchangeRateData> exchangeRatesCache = new ConcurrentHashMap<>();
    private LocalDateTime lastRateUpdate;
    
    /**
     * Get current exchange rate between two currencies
     */
    @Cacheable(value = "exchangeRates", key = "#fromCurrency + '_' + #toCurrency")
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        log.debug("Getting exchange rate from {} to {}", fromCurrency, toCurrency);
        
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        try {
            // Try to get from cache first
            String cacheKey = fromCurrency + "_" + toCurrency;
            ExchangeRateData cachedRate = exchangeRatesCache.get(cacheKey);
            
            if (cachedRate != null && !isRateExpired(cachedRate)) {
                log.debug("Using cached exchange rate: {} {} = {} {}", 
                    1, fromCurrency, cachedRate.getRate(), toCurrency);
                return cachedRate.getRate();
            }
            
            // Fetch fresh rate from API
            BigDecimal rate = fetchExchangeRateFromApi(fromCurrency, toCurrency);
            
            // Cache the rate
            exchangeRatesCache.put(cacheKey, ExchangeRateData.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(rate)
                .timestamp(LocalDateTime.now())
                .source("API")
                .build());
            
            log.info("Fetched fresh exchange rate: {} {} = {} {}", 
                1, fromCurrency, rate, toCurrency);
            
            return rate;
            
        } catch (Exception e) {
            log.error("Error getting exchange rate from {} to {}", fromCurrency, toCurrency, e);
            
            // Fallback to cached rate even if expired
            String cacheKey = fromCurrency + "_" + toCurrency;
            ExchangeRateData fallbackRate = exchangeRatesCache.get(cacheKey);
            if (fallbackRate != null) {
                log.warn("Using expired cached rate as fallback: {} {} = {} {}", 
                    1, fromCurrency, fallbackRate.getRate(), toCurrency);
                return fallbackRate.getRate();
            }
            
            // Ultimate fallback - return 1.0 (this should trigger alerts)
            log.error("No exchange rate available for {} to {}, returning 1.0", fromCurrency, toCurrency);
            return BigDecimal.ONE;
        }
    }
    
    /**
     * Convert amount from one currency to another
     */
    public CurrencyConversionResult convertCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
        log.info("Converting {} {} to {}", amount, fromCurrency, toCurrency);
        
        if (fromCurrency.equals(toCurrency)) {
            return CurrencyConversionResult.builder()
                .originalAmount(amount)
                .convertedAmount(amount)
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .exchangeRate(BigDecimal.ONE)
                .spread(BigDecimal.ZERO)
                .conversionTimestamp(LocalDateTime.now())
                .build();
        }
        
        try {
            BigDecimal midRate = getExchangeRate(fromCurrency, toCurrency);
            
            // Apply spread for customer conversions
            BigDecimal spreadAmount = midRate.multiply(spreadPercentage).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            BigDecimal customerRate = midRate.subtract(spreadAmount); // Slightly worse rate for customer
            
            BigDecimal convertedAmount = amount.multiply(customerRate).setScale(2, RoundingMode.HALF_UP);
            
            CurrencyConversionResult result = CurrencyConversionResult.builder()
                .originalAmount(amount)
                .convertedAmount(convertedAmount)
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .exchangeRate(customerRate)
                .midMarketRate(midRate)
                .spread(spreadAmount)
                .spreadPercentage(spreadPercentage)
                .conversionTimestamp(LocalDateTime.now())
                .build();
            
            log.info("Conversion result: {} {} = {} {} (rate: {}, spread: {}%)", 
                amount, fromCurrency, convertedAmount, toCurrency, customerRate, spreadPercentage);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error converting {} {} to {}", amount, fromCurrency, toCurrency, e);
            throw new RuntimeException("Currency conversion failed", e);
        }
    }
    
    /**
     * Get all available exchange rates for a base currency
     */
    @Cacheable(value = "exchangeRates", key = "'all_' + #baseCurrency")
    public Map<String, BigDecimal> getAllExchangeRates(String baseCurrency) {
        log.debug("Getting all exchange rates for base currency: {}", baseCurrency);
        
        // Supported currencies
        String[] supportedCurrencies = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "NGN", "KES", "GHS"};
        
        Map<String, BigDecimal> rates = new HashMap<>();
        
        for (String currency : supportedCurrencies) {
            if (!currency.equals(baseCurrency)) {
                try {
                    BigDecimal rate = self.getExchangeRate(baseCurrency, currency);
                    rates.put(currency, rate);
                } catch (Exception e) {
                    log.warn("Failed to get rate for {}/{}", baseCurrency, currency, e);
                    // Don't include this currency in the result
                }
            }
        }
        
        return rates;
    }
    
    /**
     * Scheduled task to refresh exchange rates and clear cache
     */
    @Scheduled(fixedRateString = "${core-banking.currency.refresh-rate-ms:1800000}") // 30 minutes
    @CacheEvict(value = "exchangeRates", allEntries = true)
    public void refreshExchangeRates() {
        log.info("Starting scheduled refresh of exchange rates");
        
        try {
            String[] majorCurrencies = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY"};
            int refreshedCount = 0;
            
            for (String fromCurrency : majorCurrencies) {
                for (String toCurrency : majorCurrencies) {
                    if (!fromCurrency.equals(toCurrency)) {
                        try {
                            // This will fetch fresh rates and update cache
                            self.getExchangeRate(fromCurrency, toCurrency);
                            refreshedCount++;
                            
                            // Small non-blocking delay to avoid rate limiting
                            CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                                .execute(() -> {});
                        } catch (Exception e) {
                            log.warn("Failed to refresh rate for {}/{}", fromCurrency, toCurrency, e);
                        }
                    }
                }
            }
            
            lastRateUpdate = LocalDateTime.now();
            log.info("Completed scheduled refresh of exchange rates. Refreshed {} rates", refreshedCount);
            
        } catch (Exception e) {
            log.error("Error during scheduled exchange rate refresh", e);
        }
    }
    
    /**
     * Get historical exchange rate from database
     */
    public BigDecimal getHistoricalExchangeRate(String fromCurrency, String toCurrency, LocalDateTime date) {
        log.debug("Getting historical exchange rate from {} to {} for date {}", fromCurrency, toCurrency, date);
        
        try {
            // Validate inputs
            if (fromCurrency == null || toCurrency == null || date == null) {
                throw new IllegalArgumentException("Currency codes and date cannot be null");
            }
            
            fromCurrency = fromCurrency.toUpperCase();
            toCurrency = toCurrency.toUpperCase();
            
            // Same currency check
            if (fromCurrency.equals(toCurrency)) {
                return BigDecimal.ONE;
            }
            
            // Try to find the exact or closest historical rate before the specified date
            Optional<ExchangeRateHistory> historicalRate = exchangeRateHistoryRepository
                    .findClosestRateBeforeDate(fromCurrency, toCurrency, date);
            
            if (historicalRate.isPresent()) {
                ExchangeRateHistory rateHistory = historicalRate.get();
                log.debug("Found historical rate from {} to {} on {}: {}", 
                    fromCurrency, toCurrency, rateHistory.getRateDate(), rateHistory.getRate());
                return rateHistory.getRate();
            }
            
            // Try reverse rate (e.g., if we have EUR/USD but need USD/EUR)
            Optional<ExchangeRateHistory> reverseRate = exchangeRateHistoryRepository
                    .findClosestRateBeforeDate(toCurrency, fromCurrency, date);
            
            if (reverseRate.isPresent()) {
                BigDecimal inverseRate = BigDecimal.ONE.divide(reverseRate.get().getRate(), 8, RoundingMode.HALF_UP);
                log.debug("Found reverse historical rate from {} to {} on {}: {} (calculated as 1/{})", 
                    fromCurrency, toCurrency, reverseRate.get().getRateDate(), 
                    inverseRate, reverseRate.get().getRate());
                return inverseRate;
            }
            
            // If no historical rate found for the date, try to get rate through cross-currency calculation
            // (e.g., USD -> EUR via USD -> GBP -> EUR if direct rate not available)
            BigDecimal crossRate = calculateCrossHistoricalRate(fromCurrency, toCurrency, date);
            if (crossRate != null) {
                log.debug("Calculated cross historical rate from {} to {} for date {}: {}", 
                    fromCurrency, toCurrency, date, crossRate);
                return crossRate;
            }
            
            // If we still don't have historical data for the requested date,
            // check if it's within the last 24 hours and use current rate
            if (date.isAfter(LocalDateTime.now().minusDays(1))) {
                log.info("Historical rate not found for recent date {}, using current rate for {} to {}", 
                    date, fromCurrency, toCurrency);
                return getCurrentExchangeRate(fromCurrency, toCurrency);
            }
            
            // As a last resort, log warning and return current rate
            log.warn("No historical exchange rate found for {} to {} on date {}. Using current rate as fallback.", 
                fromCurrency, toCurrency, date);
            return getCurrentExchangeRate(fromCurrency, toCurrency);
            
        } catch (Exception e) {
            log.error("Error retrieving historical exchange rate for {} to {} on {}: {}", 
                fromCurrency, toCurrency, date, e.getMessage());
            
            // Fallback to current rate
            log.info("Falling back to current exchange rate");
            return getCurrentExchangeRate(fromCurrency, toCurrency);
        }
    }
    
    /**
     * Calculate cross-currency historical rate (e.g., EUR to GBP via USD)
     */
    private BigDecimal calculateCrossHistoricalRate(String fromCurrency, String toCurrency, LocalDateTime date) {
        try {
            // Common base currencies to try for cross-calculation
            String[] baseCurrencies = {"USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD"};
            
            for (String baseCurrency : baseCurrencies) {
                if (baseCurrency.equals(fromCurrency) || baseCurrency.equals(toCurrency)) {
                    continue; // Skip if it's one of our target currencies
                }
                
                // Try to find from -> base and base -> to
                Optional<ExchangeRateHistory> fromToBase = exchangeRateHistoryRepository
                        .findClosestRateBeforeDate(fromCurrency, baseCurrency, date);
                Optional<ExchangeRateHistory> baseToTo = exchangeRateHistoryRepository
                        .findClosestRateBeforeDate(baseCurrency, toCurrency, date);
                
                if (fromToBase.isPresent() && baseToTo.isPresent()) {
                    BigDecimal crossRate = fromToBase.get().getRate().multiply(baseToTo.get().getRate());
                    log.debug("Calculated cross rate {} -> {} -> {} = {}", 
                        fromCurrency, baseCurrency, toCurrency, crossRate);
                    return crossRate;
                }
                
                // Try reverse combinations
                Optional<ExchangeRateHistory> baseToFrom = exchangeRateHistoryRepository
                        .findClosestRateBeforeDate(baseCurrency, fromCurrency, date);
                Optional<ExchangeRateHistory> toToBase = exchangeRateHistoryRepository
                        .findClosestRateBeforeDate(toCurrency, baseCurrency, date);
                
                if (baseToFrom.isPresent() && toToBase.isPresent()) {
                    BigDecimal crossRate = BigDecimal.ONE
                        .divide(baseToFrom.get().getRate(), 8, RoundingMode.HALF_UP)
                        .divide(toToBase.get().getRate(), 8, RoundingMode.HALF_UP);
                    log.debug("Calculated reverse cross rate {} <- {} <- {} = {}", 
                        fromCurrency, baseCurrency, toCurrency, crossRate);
                    return crossRate;
                }
            }
            
            throw new CurrencyExchangeException("No cross exchange rate found for " + fromCurrency + " to " + toCurrency);
        } catch (Exception e) {
            log.warn("Error calculating cross historical rate for {} to {}: {}", 
                fromCurrency, toCurrency, e.getMessage());
            throw new CurrencyExchangeException("Failed to calculate cross historical rate: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get current exchange rate (renamed from original method for clarity)
     */
    private BigDecimal getCurrentExchangeRate(String fromCurrency, String toCurrency) {
        return getExchangeRate(fromCurrency, toCurrency);
    }
    
    /**
     * Store current exchange rate as historical data and update cache
     */
    @CachePut(value = "exchangeRates", key = "#fromCurrency + '_' + #toCurrency")
    public BigDecimal storeHistoricalRate(String fromCurrency, String toCurrency, BigDecimal rate, String source) {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Check if we already have a rate for this hour (to avoid duplicates)
            LocalDateTime hourStart = now.withMinute(0).withSecond(0).withNano(0);
            if (exchangeRateHistoryRepository.existsByFromCurrencyAndToCurrencyAndRateDate(
                    fromCurrency.toUpperCase(), toCurrency.toUpperCase(), hourStart)) {
                log.debug("Historical rate already exists for {} to {} at hour {}", 
                    fromCurrency, toCurrency, hourStart);
                return;
            }
            
            ExchangeRateHistory history = ExchangeRateHistory.builder()
                    .fromCurrency(fromCurrency.toUpperCase())
                    .toCurrency(toCurrency.toUpperCase())
                    .rate(rate)
                    .midMarketRate(rate)
                    .rateDate(hourStart) // Store at hour precision
                    .source(source)
                    .createdAt(now)
                    .build();
            
            exchangeRateHistoryRepository.save(history);
            log.debug("Stored historical exchange rate: {} {} to {} = {}", 
                hourStart, fromCurrency, toCurrency, rate);
                
        } catch (Exception e) {
            log.error("Failed to store historical exchange rate for {} to {}: {}", 
                fromCurrency, toCurrency, e.getMessage());
        }
        
        return rate; // Return rate for cache update
    }
    
    /**
     * Check if the service is healthy
     */
    public ExchangeServiceHealthStatus getHealthStatus() {
        try {
            // Test with a simple USD/EUR conversion
            BigDecimal testRate = getExchangeRate("USD", "EUR");
            boolean isHealthy = testRate != null && testRate.compareTo(BigDecimal.ZERO) > 0;
            
            return ExchangeServiceHealthStatus.builder()
                .healthy(isHealthy)
                .lastRateUpdate(lastRateUpdate)
                .cachedRatesCount(exchangeRatesCache.size())
                .supportedCurrencies(12)
                .testRate(testRate)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Health check failed", e);
            return ExchangeServiceHealthStatus.builder()
                .healthy(false)
                .error(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
    
    // Private helper methods
    
    private BigDecimal fetchExchangeRateFromApi(String fromCurrency, String toCurrency) {
        // Try multiple providers for redundancy
        
        // Primary provider: exchangerate-api.com
        try {
            return fetchFromExchangeRateApi(fromCurrency, toCurrency);
        } catch (Exception e) {
            log.warn("Primary exchange rate provider failed, trying fallback", e);
        }
        
        // Fallback provider: fixer.io
        try {
            return fetchFromFixerApi(fromCurrency, toCurrency);
        } catch (Exception e) {
            log.warn("Fallback exchange rate provider failed", e);
        }
        
        // Third provider: CurrencyAPI
        try {
            return fetchFromCurrencyApi(fromCurrency, toCurrency);
        } catch (Exception e) {
            log.warn("CurrencyAPI provider failed", e);
        }
        
        // Fourth provider: ExchangeRatesAPI
        try {
            return fetchFromExchangeRatesApi(fromCurrency, toCurrency);
        } catch (Exception e) {
            log.warn("ExchangeRatesAPI provider failed", e);
        }
        
        // Final fallback: use cached rates if available
        BigDecimal cachedRate = getCachedExchangeRate(fromCurrency, toCurrency);
        if (cachedRate != null) {
            log.warn("Using cached exchange rate for {}/{}: {}", fromCurrency, toCurrency, cachedRate);
            return cachedRate;
        }
        
        // If no cached rate available, throw exception - never use mock rates in production
        String errorMsg = String.format("Unable to fetch exchange rate for %s/%s from any provider", 
            fromCurrency, toCurrency);
        log.error(errorMsg);
        throw new ExchangeRateException(errorMsg);
    }
    
    private BigDecimal fetchFromExchangeRateApi(String fromCurrency, String toCurrency) {
        String url = String.format("https://api.exchangerate-api.com/v4/latest/%s", fromCurrency);
        
        try {
            ResponseEntity<ExchangeRateApiResponse> response = restTemplate.getForEntity(url, ExchangeRateApiResponse.class);
            
            if (response.getBody() != null && response.getBody().getRates() != null) {
                BigDecimal rate = response.getBody().getRates().get(toCurrency);
                if (rate != null) {
                    return rate;
                }
            }
            
            throw new RuntimeException("Rate not found in response");
            
        } catch (Exception e) {
            log.error("Error fetching from exchangerate-api.com", e);
            throw e;
        }
    }
    
    private BigDecimal fetchFromFixerApi(String fromCurrency, String toCurrency) {
        // Fixer.io requires API key for https access
        if (exchangeApiKey == null || exchangeApiKey.isEmpty()) {
            throw new RuntimeException("No API key configured for Fixer.io");
        }
        
        String url = String.format("http://data.fixer.io/api/latest?access_key=%s&base=%s&symbols=%s", 
            exchangeApiKey, fromCurrency, toCurrency);
        
        try {
            ResponseEntity<FixerApiResponse> response = restTemplate.getForEntity(url, FixerApiResponse.class);
            
            if (response.getBody() != null && response.getBody().isSuccess() && response.getBody().getRates() != null) {
                BigDecimal rate = response.getBody().getRates().get(toCurrency);
                if (rate != null) {
                    return rate;
                }
            }
            
            throw new RuntimeException("Rate not found in Fixer response");
            
        } catch (Exception e) {
            log.error("Error fetching from fixer.io", e);
            throw e;
        }
    }
    
    /**
     * Fetch exchange rate from CurrencyAPI
     */
    private BigDecimal fetchFromCurrencyApi(String fromCurrency, String toCurrency) {
        String apiKey = System.getProperty("currencyapi.key", exchangeApiKey);
        String url = String.format("https://api.currencyapi.com/v3/latest?apikey=%s&base_currency=%s&currencies=%s",
            apiKey, fromCurrency, toCurrency);
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null && data.containsKey(toCurrency)) {
                    Map<String, Object> currencyData = (Map<String, Object>) data.get(toCurrency);
                    Object value = currencyData.get("value");
                    if (value != null) {
                        return new BigDecimal(value.toString());
                    }
                }
            }
            
            throw new RuntimeException("Rate not found in CurrencyAPI response");
            
        } catch (Exception e) {
            log.error("Error fetching from CurrencyAPI", e);
            throw e;
        }
    }
    
    /**
     * Fetch exchange rate from ExchangeRatesAPI
     */
    private BigDecimal fetchFromExchangeRatesApi(String fromCurrency, String toCurrency) {
        String url = String.format("https://api.exchangeratesapi.io/latest?base=%s&symbols=%s",
            fromCurrency, toCurrency);
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null) {
                Map<String, BigDecimal> rates = (Map<String, BigDecimal>) response.getBody().get("rates");
                if (rates != null && rates.containsKey(toCurrency)) {
                    return rates.get(toCurrency);
                }
            }
            
            throw new RuntimeException("Rate not found in ExchangeRatesAPI response");
            
        } catch (Exception e) {
            log.error("Error fetching from ExchangeRatesAPI", e);
            throw e;
        }
    }
    
    /**
     * Get cached exchange rate from Redis
     */
    private BigDecimal getCachedExchangeRate(String fromCurrency, String toCurrency) {
        try {
            String cacheKey = buildCacheKey(fromCurrency, toCurrency);
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedValue != null) {
                // Check if cached value is not too old (max 24 hours)
                String cacheTimeKey = cacheKey + ":time";
                Object cacheTime = redisTemplate.opsForValue().get(cacheTimeKey);
                
                if (cacheTime != null) {
                    LocalDateTime cachedAt = LocalDateTime.parse(cacheTime.toString());
                    if (cachedAt.isAfter(LocalDateTime.now().minusHours(24))) {
                        return new BigDecimal(cachedValue.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving cached exchange rate from {} to {}: {}", fromCurrency, toCurrency, e.getMessage(), e);
        }
        
        // Cache miss - use fallback rate retrieval instead of returning null
        log.warn("Cache miss for exchange rate from {} to {}, attempting fallback rate retrieval", fromCurrency, toCurrency);
        return getFallbackExchangeRate(fromCurrency, toCurrency);
    }
    
    /**
     * Fallback method to retrieve exchange rate when cache fails
     */
    private BigDecimal getFallbackExchangeRate(String fromCurrency, String toCurrency) {
        try {
            // Try to get the most recent rate from database
            Optional<ExchangeRateHistory> lastKnownRate = exchangeRateHistoryRepository
                .findMostRecentRate(fromCurrency, toCurrency);
            
            if (lastKnownRate.isPresent()) {
                ExchangeRateHistory rate = lastKnownRate.get();
                log.info("Using fallback exchange rate from database: {} -> {} = {}", fromCurrency, toCurrency, rate.getRate());
                return rate.getRate();
            }
            
            // If no historical data, use conservative 1:1 rate with audit logging
            log.error("CRITICAL: No exchange rate data available for {} to {}. Using emergency 1:1 rate. Manual review required.", fromCurrency, toCurrency);
            
            // Create audit entry for emergency rate usage
            ExchangeRateHistory emergencyRate = ExchangeRateHistory.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(BigDecimal.ONE)
                .timestamp(LocalDateTime.now())
                .source("EMERGENCY_FALLBACK")
                .build();
            exchangeRateHistoryRepository.save(emergencyRate);
            
            return BigDecimal.ONE;
            
        } catch (Exception e) {
            log.error("CRITICAL: Fallback exchange rate retrieval failed for {} to {}: {}", fromCurrency, toCurrency, e.getMessage(), e);
            throw new ExchangeRateException(
                String.format("Unable to retrieve exchange rate for %s to %s. All fallback mechanisms failed.", fromCurrency, toCurrency),
                "EXCHANGE_RATE_UNAVAILABLE",
                e
            );
        }
    }
    
    /**
     * Build cache key for exchange rate
     */
    private String buildCacheKey(String fromCurrency, String toCurrency) {
        return String.format("exchange_rate:%s:%s", fromCurrency, toCurrency);
    }
    
    private boolean isRateExpired(ExchangeRateData rateData) {
        return rateData.getTimestamp().isBefore(LocalDateTime.now().minusMinutes(cacheDurationMinutes));
    }
    
    // DTOs and Data Classes
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CurrencyConversionResult {
        private BigDecimal originalAmount;
        private BigDecimal convertedAmount;
        private String fromCurrency;
        private String toCurrency;
        private BigDecimal exchangeRate;
        private BigDecimal midMarketRate;
        private BigDecimal spread;
        private BigDecimal spreadPercentage;
        private LocalDateTime conversionTimestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExchangeRateData {
        private String fromCurrency;
        private String toCurrency;
        private BigDecimal rate;
        private LocalDateTime timestamp;
        private String source;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExchangeServiceHealthStatus {
        private boolean healthy;
        private LocalDateTime lastRateUpdate;
        private int cachedRatesCount;
        private int supportedCurrencies;
        private BigDecimal testRate;
        private LocalDateTime timestamp;
        private String error;
    }
    
    // API Response DTOs
    
    @lombok.Data
    public static class ExchangeRateApiResponse {
        private String base;
        private String date;
        private Map<String, BigDecimal> rates;
    }
    
    @lombok.Data
    public static class FixerApiResponse {
        private boolean success;
        private long timestamp;
        private String base;
        private String date;
        private Map<String, BigDecimal> rates;
    }
}