package com.waqiti.wallet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CurrencyConversionService {

    private final RestTemplate restTemplate;
    private final String exchangeRateApiUrl;
    private final String apiKey;

    public CurrencyConversionService(
            RestTemplate restTemplate,
            @Value("${exchange-rate.api.url}") String exchangeRateApiUrl,
            @Value("${exchange-rate.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.exchangeRateApiUrl = exchangeRateApiUrl;
        this.apiKey = apiKey;
    }

    /**
     * Get exchange rate from source to target currency
     */
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public BigDecimal getExchangeRate(String sourceCurrency, String targetCurrency) {
        log.info("Fetching exchange rate from {} to {}", sourceCurrency, targetCurrency);

        if (sourceCurrency.equals(targetCurrency)) {
            return BigDecimal.ONE;
        }

        try {
            String url = String.format("%s?base=%s&symbols=%s&access_key=%s",
                    exchangeRateApiUrl, sourceCurrency, targetCurrency, apiKey);

            ResponseEntity<ExchangeRateResponse> response =
                    restTemplate.getForEntity(url, ExchangeRateResponse.class);

            if (response.getBody() != null && response.getBody().isSuccess()) {
                BigDecimal rate = response.getBody().getRates().get(targetCurrency);
                if (rate != null) {
                    return rate;
                }
            }

            log.error("Could not fetch exchange rate: {}", response.getBody());
            throw new CurrencyConversionException("Failed to retrieve exchange rate");
        } catch (Exception e) {
            log.error("Exchange rate API error", e);
            throw new CurrencyConversionException("Error fetching exchange rate: " + e.getMessage(), e);
        }
    }

    /**
     * Convert amount from source currency to target currency
     */
    public BigDecimal convert(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        if (sourceCurrency.equals(targetCurrency)) {
            return amount;
        }

        BigDecimal exchangeRate = getExchangeRate(sourceCurrency, targetCurrency);
        return amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Exception for currency conversion errors
     */
    public static class CurrencyConversionException extends RuntimeException {
        public CurrencyConversionException(String message) {
            super(message);
        }

        public CurrencyConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Response model for exchange rate API
     */
    private static class ExchangeRateResponse {
        private boolean success;
        private String base;
        private Map<String, BigDecimal> rates = new HashMap<>();

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getBase() {
            return base;
        }

        public void setBase(String base) {
            this.base = base;
        }

        public Map<String, BigDecimal> getRates() {
            return rates;
        }

        public void setRates(Map<String, BigDecimal> rates) {
            this.rates = rates;
        }
    }
}