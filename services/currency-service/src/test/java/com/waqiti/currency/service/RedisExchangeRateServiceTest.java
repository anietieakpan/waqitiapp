package com.waqiti.currency.service;

import com.waqiti.currency.domain.ExchangeRate;
import com.waqiti.currency.provider.ExchangeRateProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for Redis-based Exchange Rate Service
 *
 * Tests cover:
 * - Cache hit/miss scenarios
 * - Cache stampede protection (synchronized caching)
 * - Provider failover logic
 * - TTL and cache eviction
 * - Concurrent access patterns
 * - Performance characteristics
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisExchangeRateServiceTest {

    @Autowired
    private RedisExchangeRateService exchangeRateService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ExchangeRateProvider primaryProvider;

    @MockBean
    private ExchangeRateProvider secondaryProvider;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        cacheManager.getCacheNames().forEach(cacheName ->
            cacheManager.getCache(cacheName).clear());

        meterRegistry = new SimpleMeterRegistry();

        // Setup mock providers
        when(primaryProvider.isAvailable()).thenReturn(true);
        when(primaryProvider.getPriority()).thenReturn(1);
        when(primaryProvider.getProviderName()).thenReturn("OpenExchangeRates");
        when(primaryProvider.getConfidenceScore()).thenReturn(95);

        when(secondaryProvider.isAvailable()).thenReturn(true);
        when(secondaryProvider.getPriority()).thenReturn(2);
        when(secondaryProvider.getProviderName()).thenReturn("CurrencyLayer");
        when(secondaryProvider.getConfidenceScore()).thenReturn(90);
    }

    @Test
    @DisplayName("Should cache exchange rate on first fetch and return from cache on subsequent calls")
    void testCacheHitAfterFirstFetch() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        BigDecimal expectedRate = new BigDecimal("0.85");

        ExchangeRate mockRate = ExchangeRate.builder()
            .rate(expectedRate)
            .timestamp(LocalDateTime.now())
            .build();

        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenReturn(mockRate);

        String correlationId = UUID.randomUUID().toString();

        // When - First call (cache miss)
        CurrencyConversionService.ExchangeRateResult result1 =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);

        // Then - Should fetch from provider
        assertThat(result1.isAvailable()).isTrue();
        assertThat(result1.getRate()).isEqualByComparingTo(expectedRate);
        assertThat(result1.getProvider()).isEqualTo("OpenExchangeRates");
        verify(primaryProvider, times(1)).getExchangeRate(sourceCurrency, targetCurrency);

        // When - Second call (should hit cache)
        CurrencyConversionService.ExchangeRateResult result2 =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);

        // Then - Should return same rate WITHOUT calling provider again
        assertThat(result2.isAvailable()).isTrue();
        assertThat(result2.getRate()).isEqualByComparingTo(expectedRate);
        verify(primaryProvider, times(1)).getExchangeRate(sourceCurrency, targetCurrency); // Still only 1 call
    }

    @Test
    @DisplayName("Should prevent cache stampede - only one thread fetches on cache miss")
    void testCacheStampedeProtection() throws InterruptedException {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "GBP";
        BigDecimal expectedRate = new BigDecimal("0.73");

        ExchangeRate mockRate = ExchangeRate.builder()
            .rate(expectedRate)
            .timestamp(LocalDateTime.now())
            .build();

        // Simulate slow provider (500ms delay)
        AtomicInteger providerCallCount = new AtomicInteger(0);
        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenAnswer(invocation -> {
                providerCallCount.incrementAndGet();
                Thread.sleep(500); // Simulate API latency
                return mockRate;
            });

        // When - Simulate 100 concurrent requests (cache stampede scenario)
        int concurrentRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        List<Future<CurrencyConversionService.ExchangeRateResult>> futures =
            new CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            futures.add(executor.submit(() -> {
                try {
                    String correlationId = UUID.randomUUID().toString();
                    return exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then - Provider should be called ONLY ONCE (stampede protection working)
        assertThat(providerCallCount.get()).isEqualTo(1);

        // All requests should get the same result
        for (Future<CurrencyConversionService.ExchangeRateResult> future : futures) {
            CurrencyConversionService.ExchangeRateResult result = future.get();
            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getRate()).isEqualByComparingTo(expectedRate);
        }
    }

    @Test
    @DisplayName("Should failover to secondary provider when primary fails")
    void testProviderFailover() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "JPY";
        BigDecimal expectedRate = new BigDecimal("110.50");

        // Primary provider fails
        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenThrow(new RuntimeException("Provider timeout"));

        // Secondary provider succeeds
        ExchangeRate mockRate = ExchangeRate.builder()
            .rate(expectedRate)
            .timestamp(LocalDateTime.now())
            .build();
        when(secondaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenReturn(mockRate);

        String correlationId = UUID.randomUUID().toString();

        // When
        CurrencyConversionService.ExchangeRateResult result =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);

        // Then - Should use secondary provider
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getRate()).isEqualByComparingTo(expectedRate);
        assertThat(result.getProvider()).isEqualTo("CurrencyLayer");

        verify(primaryProvider, times(1)).getExchangeRate(sourceCurrency, targetCurrency);
        verify(secondaryProvider, times(1)).getExchangeRate(sourceCurrency, targetCurrency);
    }

    @Test
    @DisplayName("Should return unavailable when all providers fail")
    void testAllProvidersFail() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "CHF";

        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenThrow(new RuntimeException("Provider 1 failed"));
        when(secondaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenThrow(new RuntimeException("Provider 2 failed"));

        String correlationId = UUID.randomUUID().toString();

        // When
        CurrencyConversionService.ExchangeRateResult result =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);

        // Then
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getReason()).contains("providers failed");
    }

    @Test
    @DisplayName("Should not cache failed results")
    void testFailedResultsNotCached() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "AUD";

        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenThrow(new RuntimeException("Provider error"));
        when(secondaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenThrow(new RuntimeException("Provider error"));

        String correlationId = UUID.randomUUID().toString();

        // When - First call fails
        CurrencyConversionService.ExchangeRateResult result1 =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);

        assertThat(result1.isAvailable()).isFalse();

        // When - Second call should retry providers (failure NOT cached)
        CurrencyConversionService.ExchangeRateResult result2 =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);

        assertThat(result2.isAvailable()).isFalse();

        // Then - Providers should be called twice (once per request)
        verify(primaryProvider, times(2)).getExchangeRate(sourceCurrency, targetCurrency);
    }

    @Test
    @DisplayName("Should evict cache and re-fetch on manual refresh")
    void testManualCacheRefresh() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "CAD";
        BigDecimal oldRate = new BigDecimal("1.25");
        BigDecimal newRate = new BigDecimal("1.27");

        ExchangeRate oldMockRate = ExchangeRate.builder()
            .rate(oldRate)
            .timestamp(LocalDateTime.now())
            .build();
        ExchangeRate newMockRate = ExchangeRate.builder()
            .rate(newRate)
            .timestamp(LocalDateTime.now())
            .build();

        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenReturn(oldMockRate, newMockRate);

        String correlationId = UUID.randomUUID().toString();

        // When - First fetch (caches old rate)
        CurrencyConversionService.ExchangeRateResult result1 =
            exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);
        assertThat(result1.getRate()).isEqualByComparingTo(oldRate);

        // When - Refresh (evicts cache and re-fetches)
        CurrencyConversionService.ExchangeRateResult result2 =
            exchangeRateService.refreshRate(sourceCurrency, targetCurrency, correlationId);

        // Then - Should return new rate
        assertThat(result2.getRate()).isEqualByComparingTo(newRate);
        verify(primaryProvider, times(2)).getExchangeRate(sourceCurrency, targetCurrency);
    }

    @Test
    @DisplayName("Should handle high concurrency without errors")
    void testHighConcurrency() throws InterruptedException, ExecutionException {
        // Given
        String sourceCurrency = "USD";
        BigDecimal expectedRate = new BigDecimal("0.85");

        ExchangeRate mockRate = ExchangeRate.builder()
            .rate(expectedRate)
            .timestamp(LocalDateTime.now())
            .build();

        when(primaryProvider.getExchangeRate(anyString(), anyString()))
            .thenReturn(mockRate);

        // When - 1000 concurrent requests for different currency pairs
        int concurrentRequests = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        List<Future<CurrencyConversionService.ExchangeRateResult>> futures =
            new CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                String targetCurrency = "EUR" + (index % 20); // 20 different pairs
                String correlationId = UUID.randomUUID().toString();
                return exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);
            }));
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Then - All requests should succeed
        int successCount = 0;
        for (Future<CurrencyConversionService.ExchangeRateResult> future : futures) {
            CurrencyConversionService.ExchangeRateResult result = future.get();
            if (result.isAvailable()) {
                successCount++;
            }
        }

        assertThat(successCount).isEqualTo(concurrentRequests);
    }

    @Test
    @DisplayName("Should return correct provider info with caching enabled")
    void testProviderInfoCaching() {
        // When
        RedisExchangeRateService.ProviderInfo info1 =
            exchangeRateService.getProviderInfo();
        RedisExchangeRateService.ProviderInfo info2 =
            exchangeRateService.getProviderInfo();

        // Then
        assertThat(info1.getTotalProviders()).isEqualTo(2);
        assertThat(info1.getAvailableProviders()).contains("OpenExchangeRates", "CurrencyLayer");
        assertThat(info1.isCacheEnabled()).isTrue();
        assertThat(info1.getCacheTtlMinutes()).isEqualTo(15);
        assertThat(info1.isStampedeProtection()).isTrue();

        // Second call should return same object (cached)
        assertThat(info1).isEqualTo(info2);
    }

    @Test
    @DisplayName("Performance: Cache hit should be <10ms, cache miss <1000ms")
    void testPerformanceCharacteristics() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        BigDecimal expectedRate = new BigDecimal("0.85");

        ExchangeRate mockRate = ExchangeRate.builder()
            .rate(expectedRate)
            .timestamp(LocalDateTime.now())
            .build();

        when(primaryProvider.getExchangeRate(sourceCurrency, targetCurrency))
            .thenAnswer(invocation -> {
                Thread.sleep(500); // Simulate 500ms API latency
                return mockRate;
            });

        String correlationId = UUID.randomUUID().toString();

        // When - First call (cache miss)
        long startMiss = System.currentTimeMillis();
        exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);
        long missDuration = System.currentTimeMillis() - startMiss;

        // Then - Cache miss should take ~500ms (API call)
        assertThat(missDuration).isGreaterThan(400).isLessThan(1000);

        // When - Second call (cache hit)
        long startHit = System.currentTimeMillis();
        exchangeRateService.getCurrentRate(sourceCurrency, targetCurrency, correlationId);
        long hitDuration = System.currentTimeMillis() - startHit;

        // Then - Cache hit should be <10ms
        assertThat(hitDuration).isLessThan(10);
    }
}
