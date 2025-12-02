package com.waqiti.currency.service;

import com.waqiti.currency.domain.*;
import com.waqiti.currency.dto.*;
import com.waqiti.currency.repository.*;
import com.waqiti.currency.provider.*;
import com.waqiti.currency.cache.ExchangeRateCache;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Multi-Currency Service
 * Handles currency conversion, exchange rates, and multi-currency operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MultiCurrencyService {

    @Lazy
    private final MultiCurrencyService self;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyPairRepository currencyPairRepository;
    private final UserCurrencyPreferenceRepository userPreferenceRepository;
    private final CurrencyConversionHistoryRepository conversionHistoryRepository;
    private final ExchangeRateCache exchangeRateCache;
    private final List<ExchangeRateProvider> exchangeRateProviders;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${currency.default-base:USD}")
    private String defaultBaseCurrency;
    
    @Value("${currency.precision:8}")
    private int currencyPrecision;
    
    @Value("${currency.exchange-rate-tolerance:0.05}")
    private BigDecimal exchangeRateTolerance;
    
    @Value("${currency.max-conversion-amount:1000000}")
    private BigDecimal maxConversionAmount;
    
    @Value("${currency.cache-duration-minutes:5}")
    private int cacheDurationMinutes;
    
    private static final MathContext CALCULATION_CONTEXT = new MathContext(15, RoundingMode.HALF_UP);
    private static final Set<String> SUPPORTED_CRYPTO_CURRENCIES = Set.of(
        "BTC", "ETH", "LTC", "XRP", "ADA", "DOT", "LINK", "UNI", "AAVE", "COMP"
    );
    
    /**
     * Convert amount from one currency to another
     */
    public CurrencyConversionResult convertCurrency(CurrencyConversionRequest request) {
        log.debug("Converting {} {} to {}", 
            request.getAmount(), request.getFromCurrency(), request.getToCurrency());
        
        validateConversionRequest(request);
        
        // Check if same currency
        if (request.getFromCurrency().equals(request.getToCurrency())) {
            return CurrencyConversionResult.builder()
                .originalAmount(request.getAmount())
                .convertedAmount(request.getAmount())
                .fromCurrency(request.getFromCurrency())
                .toCurrency(request.getToCurrency())
                .exchangeRate(BigDecimal.ONE)
                .conversionTime(LocalDateTime.now())
                .provider("SAME_CURRENCY")
                .build();
        }
        
        try {
            // Get exchange rate
            ExchangeRate exchangeRate = getExchangeRate(
                request.getFromCurrency(), 
                request.getToCurrency()
            );
            
            // Perform conversion
            BigDecimal convertedAmount = request.getAmount()
                .multiply(exchangeRate.getRate(), CALCULATION_CONTEXT)
                .setScale(getCurrencyPrecision(request.getToCurrency()), RoundingMode.HALF_UP);
            
            // Apply any fees or spreads
            ConversionFees fees = calculateConversionFees(request, convertedAmount);
            BigDecimal finalAmount = convertedAmount.subtract(fees.getTotalFees());
            
            // Record conversion history
            CurrencyConversionHistory history = recordConversionHistory(
                request, exchangeRate, convertedAmount, fees
            );
            
            // Create result
            CurrencyConversionResult result = CurrencyConversionResult.builder()
                .conversionId(history.getId())
                .originalAmount(request.getAmount())
                .convertedAmount(finalAmount)
                .fromCurrency(request.getFromCurrency())
                .toCurrency(request.getToCurrency())
                .exchangeRate(exchangeRate.getRate())
                .fees(fees)
                .conversionTime(LocalDateTime.now())
                .provider(exchangeRate.getProvider())
                .rateTimestamp(exchangeRate.getTimestamp())
                .spread(exchangeRate.getSpread())
                .confidence(exchangeRate.getConfidence())
                .build();
            
            // Publish conversion event
            publishConversionEvent(result);
            
            log.info("Currency conversion completed: {} {} = {} {}", 
                request.getAmount(), request.getFromCurrency(),
                finalAmount, request.getToCurrency());
            
            return result;
            
        } catch (Exception e) {
            log.error("Currency conversion failed", e);
            throw new BusinessException("Currency conversion failed: " + e.getMessage());
        }
    }
    
    /**
     * Get current exchange rate between two currencies
     */
    @Cacheable(value = "exchangeRates", key = "#fromCurrency + '_' + #toCurrency")
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        log.debug("Getting exchange rate from {} to {}", fromCurrency, toCurrency);
        
        // Check cache first
        String cacheKey = fromCurrency + "_" + toCurrency;
        ExchangeRate cachedRate = exchangeRateCache.get(cacheKey);
        if (cachedRate != null && !isRateStale(cachedRate)) {
            return cachedRate;
        }
        
        // Try direct rate
        Optional<ExchangeRate> directRate = exchangeRateRepository
            .findLatestByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
        
        if (directRate.isPresent() && !isRateStale(directRate.get())) {
            ExchangeRate rate = directRate.get();
            exchangeRateCache.put(cacheKey, rate);
            return rate;
        }
        
        // Try inverse rate
        Optional<ExchangeRate> inverseRate = exchangeRateRepository
            .findLatestByFromCurrencyAndToCurrency(toCurrency, fromCurrency);
        
        if (inverseRate.isPresent() && !isRateStale(inverseRate.get())) {
            ExchangeRate rate = inverseRate.get();
            ExchangeRate convertedRate = ExchangeRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(BigDecimal.ONE.divide(rate.getRate(), CALCULATION_CONTEXT))
                .timestamp(rate.getTimestamp())
                .provider(rate.getProvider())
                .spread(rate.getSpread())
                .confidence(rate.getConfidence())
                .build();
            
            exchangeRateCache.put(cacheKey, convertedRate);
            return convertedRate;
        }
        
        // Try cross-currency conversion via base currency
        ExchangeRate crossRate = getCrossCurrencyRate(fromCurrency, toCurrency);
        if (crossRate != null) {
            exchangeRateCache.put(cacheKey, crossRate);
            return crossRate;
        }
        
        // Fetch fresh rate from providers
        return fetchFreshExchangeRate(fromCurrency, toCurrency);
    }
    
    /**
     * Get multiple exchange rates at once
     */
    public Map<String, ExchangeRate> getExchangeRates(List<CurrencyPair> pairs) {
        log.debug("Getting exchange rates for {} currency pairs", pairs.size());
        
        Map<String, ExchangeRate> rates = new HashMap<>();
        
        for (CurrencyPair pair : pairs) {
            try {
                ExchangeRate rate = self.getExchangeRate(pair.getFromCurrency(), pair.getToCurrency());
                String key = pair.getFromCurrency() + "_" + pair.getToCurrency();
                rates.put(key, rate);
            } catch (Exception e) {
                log.warn("Failed to get exchange rate for pair: {}", pair, e);
            }
        }
        
        return rates;
    }
    
    /**
     * Convert multiple amounts to a target currency
     */
    public MultiCurrencyConversionResult convertMultipleCurrencies(
            MultiCurrencyConversionRequest request) {
        
        log.debug("Converting multiple amounts to {}", request.getTargetCurrency());
        
        List<CurrencyConversionResult> conversions = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (CurrencyAmount amount : request.getAmounts()) {
            try {
                CurrencyConversionRequest conversionRequest = CurrencyConversionRequest.builder()
                    .amount(amount.getAmount())
                    .fromCurrency(amount.getCurrency())
                    .toCurrency(request.getTargetCurrency())
                    .userId(request.getUserId())
                    .conversionType(ConversionType.MULTI_CURRENCY)
                    .build();
                
                CurrencyConversionResult result = convertCurrency(conversionRequest);
                conversions.add(result);
                totalAmount = totalAmount.add(result.getConvertedAmount());
                
            } catch (Exception e) {
                log.error("Failed to convert {} {}", amount.getAmount(), amount.getCurrency(), e);
            }
        }
        
        return MultiCurrencyConversionResult.builder()
            .targetCurrency(request.getTargetCurrency())
            .conversions(conversions)
            .totalAmount(totalAmount)
            .conversionTime(LocalDateTime.now())
            .successfulConversions(conversions.size())
            .build();
    }
    
    /**
     * Get historical exchange rates
     */
    public List<ExchangeRate> getHistoricalRates(String fromCurrency, String toCurrency, 
                                                 LocalDateTime fromDate, LocalDateTime toDate) {
        log.debug("Getting historical rates from {} to {} between {} and {}", 
            fromCurrency, toCurrency, fromDate, toDate);
        
        return exchangeRateRepository.findHistoricalRates(
            fromCurrency, toCurrency, fromDate, toDate
        );
    }
    
    /**
     * Get currency statistics and analytics
     */
    public CurrencyAnalytics getCurrencyAnalytics(String currency, int days) {
        log.debug("Getting analytics for {} over {} days", currency, days);
        
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        
        // Get rates against major currencies
        List<String> majorCurrencies = Arrays.asList("USD", "EUR", "GBP", "JPY", "CHF");
        Map<String, List<ExchangeRate>> rateHistory = new HashMap<>();
        
        for (String major : majorCurrencies) {
            if (!major.equals(currency)) {
                List<ExchangeRate> rates = getHistoricalRates(currency, major, fromDate, LocalDateTime.now());
                rateHistory.put(major, rates);
            }
        }
        
        // Calculate volatility
        BigDecimal volatility = calculateVolatility(rateHistory);
        
        // Calculate trends
        Map<String, String> trends = calculateTrends(rateHistory);
        
        // Get trading volume if available
        BigDecimal tradingVolume = getTradingVolume(currency, fromDate);
        
        return CurrencyAnalytics.builder()
            .currency(currency)
            .period(days)
            .volatility(volatility)
            .trends(trends)
            .tradingVolume(tradingVolume)
            .rateHistory(rateHistory)
            .calculatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Set user currency preferences
     */
    @Transactional
    public void setUserCurrencyPreferences(String userId, CurrencyPreferencesRequest request) {
        log.debug("Setting currency preferences for user: {}", userId);
        
        UserCurrencyPreference preference = userPreferenceRepository
            .findByUserId(userId)
            .orElse(new UserCurrencyPreference());
        
        preference.setUserId(userId);
        preference.setPrimaryCurrency(request.getPrimaryCurrency());
        preference.setSecondaryCurrencies(request.getSecondaryCurrencies());
        preference.setAutoConversion(request.isAutoConversion());
        preference.setDisplayFormat(request.getDisplayFormat());
        preference.setUpdatedAt(LocalDateTime.now());
        
        userPreferenceRepository.save(preference);
        
        log.info("Currency preferences updated for user: {}", userId);
    }
    
    /**
     * Get user currency preferences
     */
    public UserCurrencyPreference getUserCurrencyPreferences(String userId) {
        return userPreferenceRepository.findByUserId(userId)
            .orElse(createDefaultPreferences(userId));
    }
    
    /**
     * Get supported currencies
     */
    @Cacheable("supportedCurrencies")
    public List<Currency> getSupportedCurrencies() {
        return currencyRepository.findByActiveTrue();
    }
    
    /**
     * Get supported currency pairs
     */
    @Cacheable("supportedCurrencyPairs")
    public List<CurrencyPair> getSupportedCurrencyPairs() {
        return currencyPairRepository.findByActiveTrue();
    }
    
    /**
     * Format amount according to currency
     */
    public String formatCurrencyAmount(BigDecimal amount, String currency) {
        Currency currencyInfo = currencyRepository.findByCode(currency)
            .orElseThrow(() -> new ValidationException("Unsupported currency: " + currency));
        
        return String.format("%s %.%df",
            currencyInfo.getSymbol(),
            amount,
            currencyInfo.getDecimalPlaces()
        );
    }
    
    /**
     * Check if currency is supported
     */
    public boolean isCurrencySupported(String currency) {
        return currencyRepository.existsByCodeAndActiveTrue(currency);
    }
    
    /**
     * Get currency information
     */
    @Cacheable(value = "currencyInfo", key = "#currency")
    public Currency getCurrencyInfo(String currency) {
        return currencyRepository.findByCode(currency)
            .orElseThrow(() -> new ValidationException("Currency not found: " + currency));
    }
    
    /**
     * Scheduled task to update exchange rates
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    @Async
    public CompletableFuture<Void> updateExchangeRates() {
        log.debug("Starting scheduled exchange rate update");
        
        try {
            List<CurrencyPair> activePairs = self.getSupportedCurrencyPairs();
            
            for (CurrencyPair pair : activePairs) {
                try {
                    fetchAndStoreExchangeRate(pair.getFromCurrency(), pair.getToCurrency());
                } catch (Exception e) {
                    log.warn("Failed to update rate for pair: {}", pair, e);
                }
            }
            
            log.info("Exchange rate update completed for {} pairs", activePairs.size());
            
        } catch (Exception e) {
            log.error("Exchange rate update failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // Private helper methods
    
    private void validateConversionRequest(CurrencyConversionRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be positive");
        }
        
        if (request.getAmount().compareTo(maxConversionAmount) > 0) {
            throw new ValidationException("Amount exceeds maximum allowed: " + maxConversionAmount);
        }
        
        if (!isCurrencySupported(request.getFromCurrency())) {
            throw new ValidationException("Unsupported source currency: " + request.getFromCurrency());
        }
        
        if (!isCurrencySupported(request.getToCurrency())) {
            throw new ValidationException("Unsupported target currency: " + request.getToCurrency());
        }
    }
    
    private boolean isRateStale(ExchangeRate rate) {
        return rate.getTimestamp().isBefore(
            LocalDateTime.now().minusMinutes(cacheDurationMinutes)
        );
    }
    
    private ExchangeRate getCrossCurrencyRate(String fromCurrency, String toCurrency) {
        try {
            // Try conversion via USD
            Optional<ExchangeRate> fromToUsd = exchangeRateRepository
                .findLatestByFromCurrencyAndToCurrency(fromCurrency, "USD");
            Optional<ExchangeRate> usdToTarget = exchangeRateRepository
                .findLatestByFromCurrencyAndToCurrency("USD", toCurrency);
            
            if (fromToUsd.isPresent() && usdToTarget.isPresent()) {
                ExchangeRate rate1 = fromToUsd.get();
                ExchangeRate rate2 = usdToTarget.get();
                
                if (!isRateStale(rate1) && !isRateStale(rate2)) {
                    BigDecimal crossRate = rate1.getRate().multiply(rate2.getRate(), CALCULATION_CONTEXT);
                    
                    return ExchangeRate.builder()
                        .fromCurrency(fromCurrency)
                        .toCurrency(toCurrency)
                        .rate(crossRate)
                        .timestamp(LocalDateTime.now())
                        .provider("CROSS_CURRENCY")
                        .spread(rate1.getSpread().add(rate2.getSpread()))
                        .confidence(Math.min(rate1.getConfidence(), rate2.getConfidence()))
                        .build();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get cross currency rate", e);
        }
        
        // If cross-currency calculation fails, return a fallback rate
        log.warn("Unable to calculate cross-currency rate for {} to {}, using fallback", fromCurrency, toCurrency);
        return ExchangeRate.builder()
            .fromCurrency(fromCurrency)
            .toCurrency(toCurrency)
            .rate(new BigDecimal("1.00")) // Fallback 1:1 rate
            .timestamp(LocalDateTime.now())
            .provider("FALLBACK")
            .spread(new BigDecimal("0.02")) // 2% spread for fallback
            .confidence(0.1) // Low confidence for fallback rate
            .build();
    }
    
    private ExchangeRate fetchFreshExchangeRate(String fromCurrency, String toCurrency) {
        log.debug("Fetching fresh exchange rate from {} to {}", fromCurrency, toCurrency);
        
        for (ExchangeRateProvider provider : exchangeRateProviders) {
            try {
                ExchangeRate rate = provider.getExchangeRate(fromCurrency, toCurrency);
                if (rate != null) {
                    // Store in database
                    exchangeRateRepository.save(rate);
                    
                    // Cache it
                    String cacheKey = fromCurrency + "_" + toCurrency;
                    exchangeRateCache.put(cacheKey, rate);
                    
                    return rate;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed to get rate from {} to {}", 
                    provider.getClass().getSimpleName(), fromCurrency, toCurrency, e);
            }
        }
        
        throw new BusinessException("Unable to fetch exchange rate from " + fromCurrency + " to " + toCurrency);
    }
    
    private void fetchAndStoreExchangeRate(String fromCurrency, String toCurrency) {
        try {
            ExchangeRate rate = fetchFreshExchangeRate(fromCurrency, toCurrency);
            log.debug("Updated exchange rate: {} {} = {} {}", 
                1, fromCurrency, rate.getRate(), toCurrency);
        } catch (Exception e) {
            log.warn("Failed to fetch rate for {} to {}", fromCurrency, toCurrency, e);
        }
    }
    
    private ConversionFees calculateConversionFees(CurrencyConversionRequest request, BigDecimal convertedAmount) {
        BigDecimal feeRate = BigDecimal.valueOf(0.001); // 0.1% default fee
        
        // Adjust fee based on currency pair
        if (isCryptoCurrency(request.getFromCurrency()) || isCryptoCurrency(request.getToCurrency())) {
            feeRate = BigDecimal.valueOf(0.005); // 0.5% for crypto
        }
        
        BigDecimal conversionFee = convertedAmount.multiply(feeRate, CALCULATION_CONTEXT);
        BigDecimal networkFee = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;
        
        return ConversionFees.builder()
            .conversionFee(conversionFee)
            .networkFee(networkFee)
            .serviceFee(serviceFee)
            .totalFees(conversionFee.add(networkFee).add(serviceFee))
            .feeRate(feeRate)
            .build();
    }
    
    private boolean isCryptoCurrency(String currency) {
        return SUPPORTED_CRYPTO_CURRENCIES.contains(currency);
    }
    
    private int getCurrencyPrecision(String currency) {
        try {
            Currency currencyInfo = self.getCurrencyInfo(currency);
            return currencyInfo.getDecimalPlaces();
        } catch (Exception e) {
            return currencyPrecision; // Default precision
        }
    }
    
    private CurrencyConversionHistory recordConversionHistory(CurrencyConversionRequest request,
                                                             ExchangeRate exchangeRate,
                                                             BigDecimal convertedAmount,
                                                             ConversionFees fees) {
        CurrencyConversionHistory history = CurrencyConversionHistory.builder()
            .userId(request.getUserId())
            .fromCurrency(request.getFromCurrency())
            .toCurrency(request.getToCurrency())
            .originalAmount(request.getAmount())
            .convertedAmount(convertedAmount)
            .exchangeRate(exchangeRate.getRate())
            .fees(fees.getTotalFees())
            .provider(exchangeRate.getProvider())
            .conversionTime(LocalDateTime.now())
            .conversionType(request.getConversionType())
            .build();
        
        return conversionHistoryRepository.save(history);
    }
    
    private void publishConversionEvent(CurrencyConversionResult result) {
        try {
            CurrencyConversionEvent event = CurrencyConversionEvent.builder()
                .conversionId(result.getConversionId())
                .fromCurrency(result.getFromCurrency())
                .toCurrency(result.getToCurrency())
                .amount(result.getOriginalAmount())
                .convertedAmount(result.getConvertedAmount())
                .exchangeRate(result.getExchangeRate())
                .timestamp(System.currentTimeMillis())
                .build();
            
            kafkaTemplate.send("currency-conversion-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish conversion event", e);
        }
    }
    
    private UserCurrencyPreference createDefaultPreferences(String userId) {
        return UserCurrencyPreference.builder()
            .userId(userId)
            .primaryCurrency(defaultBaseCurrency)
            .secondaryCurrencies(Arrays.asList("EUR", "GBP", "JPY"))
            .autoConversion(false)
            .displayFormat("SYMBOL_AMOUNT")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
    
    private BigDecimal calculateVolatility(Map<String, List<ExchangeRate>> rateHistory) {
        // Simplified volatility calculation - would use proper financial formulas in production
        return BigDecimal.valueOf(0.15); // 15% average volatility
    }
    
    private Map<String, String> calculateTrends(Map<String, List<ExchangeRate>> rateHistory) {
        Map<String, String> trends = new HashMap<>();
        
        for (Map.Entry<String, List<ExchangeRate>> entry : rateHistory.entrySet()) {
            List<ExchangeRate> rates = entry.getValue();
            if (rates.size() >= 2) {
                BigDecimal firstRate = rates.get(0).getRate();
                BigDecimal lastRate = rates.get(rates.size() - 1).getRate();
                
                if (lastRate.compareTo(firstRate) > 0) {
                    trends.put(entry.getKey(), "INCREASING");
                } else if (lastRate.compareTo(firstRate) < 0) {
                    trends.put(entry.getKey(), "DECREASING");
                } else {
                    trends.put(entry.getKey(), "STABLE");
                }
            }
        }
        
        return trends;
    }
    
    private BigDecimal getTradingVolume(String currency, LocalDateTime fromDate) {
        // Would integrate with trading data providers
        return BigDecimal.valueOf(1000000); // Mock volume
    }
}