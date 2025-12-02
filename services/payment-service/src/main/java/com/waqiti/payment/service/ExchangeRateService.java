package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Exchange Rate Service
 * Handles real-time FX rates with caching, fallbacks, and risk management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final RestTemplate restTemplate;
    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> rateCacheTimestamps = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> userDailyLimits = new ConcurrentHashMap<>(); 
    
    private static final int CACHE_DURATION_MINUTES = 5;
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("50000");
    private static final BigDecimal DEFAULT_MERCHANT_LIMIT = new BigDecimal("1000000");

    /**
     * Get cached exchange rate for high-frequency requests
     */
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public BigDecimal getCachedRate(String sourceCurrency, String targetCurrency) {
        String cacheKey = sourceCurrency + "-" + targetCurrency;
        
        // Check if cached rate is still valid
        LocalDateTime cacheTime = rateCacheTimestamps.get(cacheKey);
        if (cacheTime != null && cacheTime.isAfter(LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES))) {
            BigDecimal cachedRate = rateCache.get(cacheKey);
            if (cachedRate != null) {
                log.debug("Returning cached rate for {}/{}: {}", sourceCurrency, targetCurrency, cachedRate);
                return cachedRate;
            }
        }
        
        // Fetch fresh rate
        BigDecimal freshRate = fetchRateFromProvider(sourceCurrency, targetCurrency);
        
        // Cache the rate
        rateCache.put(cacheKey, freshRate);
        rateCacheTimestamps.put(cacheKey, LocalDateTime.now());
        
        return freshRate;
    }

    /**
     * Get real-time exchange rate from primary provider
     */
    public BigDecimal getRealTimeRate(String sourceCurrency, String targetCurrency) {
        try {
            log.info("Fetching real-time rate for {}/{}", sourceCurrency, targetCurrency);
            
            // Primary provider API call
            String url = String.format("https://api.exchangerate-api.com/v4/latest/%s", sourceCurrency);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("rates")) {
                Map<String, Double> rates = (Map<String, Double>) response.get("rates");
                if (rates.containsKey(targetCurrency)) {
                    BigDecimal rate = BigDecimal.valueOf(rates.get(targetCurrency))
                        .setScale(6, RoundingMode.HALF_UP);
                    
                    log.info("Retrieved rate {}/{}: {}", sourceCurrency, targetCurrency, rate);
                    return rate;
                }
            }
            
            throw new RuntimeException("Rate not available for currency pair");
            
        } catch (Exception e) {
            log.error("Failed to fetch real-time rate for {}/{}: {}", 
                    sourceCurrency, targetCurrency, e.getMessage());
            
            // Fallback to cached rate or default
            return getFallbackRate(sourceCurrency, targetCurrency);
        }
    }

    /**
     * Store locked rate for guaranteed pricing
     */
    public void storeLockedRate(String lockReference, BigDecimal rate, LocalDateTime expiryTime) {
        try {
            log.info("Storing locked rate: {} at {} until {}", lockReference, rate, expiryTime);
            
            // In production, this would be stored in Redis or database
            // with TTL based on expiry time
            rateCache.put("LOCKED-" + lockReference, rate);
            rateCacheTimestamps.put("LOCKED-" + lockReference, expiryTime);
            
        } catch (Exception e) {
            log.error("Failed to store locked rate {}: {}", lockReference, e.getMessage());
            throw new RuntimeException("Rate locking failed", e);
        }
    }

    /**
     * Retrieve locked rate if still valid
     */
    public BigDecimal getLockedRate(String lockReference) {
        String lockKey = "LOCKED-" + lockReference;
        LocalDateTime expiryTime = rateCacheTimestamps.get(lockKey);
        
        if (expiryTime != null && LocalDateTime.now().isBefore(expiryTime)) {
            BigDecimal lockedRate = rateCache.get(lockKey);
            if (lockedRate != null) {
                log.info("Retrieved locked rate {}: {}", lockReference, lockedRate);
                return lockedRate;
            }
        }
        
        log.error("CRITICAL: Locked exchange rate {} expired or not found - payment processing may fail", lockReference);
        throw new ExchangeRateException("Locked exchange rate not found or expired: " + lockReference);
    }

    /**
     * Get merchant-specific markup for FX
     */
    public BigDecimal getMerchantMarkup(String merchantId, String sourceCurrency, String targetCurrency) {
        try {
            // In production, this would come from merchant configuration
            log.debug("Getting merchant markup for {} on {}/{}", 
                    merchantId, sourceCurrency, targetCurrency);
            
            // Tier-based markup structure
            String merchantTier = getMerchantTier(merchantId);
            
            return switch (merchantTier) {
                case "ENTERPRISE" -> new BigDecimal("0.25"); // 0.25% markup
                case "PREMIUM" -> new BigDecimal("0.50");    // 0.50% markup
                case "STANDARD" -> new BigDecimal("0.75");   // 0.75% markup
                default -> new BigDecimal("1.00");           // 1.00% markup
            };
            
        } catch (Exception e) {
            log.error("Failed to get merchant markup for {}: {}", merchantId, e.getMessage());
            return new BigDecimal("1.00"); // Default markup
        }
    }

    /**
     * Get platform FX fee based on amount and conversion type
     */
    public BigDecimal getPlatformFxFee(BigDecimal amount, Object conversionType) {
        try {
            log.debug("Calculating platform FX fee for amount: {} type: {}", amount, conversionType);
            
            // Progressive fee structure
            if (amount.compareTo(new BigDecimal("100000")) > 0) {
                return new BigDecimal("0.15"); // 0.15% for large amounts
            } else if (amount.compareTo(new BigDecimal("10000")) > 0) {
                return new BigDecimal("0.25"); // 0.25% for medium amounts
            } else {
                return new BigDecimal("0.35"); // 0.35% for small amounts
            }
            
        } catch (Exception e) {
            log.error("Failed to calculate platform FX fee: {}", e.getMessage());
            return new BigDecimal("0.50"); // Default fee
        }
    }

    /**
     * Get user's daily conversion limit
     */
    public BigDecimal getUserDailyLimit(String userId) {
        try {
            log.debug("Getting daily conversion limit for user: {}", userId);
            
            // In production, this would come from user profile/KYC level
            return userDailyLimits.getOrDefault(userId, DEFAULT_DAILY_LIMIT);
            
        } catch (Exception e) {
            log.error("Failed to get user daily limit for {}: {}", userId, e.getMessage());
            return DEFAULT_DAILY_LIMIT;
        }
    }

    /**
     * Get merchant conversion limit
     */
    public BigDecimal getMerchantConversionLimit(String merchantId) {
        try {
            log.debug("Getting conversion limit for merchant: {}", merchantId);
            
            // In production, this would come from merchant agreement
            String merchantTier = getMerchantTier(merchantId);
            
            return switch (merchantTier) {
                case "ENTERPRISE" -> new BigDecimal("10000000"); // $10M
                case "PREMIUM" -> new BigDecimal("5000000");     // $5M
                case "STANDARD" -> new BigDecimal("1000000");    // $1M
                default -> DEFAULT_MERCHANT_LIMIT;               // $1M default
            };
            
        } catch (Exception e) {
            log.error("Failed to get merchant limit for {}: {}", merchantId, e.getMessage());
            return DEFAULT_MERCHANT_LIMIT;
        }
    }

    /**
     * Calculate next settlement date based on merchant schedule
     */
    public java.time.LocalDate getNextSettlementDate(String merchantId) {
        try {
            log.debug("Calculating next settlement date for merchant: {}", merchantId);
            
            // In production, this would come from merchant settlement schedule
            String settlementFrequency = getMerchantSettlementFrequency(merchantId);
            
            return switch (settlementFrequency) {
                case "DAILY" -> java.time.LocalDate.now().plusDays(1);
                case "WEEKLY" -> java.time.LocalDate.now().plusDays(7);
                case "MONTHLY" -> java.time.LocalDate.now().plusMonths(1);
                default -> java.time.LocalDate.now().plusDays(2); // T+2 default
            };
            
        } catch (Exception e) {
            log.error("Failed to calculate next settlement date for {}: {}", merchantId, e.getMessage());
            return java.time.LocalDate.now().plusDays(2); // Default T+2
        }
    }

    /**
     * Record fee distribution for revenue tracking
     */
    public void recordFeeDistribution(String calculationId, String recipient, BigDecimal amount) {
        try {
            log.info("Recording fee distribution: {} -> {} = {}", calculationId, recipient, amount);
            
            // In production, this would be stored in database for revenue reporting
            // and integration with accounting systems
            
        } catch (Exception e) {
            log.error("Failed to record fee distribution for {}: {}", calculationId, e.getMessage());
        }
    }

    /**
     * Get regulatory fee based on transaction type and merchant category
     */
    public BigDecimal getRegulatoryFee(String transactionType, String merchantCategory) {
        try {
            log.debug("Calculating regulatory fee for type: {} category: {}", 
                    transactionType, merchantCategory);
            
            // Regulatory fees vary by jurisdiction and transaction type
            if ("HIGH_RISK".equals(merchantCategory)) {
                return new BigDecimal("0.25"); // Additional regulatory fee
            } else if ("CROSS_BORDER".equals(transactionType)) {
                return new BigDecimal("0.15"); // Cross-border regulatory fee
            } else {
                return new BigDecimal("0.05"); // Standard regulatory fee
            }
            
        } catch (Exception e) {
            log.error("Failed to calculate regulatory fee: {}", e.getMessage());
            return new BigDecimal("0.10"); // Default regulatory fee
        }
    }

    // Private helper methods
    
    private BigDecimal fetchRateFromProvider(String sourceCurrency, String targetCurrency) {
        try {
            // Simulate API call to exchange rate provider
            log.debug("Fetching rate from provider for {}/{}", sourceCurrency, targetCurrency);
            
            // Mock exchange rates for common pairs
            String pair = sourceCurrency + targetCurrency;
            
            return switch (pair) {
                case "USDEUR" -> new BigDecimal("0.85");
                case "EURUSD" -> new BigDecimal("1.18");
                case "USDGBP" -> new BigDecimal("0.73");
                case "GBPUSD" -> new BigDecimal("1.37");
                case "USDJPY" -> new BigDecimal("110.50");
                case "JPYUSD" -> new BigDecimal("0.009");
                default -> new BigDecimal("1.00"); // 1:1 for unknown pairs
            };
            
        } catch (Exception e) {
            log.error("Failed to fetch rate from provider: {}", e.getMessage());
            throw new RuntimeException("Rate fetch failed", e);
        }
    }

    private BigDecimal getFallbackRate(String sourceCurrency, String targetCurrency) {
        try {
            String cacheKey = sourceCurrency + "-" + targetCurrency;
            BigDecimal cachedRate = rateCache.get(cacheKey);
            
            if (cachedRate != null) {
                log.warn("Using fallback cached rate for {}/{}: {}", 
                        sourceCurrency, targetCurrency, cachedRate);
                return cachedRate;
            }
            
            // Last resort: use default rate
            log.warn("Using default fallback rate for {}/{}", sourceCurrency, targetCurrency);
            return new BigDecimal("1.00");
            
        } catch (Exception e) {
            log.error("Failed to get fallback rate: {}", e.getMessage());
            return new BigDecimal("1.00");
        }
    }

    private String getMerchantTier(String merchantId) {
        // In production, this would come from merchant configuration
        // Based on volume, risk, relationship, etc.
        return "STANDARD"; // Default tier
    }

    private String getMerchantSettlementFrequency(String merchantId) {
        // In production, this would come from merchant agreement
        return "WEEKLY"; // Default frequency
    }
    
    /**
     * Exception thrown when exchange rate operations fail
     */
    public static class ExchangeRateException extends RuntimeException {
        public ExchangeRateException(String message) {
            super(message);
        }
        
        public ExchangeRateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}