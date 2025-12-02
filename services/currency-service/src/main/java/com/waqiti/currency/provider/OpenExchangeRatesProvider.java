package com.waqiti.currency.provider;

import com.waqiti.currency.domain.ExchangeRate;
import com.waqiti.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenExchangeRates API Provider Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenExchangeRatesProvider implements ExchangeRateProvider {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${exchange-rate.providers.openexchangerates.api-key}")
    private String apiKey;
    
    @Value("${exchange-rate.providers.openexchangerates.base-url:https://openexchangerates.org/api}")
    private String baseUrl;
    
    @Value("${exchange-rate.providers.openexchangerates.enabled:true}")
    private boolean enabled;
    
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final Map<String, BigDecimal> cachedRates = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastUpdate;
    
    private static final int RATE_LIMIT_PER_MINUTE = 100;
    private static final int RATE_LIMIT_PER_HOUR = 1000;
    private static final int RATE_LIMIT_PER_DAY = 10000;
    
    @Override
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        log.debug("Fetching exchange rate from {} to {} using OpenExchangeRates", fromCurrency, toCurrency);
        
        if (!isAvailable()) {
            throw new BusinessException("OpenExchangeRates provider is not available");
        }
        
        try {
            // Get rates for base currency (USD)
            Map<String, BigDecimal> rates = fetchLatestRates();
            
            BigDecimal rate;
            if ("USD".equals(fromCurrency)) {
                rate = rates.get(toCurrency);
            } else if ("USD".equals(toCurrency)) {
                BigDecimal fromRate = rates.get(fromCurrency);
                rate = BigDecimal.ONE.divide(fromRate, 8, RoundingMode.HALF_UP);
            } else {
                // Cross rate calculation
                BigDecimal fromRate = rates.get(fromCurrency);
                BigDecimal toRate = rates.get(toCurrency);
                rate = toRate.divide(fromRate, 8, RoundingMode.HALF_UP);
            }
            
            if (rate == null) {
                throw new BusinessException("Exchange rate not available for " + fromCurrency + " to " + toCurrency);
            }
            
            return ExchangeRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(rate)
                .provider(getProviderName())
                .timestamp(LocalDateTime.now())
                .spread(BigDecimal.valueOf(0.001)) // 0.1% spread
                .confidence(0.95)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to fetch exchange rate from OpenExchangeRates", e);
            throw new BusinessException("Failed to fetch exchange rate", e);
        }
    }
    
    @Override
    public Map<String, ExchangeRate> getBulkExchangeRates(List<String> fromCurrencies, String baseCurrency) {
        Map<String, ExchangeRate> exchangeRates = new HashMap<>();
        
        Map<String, BigDecimal> rates = fetchLatestRates();
        BigDecimal baseRate = rates.get(baseCurrency);
        
        for (String fromCurrency : fromCurrencies) {
            if (fromCurrency.equals(baseCurrency)) {
                exchangeRates.put(fromCurrency, ExchangeRate.builder()
                    .fromCurrency(fromCurrency)
                    .toCurrency(baseCurrency)
                    .rate(BigDecimal.ONE)
                    .provider(getProviderName())
                    .timestamp(LocalDateTime.now())
                    .build());
            } else {
                BigDecimal fromRate = rates.get(fromCurrency);
                if (fromRate != null && baseRate != null) {
                    BigDecimal rate = baseRate.divide(fromRate, 8, RoundingMode.HALF_UP);
                    exchangeRates.put(fromCurrency, ExchangeRate.builder()
                        .fromCurrency(fromCurrency)
                        .toCurrency(baseCurrency)
                        .rate(rate)
                        .provider(getProviderName())
                        .timestamp(LocalDateTime.now())
                        .build());
                }
            }
        }
        
        return exchangeRates;
    }
    
    @Override
    public Map<String, BigDecimal> getAllRatesForBase(String baseCurrency) {
        if ("USD".equals(baseCurrency)) {
            return fetchLatestRates();
        }
        
        Map<String, BigDecimal> usdRates = fetchLatestRates();
        Map<String, BigDecimal> convertedRates = new HashMap<>();
        
        BigDecimal baseRate = usdRates.get(baseCurrency);
        if (baseRate == null) {
            throw new BusinessException("Base currency " + baseCurrency + " not supported");
        }
        
        for (Map.Entry<String, BigDecimal> entry : usdRates.entrySet()) {
            if (!entry.getKey().equals(baseCurrency)) {
                BigDecimal rate = entry.getValue().divide(baseRate, 8, RoundingMode.HALF_UP);
                convertedRates.put(entry.getKey(), rate);
            }
        }
        
        convertedRates.put(baseCurrency, BigDecimal.ONE);
        return convertedRates;
    }
    
    @Override
    public boolean supportsCurrency(String currency) {
        return getSupportedCurrencies().contains(currency);
    }
    
    @Override
    public String getProviderName() {
        return "OpenExchangeRates";
    }
    
    @Override
    public int getPriority() {
        return 1; // Highest priority
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }
    
    @Override
    public double getConfidenceScore() {
        return 0.95; // High confidence for OpenExchangeRates
    }
    
    @Override
    @Cacheable("supportedCurrencies")
    public List<String> getSupportedCurrencies() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/currencies.json")
                .queryParam("app_id", apiKey)
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null) {
                return new ArrayList<>(response.getBody().keySet());
            }
        } catch (Exception e) {
            log.error("Failed to fetch supported currencies", e);
        }
        
        // Return default list if API call fails
        return Arrays.asList(
            "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD",
            "CNY", "INR", "KRW", "SGD", "HKD", "NOK", "SEK", "DKK",
            "PLN", "CZK", "HUF", "RON", "BGN", "HRK", "RUB", "TRY",
            "BRL", "MXN", "ARS", "CLP", "COP", "PEN", "UYU", "ZAR",
            "THB", "MYR", "IDR", "PHP", "VND"
        );
    }
    
    @Override
    public RateLimitInfo getRateLimitInfo() {
        RateLimitInfo info = new RateLimitInfo();
        info.setRequestsPerMinute(RATE_LIMIT_PER_MINUTE);
        info.setRequestsPerHour(RATE_LIMIT_PER_HOUR);
        info.setRequestsPerDay(RATE_LIMIT_PER_DAY);
        info.setRemainingRequests(RATE_LIMIT_PER_DAY - requestCount.get());
        info.setResetTime(System.currentTimeMillis() + 86400000); // 24 hours from now
        return info;
    }
    
    @Cacheable(value = "exchangeRates", key = "'latest'")
    private Map<String, BigDecimal> fetchLatestRates() {
        log.debug("Fetching latest rates from OpenExchangeRates");
        
        // Check if we have recent cached data
        if (lastUpdate != null && lastUpdate.plusMinutes(5).isAfter(LocalDateTime.now())) {
            return cachedRates;
        }
        
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/latest.json")
                .queryParam("app_id", apiKey)
                .queryParam("base", "USD")
                .toUriString();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class
            );
            
            requestCount.incrementAndGet();
            
            if (response.getBody() != null && response.getBody().containsKey("rates")) {
                Map<String, Object> rates = (Map<String, Object>) response.getBody().get("rates");
                Map<String, BigDecimal> result = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : rates.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        result.put(entry.getKey(), 
                            new BigDecimal(entry.getValue().toString()));
                    }
                }
                
                cachedRates.clear();
                cachedRates.putAll(result);
                lastUpdate = LocalDateTime.now();
                
                return result;
            }
            
            throw new BusinessException("Invalid response from OpenExchangeRates");
            
        } catch (Exception e) {
            log.error("Failed to fetch latest rates from OpenExchangeRates", e);
            
            // Return cached rates if available
            if (!cachedRates.isEmpty()) {
                log.warn("Returning cached rates due to API failure");
                return cachedRates;
            }
            
            throw new BusinessException("Failed to fetch exchange rates", e);
        }
    }
}