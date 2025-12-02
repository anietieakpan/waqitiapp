package com.waqiti.currency.provider;

import com.waqiti.currency.domain.ExchangeRate;
import com.waqiti.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CurrencyLayer API Provider Implementation
 * 
 * Production-ready implementation with:
 * - Real-time and historical exchange rates
 * - Circuit breaker pattern for fault tolerance
 * - Rate limiting to respect API quotas
 * - Automatic retry with exponential backoff
 * - Response caching for performance
 * - Health monitoring and metrics
 * - Fallback mechanisms
 * - Multi-tier caching strategy
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyLayerProvider implements ExchangeRateProvider {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${exchange-rate.providers.currencylayer.api-key}")
    private String apiKey;
    
    @Value("${exchange-rate.providers.currencylayer.base-url:http://api.currencylayer.com}")
    private String baseUrl;
    
    @Value("${exchange-rate.providers.currencylayer.enabled:true}")
    private boolean enabled;
    
    @Value("${exchange-rate.providers.currencylayer.use-https:true}")
    private boolean useHttps;
    
    @Value("${exchange-rate.providers.currencylayer.cache-ttl-minutes:5}")
    private int cacheTtlMinutes;
    
    @Value("${exchange-rate.providers.currencylayer.max-retries:3}")
    private int maxRetries;
    
    @Value("${exchange-rate.providers.currencylayer.timeout-seconds:10}")
    private int timeoutSeconds;
    
    // Rate limiting
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    
    // Caching
    private final Map<String, ExchangeRateCache> rateCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> latestRates = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastUpdate;
    
    // Health monitoring
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private volatile boolean circuitOpen = false;
    
    // Supported currencies cache
    private List<String> supportedCurrencies;
    private LocalDateTime supportedCurrenciesLastUpdate;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Rate limits based on subscription plan
    private static final int RATE_LIMIT_PER_MINUTE = 60;
    private static final int RATE_LIMIT_PER_HOUR = 1000;
    private static final int RATE_LIMIT_PER_MONTH = 100000;
    
    @PostConstruct
    public void init() {
        log.info("Initializing CurrencyLayer provider with API key: {}...", 
            apiKey != null ? apiKey.substring(0, 4) + "****" : "NOT_SET");
        
        // Schedule cache cleanup
        scheduler.scheduleAtFixedRate(this::cleanupCache, 0, 1, TimeUnit.HOURS);
        
        // Schedule health check
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 0, 5, TimeUnit.MINUTES);
        
        // Load supported currencies
        try {
            this.supportedCurrencies = fetchSupportedCurrencies();
        } catch (Exception e) {
            log.warn("Failed to load supported currencies initially", e);
        }
    }
    
    @Override
    @CircuitBreaker(name = "currencylayer", fallbackMethod = "getExchangeRateFallback")
    @RateLimiter(name = "currencylayer")
    @Retry(name = "currencylayer")
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        log.debug("Fetching exchange rate from {} to {} using CurrencyLayer", fromCurrency, toCurrency);
        
        if (!isAvailable()) {
            throw new BusinessException("CurrencyLayer provider is not available");
        }
        
        // Check cache first
        String cacheKey = fromCurrency + "_" + toCurrency;
        ExchangeRateCache cached = rateCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached rate for {}", cacheKey);
            return cached.getRate();
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Fetch live rates
            Map<String, Object> response = fetchLiveRates(fromCurrency, Arrays.asList(toCurrency));
            
            if (!validateResponse(response)) {
                throw new BusinessException("Invalid response from CurrencyLayer");
            }
            
            BigDecimal rate = extractRate(response, fromCurrency, toCurrency);
            
            ExchangeRate exchangeRate = ExchangeRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(rate)
                .provider(getProviderName())
                .timestamp(LocalDateTime.now())
                .spread(calculateSpread(fromCurrency, toCurrency))
                .confidence(calculateConfidence())
                .source("LIVE")
                .build();
            
            // Cache the result
            rateCache.put(cacheKey, new ExchangeRateCache(exchangeRate, cacheTtlMinutes));
            
            // Update metrics
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            log.info("Successfully fetched rate: 1 {} = {} {}", 
                fromCurrency, rate, toCurrency);
            
            return exchangeRate;
            
        } catch (Exception e) {
            log.error("Failed to fetch exchange rate from CurrencyLayer", e);
            updateMetrics(false, System.currentTimeMillis() - startTime);
            throw new BusinessException("Failed to fetch exchange rate", e);
        }
    }
    
    @Override
    public Map<String, ExchangeRate> getBulkExchangeRates(List<String> fromCurrencies, String baseCurrency) {
        log.debug("Fetching bulk exchange rates for {} currencies to {}", 
            fromCurrencies.size(), baseCurrency);
        
        Map<String, ExchangeRate> result = new HashMap<>();
        
        try {
            // CurrencyLayer supports fetching multiple rates in one call
            Map<String, Object> response = fetchLiveRates(baseCurrency, fromCurrencies);
            
            if (!validateResponse(response)) {
                throw new BusinessException("Invalid bulk response from CurrencyLayer");
            }
            
            Map<String, Object> quotes = (Map<String, Object>) response.get("quotes");
            
            for (String fromCurrency : fromCurrencies) {
                try {
                    String quoteKey = baseCurrency + fromCurrency;
                    if (quotes.containsKey(quoteKey)) {
                        BigDecimal rate = new BigDecimal(quotes.get(quoteKey).toString());
                        
                        // Need to invert the rate since we want from->base
                        if (!fromCurrency.equals(baseCurrency)) {
                            rate = BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP);
                        }
                        
                        ExchangeRate exchangeRate = ExchangeRate.builder()
                            .fromCurrency(fromCurrency)
                            .toCurrency(baseCurrency)
                            .rate(rate)
                            .provider(getProviderName())
                            .timestamp(LocalDateTime.now())
                            .spread(calculateSpread(fromCurrency, baseCurrency))
                            .confidence(calculateConfidence())
                            .build();
                        
                        result.put(fromCurrency, exchangeRate);
                        
                        // Cache individual rates
                        String cacheKey = fromCurrency + "_" + baseCurrency;
                        rateCache.put(cacheKey, new ExchangeRateCache(exchangeRate, cacheTtlMinutes));
                    }
                } catch (Exception e) {
                    log.warn("Failed to process rate for {}", fromCurrency, e);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch bulk exchange rates", e);
        }
        
        return result;
    }
    
    @Override
    @Cacheable(value = "allRates", key = "#baseCurrency")
    public Map<String, BigDecimal> getAllRatesForBase(String baseCurrency) {
        log.debug("Fetching all rates for base currency: {}", baseCurrency);
        
        try {
            Map<String, Object> response = fetchLiveRates(baseCurrency, null);
            
            if (!validateResponse(response)) {
                throw new BusinessException("Invalid response for all rates");
            }
            
            Map<String, Object> quotes = (Map<String, Object>) response.get("quotes");
            Map<String, BigDecimal> rates = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : quotes.entrySet()) {
                String key = entry.getKey();
                // Extract target currency from quote key (e.g., "USDEUR" -> "EUR")
                if (key.startsWith(baseCurrency) && key.length() > baseCurrency.length()) {
                    String targetCurrency = key.substring(baseCurrency.length());
                    BigDecimal rate = new BigDecimal(entry.getValue().toString());
                    rates.put(targetCurrency, rate);
                }
            }
            
            rates.put(baseCurrency, BigDecimal.ONE);
            
            // Update cache
            latestRates.clear();
            latestRates.putAll(rates);
            lastUpdate = LocalDateTime.now();
            
            return rates;
            
        } catch (Exception e) {
            log.error("Failed to fetch all rates for base currency", e);
            
            // Return cached rates if available
            if (!latestRates.isEmpty()) {
                log.warn("Returning cached rates due to API failure");
                return new HashMap<>(latestRates);
            }
            
            throw new BusinessException("Failed to fetch exchange rates", e);
        }
    }
    
    /**
     * Get historical exchange rate for a specific date
     */
    public ExchangeRate getHistoricalRate(String fromCurrency, String toCurrency, String date) {
        log.debug("Fetching historical rate from {} to {} for date {}", 
            fromCurrency, toCurrency, date);
        
        try {
            String url = UriComponentsBuilder.fromHttpUrl(getApiUrl() + "/historical")
                .queryParam("access_key", apiKey)
                .queryParam("date", date)
                .queryParam("source", fromCurrency)
                .queryParam("currencies", toCurrency)
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null && (boolean) response.getBody().get("success")) {
                Map<String, Object> quotes = (Map<String, Object>) response.getBody().get("quotes");
                String quoteKey = fromCurrency + toCurrency;
                
                if (quotes.containsKey(quoteKey)) {
                    BigDecimal rate = new BigDecimal(quotes.get(quoteKey).toString());
                    
                    return ExchangeRate.builder()
                        .fromCurrency(fromCurrency)
                        .toCurrency(toCurrency)
                        .rate(rate)
                        .provider(getProviderName())
                        .timestamp(LocalDateTime.parse(date + "T00:00:00"))
                        .spread(calculateSpread(fromCurrency, toCurrency))
                        .confidence(0.9) // Slightly lower confidence for historical data
                        .source("HISTORICAL")
                        .build();
                }
            }
            
            throw new BusinessException("Historical rate not available");
            
        } catch (Exception e) {
            log.error("Failed to fetch historical rate", e);
            throw new BusinessException("Failed to fetch historical rate", e);
        }
    }
    
    /**
     * Convert amount with real-time rates
     */
    public BigDecimal convertAmount(BigDecimal amount, String fromCurrency, String toCurrency) {
        log.debug("Converting {} {} to {}", amount, fromCurrency, toCurrency);
        
        try {
            String url = UriComponentsBuilder.fromHttpUrl(getApiUrl() + "/convert")
                .queryParam("access_key", apiKey)
                .queryParam("from", fromCurrency)
                .queryParam("to", toCurrency)
                .queryParam("amount", amount)
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null && (boolean) response.getBody().get("success")) {
                return new BigDecimal(response.getBody().get("result").toString());
            }
            
            throw new BusinessException("Conversion failed");
            
        } catch (Exception e) {
            log.error("Failed to convert amount", e);
            // Fallback to manual calculation
            ExchangeRate rate = getExchangeRate(fromCurrency, toCurrency);
            return amount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP);
        }
    }
    
    @Override
    public boolean supportsCurrency(String currency) {
        if (supportedCurrencies == null || supportedCurrenciesLastUpdate == null ||
            supportedCurrenciesLastUpdate.plusHours(24).isBefore(LocalDateTime.now())) {
            try {
                supportedCurrencies = fetchSupportedCurrencies();
                supportedCurrenciesLastUpdate = LocalDateTime.now();
            } catch (Exception e) {
                log.warn("Failed to refresh supported currencies", e);
            }
        }
        
        return supportedCurrencies != null && supportedCurrencies.contains(currency);
    }
    
    @Override
    public String getProviderName() {
        return "CurrencyLayer";
    }
    
    @Override
    public int getPriority() {
        return 2; // Second priority after OpenExchangeRates
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && 
               apiKey != null && 
               !apiKey.isEmpty() && 
               !circuitOpen &&
               !isRateLimitExceeded();
    }
    
    @Override
    public double getConfidenceScore() {
        if (failureCount.get() == 0) {
            return 0.95;
        }
        
        int total = successCount.get() + failureCount.get();
        if (total == 0) {
            return 0.5;
        }
        
        return Math.max(0.5, Math.min(0.95, (double) successCount.get() / total));
    }
    
    @Override
    public List<String> getSupportedCurrencies() {
        if (supportedCurrencies != null && !supportedCurrencies.isEmpty()) {
            return supportedCurrencies;
        }
        
        // Return default list
        return Arrays.asList(
            "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "CNY", "INR",
            "KRW", "SGD", "HKD", "NOK", "SEK", "DKK", "PLN", "CZK", "HUF", "RON",
            "BGN", "HRK", "RUB", "TRY", "BRL", "MXN", "ARS", "CLP", "COP", "PEN",
            "UYU", "ZAR", "THB", "MYR", "IDR", "PHP", "VND", "EGP", "PKR", "BDT",
            "NGN", "UAH", "KES", "GHS", "MAD", "AED", "SAR", "QAR", "KWD", "BHD",
            "OMR", "JOD", "ILS", "TWD", "ISK", "PAB", "BOB", "PYG", "GTQ", "HNL",
            "NIO", "DOP", "CRC", "TTD", "JMD", "BBD", "BSD", "BZD", "XCD", "AWG"
        );
    }
    
    @Override
    public RateLimitInfo getRateLimitInfo() {
        RateLimitInfo info = new RateLimitInfo();
        info.setRequestsPerMinute(RATE_LIMIT_PER_MINUTE);
        info.setRequestsPerHour(RATE_LIMIT_PER_HOUR);
        info.setRequestsPerDay(RATE_LIMIT_PER_MONTH / 30); // Approximate daily limit
        info.setRemainingRequests(calculateRemainingRequests());
        info.setResetTime(calculateResetTime());
        return info;
    }
    
    // Private helper methods
    
    private Map<String, Object> fetchLiveRates(String source, List<String> currencies) throws Exception {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(getApiUrl() + "/live")
            .queryParam("access_key", apiKey);
        
        if (source != null && !"USD".equals(source)) {
            builder.queryParam("source", source);
        }
        
        if (currencies != null && !currencies.isEmpty()) {
            builder.queryParam("currencies", String.join(",", currencies));
        }
        
        String url = builder.toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("User-Agent", "Waqiti-FinTech/1.0");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        );
        
        requestCount.incrementAndGet();
        
        return response.getBody();
    }
    
    private List<String> fetchSupportedCurrencies() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(getApiUrl() + "/list")
                .queryParam("access_key", apiKey)
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null && (boolean) response.getBody().get("success")) {
                Map<String, String> currencies = (Map<String, String>) response.getBody().get("currencies");
                return new ArrayList<>(currencies.keySet());
            }
        } catch (Exception e) {
            log.error("Failed to fetch supported currencies", e);
        }
        
        return getSupportedCurrencies(); // Return default list
    }
    
    private boolean validateResponse(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        
        if (response.containsKey("success") && !(boolean) response.get("success")) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            if (error != null) {
                log.error("CurrencyLayer API error: {} - {}", 
                    error.get("code"), error.get("info"));
            }
            return false;
        }
        
        return response.containsKey("quotes") || response.containsKey("result");
    }
    
    private BigDecimal extractRate(Map<String, Object> response, String from, String to) {
        Map<String, Object> quotes = (Map<String, Object>) response.get("quotes");
        
        if (quotes == null) {
            throw new BusinessException("No quotes in response");
        }
        
        // CurrencyLayer returns quotes as FROMTO pairs
        String directKey = from + to;
        String inverseKey = to + from;
        
        if (quotes.containsKey(directKey)) {
            return new BigDecimal(quotes.get(directKey).toString());
        } else if (quotes.containsKey(inverseKey)) {
            BigDecimal inverseRate = new BigDecimal(quotes.get(inverseKey).toString());
            return BigDecimal.ONE.divide(inverseRate, 8, RoundingMode.HALF_UP);
        }
        
        // Try cross rate through USD
        String fromUsdKey = "USD" + from;
        String toUsdKey = "USD" + to;
        
        if (quotes.containsKey(fromUsdKey) && quotes.containsKey(toUsdKey)) {
            BigDecimal fromUsdRate = new BigDecimal(quotes.get(fromUsdKey).toString());
            BigDecimal toUsdRate = new BigDecimal(quotes.get(toUsdKey).toString());
            return toUsdRate.divide(fromUsdRate, 8, RoundingMode.HALF_UP);
        }
        
        throw new BusinessException("Rate not found in response");
    }
    
    private BigDecimal calculateSpread(String from, String to) {
        // Major pairs have tighter spreads
        Set<String> majorCurrencies = Set.of("USD", "EUR", "GBP", "JPY", "CHF");
        
        if (majorCurrencies.contains(from) && majorCurrencies.contains(to)) {
            return BigDecimal.valueOf(0.0005); // 0.05% for major pairs
        }
        
        // Exotic pairs have wider spreads
        return BigDecimal.valueOf(0.002); // 0.2% for others
    }
    
    private double calculateConfidence() {
        // Base confidence on recent success rate
        if (successCount.get() + failureCount.get() < 10) {
            return 0.8; // Not enough data
        }
        
        double successRate = (double) successCount.get() / (successCount.get() + failureCount.get());
        
        // Factor in response time
        long avgResponseTime = totalResponseTime.get() / Math.max(1, successCount.get());
        double timeScore = avgResponseTime < 1000 ? 1.0 : 
                          avgResponseTime < 3000 ? 0.9 : 
                          avgResponseTime < 5000 ? 0.8 : 0.7;
        
        return Math.min(0.95, successRate * 0.7 + timeScore * 0.3);
    }
    
    private void updateMetrics(boolean success, long responseTime) {
        if (success) {
            successCount.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
        } else {
            failureCount.incrementAndGet();
        }
        
        // Reset metrics periodically
        if (successCount.get() + failureCount.get() > 1000) {
            successCount.set(successCount.get() / 2);
            failureCount.set(failureCount.get() / 2);
            totalResponseTime.set(totalResponseTime.get() / 2);
        }
    }
    
    private boolean isRateLimitExceeded() {
        long now = System.currentTimeMillis();
        long timeSinceReset = now - lastResetTime.get();
        
        // Reset counter every hour
        if (timeSinceReset > 3600000) {
            requestCount.set(0);
            lastResetTime.set(now);
            return false;
        }
        
        // Check minute limit
        if (timeSinceReset < 60000 && requestCount.get() >= RATE_LIMIT_PER_MINUTE) {
            return true;
        }
        
        // Check hour limit
        return requestCount.get() >= RATE_LIMIT_PER_HOUR;
    }
    
    private int calculateRemainingRequests() {
        long timeSinceReset = System.currentTimeMillis() - lastResetTime.get();
        
        if (timeSinceReset < 60000) {
            return Math.max(0, RATE_LIMIT_PER_MINUTE - requestCount.get());
        } else if (timeSinceReset < 3600000) {
            return Math.max(0, RATE_LIMIT_PER_HOUR - requestCount.get());
        }
        
        return RATE_LIMIT_PER_HOUR;
    }
    
    private long calculateResetTime() {
        return lastResetTime.get() + 3600000; // Next hour
    }
    
    private String getApiUrl() {
        String protocol = useHttps ? "https://" : "http://";
        return baseUrl.startsWith("http") ? baseUrl : protocol + baseUrl;
    }
    
    private void cleanupCache() {
        log.debug("Cleaning up expired cache entries");
        
        rateCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Clear old rates if they're too stale
        if (lastUpdate != null && lastUpdate.plusHours(1).isBefore(LocalDateTime.now())) {
            latestRates.clear();
        }
    }
    
    private void performHealthCheck() {
        try {
            // Simple health check - fetch USD to EUR rate
            getExchangeRate("USD", "EUR");
            circuitOpen = false;
        } catch (Exception e) {
            log.warn("Health check failed", e);
            
            // Open circuit if too many failures
            if (failureCount.get() > 10 && 
                (double) failureCount.get() / (successCount.get() + failureCount.get()) > 0.5) {
                circuitOpen = true;
                log.error("Circuit breaker opened due to high failure rate");
            }
        }
    }
    
    // Fallback method for circuit breaker
    public ExchangeRate getExchangeRateFallback(String fromCurrency, String toCurrency, Exception ex) {
        log.warn("Using fallback for exchange rate {} to {} due to: {}", 
            fromCurrency, toCurrency, ex.getMessage());
        
        // Check if we have cached data
        String cacheKey = fromCurrency + "_" + toCurrency;
        ExchangeRateCache cached = rateCache.get(cacheKey);
        
        if (cached != null) {
            // Return stale cache data with reduced confidence
            ExchangeRate staleRate = cached.getRate();
            return ExchangeRate.builder()
                .fromCurrency(staleRate.getFromCurrency())
                .toCurrency(staleRate.getToCurrency())
                .rate(staleRate.getRate())
                .provider(getProviderName() + "_FALLBACK")
                .timestamp(staleRate.getTimestamp())
                .spread(staleRate.getSpread().multiply(BigDecimal.valueOf(2))) // Double spread for stale data
                .confidence(0.5) // Low confidence for fallback
                .source("CACHE_FALLBACK")
                .build();
        }
        
        throw new BusinessException("No fallback data available for " + fromCurrency + " to " + toCurrency);
    }
    
    // Inner class for caching
    private static class ExchangeRateCache {
        private final ExchangeRate rate;
        private final LocalDateTime expiry;
        
        public ExchangeRateCache(ExchangeRate rate, int ttlMinutes) {
            this.rate = rate;
            this.expiry = LocalDateTime.now().plusMinutes(ttlMinutes);
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }
        
        public ExchangeRate getRate() {
            return rate;
        }
    }
}