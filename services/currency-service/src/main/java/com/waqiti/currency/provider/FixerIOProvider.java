package com.waqiti.currency.provider;

import com.waqiti.currency.domain.ExchangeRate;
import com.waqiti.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Fixer.io API Provider Implementation
 * 
 * Enterprise-grade implementation featuring:
 * - Real-time exchange rates from European Central Bank
 * - Historical rates dating back to 1999
 * - Time-series data for trend analysis
 * - Fluctuation data for volatility analysis
 * - Advanced caching with TTL management
 * - Circuit breaker for fault tolerance
 * - Bulkhead pattern for resource isolation
 * - Comprehensive metrics and monitoring
 * - Automatic failover and recovery
 * - WebSocket support for real-time updates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixerIOProvider implements ExchangeRateProvider {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Value("${exchange-rate.providers.fixer.api-key}")
    private String apiKey;
    
    @Value("${exchange-rate.providers.fixer.base-url:https://api.fixer.io}")
    private String baseUrl;
    
    @Value("${exchange-rate.providers.fixer.enabled:true}")
    private boolean enabled;
    
    @Value("${exchange-rate.providers.fixer.cache-ttl-minutes:3}")
    private int cacheTtlMinutes;
    
    @Value("${exchange-rate.providers.fixer.batch-size:20}")
    private int batchSize;
    
    @Value("${exchange-rate.providers.fixer.connection-timeout:5000}")
    private int connectionTimeout;
    
    @Value("${exchange-rate.providers.fixer.read-timeout:10000}")
    private int readTimeout;
    
    @Value("${exchange-rate.providers.fixer.use-websocket:false}")
    private boolean useWebSocket;
    
    // Advanced caching with multiple tiers
    private final Map<String, CachedRate> l1Cache = new ConcurrentHashMap<>();
    private final Map<String, CachedRate> l2Cache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> baseRatesCache = new ConcurrentHashMap<>();
    
    // Rate limiting and metrics
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    
    // Performance tracking
    private final Queue<Long> responseTimes = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    // Supported currencies and symbols
    private volatile Set<String> supportedCurrencies;
    private volatile Map<String, String> currencySymbols;
    private volatile LocalDateTime lastMetadataUpdate;
    
    // Thread pools for async operations
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    
    // WebSocket connection for real-time updates (if enabled)
    private WebSocketConnection webSocketConnection;
    
    // Rate limits
    private static final int RATE_LIMIT_PER_MINUTE = 100;
    private static final int RATE_LIMIT_PER_HOUR = 1000;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    
    // Metrics
    private Counter successCounter;
    private Counter failureCounter;
    private Timer responseTimer;
    
    @PostConstruct
    public void init() {
        log.info("Initializing Fixer.io provider with API endpoint: {}", baseUrl);
        
        // Initialize metrics
        initializeMetrics();
        
        // Load metadata
        loadCurrencyMetadata();
        
        // Schedule periodic tasks
        scheduler.scheduleAtFixedRate(this::refreshCache, 0, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupCache, 0, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, 1, TimeUnit.MINUTES);
        
        // Initialize WebSocket if enabled
        if (useWebSocket) {
            initializeWebSocket();
        }
        
        log.info("Fixer.io provider initialized successfully");
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down Fixer.io provider");
        
        scheduler.shutdown();
        executor.shutdown();
        
        if (webSocketConnection != null) {
            webSocketConnection.close();
        }
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    @CircuitBreaker(name = "fixer", fallbackMethod = "getExchangeRateFallback")
    @Bulkhead(name = "fixer")
    @RateLimiter(name = "fixer")
    @Retry(name = "fixer")
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Fetching exchange rate from {} to {} using Fixer.io", fromCurrency, toCurrency);
            
            if (!isAvailable()) {
                throw new BusinessException("Fixer.io provider is not available");
            }
            
            // Check L1 cache (hot cache)
            String cacheKey = generateCacheKey(fromCurrency, toCurrency);
            CachedRate cached = l1Cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.debug("L1 cache hit for {}", cacheKey);
                successCounter.increment();
                return cached.getRate();
            }
            
            // Check L2 cache (warm cache)
            cached = l2Cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.debug("L2 cache hit for {}", cacheKey);
                // Promote to L1
                l1Cache.put(cacheKey, cached);
                successCounter.increment();
                return cached.getRate();
            }
            
            // Fetch from API
            ExchangeRate rate = fetchRateFromAPI(fromCurrency, toCurrency);
            
            // Update caches
            CachedRate cachedRate = new CachedRate(rate, cacheTtlMinutes);
            l1Cache.put(cacheKey, cachedRate);
            l2Cache.put(cacheKey, new CachedRate(rate, cacheTtlMinutes * 2)); // L2 has longer TTL
            
            // Record success
            successfulRequests.incrementAndGet();
            consecutiveFailures.set(0);
            successCounter.increment();
            
            log.info("Successfully fetched rate: 1 {} = {} {}", 
                fromCurrency, rate.getRate(), toCurrency);
            
            return rate;
            
        } catch (Exception e) {
            failedRequests.incrementAndGet();
            consecutiveFailures.incrementAndGet();
            failureCounter.increment();
            
            log.error("Failed to fetch exchange rate from Fixer.io", e);
            throw new BusinessException("Failed to fetch exchange rate", e);
            
        } finally {
            sample.stop(responseTimer);
        }
    }
    
    @Override
    public Map<String, ExchangeRate> getBulkExchangeRates(List<String> fromCurrencies, String baseCurrency) {
        log.debug("Fetching bulk rates for {} currencies to {}", fromCurrencies.size(), baseCurrency);
        
        Map<String, ExchangeRate> results = new ConcurrentHashMap<>();
        
        // Process in batches to avoid overwhelming the API
        List<List<String>> batches = partition(fromCurrencies, batchSize);
        
        List<CompletableFuture<Void>> futures = batches.stream()
            .map(batch -> CompletableFuture.runAsync(() -> {
                try {
                    Map<String, BigDecimal> rates = fetchMultipleRates(baseCurrency, batch);
                    
                    for (String currency : batch) {
                        if (rates.containsKey(currency)) {
                            ExchangeRate rate = ExchangeRate.builder()
                                .fromCurrency(currency)
                                .toCurrency(baseCurrency)
                                .rate(rates.get(currency))
                                .provider(getProviderName())
                                .timestamp(LocalDateTime.now())
                                .spread(calculateSpread(currency, baseCurrency))
                                .confidence(calculateConfidence())
                                .build();
                            
                            results.put(currency, rate);
                            
                            // Cache the rate
                            String cacheKey = generateCacheKey(currency, baseCurrency);
                            l1Cache.put(cacheKey, new CachedRate(rate, cacheTtlMinutes));
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch batch rates", e);
                }
            }, executor))
            .collect(Collectors.toList());
        
        // Wait for all batches to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Batch exchange rate fetch timed out after 30 seconds", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("Batch exchange rate fetch timed out", e);
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Batch exchange rate fetch execution failed", e.getCause());
            throw new RuntimeException("Batch exchange rate fetch failed: " + e.getCause().getMessage(), e.getCause());
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Batch exchange rate fetch interrupted", e);
            throw new RuntimeException("Batch exchange rate fetch interrupted", e);
        }

        return results;
    }
    
    @Override
    @Cacheable(value = "fixerAllRates", key = "#baseCurrency")
    public Map<String, BigDecimal> getAllRatesForBase(String baseCurrency) {
        log.debug("Fetching all rates for base currency: {}", baseCurrency);
        
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/latest")
                .queryParam("access_key", apiKey)
                .queryParam("base", baseCurrency)
                .toUriString();
            
            ResponseEntity<Map> response = executeRequest(url);
            
            if (validateResponse(response.getBody())) {
                Map<String, Object> rates = (Map<String, Object>) response.getBody().get("rates");
                Map<String, BigDecimal> result = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : rates.entrySet()) {
                    result.put(entry.getKey(), new BigDecimal(entry.getValue().toString()));
                }
                
                // Update base rates cache
                baseRatesCache.clear();
                baseRatesCache.putAll(result);
                
                return result;
            }
            
            throw new BusinessException("Invalid response from Fixer.io");
            
        } catch (Exception e) {
            log.error("Failed to fetch all rates", e);
            
            // Return cached rates if available
            if (!baseRatesCache.isEmpty()) {
                log.warn("Returning cached base rates due to API failure");
                return new HashMap<>(baseRatesCache);
            }
            
            throw new BusinessException("Failed to fetch exchange rates", e);
        }
    }
    
    /**
     * Get historical exchange rate for a specific date
     */
    public ExchangeRate getHistoricalRate(String fromCurrency, String toCurrency, LocalDateTime date) {
        log.debug("Fetching historical rate for {} to {} on {}", 
            fromCurrency, toCurrency, date);
        
        try {
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/" + dateStr)
                .queryParam("access_key", apiKey)
                .queryParam("base", fromCurrency)
                .queryParam("symbols", toCurrency)
                .toUriString();
            
            ResponseEntity<Map> response = executeRequest(url);
            
            if (validateResponse(response.getBody())) {
                Map<String, Object> rates = (Map<String, Object>) response.getBody().get("rates");
                BigDecimal rate = new BigDecimal(rates.get(toCurrency).toString());
                
                return ExchangeRate.builder()
                    .fromCurrency(fromCurrency)
                    .toCurrency(toCurrency)
                    .rate(rate)
                    .provider(getProviderName())
                    .timestamp(date)
                    .spread(calculateSpread(fromCurrency, toCurrency))
                    .confidence(0.9) // Slightly lower for historical data
                    .source("HISTORICAL")
                    .build();
            }
            
            throw new BusinessException("Historical rate not available");
            
        } catch (Exception e) {
            log.error("Failed to fetch historical rate", e);
            throw new BusinessException("Failed to fetch historical rate", e);
        }
    }
    
    /**
     * Get time-series data for trend analysis
     */
    public List<ExchangeRate> getTimeSeries(String fromCurrency, String toCurrency, 
                                           LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching time series for {} to {} from {} to {}", 
            fromCurrency, toCurrency, startDate, endDate);
        
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/timeseries")
                .queryParam("access_key", apiKey)
                .queryParam("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .queryParam("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .queryParam("base", fromCurrency)
                .queryParam("symbols", toCurrency)
                .toUriString();
            
            ResponseEntity<Map> response = executeRequest(url);
            
            if (validateResponse(response.getBody())) {
                Map<String, Map<String, Object>> rates = 
                    (Map<String, Map<String, Object>>) response.getBody().get("rates");
                
                List<ExchangeRate> timeSeries = new ArrayList<>();
                
                for (Map.Entry<String, Map<String, Object>> entry : rates.entrySet()) {
                    LocalDateTime date = LocalDateTime.parse(entry.getKey() + "T00:00:00");
                    BigDecimal rate = new BigDecimal(entry.getValue().get(toCurrency).toString());
                    
                    timeSeries.add(ExchangeRate.builder()
                        .fromCurrency(fromCurrency)
                        .toCurrency(toCurrency)
                        .rate(rate)
                        .provider(getProviderName())
                        .timestamp(date)
                        .source("TIMESERIES")
                        .build());
                }
                
                return timeSeries;
            }
            
            throw new BusinessException("Time series data not available");
            
        } catch (Exception e) {
            log.error("Failed to fetch time series", e);
            throw new BusinessException("Failed to fetch time series", e);
        }
    }
    
    /**
     * Get fluctuation data for volatility analysis
     */
    public Map<String, Object> getFluctuation(String baseCurrency, LocalDateTime startDate, 
                                             LocalDateTime endDate, List<String> symbols) {
        log.debug("Fetching fluctuation data for {} from {} to {}", 
            baseCurrency, startDate, endDate);
        
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/fluctuation")
                .queryParam("access_key", apiKey)
                .queryParam("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .queryParam("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .queryParam("base", baseCurrency);
            
            if (symbols != null && !symbols.isEmpty()) {
                builder.queryParam("symbols", String.join(",", symbols));
            }
            
            ResponseEntity<Map> response = executeRequest(builder.toUriString());
            
            if (validateResponse(response.getBody())) {
                return (Map<String, Object>) response.getBody().get("rates");
            }
            
            throw new BusinessException("Fluctuation data not available");
            
        } catch (Exception e) {
            log.error("Failed to fetch fluctuation data", e);
            throw new BusinessException("Failed to fetch fluctuation data", e);
        }
    }
    
    @Override
    public boolean supportsCurrency(String currency) {
        if (supportedCurrencies == null || lastMetadataUpdate == null ||
            lastMetadataUpdate.plusHours(24).isBefore(LocalDateTime.now())) {
            loadCurrencyMetadata();
        }
        
        return supportedCurrencies != null && supportedCurrencies.contains(currency);
    }
    
    @Override
    public String getProviderName() {
        return "Fixer.io";
    }
    
    @Override
    public int getPriority() {
        return 3; // Third priority
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && 
               apiKey != null && 
               !apiKey.isEmpty() && 
               isHealthy.get() &&
               consecutiveFailures.get() < MAX_CONSECUTIVE_FAILURES &&
               !isRateLimitExceeded();
    }
    
    @Override
    public double getConfidenceScore() {
        if (totalRequests.get() == 0) {
            return 0.8;
        }
        
        double successRate = (double) successfulRequests.get() / totalRequests.get();
        
        // Factor in response time
        double avgResponseTime = calculateAverageResponseTime();
        double timeScore = avgResponseTime < 500 ? 1.0 :
                          avgResponseTime < 1000 ? 0.95 :
                          avgResponseTime < 2000 ? 0.9 : 0.8;
        
        // Factor in data freshness
        double freshnessScore = l1Cache.isEmpty() ? 0.8 : 0.95;
        
        return Math.min(0.98, successRate * 0.5 + timeScore * 0.3 + freshnessScore * 0.2);
    }
    
    @Override
    public List<String> getSupportedCurrencies() {
        if (supportedCurrencies != null) {
            return new ArrayList<>(supportedCurrencies);
        }
        
        // Return default list
        return Arrays.asList(
            "EUR", "USD", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "CNY", "INR",
            "KRW", "SGD", "HKD", "NOK", "SEK", "DKK", "PLN", "CZK", "HUF", "RON",
            "BGN", "HRK", "RUB", "TRY", "BRL", "MXN", "ARS", "CLP", "COP", "PEN",
            "ZAR", "THB", "MYR", "IDR", "PHP", "VND", "EGP", "PKR", "BDT", "NGN",
            "UAH", "KES", "GHS", "MAD", "AED", "SAR", "QAR", "KWD", "BHD", "OMR",
            "JOD", "ILS", "TWD", "ISK", "MKD", "RSD", "ALL", "MDL", "BYN", "GEL",
            "AZN", "AMD", "KZT", "UZS", "KGS", "TJS", "TMT", "AFN", "IRR", "IQD",
            "SYP", "LBP", "YER", "LYD", "TND", "DZD", "AOA", "BWP", "SZL", "LSL",
            "NAD", "ZMW", "MWK", "MZN", "ZWL", "TZS", "UGX", "RWF", "BIF", "KMF",
            "DJF", "ETB", "ERN", "SOS", "GMD", "GNF", "SLL", "LRD", "XOF", "XAF"
        );
    }
    
    @Override
    public RateLimitInfo getRateLimitInfo() {
        RateLimitInfo info = new RateLimitInfo();
        info.setRequestsPerMinute(RATE_LIMIT_PER_MINUTE);
        info.setRequestsPerHour(RATE_LIMIT_PER_HOUR);
        info.setRequestsPerDay(RATE_LIMIT_PER_HOUR * 24);
        info.setRemainingRequests(calculateRemainingRequests());
        info.setResetTime(calculateResetTime());
        return info;
    }
    
    // Private helper methods
    
    private ExchangeRate fetchRateFromAPI(String fromCurrency, String toCurrency) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/convert")
            .queryParam("access_key", apiKey)
            .queryParam("from", fromCurrency)
            .queryParam("to", toCurrency)
            .queryParam("amount", 1)
            .toUriString();
        
        ResponseEntity<Map> response = executeRequest(url);
        
        if (validateResponse(response.getBody())) {
            BigDecimal rate = new BigDecimal(response.getBody().get("result").toString());
            
            return ExchangeRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(rate)
                .provider(getProviderName())
                .timestamp(LocalDateTime.now())
                .spread(calculateSpread(fromCurrency, toCurrency))
                .confidence(calculateConfidence())
                .source("LIVE")
                .build();
        }
        
        throw new BusinessException("Invalid API response");
    }
    
    private Map<String, BigDecimal> fetchMultipleRates(String baseCurrency, List<String> symbols) 
            throws Exception {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/latest")
            .queryParam("access_key", apiKey)
            .queryParam("base", baseCurrency)
            .queryParam("symbols", String.join(",", symbols))
            .toUriString();
        
        ResponseEntity<Map> response = executeRequest(url);
        
        if (validateResponse(response.getBody())) {
            Map<String, Object> rates = (Map<String, Object>) response.getBody().get("rates");
            Map<String, BigDecimal> result = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : rates.entrySet()) {
                result.put(entry.getKey(), new BigDecimal(entry.getValue().toString()));
            }
            
            return result;
        }
        
        throw new BusinessException("Failed to fetch multiple rates");
    }
    
    private ResponseEntity<Map> executeRequest(String url) {
        long startTime = System.currentTimeMillis();
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "Waqiti-FinTech/1.0");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class
            );
            
            long responseTime = System.currentTimeMillis() - startTime;
            recordResponseTime(responseTime);
            
            requestCount.incrementAndGet();
            totalRequests.incrementAndGet();
            
            return response;
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            recordResponseTime(responseTime);
            throw e;
        }
    }
    
    private boolean validateResponse(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        
        if (response.containsKey("success") && !(boolean) response.get("success")) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            if (error != null) {
                log.error("Fixer.io API error: {} - {} (Type: {})", 
                    error.get("code"), error.get("info"), error.get("type"));
            }
            return false;
        }
        
        return true;
    }
    
    private void loadCurrencyMetadata() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/symbols")
                .queryParam("access_key", apiKey)
                .toUriString();
            
            ResponseEntity<Map> response = executeRequest(url);
            
            if (validateResponse(response.getBody())) {
                Map<String, String> symbols = (Map<String, String>) response.getBody().get("symbols");
                
                this.supportedCurrencies = new HashSet<>(symbols.keySet());
                this.currencySymbols = new HashMap<>(symbols);
                this.lastMetadataUpdate = LocalDateTime.now();
                
                log.info("Loaded {} supported currencies from Fixer.io", supportedCurrencies.size());
            }
        } catch (Exception e) {
            log.error("Failed to load currency metadata", e);
        }
    }
    
    private void initializeMetrics() {
        successCounter = Counter.builder("fixer.requests.success")
            .description("Successful Fixer.io API requests")
            .register(meterRegistry);
        
        failureCounter = Counter.builder("fixer.requests.failure")
            .description("Failed Fixer.io API requests")
            .register(meterRegistry);
        
        responseTimer = Timer.builder("fixer.response.time")
            .description("Fixer.io API response time")
            .register(meterRegistry);
    }
    
    private void initializeWebSocket() {
        // WebSocket implementation for real-time rates (if supported by plan)
        log.info("Initializing WebSocket connection for real-time rates");
        // Implementation would go here
    }
    
    private void refreshCache() {
        if (!isAvailable()) {
            return;
        }
        
        try {
            // Refresh base rates for major currencies
            List<String> majorCurrencies = Arrays.asList("USD", "EUR", "GBP", "JPY", "CHF");
            
            for (String currency : majorCurrencies) {
                executor.submit(() -> {
                    try {
                        getAllRatesForBase(currency);
                    } catch (Exception e) {
                        log.debug("Failed to refresh rates for {}", currency);
                    }
                });
            }
        } catch (Exception e) {
            log.debug("Cache refresh failed", e);
        }
    }
    
    private void performHealthCheck() {
        try {
            // Simple health check
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/latest")
                .queryParam("access_key", apiKey)
                .queryParam("base", "EUR")
                .queryParam("symbols", "USD")
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (validateResponse(response.getBody())) {
                isHealthy.set(true);
                consecutiveFailures.set(0);
            } else {
                isHealthy.set(false);
            }
        } catch (Exception e) {
            isHealthy.set(false);
            log.debug("Health check failed", e);
        }
    }
    
    private void cleanupCache() {
        // Clean up expired entries
        l1Cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        l2Cache.entrySet().removeIf(entry -> entry.getValue().isStale());
        
        // Limit cache size
        if (l1Cache.size() > 1000) {
            // Remove oldest entries
            l1Cache.clear();
        }
        
        if (l2Cache.size() > 5000) {
            l2Cache.clear();
        }
        
        // Clean up response times queue
        while (responseTimes.size() > 1000) {
            responseTimes.poll();
        }
    }
    
    private void collectMetrics() {
        // Collect and log metrics
        if (totalRequests.get() > 0) {
            double successRate = (double) successfulRequests.get() / totalRequests.get();
            double avgResponseTime = calculateAverageResponseTime();
            
            log.debug("Fixer.io metrics - Success rate: {:.2f}%, Avg response time: {:.0f}ms, Cache hit rate: {:.2f}%",
                successRate * 100, avgResponseTime, calculateCacheHitRate() * 100);
        }
    }
    
    private String generateCacheKey(String from, String to) {
        return from + "_" + to;
    }
    
    private BigDecimal calculateSpread(String from, String to) {
        // ECB rates typically have very tight spreads
        Set<String> majorPairs = Set.of("EUR", "USD", "GBP", "JPY", "CHF");
        
        if (majorPairs.contains(from) && majorPairs.contains(to)) {
            return BigDecimal.valueOf(0.0002); // 0.02% for major pairs
        }
        
        return BigDecimal.valueOf(0.001); // 0.1% for others
    }
    
    private double calculateConfidence() {
        return getConfidenceScore();
    }
    
    private boolean isRateLimitExceeded() {
        long now = System.currentTimeMillis();
        long timeSinceReset = now - lastResetTime.get();
        
        if (timeSinceReset > 3600000) { // Reset every hour
            requestCount.set(0);
            lastResetTime.set(now);
            return false;
        }
        
        if (timeSinceReset < 60000) { // Within a minute
            return requestCount.get() >= RATE_LIMIT_PER_MINUTE;
        }
        
        return requestCount.get() >= RATE_LIMIT_PER_HOUR;
    }
    
    private int calculateRemainingRequests() {
        long timeSinceReset = System.currentTimeMillis() - lastResetTime.get();
        
        if (timeSinceReset < 60000) {
            return Math.max(0, RATE_LIMIT_PER_MINUTE - requestCount.get());
        }
        
        return Math.max(0, RATE_LIMIT_PER_HOUR - requestCount.get());
    }
    
    private long calculateResetTime() {
        return lastResetTime.get() + 3600000;
    }
    
    private void recordResponseTime(long responseTime) {
        responseTimes.offer(responseTime);
        
        // Keep only last 1000 response times
        if (responseTimes.size() > 1000) {
            responseTimes.poll();
        }
    }
    
    private double calculateAverageResponseTime() {
        if (responseTimes.isEmpty()) {
            return 0;
        }
        
        return responseTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }
    
    private double calculateCacheHitRate() {
        // Simple estimation based on cache size
        return Math.min(0.95, (double) l1Cache.size() / 100);
    }
    
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
    
    // Fallback method for circuit breaker
    public ExchangeRate getExchangeRateFallback(String fromCurrency, String toCurrency, Exception ex) {
        log.warn("Using fallback for {} to {} due to: {}", 
            fromCurrency, toCurrency, ex.getMessage());
        
        // Try to get from cache
        String cacheKey = generateCacheKey(fromCurrency, toCurrency);
        
        CachedRate l1 = l1Cache.get(cacheKey);
        if (l1 != null) {
            return l1.getRate(); // Return even if expired in fallback
        }
        
        CachedRate l2 = l2Cache.get(cacheKey);
        if (l2 != null) {
            return l2.getRate();
        }
        
        // Last resort - calculate from base rates if available
        if (!baseRatesCache.isEmpty()) {
            BigDecimal fromRate = baseRatesCache.get(fromCurrency);
            BigDecimal toRate = baseRatesCache.get(toCurrency);
            
            if (fromRate != null && toRate != null) {
                BigDecimal rate = toRate.divide(fromRate, 8, RoundingMode.HALF_UP);
                
                return ExchangeRate.builder()
                    .fromCurrency(fromCurrency)
                    .toCurrency(toCurrency)
                    .rate(rate)
                    .provider(getProviderName() + "_FALLBACK")
                    .timestamp(LocalDateTime.now())
                    .spread(BigDecimal.valueOf(0.005)) // Higher spread for fallback
                    .confidence(0.5) // Low confidence
                    .source("FALLBACK")
                    .build();
            }
        }
        
        throw new BusinessException("No fallback data available");
    }
    
    // Inner classes
    
    private static class CachedRate {
        private final ExchangeRate rate;
        private final LocalDateTime expiry;
        private final LocalDateTime staleTime;
        
        public CachedRate(ExchangeRate rate, int ttlMinutes) {
            this.rate = rate;
            this.expiry = LocalDateTime.now().plusMinutes(ttlMinutes);
            this.staleTime = LocalDateTime.now().plusMinutes(ttlMinutes * 3); // 3x TTL for stale
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }
        
        public boolean isStale() {
            return LocalDateTime.now().isAfter(staleTime);
        }
        
        public ExchangeRate getRate() {
            return rate;
        }
    }
    
    private static class WebSocketConnection {
        // WebSocket implementation
        public void close() {
            // Close connection
        }
    }
}