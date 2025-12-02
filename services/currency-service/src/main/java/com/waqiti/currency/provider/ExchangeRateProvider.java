package com.waqiti.currency.provider;

import com.waqiti.currency.domain.ExchangeRate;
import com.waqiti.currency.dto.ExchangeRateResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interface for Exchange Rate Providers
 */
public interface ExchangeRateProvider {
    
    /**
     * Get exchange rate between two currencies
     */
    ExchangeRate getExchangeRate(String fromCurrency, String toCurrency);
    
    /**
     * Get multiple exchange rates at once
     */
    Map<String, ExchangeRate> getBulkExchangeRates(List<String> fromCurrencies, String baseCurrency);
    
    /**
     * Get all available exchange rates for a base currency
     */
    Map<String, BigDecimal> getAllRatesForBase(String baseCurrency);
    
    /**
     * Check if provider supports a currency
     */
    boolean supportsCurrency(String currency);
    
    /**
     * Get provider name
     */
    String getProviderName();
    
    /**
     * Get provider priority (lower is higher priority)
     */
    int getPriority();
    
    /**
     * Check if provider is available
     */
    boolean isAvailable();
    
    /**
     * Get provider confidence score
     */
    double getConfidenceScore();
    
    /**
     * Get supported currencies
     */
    List<String> getSupportedCurrencies();
    
    /**
     * Get rate limits info
     */
    RateLimitInfo getRateLimitInfo();
    
    /**
     * Rate Limit Information
     */
    class RateLimitInfo {
        private int requestsPerMinute;
        private int requestsPerHour;
        private int requestsPerDay;
        private long resetTime;
        private int remainingRequests;
        
        // Getters and setters
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
        
        public int getRequestsPerHour() { return requestsPerHour; }
        public void setRequestsPerHour(int requestsPerHour) { this.requestsPerHour = requestsPerHour; }
        
        public int getRequestsPerDay() { return requestsPerDay; }
        public void setRequestsPerDay(int requestsPerDay) { this.requestsPerDay = requestsPerDay; }
        
        public long getResetTime() { return resetTime; }
        public void setResetTime(long resetTime) { this.resetTime = resetTime; }
        
        public int getRemainingRequests() { return remainingRequests; }
        public void setRemainingRequests(int remainingRequests) { this.remainingRequests = remainingRequests; }
    }
}